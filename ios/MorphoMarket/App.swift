import SwiftUI

// The app: tabs, navigation, links and the bug switch's screen. The ids are
// the Android app's, so one mobium test file drives both.

enum Route: Hashable {
    case product(String), bugs, delivery, address, payment, review, confirmation(String), receipt(String)
}

enum Tab: String, Hashable { case shop, cart, orders }

/// Where the app is: a tab, and a path on each. Back pops the current one.
@Observable
final class Router {
    var tab: Tab = .shop
    var shop: [Route] = []
    var cart: [Route] = []
    var orders: [Route] = []

    var current: [Route] {
        get { switch tab { case .shop: shop; case .cart: cart; case .orders: orders } }
        set { switch tab { case .shop: shop = newValue; case .cart: cart = newValue; case .orders: orders = newValue } }
    }

    func go(_ t: Tab) {
        tab = t
        current = []
    }

    func push(_ r: Route) { current.append(r) }

    func pop() { if !current.isEmpty { current.removeLast() } }
}

@main
struct MorphoMarketApp: App {
    @State private var shop = Shop()
    @State private var router = Router()
    @State private var checkout = CheckoutState()
    @State private var linkError: String?

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(shop)
                .environment(router)
                .environment(checkout)
                .onOpenURL(perform: handleLink)
                .alert("Link refused", isPresented: Binding(get: { linkError != nil }, set: { if !$0 { linkError = nil } })) {
                    Button("OK") { linkError = nil }.accessibilityIdentifier("linkErrorOk")
                } message: {
                    Text(linkError ?? "")
                }
        }
    }

    /// mobiumshop://bugs?on=a,b&off=c (off=all turns every bug off), and
    /// mobiumshop://reset. A link naming something that is not a bug is
    /// refused whole, with a dialog, before anything changes.
    private func handleLink(_ url: URL) {
        guard url.scheme == "mobiumshop" else { return }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func ids(_ name: String) -> Set<String> {
            Set((items.first { $0.name == name }?.value ?? "").split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
        }
        switch url.host {
        case "bugs":
            let on = ids("on")
            var off = ids("off")
            if off.contains("all") { off = off.subtracting(["all"]).union(bugIDs) }
            let unknown = on.union(off).subtracting(bugIDs)
            if !unknown.isEmpty {
                linkError = "Not a bug: \(unknown.sorted().joined(separator: ", "))"
                return
            }
            if on.isEmpty && off.isEmpty {
                linkError = "The link names no bug: add on= or off="
                return
            }
            shop.setBugs(on: on, off: off)
        case "reset":
            shop.reset()
            checkout.clear()
            router.shop = []
            router.cart = []
            router.orders = []
            router.tab = .shop
        default:
            linkError = "Not a link this app knows: \(url.absoluteString)"
        }
    }
}

struct RootView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.tab) {
            NavigationStack(path: $router.shop) {
                CatalogView().destinations()
            }
            .tabItem { Label("Shop", systemImage: "sparkles") }
            .tag(Tab.shop)

            NavigationStack(path: $router.cart) {
                CartView().destinations()
            }
            .tabItem {
                // The badge is drawn and nothing reads it: the count is said in the label.
                Label("Cart", systemImage: "cart")
                    .accessibilityLabel("Cart, \(shop.badge) item\(shop.badge == 1 ? "" : "s")")
            }
            .badge(shop.badge)
            .tag(Tab.cart)

            NavigationStack(path: $router.orders) {
                OrdersView().destinations()
            }
            .tabItem { Label("Orders", systemImage: "list.bullet") }
            .tag(Tab.orders)
        }
        .tint(Color(red: 0x15 / 255, green: 0x65 / 255, blue: 0xC0 / 255))
    }
}

/// A pushed screen's chrome: its title as `screenTitle`, and a Back button
/// with the id the Android app's has — iOS has no back key to press.
struct Screen: ViewModifier {
    @Environment(Router.self) private var router
    @Environment(Shop.self) private var shop
    @Environment(CheckoutState.self) private var checkout
    let title: String
    let isPayment: Bool

    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        // Back from payment: the lost-delivery bug forgets the delivery chosen.
                        if isPayment && shop.bug("lost-delivery") { checkout.method = .standard }
                        router.pop()
                    } label: {
                        Label("Back", systemImage: "chevron.left").labelStyle(.titleAndIcon)
                    }
                    .accessibilityIdentifier("backBtn")
                }
                ToolbarItem(placement: .principal) {
                    Text(title).font(.headline).accessibilityIdentifier("screenTitle")
                }
            }
    }
}

extension View {
    func screen(_ title: String, isPayment: Bool = false) -> some View {
        modifier(Screen(title: title, isPayment: isPayment))
    }

    func destinations() -> some View {
        navigationDestination(for: Route.self) { r in
            switch r {
            case let .product(id): ProductView(id: id).screen(product(id).name)
            case .bugs: BugsView().screen("Bugs")
            case .delivery: DeliveryView().screen("Delivery")
            case .address: AddressView().screen("Address")
            case .payment: PaymentView().screen("Payment", isPayment: true)
            case .review: ReviewView().screen("Review")
            case let .confirmation(n): ConfirmationView(number: n).screen("Thank you")
            case let .receipt(n): ReceiptView(number: n).screen(n)
            }
        }
    }
}

/// The morpho, drawn: no image files, nothing to load.
struct Butterfly: View {
    let color: Color

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let wing = min(w, h) * 0.42
            ZStack {
                Ellipse().fill(color).frame(width: wing, height: wing * 0.9)
                    .rotationEffect(.degrees(-20)).position(x: w / 2 - wing / 2, y: h * 0.12 + wing * 0.45)
                Ellipse().fill(color).frame(width: wing, height: wing * 0.9)
                    .rotationEffect(.degrees(20)).position(x: w / 2 + wing / 2, y: h * 0.12 + wing * 0.45)
                Ellipse().fill(color.opacity(0.75)).frame(width: wing * 0.8, height: wing * 0.65)
                    .position(x: w / 2 - wing * 0.4, y: h * 0.48 + wing * 0.33)
                Ellipse().fill(color.opacity(0.75)).frame(width: wing * 0.8, height: wing * 0.65)
                    .position(x: w / 2 + wing * 0.4, y: h * 0.48 + wing * 0.33)
                RoundedRectangle(cornerRadius: 2).fill(Color(white: 0.15))
                    .frame(width: w * 0.03, height: h * 0.62).position(x: w / 2, y: h * 0.49)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A summary row: the value carries the id, as on Android.
struct SummaryRow: View {
    let label: String
    let value: String
    let id: String
    var bold = false

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).accessibilityIdentifier(id)
        }
        .fontWeight(bold ? .bold : .regular)
    }
}
