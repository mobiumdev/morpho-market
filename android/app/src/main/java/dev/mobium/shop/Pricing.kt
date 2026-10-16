package dev.mobium.shop

// The pricing rules, line for line with spec/pricing.py: integer cents and
// grams, nothing a float. PricingTest runs spec/pricing-cases.json against it.

data class Line(val id: String, val variant: String? = null, val qty: Int)

enum class Method { STANDARD, EXPRESS, PICKUP }

/** Demo rates in thousandths of a percent, and whether shipping is taxed. */
data class TaxRate(val rate: Int, val shippingTaxed: Boolean)

val TAX: Map<String, TaxRate> = linkedMapOf(
    "CA" to TaxRate(7250, false),
    "NY" to TaxRate(8875, true),
    "OR" to TaxRate(0, false),
    "TX" to TaxRate(6250, true),
    "WA" to TaxRate(6500, true),
)
const val PICKUP_STATE = "CA"
const val FREE_SHIPPING_AT = 7500
private val STANDARD = 599 to 150
private val EXPRESS = 1499 to 250

private sealed interface Code {
    data class Percent(val percent: Int) : Code
    data class Off(val cents: Int, val min: Int) : Code
    data object FreeStandard : Code
    data object Expired : Code
}

private val CODES: Map<String, Code> = mapOf(
    "WELCOME10" to Code.Percent(10),
    "MOBIUM5" to Code.Off(500, 3000),
    "SHIPFREE" to Code.FreeStandard,
    "EXPIRED20" to Code.Expired,
)

/** The bugs that live in the arithmetic; the rest of [BUGS] live in screens. */
val PRICING_BUGS = setOf(
    "tax-before-discount", "free-shipping-before-discount", "free-shipping-over",
    "round-down", "expired-accepted", "express-free", "xxl-no-surcharge",
)

sealed interface Priced {
    data class Order(
        val merch: Int, val discount: Int, val shipping: Int, val tax: Int,
        val gift: Int, val total: Int, val grams: Int,
    ) : Priced

    data class CodeRefused(val message: String) : Priced
}

fun normalizeCode(raw: String): String = raw.trim().uppercase()

fun isKnownCode(code: String): Boolean = normalizeCode(code) in CODES

fun unitPrice(line: Line, bugs: Set<String> = emptySet()): Int {
    if (line.id == "giftcard") return GIFT_AMOUNTS.getValue(line.variant!!)
    val p = product(line.id)
    if ("xxl-no-surcharge" in bugs) return p.cents
    return p.cents + (SURCHARGE[line.id to (line.variant ?: "")] ?: 0)
}

fun halfUp(numerator: Long, denominator: Long): Long = (numerator * 2 + denominator) / (denominator * 2)

fun shippingCost(method: Method, grams: Int): Int {
    if (method == Method.PICKUP) return 0
    val (base, per) = if (method == Method.STANDARD) STANDARD else EXPRESS
    val over = maxOf(0, grams - 1000)
    return base + per * ((over + 499) / 500)
}

/**
 * Prices an order, or refuses its code. [times] is how often the code was
 * applied: 1, unless the coupon-twice bug let it be applied again.
 * Without a physical item there is no shipping, whatever [method] says.
 */
fun price(
    cart: List<Line>,
    code: String? = null,
    method: Method = Method.STANDARD,
    state: String = "CA",
    bugs: Set<String> = emptySet(),
    times: Int = 1,
): Priced {
    val rnd: (Long, Long) -> Long =
        if ("round-down" in bugs) { n, d -> n / d } else ::halfUp
    val merch = cart.filter { !product(it.id).digital }.sumOf { unitPrice(it, bugs) * it.qty }
    val gift = cart.filter { product(it.id).digital }.sumOf { unitPrice(it, bugs) * it.qty }
    val grams = cart.sumOf { product(it.id).grams * it.qty }
    val physical = merch > 0

    var discount = 0
    var freeStandard = false
    if (code != null) {
        var c = CODES[normalizeCode(code)] ?: return Priced.CodeRefused("That code isn't valid.")
        if (c == Code.Expired && "expired-accepted" in bugs) c = Code.Percent(20)
        if (c == Code.Expired) return Priced.CodeRefused("That code has expired.")
        if (!physical) return Priced.CodeRefused("Codes don't apply to gift cards.")
        if (c is Code.Off && merch < c.min) {
            return Priced.CodeRefused("Add ${money(c.min - merch)} more to use this code.")
        }
        when (c) {
            is Code.Percent -> discount = rnd(merch.toLong() * c.percent, 100).toInt()
            is Code.Off -> discount = minOf(c.cents, merch)
            Code.FreeStandard -> freeStandard = true
            Code.Expired -> Unit
        }
        discount = minOf(discount * times, merch)
    }

    val goods = merch - discount
    var shipping = 0
    var effective: Method? = null
    if (physical) {
        effective = method
        shipping = shippingCost(method, grams)
        val judged = if ("free-shipping-before-discount" in bugs) merch else goods
        val overLine = if ("free-shipping-over" in bugs) judged > FREE_SHIPPING_AT else judged >= FREE_SHIPPING_AT
        if (method == Method.STANDARD && (overLine || freeStandard)) shipping = 0
        if (method == Method.EXPRESS && freeStandard && "express-free" in bugs) shipping = 0
    }
    val tax = TAX.getValue(if (effective == Method.PICKUP) PICKUP_STATE else state)
    val base = (if ("tax-before-discount" in bugs) merch else goods) + (if (tax.shippingTaxed) shipping else 0)
    val taxCents = rnd(base.toLong() * tax.rate, 100000).toInt()
    return Priced.Order(merch, discount, shipping, taxCents, gift, goods + shipping + taxCents + gift, grams)
}

/** "$1,234.56"; a negative amount is "-$6.40". */
fun money(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    val c = kotlin.math.abs(cents)
    return "%s$%,d.%02d".format(java.util.Locale.US, sign, c / 100, c % 100)
}
