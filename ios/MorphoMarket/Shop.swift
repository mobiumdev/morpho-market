import Foundation
import Observation
import os

// The bug switch (SPEC.md, "The bug switch"), the store's state, and
// checkout's rules: the same as the Android app's Bugs.kt, Shop.kt and
// Checkout.kt, written once more for SwiftUI.

struct Bug: Identifiable {
    let id: String
    let what: String
}

let bugs: [Bug] = [
    Bug(id: "tax-before-discount", what: "Tax is charged on merchandise before the code."),
    Bug(id: "free-shipping-before-discount", what: "The $75 free-shipping line is judged before the code."),
    Bug(id: "free-shipping-over", what: "$75.00 exactly does not ship free."),
    Bug(id: "round-down", what: "Percentages and tax are truncated, not rounded."),
    Bug(id: "coupon-twice", what: "The same code can be applied twice."),
    Bug(id: "expired-accepted", what: "EXPIRED20 gives 20% off."),
    Bug(id: "express-free", what: "SHIPFREE frees express too."),
    Bug(id: "xxl-no-surcharge", what: "The XXL tee costs the same as the others."),
    Bug(id: "out-of-stock-addable", what: "Out-of-stock items can be added to the cart."),
    Bug(id: "stale-badge", what: "The cart badge does not drop when items are removed."),
    Bug(id: "qty-eleven", what: "The quantity stepper goes to 11."),
    Bug(id: "declined-succeeds", what: "The declined card places an order."),
    Bug(id: "double-order", what: "Place order can be tapped twice while placing."),
    Bug(id: "sort-as-text", what: "Sorting by price compares prices as text."),
    Bug(id: "slow-catalog", what: "The catalog appears after 8 seconds."),
    Bug(id: "lost-delivery", what: "Going back from payment resets delivery to standard."),
]
let bugIDs = Set(bugs.map(\.id))

/// A line in the cart. A gift card keeps its recipient, which pricing never reads.
struct CartLine: Codable, Hashable {
    let id: String
    let variant: String?
    var qty: Int
    var email: String?

    var key: String { variant.map { "\(id)_\($0)" } ?? id }
    var label: String {
        let p = product(id)
        guard let v = p.variants.first(where: { $0.id == variant }) else { return p.name }
        return "\(p.name) (\(v.label))"
    }
    var line: Line { Line(id: id, variant: variant, qty: qty) }
}

struct Address: Codable, Hashable {
    var name = "", street = "", city = "", state = "", zip = "", email = ""
}

struct Order: Codable, Hashable, Identifiable {
    let number: String
    let lines: [CartLine]
    let method: Method?
    let address: Address
    let code: String?
    let merch, discount, shipping, tax, gift, total: Int
    var id: String { number }
}

struct CodeMessage: Equatable {
    let text: String
    let error: Bool
}

/// What is persisted: everything a launch should find as the last one left it.
private struct Saved: Codable {
    var cart: [CartLine] = []
    var code: String?
    var codeTimes = 1
    var bugs: Set<String> = []
    var spent: Set<String> = []
    var badge = 0
    var orders: [Order] = []
    var orderCounter = 0
}

/// The store's state. Everything here persists across launches, so a test
/// starts from `mobiumshop://reset` rather than from whatever the last one left.
@Observable
final class Shop {
    private(set) var cart: [CartLine] = []
    private(set) var code: String?
    private(set) var codeTimes = 1
    var codeMessage: CodeMessage?
    private(set) var bugs: Set<String> = []
    private(set) var spent: Set<String> = []
    private(set) var badge = 0
    private(set) var orders: [Order] = []
    private var orderCounter = 0
    /// The slow-catalog bug delays the catalog's first showing after the store
    /// is made or reset, so every test that starts from a reset meets it.
    var catalogShown = false
    /// Counts resets, so a catalog already on screen loads again after one.
    private(set) var resets = 0
    /// This process, shown on the bug screen and logged, to tell a relaunch from a reset.
    @ObservationIgnored let pid = ProcessInfo.processInfo.processIdentifier
    @ObservationIgnored private let log = Logger(subsystem: "dev.mobium.shop", category: "process")

    @ObservationIgnored private let defaults: UserDefaults
    private static let key = "shop"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.key), let s = try? JSONDecoder().decode(Saved.self, from: data) {
            cart = s.cart; code = s.code; codeTimes = s.codeTimes; bugs = s.bugs; spent = s.spent
            badge = s.badge; orders = s.orders; orderCounter = s.orderCounter
        }
        log.notice("process \(self.pid, privacy: .public): store made")
    }

    func bug(_ id: String) -> Bool { bugs.contains(id) }
    var activePricingBugs: Set<String> { bugs.intersection(pricingBugs) }
    var maxQty: Int { bug("qty-eleven") ? 11 : 10 }
    var itemCount: Int { cart.reduce(0) { $0 + $1.qty } }
    var lines: [Line] { cart.map(\.line) }
    var hasPhysical: Bool { cart.contains { !product($0.id).digital } }

    func add(_ id: String, variant: String?, qty: Int, email: String? = nil) {
        if let i = cart.firstIndex(where: { $0.id == id && $0.variant == variant }) {
            cart[i].qty = min(cart[i].qty + qty, maxQty)
            if let email { cart[i].email = email }
        } else {
            cart.append(CartLine(id: id, variant: variant, qty: min(qty, maxQty), email: email))
        }
        changed()
    }

    func setQty(_ key: String, _ qty: Int) {
        guard let i = cart.firstIndex(where: { $0.key == key }) else { return }
        if qty <= 0 { cart.remove(at: i) } else { cart[i].qty = min(qty, maxQty) }
        changed()
    }

    func remove(_ key: String) { setQty(key, 0) }

    func applyCode(_ raw: String) {
        let c = normalizeCode(raw)
        if c.isEmpty {
            codeMessage = CodeMessage(text: "Enter a code.", error: true)
        } else if code == c && bug("coupon-twice") && codeTimes < 2 {
            codeTimes = 2
            codeMessage = CodeMessage(text: appliedMessage(c), error: false)
        } else if code == c {
            codeMessage = CodeMessage(text: "\(c) is already applied.", error: true)
        } else if let applied = code {
            codeMessage = CodeMessage(text: "Remove \(applied) to use another code.", error: true)
        } else if spent.contains(c) {
            codeMessage = CodeMessage(text: "That code has already been used.", error: true)
        } else {
            switch price(lines, code: c, bugs: activePricingBugs) {
            case let .codeRefused(m): codeMessage = CodeMessage(text: m, error: true)
            case .order:
                code = c
                codeTimes = 1
                codeMessage = CodeMessage(text: appliedMessage(c), error: false)
            }
        }
        save()
    }

    func removeCode() {
        code = nil
        codeTimes = 1
        codeMessage = nil
        save()
    }

    private func appliedMessage(_ c: String) -> String {
        switch c {
        case "WELCOME10": "WELCOME10 applied: 10% off merchandise."
        case "MOBIUM5": "MOBIUM5 applied: $5.00 off."
        case "SHIPFREE": "SHIPFREE applied: free standard shipping."
        case "EXPIRED20": "EXPIRED20 applied: 20% off merchandise."
        default: "\(c) applied."
        }
    }

    /// The cart moved: a code that no longer qualifies comes off, saying why; the badge follows.
    private func changed() {
        if let c = code, case let .codeRefused(m) = price(lines, code: c, bugs: activePricingBugs) {
            code = nil
            codeTimes = 1
            codeMessage = CodeMessage(text: m, error: true)
        }
        let n = itemCount
        if !(bug("stale-badge") && n < badge) { badge = n }
        save()
    }

    /// Places an order for `lines` as they were when Place order was tapped,
    /// which is what lets the double-order bug place the same cart twice.
    @discardableResult
    func place(_ lines: [CartLine], code codeAtTap: String?, times: Int, method: Method, address: Address) -> Order {
        let physical = lines.contains { !product($0.id).digital }
        guard case let .order(o) = price(lines.map(\.line), code: codeAtTap, method: method,
                                         state: address.state.isEmpty ? "CA" : address.state,
                                         bugs: activePricingBugs, times: times) else {
            fatalError("an order whose code was refused cannot be placed")
        }
        orderCounter += 1
        let order = Order(number: String(format: "MS-%06d", orderCounter), lines: lines,
                          method: physical ? method : nil, address: address, code: codeAtTap,
                          merch: o.merch, discount: o.discount, shipping: o.shipping, tax: o.tax, gift: o.gift,
                          total: o.total)
        orders.insert(order, at: 0)
        if codeAtTap == "WELCOME10" { spent.insert("WELCOME10") }
        cart = []
        code = nil
        codeTimes = 1
        codeMessage = nil
        changed()
        return order
    }

    func setBugs(on: Set<String>, off: Set<String>) {
        bugs = bugs.subtracting(off).union(on)
        save()
    }

    /// Cart, code, orders and spent codes; the bugs stay as they are.
    func reset() {
        cart = []
        code = nil
        codeTimes = 1
        codeMessage = nil
        spent = []
        badge = 0
        orders = []
        orderCounter = 0
        catalogShown = false
        resets += 1
        save()
        log.notice("process \(self.pid, privacy: .public): reset")
    }

    private func save() {
        let s = Saved(cart: cart, code: code, codeTimes: codeTimes, bugs: bugs, spent: spent, badge: badge,
                      orders: orders, orderCounter: orderCounter)
        if let data = try? JSONEncoder().encode(s) { defaults.set(data, forKey: Self.key) }
    }
}

// MARK: Checkout's rules

let emailPattern = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/// Each field that fails, with what to do about it; empty when the address will do.
func addressErrors(_ a: Address, method: Method?) -> [String: String] {
    var e: [String: String] = [:]
    if a.name.trimmingCharacters(in: .whitespaces).isEmpty { e["name"] = "Enter your name." }
    if method == .standard || method == .express {
        if a.street.trimmingCharacters(in: .whitespaces).isEmpty { e["street"] = "Enter a street address." }
        if a.city.trimmingCharacters(in: .whitespaces).isEmpty { e["city"] = "Enter a city." }
        if tax[a.state] == nil { e["state"] = "Choose a state." }
        if a.zip.trimmingCharacters(in: .whitespaces).wholeMatch(of: /\d{5}/) == nil { e["zip"] = "Enter a 5-digit ZIP code." }
    }
    if a.email.trimmingCharacters(in: .whitespaces).wholeMatch(of: emailPattern) == nil {
        e["email"] = "Enter a valid email address."
    }
    return e
}

func digitsOf(_ s: String) -> String { s.filter(\.isNumber) }

func luhn(_ digits: String) -> Bool {
    guard !digits.isEmpty else { return false }
    var sum = 0
    for (i, ch) in digits.reversed().enumerated() {
        var d = ch.wholeNumberValue ?? 0
        if i % 2 == 1 {
            d *= 2
            if d > 9 { d -= 9 }
        }
        sum += d
    }
    return sum % 10 == 0
}

struct Card: Hashable {
    var number = "", expiry = "", cvc = "", name = ""
}

func cardErrors(_ c: Card, now: Date = .now) -> [String: String] {
    var e: [String: String] = [:]
    let n = digitsOf(c.number)
    if n.count != 16 || !luhn(n) { e["cardNumber"] = "Enter a valid card number." }
    let cal = Calendar(identifier: .gregorian)
    let thisMonth = cal.component(.year, from: now) * 12 + cal.component(.month, from: now)
    if let m = c.expiry.trimmingCharacters(in: .whitespaces).wholeMatch(of: /(\d{2})\/(\d{2})/),
       let month = Int(m.1), let year = Int(m.2), (1...12).contains(month), (2000 + year) * 12 + month >= thisMonth {
        // a month not yet past
    } else {
        e["cardExpiry"] = "Enter an expiry date that hasn't passed."
    }
    if c.cvc.wholeMatch(of: /\d{3}/) == nil { e["cardCvc"] = "Enter the 3-digit security code." }
    if c.name.trimmingCharacters(in: .whitespaces).isEmpty { e["cardName"] = "Enter the name on the card." }
    return e
}

/// What a test card does: nil approves, else the message it declines with.
func cardOutcome(_ number: String, bugs: Set<String>) -> String? {
    switch digitsOf(number) {
    case "4000000000000002": bugs.contains("declined-succeeds") ? nil : "Your card was declined."
    case "4000000000009995": "Insufficient funds."
    default: nil
    }
}

/// How long placing takes: the slow test card takes six seconds.
func placingSeconds(_ number: String) -> Double { digitsOf(number) == "4000000000000119" ? 6 : 1.5 }

func groupCard(_ digits: String) -> String {
    stride(from: 0, to: digits.count, by: 4).map { i -> String in
        let start = digits.index(digits.startIndex, offsetBy: i)
        let end = digits.index(start, offsetBy: min(4, digits.count - i))
        return String(digits[start..<end])
    }.joined(separator: " ")
}

func slashExpiry(_ digits: String) -> String {
    digits.count <= 2 ? digits : String(digits.prefix(2)) + "/" + String(digits.dropFirst(2))
}

/// The checkout in progress: kept while the person moves back and forth, dropped once placed.
@Observable
final class CheckoutState {
    var method: Method = .standard
    var address = Address()
    var card = Card()
    var placing = 0
    var paymentError: String?

    func clear() {
        method = .standard
        address = Address()
        card = Card()
        placing = 0
        paymentError = nil
    }
}
