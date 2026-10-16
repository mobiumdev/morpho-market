import Foundation

// The pricing rules, line for line with spec/pricing.py and the Android
// app's Pricing.kt: integer cents and grams, nothing a float. PricingTests
// runs spec/pricing-cases.json against it.

struct Line: Hashable {
    let id: String
    var variant: String? = nil
    let qty: Int
}

enum Method: String, CaseIterable, Codable {
    case standard, express, pickup
}

/// Demo rates in thousandths of a percent, and whether shipping is taxed.
struct TaxRate {
    let rate: Int
    let shippingTaxed: Bool
}

let taxStates = ["CA", "NY", "OR", "TX", "WA"]
let tax: [String: TaxRate] = [
    "CA": TaxRate(rate: 7250, shippingTaxed: false),
    "NY": TaxRate(rate: 8875, shippingTaxed: true),
    "OR": TaxRate(rate: 0, shippingTaxed: false),
    "TX": TaxRate(rate: 6250, shippingTaxed: true),
    "WA": TaxRate(rate: 6500, shippingTaxed: true),
]
let pickupState = "CA"
let freeShippingAt = 7500
private let standardRate = (base: 599, per: 150)
private let expressRate = (base: 1499, per: 250)

private enum Code {
    case percent(Int)
    case off(cents: Int, min: Int)
    case freeStandard
    case expired
}

private let codes: [String: Code] = [
    "WELCOME10": .percent(10),
    "MOBIUM5": .off(cents: 500, min: 3000),
    "SHIPFREE": .freeStandard,
    "EXPIRED20": .expired,
]

/// The bugs that live in the arithmetic; the rest of `bugs` live in screens.
let pricingBugs: Set<String> = [
    "tax-before-discount", "free-shipping-before-discount", "free-shipping-over",
    "round-down", "expired-accepted", "express-free", "xxl-no-surcharge",
]

struct PricedOrder: Equatable {
    let merch, discount, shipping, tax, gift, total, grams: Int
}

enum Priced: Equatable {
    case order(PricedOrder)
    case codeRefused(String)
}

func normalizeCode(_ raw: String) -> String {
    raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
}

func unitPrice(_ line: Line, bugs: Set<String> = []) -> Int {
    if line.id == "giftcard" { return giftAmounts[line.variant!]! }
    let p = product(line.id)
    if bugs.contains("xxl-no-surcharge") { return p.cents }
    return p.cents + (surcharge[SurchargeKey(id: line.id, variant: line.variant ?? "")] ?? 0)
}

func halfUp(_ numerator: Int, _ denominator: Int) -> Int { (numerator * 2 + denominator) / (denominator * 2) }

func shippingCost(_ method: Method, grams: Int) -> Int {
    if method == .pickup { return 0 }
    let r = method == .standard ? standardRate : expressRate
    let over = max(0, grams - 1000)
    return r.base + r.per * ((over + 499) / 500)
}

/// Prices an order, or refuses its code. `times` is how often the code was
/// applied: 1, unless the coupon-twice bug let it be applied again. Without
/// a physical item there is no shipping, whatever `method` says.
func price(_ cart: [Line], code: String? = nil, method: Method = .standard, state: String = "CA",
           bugs: Set<String> = [], times: Int = 1) -> Priced {
    let rnd: (Int, Int) -> Int = bugs.contains("round-down") ? { $0 / $1 } : halfUp
    let merch = cart.filter { !product($0.id).digital }.reduce(0) { $0 + unitPrice($1, bugs: bugs) * $1.qty }
    let gift = cart.filter { product($0.id).digital }.reduce(0) { $0 + unitPrice($1, bugs: bugs) * $1.qty }
    let grams = cart.reduce(0) { $0 + product($1.id).grams * $1.qty }
    let physical = merch > 0

    var discount = 0
    var freeStandard = false
    if let code {
        guard var c = codes[normalizeCode(code)] else { return .codeRefused("That code isn't valid.") }
        if case .expired = c, bugs.contains("expired-accepted") { c = .percent(20) }
        if case .expired = c { return .codeRefused("That code has expired.") }
        if !physical { return .codeRefused("Codes don't apply to gift cards.") }
        if case let .off(_, min) = c, merch < min {
            return .codeRefused("Add \(money(min - merch)) more to use this code.")
        }
        switch c {
        case let .percent(p): discount = rnd(merch * p, 100)
        case let .off(cents, _): discount = Swift.min(cents, merch)
        case .freeStandard: freeStandard = true
        case .expired: break
        }
        discount = Swift.min(discount * times, merch)
    }

    let goods = merch - discount
    var shipping = 0
    var effective: Method? = nil
    if physical {
        effective = method
        shipping = shippingCost(method, grams: grams)
        let judged = bugs.contains("free-shipping-before-discount") ? merch : goods
        let overLine = bugs.contains("free-shipping-over") ? judged > freeShippingAt : judged >= freeShippingAt
        if method == .standard && (overLine || freeStandard) { shipping = 0 }
        if method == .express && freeStandard && bugs.contains("express-free") { shipping = 0 }
    }
    let rate = tax[effective == .pickup ? pickupState : state]!
    let base = (bugs.contains("tax-before-discount") ? merch : goods) + (rate.shippingTaxed ? shipping : 0)
    let taxCents = rnd(base * rate.rate, 100000)
    return .order(PricedOrder(merch: merch, discount: discount, shipping: shipping, tax: taxCents, gift: gift,
                              total: goods + shipping + taxCents + gift, grams: grams))
}

/// "$1,234.56"; a negative amount is "-$6.40".
func money(_ cents: Int) -> String {
    let sign = cents < 0 ? "-" : ""
    let c = abs(cents)
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.locale = Locale(identifier: "en_US")
    let dollars = f.string(from: NSNumber(value: c / 100)) ?? "\(c / 100)"
    return "\(sign)$\(dollars).\(String(format: "%02d", c % 100))"
}
