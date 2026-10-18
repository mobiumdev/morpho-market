import SwiftUI

// The screens, with the Android app's ids.

private enum Sort: String, CaseIterable {
    case featured, price_asc, price_desc, name

    var label: String {
        switch self {
        case .featured: "Featured"
        case .price_asc: "Price: low to high"
        case .price_desc: "Price: high to low"
        case .name: "Name"
        }
    }
}

/// What a product's card says it costs: a gift card, from its smaller amount.
private func listPrice(_ p: Product) -> Int { p.digital ? giftAmounts.values.min()! : p.cents }

private func priceText(_ p: Product) -> String { p.digital ? "\(money(2500)) or \(money(5000))" : money(p.cents) }

/// A tappable card that stays a container: a SwiftUI Button would merge its
/// label into one element, and the price inside would lose its id. On a TV it
/// is a Button all the same, since only a button takes the remote's focus.
private struct Tappable: ViewModifier {
    let id: String
    let action: () -> Void

    func body(content: Content) -> some View {
        #if os(tvOS)
        Button(action: action) { content }
            .buttonStyle(.card)
            .accessibilityIdentifier(id)
        #else
        content
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier(id)
        #endif
    }
}

struct CatalogView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(\.shopLayout) private var layout
    @State private var query = ""
    @State private var category: Category?
    @State private var sort: Sort = .featured
    @State private var loading = false

    private var shown: [Product] {
        let list = catalog
            .filter { category == nil || $0.category == category }
            .filter { query.trimmingCharacters(in: .whitespaces).isEmpty
                || $0.name.localizedCaseInsensitiveContains(query.trimmingCharacters(in: .whitespaces)) }
        switch sort {
        case .featured: return list
        case .name: return list.sorted { $0.name.lowercased() < $1.name.lowercased() }
        case .price_asc, .price_desc:
            let sorted = shop.bug("sort-as-text") ? list.sorted { money(listPrice($0)) < money(listPrice($1)) }
                : list.sorted { listPrice($0) < listPrice($1) }
            return sort == .price_desc ? sorted.reversed() : sorted
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            if layout.remote {
                // A remote starts on the first control, so the categories come
                // first: the first press down reaches the first card, and the
                // search field, whose keyboard takes the screen, is passed by.
                chips
                HStack(spacing: 24) {
                    count
                    Spacer()
                    search.frame(width: 560)
                    sortMenu
                }
                .padding(.horizontal)
            } else {
                search.padding(.horizontal)
                chips
                HStack {
                    count
                    Spacer()
                    sortMenu
                }
                .padding(.horizontal)
            }
            if loading {
                Spacer()
                ProgressView().accessibilityIdentifier("catalogLoading")
                Spacer()
            } else if shown.isEmpty {
                Text("No products match \"\(query.trimmingCharacters(in: .whitespaces))\".")
                    .accessibilityIdentifier("noResults").padding()
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: layout.catalogColumns), spacing: 12) {
                            ForEach(shown) { p in card(p).id(p.id) }
                        }
                        .padding()
                    }
                    .accessibilityIdentifier("catalogList")
                    // A new sort or filter starts at the top, as it does on Android.
                    .onChange(of: sort) { proxy.scrollTo(shown.first?.id, anchor: .top) }
                    .onChange(of: category) { proxy.scrollTo(shown.first?.id, anchor: .top) }
                }
            }
        }
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                // A long press on the store's name opens the bug switch, for a person.
                Text("Morpho Market").font(.headline)
                    .accessibilityIdentifier("screenTitle")
                    .onLongPressGesture { router.push(.bugs) }
            }
        }
        #endif
        .task(id: shop.resets) {
            // A reset cancels this task and starts it again: a canceled sleep
            // returns at once, and must not show the catalog early.
            loading = shop.bug("slow-catalog") && !shop.catalogShown
            if loading {
                do { try await Task.sleep(for: .seconds(8)) } catch { return }
                loading = false
            }
            shop.catalogShown = true
        }
    }

    private var search: some View {
        TextField("Search", text: $query)
            .roundedField()
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .accessibilityIdentifier("searchField")
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                chip("All", selected: category == nil, id: "category_all") { category = nil }
                ForEach(Category.allCases, id: \.self) { c in
                    chip(c.label, selected: category == c, id: "category_\(c.rawValue)") { category = c }
                }
            }
            .padding(.horizontal)
        }
    }

    private var count: some View {
        let n = shown.count
        return Text("\(n) item\(n == 1 ? "" : "s")").accessibilityIdentifier("resultCount")
    }

    private var sortMenu: some View {
        Menu {
            ForEach(Sort.allCases, id: \.self) { s in
                Button(s.label) { sort = s }.accessibilityIdentifier("sort_\(s.rawValue)")
            }
        } label: {
            Text("Sort: \(sort.label)")
        }
        .accessibilityIdentifier("sortMenu")
    }

    private func chip(_ label: String, selected: Bool, id: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(selected ? Color.accentColor.opacity(0.18) : Color.clear))
                .overlay(Capsule().stroke(Color.secondary.opacity(0.5)))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier(id)
    }

    private func card(_ p: Product) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ProductImage(id: p.id).frame(height: layout.catalogImageHeight)
            Text(p.name).font(.subheadline).lineLimit(2)
            Text(priceText(p)).font(.subheadline.weight(.semibold)).accessibilityIdentifier("price_\(p.id)")
            if p.outOfStock {
                Text("Out of stock").font(.caption).foregroundStyle(.red).accessibilityIdentifier("stock_\(p.id)")
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(cardBackground))
        .modifier(Tappable(id: "product_\(p.id)") { router.push(.product(p.id)) })
    }
}

private func optionPrefix(_ p: Product) -> String {
    switch p.optionName {
    case "Size": "size"
    case "Model": "model"
    case "Amount": "amount"
    default: "option"
    }
}

struct ProductView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @Environment(\.shopLayout) private var layout
    @FocusState private var viewCartFocused: Bool
    let id: String
    @State private var variant: String?
    @State private var qty = 1
    @State private var email = ""
    @State private var emailError: String?
    @State private var notice = 0

    var body: some View {
        let p = product(id)
        let needsVariant = !p.variants.isEmpty
        let outOfStock = p.outOfStock || (variant.map { p.outOfStockVariants.contains($0) } ?? false)
        let canAdd = (!needsVariant || variant != nil) && (!outOfStock || shop.bug("out-of-stock-addable"))
        let shownPrice: String = p.digital
            ? (variant.map { money(giftAmounts[$0]!) } ?? priceText(p))
            : money(unitPrice(Line(id: p.id, variant: variant, qty: 1), bugs: shop.activePricingBugs))

        ZStack(alignment: .bottom) {
            ScrollView {
                HStack(alignment: .top, spacing: 40) {
                if layout.wideProduct { ProductImage(id: p.id).frame(width: 560, height: 560) }
                VStack(alignment: .leading, spacing: 12) {
                    if !layout.wideProduct { ProductImage(id: p.id).frame(height: 170) }
                    Text(p.name).font(.title2).accessibilityIdentifier("productTitle")
                    Text(shownPrice).font(.title3.weight(.semibold)).accessibilityIdentifier("productPrice")
                    Text(p.blurb).font(.body)
                    if p.outOfStock {
                        Text("Out of stock").foregroundStyle(.red).accessibilityIdentifier("productStock")
                    }
                    if needsVariant {
                        Text(p.optionName!).font(.headline)
                        // A TV has the width for every option, and a scrolling
                        // row would hold the remote's focus inside it.
                        if layout.remote {
                            variants(p)
                        } else {
                            ScrollView(.horizontal, showsIndicators: false) { variants(p) }
                        }
                        if let v = variant, p.outOfStockVariants.contains(v) {
                            Text("Out of stock in \(p.variants.first { $0.id == v }!.label)")
                                .foregroundStyle(.red).accessibilityIdentifier("variantStock")
                        }
                    }
                    if p.digital {
                        TextField("Recipient email", text: $email)
                            .roundedField()
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("giftEmail")
                            .onChange(of: email) { emailError = nil }
                        if let emailError {
                            Text(emailError).foregroundStyle(.red).accessibilityIdentifier("giftEmailError")
                        }
                    }
                    HStack(spacing: 12) {
                        Text("Quantity")
                        Button("−") { qty -= 1 }.buttonStyle(.bordered).disabled(qty <= 1).accessibilityIdentifier("qtyMinus")
                        Text("\(qty)").font(.headline).accessibilityIdentifier("qtyValue")
                        Button("+") { qty += 1 }.buttonStyle(.bordered).disabled(qty >= shop.maxQty).accessibilityIdentifier("qtyPlus")
                    }
                    Button {
                        if p.digital && email.trimmingCharacters(in: .whitespaces).wholeMatch(of: emailPattern) == nil {
                            emailError = "Enter a valid email address."
                            return
                        }
                        shop.add(p.id, variant: variant, qty: qty, email: p.digital ? email.trimmingCharacters(in: .whitespaces) : nil)
                        notice += 1
                    } label: {
                        Text("Add to cart").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canAdd)
                    .accessibilityIdentifier("addToCart")
                    Spacer(minLength: 64)
                }
                }
                .padding()
            }
            if notice > 0 {
                HStack {
                    Text("Added to cart").foregroundStyle(.white).accessibilityIdentifier("addedNotice")
                    Spacer()
                    Button("View cart") { router.go(.cart) }.foregroundStyle(.cyan).accessibilityIdentifier("viewCart")
                        .focused($viewCartFocused)
                }
                .padding()
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.2)))
                .padding()
                #if os(tvOS)
                // It stays while its View cart has focus: a remote takes
                // longer than four seconds to reach it.
                .task(id: [notice, viewCartFocused ? 1 : 0]) {
                    if viewCartFocused { return }
                    do { try await Task.sleep(for: .seconds(4)) } catch { return }
                    notice = 0
                }
                #else
                .task(id: notice) {
                    try? await Task.sleep(for: .seconds(4))
                    notice = 0
                }
                #endif
            }
        }
    }

    private func variants(_ p: Product) -> some View {
        HStack {
            ForEach(p.variants, id: \.self) { v in
                Button { variant = v.id } label: {
                    Text(v.label).padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Capsule().fill(variant == v.id ? Color.accentColor.opacity(0.18) : .clear))
                        .overlay(Capsule().stroke(Color.secondary.opacity(0.5)))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(variant == v.id ? .isSelected : [])
                .accessibilityIdentifier("\(optionPrefix(p))_\(v.id)")
            }
        }
    }
}

struct CartView: View {
    @Environment(Shop.self) private var shop
    @Environment(Router.self) private var router
    @State private var confirm: CartLine?
    @State private var codeText = ""
    @FocusState private var codeFocused: Bool

    var body: some View {
        List {
            if shop.cart.isEmpty {
                VStack(spacing: 8) {
                    Text("Your cart is empty").font(.headline).accessibilityIdentifier("emptyCart")
                    Button("Keep shopping") { router.go(.shop) }.accessibilityIdentifier("keepShopping")
                }
                .frame(maxWidth: .infinity)
            }
            ForEach(shop.cart, id: \.key) { line in
                let unit = unitPrice(line.line, bugs: shop.activePricingBugs)
                HStack(spacing: 10) {
                    ProductImage(id: line.id).frame(width: 44, height: 44)
                    VStack(alignment: .leading) {
                        Text(line.label).font(.subheadline)
                        Text("\(money(unit)) each").font(.caption)
                        Text(money(unit * line.qty)).fontWeight(.semibold).accessibilityIdentifier("lineTotal_\(line.key)")
                    }
                    Spacer()
                    Button("−") { if line.qty <= 1 { confirm = line } else { shop.setQty(line.key, line.qty - 1) } }
                        .buttonStyle(.bordered).accessibilityIdentifier("lineMinus_\(line.key)")
                    Text("\(line.qty)").accessibilityIdentifier("lineQty_\(line.key)")
                    Button("+") { shop.setQty(line.key, line.qty + 1) }
                        .buttonStyle(.bordered).disabled(line.qty >= shop.maxQty).accessibilityIdentifier("linePlus_\(line.key)")
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("line_\(line.key)")
                #if os(iOS)
                .swipeActions {
                    Button("Remove", role: .destructive) { confirm = line }
                }
                #endif
            }
            if !shop.cart.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        TextField("Discount code", text: $codeText)
                            .roundedField()
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("codeField")
                            .focused($codeFocused)
                            // Return applies it, as Done does on Android (a TV's remote gets there that way).
                            .submitLabel(.done)
                            .onSubmit { codeFocused = false; shop.applyCode(codeText) }
                        // Applying a code is done typing: the keyboard goes, as it does in any store.
                        Button("Apply") { codeFocused = false; shop.applyCode(codeText) }.buttonStyle(.bordered).accessibilityIdentifier("applyCode")
                    }
                    if let m = shop.codeMessage {
                        Text(m.text).foregroundStyle(m.error ? .red : .accentColor).accessibilityIdentifier("codeMessage")
                    }
                    if let c = shop.code {
                        Button("Remove code \(c)") { shop.removeCode(); codeText = "" }.accessibilityIdentifier("removeCode")
                    }
                }
                .buttonStyle(.borderless)
            }
            if case let .order(o) = price(shop.lines, code: shop.code, bugs: shop.activePricingBugs, times: shop.codeTimes) {
                VStack(spacing: 4) {
                    SummaryRow(label: "Merchandise", value: money(o.merch), id: "sumMerch")
                    if o.discount > 0 { SummaryRow(label: "Discount (\(shop.code ?? ""))", value: money(-o.discount), id: "sumDiscount") }
                    if o.gift > 0 { SummaryRow(label: "Gift cards", value: money(o.gift), id: "sumGift") }
                    if o.merch > 0 {
                        SummaryRow(label: "Shipping", value: "Calculated at checkout", id: "sumShipping")
                        SummaryRow(label: "Tax", value: "Calculated at checkout", id: "sumTax")
                    }
                    SummaryRow(label: "Estimated total", value: money(o.merch - o.discount + o.gift), id: "sumTotal", bold: true)
                    Button {
                        router.push(shop.hasPhysical ? .delivery : .address)
                    } label: {
                        Text("Checkout").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(shop.cart.isEmpty)
                    .accessibilityIdentifier("checkoutBtn")
                    .padding(.top, 8)
                }
            }
        }
        .accessibilityIdentifier("cartList")
        #if os(iOS)
        .navigationTitle("Cart")
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .alert(confirm.map { "Remove \($0.label)?" } ?? "", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } })) {
            Button("Remove", role: .destructive) {
                if let c = confirm { shop.remove(c.key) }
                confirm = nil
            }
            .accessibilityIdentifier("removeConfirm")
            Button("Cancel", role: .cancel) { confirm = nil }.accessibilityIdentifier("removeCancel")
        }
    }
}

struct BugsView: View {
    @Environment(Shop.self) private var shop

    var body: some View {
        List {
            Text("Deliberate bugs, for testing. Each stays on until turned off.").font(.callout)
            Text("Process \(shop.pid)").font(.caption).accessibilityIdentifier("processId")
            ForEach(bugs) { b in
                Toggle(isOn: Binding(get: { shop.bug(b.id) },
                                     set: { $0 ? shop.setBugs(on: [b.id], off: []) : shop.setBugs(on: [], off: [b.id]) })) {
                    VStack(alignment: .leading) {
                        Text(b.id).font(.subheadline.weight(.semibold))
                        Text(b.what).font(.caption)
                    }
                }
                .accessibilityIdentifier("bug_\(b.id)")
            }
            Button("Reset the store (bugs stay)") { shop.reset() }.accessibilityIdentifier("resetAll")
        }
        .accessibilityIdentifier("bugList")
    }
}
