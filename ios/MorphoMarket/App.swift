import SwiftUI

// The app: tabs, navigation, links and the bug switch's screen. The ids are
// the Android app's, so one mobium test file drives both.

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

    private func handleLink(_ url: URL) {
        if let refused = followLink(url, shop: shop, router: router, checkout: checkout) { linkError = refused }
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
        .tint(morpho)
    }
}
