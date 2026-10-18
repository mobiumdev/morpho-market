import SwiftUI

// The store on Apple TV: the phone app's model and screens, with a TV's
// layout, and the bar across the top that the Fire TV app has. The remote's
// Menu is Back, through the navigation stack. The app id is the Fire TV
// app's, dev.mobium.shop.tv, and the ids are the same.

@main
struct MorphoMarketTVApp: App {
    @State private var shop = Shop()
    @State private var router = Router()
    @State private var checkout = CheckoutState()
    @State private var linkError: String?

    var body: some Scene {
        WindowGroup {
            TVRoot()
                .environment(shop)
                .environment(router)
                .environment(checkout)
                .environment(\.shopLayout, ShopLayout(catalogColumns: 5, catalogImageHeight: 150, wideProduct: true, remote: true))
                .onOpenURL { url in
                    if let refused = followLink(url, shop: shop, router: router, checkout: checkout) { linkError = refused }
                }
                .alert("Link refused", isPresented: Binding(get: { linkError != nil }, set: { if !$0 { linkError = nil } })) {
                    Button("OK") { linkError = nil }.accessibilityIdentifier("linkErrorOk")
                } message: {
                    Text(linkError ?? "")
                }
        }
    }
}

struct TVRoot: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(CheckoutState.self) private var checkout

    var body: some View {
        let top = router.current.last
        // The catalog and a product use the width; a form reads better narrow.
        let wide: Bool = {
            switch top {
            case nil: router.tab == .shop
            case .product: true
            default: false
            }
        }()
        VStack(alignment: .leading, spacing: 16) {
            TopBar(title: screenTitle(top, tab: router.tab))
            NavigationStack(path: Binding(get: { router.current }, set: { router.current = $0 })) {
                Group {
                    switch router.tab {
                    case .shop: CatalogView()
                    case .cart: CartView()
                    case .orders: OrdersView()
                    }
                }
                .destinations()
            }
            .id(router.tab)
            .frame(maxWidth: wide ? .infinity : 1000)
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 48)
        .onChange(of: router.current) { old, new in
            // Back from payment: the lost-delivery bug forgets the delivery chosen.
            if old.last == .payment && new.count < old.count && shop.bug("lost-delivery") { checkout.method = .standard }
        }
    }
}

/// The screen's title, and the three tabs: the phone's tab bar, across the top.
private struct TopBar: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    let title: String

    var body: some View {
        HStack(spacing: 16) {
            Text(title).font(.title2).accessibilityIdentifier("screenTitle")
            Spacer()
            ForEach(Tab.allCases, id: \.self) { t in
                let label = t == .cart && shop.badge > 0 ? "Cart (\(shop.badge))" : t.label
                Group {
                    if router.tab == t {
                        Button(label) { router.go(t) }.buttonStyle(.borderedProminent).tint(morpho)
                    } else {
                        Button(label) { router.go(t) }.buttonStyle(.bordered)
                    }
                }
                    // Every tab is named in its label, as on the phone, so `label=Orders` finds it.
                    .accessibilityLabel(t == .cart ? "Cart, \(shop.badge) item\(shop.badge == 1 ? "" : "s")" : t.label)
                    .accessibilityIdentifier("tab_\(t.rawValue)")
            }
        }
    }
}
