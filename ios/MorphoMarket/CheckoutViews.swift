import SwiftUI

// Checkout's screens, with the Android app's ids.

private func methodLabel(_ m: Method?) -> String {
    switch m {
    case .standard: "Standard, 3–5 days"
    case .express: "Express, 1–2 days"
    case .pickup: "Pickup: San Francisco store"
    case nil: "Delivered by email"
    }
}

/// The summary rows, each value with its id, as on every screen that shows the money.
struct OrderSummary: View {
    let o: PricedOrder
    let code: String?
    let taxKnown: Bool
    let totalLabel: String

    var body: some View {
        VStack(spacing: 4) {
            Divider()
            SummaryRow(label: "Merchandise", value: money(o.merch), id: "sumMerch")
            if o.discount > 0 { SummaryRow(label: "Discount (\(code ?? ""))", value: money(-o.discount), id: "sumDiscount") }
            if o.gift > 0 { SummaryRow(label: "Gift cards", value: money(o.gift), id: "sumGift") }
            if o.merch > 0 {
                SummaryRow(label: "Shipping", value: o.shipping == 0 ? "Free" : money(o.shipping), id: "sumShipping")
            }
            SummaryRow(label: "Tax", value: taxKnown ? money(o.tax) : "Calculated with your address", id: "sumTax")
            SummaryRow(label: totalLabel, value: money(taxKnown ? o.total : o.total - o.tax), id: "sumTotal", bold: true)
        }
    }
}

struct DeliveryView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(CheckoutState.self) private var checkout

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("How should it reach you?").font(.headline)
                ForEach(Method.allCases, id: \.self) { m in
                    let cost: Int = {
                        if case let .order(o) = price(shop.lines, code: shop.code, method: m, bugs: shop.activePricingBugs,
                                                      times: shop.codeTimes) { return o.shipping }
                        return 0
                    }()
                    let selected = checkout.method == m
                    HStack {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                        Text(methodLabel(m))
                        Spacer()
                        Text(cost == 0 ? "Free" : money(cost)).accessibilityIdentifier("shipPrice_\(m.rawValue)")
                    }
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                    .onTapGesture { checkout.method = m }
                    .accessibilityElement(children: .contain)
                    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    .accessibilityIdentifier("ship_\(m.rawValue)")
                }
                if case let .order(o) = price(shop.lines, code: shop.code, method: checkout.method, bugs: shop.activePricingBugs,
                                              times: shop.codeTimes) {
                    OrderSummary(o: o, code: shop.code, taxKnown: false, totalLabel: "Before tax")
                }
                Button { router.push(.address) } label: { Text("Continue").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).accessibilityIdentifier("continueDelivery")
            }
            .padding()
        }
    }
}

private struct Field: View {
    let id: String
    let label: String
    @Binding var text: String
    let error: String?
    var keyboard: UIKeyboardType = .default
    var secret = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Group {
                if secret {
                    SecureField(label, text: $text)
                } else {
                    TextField(label, text: $text)
                }
            }
            .roundedField()
            .keyboardType(keyboard)
            .autocorrectionDisabled()
            .accessibilityIdentifier("field_\(id)")
            if let error { Text(error).font(.caption).foregroundStyle(.red).accessibilityIdentifier("error_\(id)") }
        }
    }
}

struct AddressView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(CheckoutState.self) private var checkout
    @State private var errors: [String: String] = [:]

    var body: some View {
        @Bindable var checkout = checkout
        let method: Method? = shop.hasPhysical ? checkout.method : nil
        let full = method == .standard || method == .express
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(full ? "Where should we send it?" : "Who is it for?").font(.headline)
                Field(id: "name", label: "Full name", text: $checkout.address.name, error: errors["name"])
                if full {
                    Field(id: "street", label: "Street address", text: $checkout.address.street, error: errors["street"])
                    Field(id: "city", label: "City", text: $checkout.address.city, error: errors["city"])
                    Menu {
                        ForEach(taxStates, id: \.self) { st in
                            Button(st) { checkout.address.state = st }.accessibilityIdentifier("state_\(st)")
                        }
                    } label: {
                        Text(checkout.address.state.isEmpty ? "Choose a state" : "State: \(checkout.address.state)")
                    }
                    .accessibilityIdentifier("statePicker")
                    if let e = errors["state"] { Text(e).font(.caption).foregroundStyle(.red).accessibilityIdentifier("error_state") }
                    Text("We ship to CA, NY, OR, TX and WA.").font(.caption)
                    Field(id: "zip", label: "ZIP code", text: $checkout.address.zip, error: errors["zip"], keyboard: .numberPad)
                        .onChange(of: checkout.address.zip) { _, v in
                            let d = String(digitsOf(v).prefix(5))
                            if d != v { checkout.address.zip = d }
                        }
                }
                Field(id: "email", label: "Email for the receipt", text: $checkout.address.email, error: errors["email"],
                      keyboard: .emailAddress)
                Button {
                    errors = addressErrors(checkout.address, method: method)
                    if errors.isEmpty { router.push(.payment) }
                } label: { Text("Continue").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).accessibilityIdentifier("continueBtn")
                    .padding(.top, 8)
            }
            .padding()
        }
        .accessibilityIdentifier("addressForm")
    }
}

struct PaymentView: View {
    @Environment(Router.self) private var router
    @Environment(CheckoutState.self) private var checkout
    @State private var errors: [String: String] = [:]

    var body: some View {
        @Bindable var checkout = checkout
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Test cards only: nothing is charged.").font(.caption)
                Field(id: "cardNumber", label: "Card number", text: $checkout.card.number, error: errors["cardNumber"], keyboard: .numberPad)
                    .onChange(of: checkout.card.number) { _, v in
                        let f = groupCard(String(digitsOf(v).prefix(16)))
                        if f != v { checkout.card.number = f }
                    }
                Field(id: "cardExpiry", label: "Expiry (MM/YY)", text: $checkout.card.expiry, error: errors["cardExpiry"], keyboard: .numberPad)
                    .onChange(of: checkout.card.expiry) { _, v in
                        let f = slashExpiry(String(digitsOf(v).prefix(4)))
                        if f != v { checkout.card.expiry = f }
                    }
                Field(id: "cardCvc", label: "Security code", text: $checkout.card.cvc, error: errors["cardCvc"],
                      keyboard: .numberPad, secret: true)
                Field(id: "cardName", label: "Name on card", text: $checkout.card.name, error: errors["cardName"])
                Button {
                    errors = cardErrors(checkout.card)
                    if errors.isEmpty { router.push(.review) }
                } label: { Text("Review order").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).accessibilityIdentifier("continuePayment")
            }
            .padding()
        }
    }
}

struct ReviewView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(CheckoutState.self) private var checkout

    var body: some View {
        let method: Method? = shop.hasPhysical ? checkout.method : nil
        let a = checkout.address
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(shop.cart, id: \.key) { l in
                    HStack {
                        Text("\(l.label) × \(l.qty)")
                        Spacer()
                        Text(money(unitPrice(l.line, bugs: shop.activePricingBugs) * l.qty))
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("reviewLine_\(l.key)")
                }
                Text(methodLabel(method)).fontWeight(.semibold).accessibilityIdentifier("reviewDelivery")
                Text(method == .standard || method == .express ? "\(a.name), \(a.street), \(a.city), \(a.state) \(a.zip)" : a.name)
                    .accessibilityIdentifier("reviewAddress")
                Text("Receipt to \(a.email)").font(.caption)
                Text("Card ending \(String(digitsOf(checkout.card.number).suffix(4)))").accessibilityIdentifier("reviewCard")
                if case let .order(o) = price(shop.lines, code: shop.code, method: checkout.method,
                                              state: a.state.isEmpty ? "CA" : a.state, bugs: shop.activePricingBugs,
                                              times: shop.codeTimes) {
                    OrderSummary(o: o, code: shop.code, taxKnown: true, totalLabel: "Total")
                }
                Text("Tax rates here are demo rates.").font(.caption).accessibilityIdentifier("taxNote")
                if let e = checkout.paymentError {
                    Text(e).foregroundStyle(.red).accessibilityIdentifier("paymentError")
                }
                Button {
                    // What is placed is the cart as it is at the tap.
                    let lines = shop.cart, code = shop.code, times = shop.codeTimes
                    let m = checkout.method, address = checkout.address, card = checkout.card.number
                    checkout.paymentError = nil
                    checkout.placing += 1
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(placingSeconds(card)))
                        checkout.placing -= 1
                        if let declined = cardOutcome(card, bugs: shop.bugs) {
                            checkout.paymentError = declined
                        } else {
                            let order = shop.place(lines, code: code, times: times, method: m, address: address)
                            checkout.clear()
                            router.cart = []
                            router.tab = .shop
                            router.shop = [.confirmation(order.number)]
                        }
                    }
                } label: { Text("Place order").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .disabled(checkout.placing > 0 && !shop.bug("double-order"))
                    .accessibilityIdentifier("placeOrder")
                if checkout.placing > 0 {
                    HStack {
                        ProgressView().accessibilityIdentifier("placingOrder")
                        Text("Placing your order…")
                    }
                }
            }
            .padding()
        }
    }
}

struct ConfirmationView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    let number: String

    var body: some View {
        if let o = shop.orders.first(where: { $0.number == number }) {
            VStack(spacing: 12) {
                Butterfly(color: morpho).frame(width: 120, height: 120)
                Text("Order \(o.number) placed").font(.title2).accessibilityIdentifier("orderNumber")
                Text(money(o.total)).font(.title3).accessibilityIdentifier("confirmTotal")
                Text("A receipt is on its way to \(o.address.email).")
                Button("Keep shopping") { router.go(.shop) }.buttonStyle(.borderedProminent).accessibilityIdentifier("keepShopping")
            }
            .padding()
        }
    }
}

struct OrdersView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router

    var body: some View {
        Group {
            if shop.orders.isEmpty {
                Text("No orders yet").font(.headline).accessibilityIdentifier("noOrders")
            } else {
                List {
                    let n = shop.orders.count
                    Text("\(n) order\(n == 1 ? "" : "s")").accessibilityIdentifier("orderCount")
                    ForEach(shop.orders) { o in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(o.number).font(.subheadline.weight(.semibold))
                                Text("\(o.lines.reduce(0) { $0 + $1.qty }) items · \(methodLabel(o.method))").font(.caption)
                            }
                            Spacer()
                            Text(money(o.total)).fontWeight(.semibold)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { router.push(.receipt(o.number)) }
                        .accessibilityElement(children: .contain)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityIdentifier("order_\(o.number)")
                    }
                }
                .accessibilityIdentifier("orderList")
            }
        }
        #if os(iOS)
        .navigationTitle("Orders")
        .navigationBarTitleDisplayMode(.inline)
        #endif
    }
}

struct ReceiptView: View {
    @Environment(Shop.self) private var shop
    let number: String

    var body: some View {
        if let o = shop.orders.first(where: { $0.number == number }) {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text(o.number).font(.title2).accessibilityIdentifier("receiptNumber")
                    ForEach(o.lines, id: \.key) { l in Text("\(l.label) × \(l.qty)") }
                    Text(methodLabel(o.method)).accessibilityIdentifier("receiptDelivery")
                    OrderSummary(o: PricedOrder(merch: o.merch, discount: o.discount, shipping: o.shipping, tax: o.tax,
                                                gift: o.gift, total: o.total, grams: 0),
                                 code: o.code, taxKnown: true, totalLabel: "Total")
                }
                .padding()
            }
        }
    }
}
