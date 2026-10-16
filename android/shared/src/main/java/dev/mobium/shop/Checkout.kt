package dev.mobium.shop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.YearMonth

// Checkout's state and its rules: what an address and a card must be, and
// what each test card does. Nothing leaves the app; the "payment" is a table.

data class Address(
    val name: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val email: String = "",
)

/** What a placed order keeps, enough for its receipt. */
data class Order(
    val number: String,
    val lines: List<CartLine>,
    val method: Method?,
    val address: Address,
    val code: String?,
    val merch: Int,
    val discount: Int,
    val shipping: Int,
    val tax: Int,
    val gift: Int,
    val total: Int,
)

val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Each field that fails, with what to do about it; empty when the address will do. */
fun addressErrors(a: Address, method: Method?): Map<String, String> {
    val e = linkedMapOf<String, String>()
    if (a.name.isBlank()) e["name"] = "Enter your name."
    if (method != Method.PICKUP && method != null) {
        if (a.street.isBlank()) e["street"] = "Enter a street address."
        if (a.city.isBlank()) e["city"] = "Enter a city."
        if (a.state !in TAX) e["state"] = "Choose a state."
        if (!Regex("^\\d{5}$").matches(a.zip.trim())) e["zip"] = "Enter a 5-digit ZIP code."
    }
    if (!EMAIL_PATTERN.matches(a.email.trim())) e["email"] = "Enter a valid email address."
    return e
}

fun digitsOf(s: String) = s.filter { it.isDigit() }

fun luhn(digits: String): Boolean {
    if (digits.isEmpty()) return false
    var sum = 0
    digits.reversed().forEachIndexed { i, ch ->
        var d = ch - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return sum % 10 == 0
}

data class Card(val number: String = "", val expiry: String = "", val cvc: String = "", val name: String = "")

fun cardErrors(c: Card, now: YearMonth = YearMonth.now()): Map<String, String> {
    val e = linkedMapOf<String, String>()
    val n = digitsOf(c.number)
    if (n.length != 16 || !luhn(n)) e["cardNumber"] = "Enter a valid card number."
    val m = Regex("^(\\d{2})/(\\d{2})$").find(c.expiry.trim())
    val month = m?.groupValues?.get(1)?.toInt()
    if (m == null || month == null || month !in 1..12 ||
        YearMonth.of(2000 + m.groupValues[2].toInt(), month).isBefore(now)
    ) {
        e["cardExpiry"] = "Enter an expiry date that hasn't passed."
    }
    if (!Regex("^\\d{3}$").matches(c.cvc)) e["cardCvc"] = "Enter the 3-digit security code."
    if (c.name.isBlank()) e["cardName"] = "Enter the name on the card."
    return e
}

/** What a test card does: null approves, else the message it declines with. */
fun cardOutcome(number: String, bugs: Set<String>): String? = when (digitsOf(number)) {
    "4000000000000002" -> if ("declined-succeeds" in bugs) null else "Your card was declined."
    "4000000000009995" -> "Insufficient funds."
    else -> null
}

/** How long placing takes: the slow test card takes six seconds. */
fun placingMillis(number: String): Long = if (digitsOf(number) == "4000000000000119") 6000 else 1500

/** "4242424242424242" as typed so far, grouped in fours. */
fun groupCard(digits: String) = digits.chunked(4).joinToString(" ")

/** "1229" as typed so far, as MM/YY. */
fun slashExpiry(digits: String) = if (digits.length <= 2) digits else digits.take(2) + "/" + digits.drop(2)

/** The checkout in progress: kept while the person moves back and forth, dropped once placed. */
class CheckoutState {
    var method by mutableStateOf(Method.STANDARD)
    var address by mutableStateOf(Address())
    var card by mutableStateOf(Card())
    var placing by mutableStateOf(0)
    var paymentError by mutableStateOf<String?>(null)

    fun clear() {
        method = Method.STANDARD
        address = Address()
        card = Card()
        placing = 0
        paymentError = null
    }
}
