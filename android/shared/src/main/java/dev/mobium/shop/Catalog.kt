package dev.mobium.shop

// The catalog. spec/pricing.py holds the same table, and CatalogTest checks
// this one against the catalog.json it writes.

enum class Category(val id: String, val label: String) {
    APPAREL("apparel", "Apparel"),
    DRINKWARE("drinkware", "Drinkware"),
    ACCESSORIES("accessories", "Accessories"),
    GIFT_CARDS("giftcards", "Gift Cards"),
}

/** One choice of a product's option: its id in tags, and what a person reads. */
data class Variant(val id: String, val label: String)

data class Product(
    val id: String,
    val name: String,
    /** Price in cents; for the gift card, see [giftAmounts]. */
    val cents: Int,
    val grams: Int,
    val category: Category,
    val color: Long,
    val digital: Boolean = false,
    /** What the option is called ("Size"), and its choices; none for most. */
    val optionName: String? = null,
    val variants: List<Variant> = emptyList(),
    val outOfStock: Boolean = false,
    val outOfStockVariants: Set<String> = emptySet(),
    val blurb: String,
)

val SURCHARGE: Map<Pair<String, String>, Int> = mapOf(("tee" to "XXL") to 200)
val GIFT_AMOUNTS: Map<String, Int> = mapOf("25" to 2500, "50" to 5000)

private fun sizes(vararg s: String) = s.map { Variant(it, it) }

val CATALOG: List<Product> = listOf(
    Product("tee", "Logo Tee", 2400, 200, Category.APPAREL, 0xFF1565C0,
        optionName = "Size", variants = sizes("XS", "S", "M", "L", "XL", "XXL"),
        blurb = "The morpho on soft cotton. XXL is $2.00 more."),
    Product("hoodie", "Map Before You Tap Hoodie", 5800, 700, Category.APPAREL, 0xFF283593,
        optionName = "Size", variants = sizes("S", "M", "L", "XL"), outOfStockVariants = setOf("XL"),
        blurb = "Heavyweight fleece for long test runs."),
    Product("hat", "Morpho Dad Hat", 2600, 150, Category.APPAREL, 0xFF00838F,
        blurb = "An embroidered butterfly, an adjustable strap."),
    Product("beanie", "Headless Beanie", 2200, 120, Category.APPAREL, 0xFF37474F, outOfStock = true,
        blurb = "Runs without a window."),
    Product("socks", "Zero Runtime Dependencies Socks", 1399, 80, Category.APPAREL, 0xFF6A1B9A,
        blurb = "Nothing to install on your feet."),
    Product("mug", "Wait For It Mug", 1600, 400, Category.DRINKWARE, 0xFF0277BD,
        blurb = "Auto-wait, in ceramic."),
    Product("tumbler", "No sleep() Tumbler", 3200, 450, Category.DRINKWARE, 0xFF00695C,
        blurb = "Keeps coffee hot through a full sweep."),
    Product("case", "Real Device Verified Phone Case", 2900, 60, Category.ACCESSORIES, 0xFF4527A0,
        optionName = "Model",
        variants = listOf(Variant("iphone15", "iPhone 15"), Variant("iphone17pro", "iPhone 17 Pro"),
            Variant("pixel7", "Pixel 7"), Variant("pixel8pro", "Pixel 8 Pro")),
        blurb = "Measured on hardware, never merely compiled."),
    Product("tote", "Leave Nothing On The Device Tote", 1900, 250, Category.ACCESSORIES, 0xFF2E7D32,
        blurb = "Carry everything off; leave nothing behind."),
    Product("pins", "@e1 Enamel Pin Set", 1200, 50, Category.ACCESSORIES, 0xFFAD1457,
        blurb = "Three refs, each resolving to exactly one pin."),
    Product("magnet", "Works On My Emulator Magnet", 599, 20, Category.ACCESSORIES, 0xFFEF6C00,
        blurb = "For the fridge, not the phone."),
    Product("stickers", "CHALLENGES Sticker Pack", 749, 10, Category.ACCESSORIES, 0xFFC62828,
        blurb = "Eight defects, die-cut."),
    Product("giftcard", "Gift Card", 0, 0, Category.GIFT_CARDS, 0xFF1565C0, digital = true,
        optionName = "Amount", variants = listOf(Variant("25", "$25"), Variant("50", "$50")),
        blurb = "Sent by email. No shipping, no tax, and codes don't apply."),
)

fun product(id: String): Product = CATALOG.first { it.id == id }
