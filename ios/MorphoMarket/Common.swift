import SwiftUI

// What the phone and TV apps share besides the screens: where the app is,
// the links a test opens, a screen's title, and the small views every screen
// uses. The ids are the Android app's, so one mobium test file drives both.

enum Route: Hashable {
    case product(String), bugs, delivery, address, payment, review, confirmation(String), receipt(String)
}

enum Tab: String, Hashable, CaseIterable {
    case shop, cart, orders

    var label: String {
        switch self {
        case .shop: "Shop"
        case .cart: "Cart"
        case .orders: "Orders"
        }
    }
}

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

/// A screen's title, as either app's bar shows it.
func screenTitle(_ r: Route?, tab: Tab) -> String {
    switch r {
    case nil:
        switch tab {
        case .shop: "Morpho Market"
        case .cart: "Cart"
        case .orders: "Orders"
        }
    case let .product(id): product(id).name
    case .bugs: "Bugs"
    case .delivery: "Delivery"
    case .address: "Address"
    case .payment: "Payment"
    case .review: "Review"
    case .confirmation: "Thank you"
    case let .receipt(n): n
    }
}

/// mobiumshop://bugs?on=a,b&off=c (off=all turns every bug off), and
/// mobiumshop://reset. A link naming something that is not a bug is refused
/// whole, before anything changes: the answer is what to tell the person, or
/// nil when the link was followed.
func followLink(_ url: URL, shop: Shop, router: Router, checkout: CheckoutState) -> String? {
    guard url.scheme == "mobiumshop" else { return nil }
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
        if !unknown.isEmpty { return "Not a bug: \(unknown.sorted().joined(separator: ", "))" }
        if on.isEmpty && off.isEmpty { return "The link names no bug: add on= or off=" }
        shop.setBugs(on: on, off: off)
        return nil
    case "reset":
        shop.reset()
        checkout.clear()
        router.shop = []
        router.cart = []
        router.orders = []
        router.tab = .shop
        return nil
    default:
        return "Not a link this app knows: \(url.absoluteString)"
    }
}

/// What a screen of one size wants: the phone's are the defaults.
struct ShopLayout {
    var catalogColumns = 2
    var catalogImageHeight: CGFloat = 100
    /// The product page as two panes, the photograph beside the details.
    var wideProduct = false
    /// Where a remote drives the app, as on a TV.
    var remote = false
}

private struct ShopLayoutKey: EnvironmentKey {
    static let defaultValue = ShopLayout()
}

extension EnvironmentValues {
    var shopLayout: ShopLayout {
        get { self[ShopLayoutKey.self] }
        set { self[ShopLayoutKey.self] = newValue }
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
        #if os(tvOS)
        // A TV's bar is the shell's, and the remote's Menu is Back.
        content
        #else
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
        #endif
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

/// A tappable card that stays a container: a SwiftUI Button would merge its
/// label into one element, and the price inside would lose its id. On a TV it
/// is a Button all the same, since only a button takes the remote's focus.
struct Tappable: ViewModifier {
    let id: String
    var selected = false
    let action: () -> Void

    func body(content: Content) -> some View {
        #if os(tvOS)
        Button(action: action) { content }
            .buttonStyle(.card)
            .accessibilityAddTraits(selected ? .isSelected : [])
            .accessibilityIdentifier(id)
        #else
        content
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
            .accessibilityIdentifier(id)
        #endif
    }
}

let morpho = Color(red: 0x15 / 255, green: 0x65 / 255, blue: 0xC0 / 255)

/// A product's photograph (product_<id>.jpg in the bundle), filling whatever
/// frame it is given. Decorative: hidden from accessibility, so the name beside
/// it stays the one thing VoiceOver and a test find.
struct ProductImage: View {
    let id: String

    var body: some View {
        Color.clear
            .overlay {
                if let photo = UIImage(named: "product_\(id).jpg") {
                    Image(uiImage: photo).resizable().scaledToFill()
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .accessibilityHidden(true)
    }
}

/// The morpho, drawn: the brand mark, where no product is shown.
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

extension View {
    /// A phone's bordered text field. tvOS draws its own and has no such style.
    @ViewBuilder func roundedField() -> some View {
        #if os(tvOS)
        self
        #else
        textFieldStyle(.roundedBorder)
        #endif
    }
}

/// A card's background, from the system on a phone; tvOS has no grouped colors.
var cardBackground: Color {
    #if os(tvOS)
    Color.gray.opacity(0.15)
    #else
    Color(.secondarySystemBackground)
    #endif
}

/// A choice among a few options: a menu on a phone. On a TV a menu's items
/// carry no ids — tvOS reports each as a bare cell — so there the options
/// are buttons in a sheet, which opens with focus on the first and closes on
/// a choice, giving focus back to the button that opened it.
struct Choice<Option: Hashable>: View {
    let id: String
    let label: String
    let options: [Option]
    let optionLabel: (Option) -> String
    let optionID: (Option) -> String
    let pick: (Option) -> Void
    #if os(tvOS)
    @State private var open = false
    #endif

    var body: some View {
        #if os(tvOS)
        Button(label) { open = true }
            .accessibilityIdentifier(id)
            .sheet(isPresented: $open) {
                VStack(spacing: 16) {
                    ForEach(options, id: \.self) { o in
                        Button(optionLabel(o)) {
                            pick(o)
                            open = false
                        }
                        .accessibilityIdentifier(optionID(o))
                    }
                }
            }
        #else
        Menu {
            ForEach(options, id: \.self) { o in
                Button(optionLabel(o)) { pick(o) }.accessibilityIdentifier(optionID(o))
            }
        } label: {
            Text(label)
        }
        .accessibilityIdentifier(id)
        #endif
    }
}
