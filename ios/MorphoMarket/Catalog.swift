import SwiftUI

// The catalog. spec/pricing.py holds the same table, and CatalogTests checks
// this one against the catalog.json it writes.

enum Category: String, CaseIterable, Codable {
    case apparel, drinkware, accessories, giftcards

    var label: String {
        switch self {
        case .apparel: "Apparel"
        case .drinkware: "Drinkware"
        case .accessories: "Accessories"
        case .giftcards: "Gift Cards"
        }
    }
}

/// One choice of a product's option: its id in identifiers, and what a person reads.
struct Variant: Hashable {
    let id: String
    let label: String
}

struct Product: Identifiable {
    let id: String
    let name: String
    /// Price in cents; for the gift card, see `giftAmounts`.
    let cents: Int
    let grams: Int
    let category: Category
    let color: UInt32
    var digital = false
    /// What the option is called ("Size"), and its choices; none for most.
    var optionName: String? = nil
    var variants: [Variant] = []
    var outOfStock = false
    var outOfStockVariants: Set<String> = []
    let blurb: String

    var swiftColor: Color {
        Color(red: Double((color >> 16) & 0xFF) / 255, green: Double((color >> 8) & 0xFF) / 255,
              blue: Double(color & 0xFF) / 255)
    }
}

struct SurchargeKey: Hashable {
    let id: String
    let variant: String
}

let surcharge: [SurchargeKey: Int] = [SurchargeKey(id: "tee", variant: "XXL"): 200]
let giftAmounts: [String: Int] = ["25": 2500, "50": 5000]

private func sizes(_ s: String...) -> [Variant] { s.map { Variant(id: $0, label: $0) } }

let catalog: [Product] = [
    Product(id: "tee", name: "Logo Tee", cents: 2400, grams: 200, category: .apparel, color: 0x1565C0,
            optionName: "Size", variants: sizes("XS", "S", "M", "L", "XL", "XXL"),
            blurb: "The morpho on soft cotton. XXL is $2.00 more."),
    Product(id: "hoodie", name: "Map Before You Tap Hoodie", cents: 5800, grams: 700, category: .apparel, color: 0x283593,
            optionName: "Size", variants: sizes("S", "M", "L", "XL"), outOfStockVariants: ["XL"],
            blurb: "Heavyweight fleece for long test runs."),
    Product(id: "hat", name: "Morpho Dad Hat", cents: 2600, grams: 150, category: .apparel, color: 0x00838F,
            blurb: "An embroidered butterfly, an adjustable strap."),
    Product(id: "beanie", name: "Headless Beanie", cents: 2200, grams: 120, category: .apparel, color: 0x37474F,
            outOfStock: true, blurb: "Runs without a window."),
    Product(id: "socks", name: "Zero Runtime Dependencies Socks", cents: 1399, grams: 80, category: .apparel, color: 0x6A1B9A,
            blurb: "Nothing to install on your feet."),
    Product(id: "mug", name: "Wait For It Mug", cents: 1600, grams: 400, category: .drinkware, color: 0x0277BD,
            blurb: "Auto-wait, in ceramic."),
    Product(id: "tumbler", name: "No sleep() Tumbler", cents: 3200, grams: 450, category: .drinkware, color: 0x00695C,
            blurb: "Keeps coffee hot through a full sweep."),
    Product(id: "case", name: "Real Device Verified Phone Case", cents: 2900, grams: 60, category: .accessories, color: 0x4527A0,
            optionName: "Model",
            variants: [Variant(id: "iphone15", label: "iPhone 15"), Variant(id: "iphone17pro", label: "iPhone 17 Pro"),
                       Variant(id: "pixel7", label: "Pixel 7"), Variant(id: "pixel8pro", label: "Pixel 8 Pro")],
            blurb: "Measured on hardware, never merely compiled."),
    Product(id: "tote", name: "Leave Nothing On The Device Tote", cents: 1900, grams: 250, category: .accessories, color: 0x2E7D32,
            blurb: "Carry everything off; leave nothing behind."),
    Product(id: "pins", name: "@e1 Enamel Pin Set", cents: 1200, grams: 50, category: .accessories, color: 0xAD1457,
            blurb: "Three refs, each resolving to exactly one pin."),
    Product(id: "magnet", name: "Works On My Emulator Magnet", cents: 599, grams: 20, category: .accessories, color: 0xEF6C00,
            blurb: "For the fridge, not the phone."),
    Product(id: "stickers", name: "CHALLENGES Sticker Pack", cents: 749, grams: 10, category: .accessories, color: 0xC62828,
            blurb: "Eight defects, die-cut."),
    Product(id: "giftcard", name: "Gift Card", cents: 0, grams: 0, category: .giftcards, color: 0x1565C0, digital: true,
            optionName: "Amount", variants: [Variant(id: "25", label: "$25"), Variant(id: "50", label: "$50")],
            blurb: "Sent by email. No shipping, no tax, and codes don't apply."),
]

func product(_ id: String) -> Product { catalog.first { $0.id == id }! }
