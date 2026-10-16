package dev.mobium.shop

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun specFile(name: String): File {
    val dir = System.getProperty("spec.dir") ?: error("spec.dir is not set: run through Gradle")
    return File(dir, name).also { assertTrue("missing $it", it.isFile) }
}

class PricingTest {
    /** Every case in the spec's table, the bug cases included, against this implementation. */
    @Test
    fun everySpecCase() {
        val cases = JSONArray(specFile("pricing-cases.json").readText())
        assertTrue("no cases", cases.length() > 0)
        val failures = mutableListOf<String>()
        var checked = 0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val cart = c.getJSONArray("cart").let { a ->
                (0 until a.length()).map { j ->
                    val l = a.getJSONObject(j)
                    Line(l.getString("id"), l.optString("variant").ifEmpty { null }, l.getInt("qty"))
                }
            }
            val bugs = c.optJSONArray("bugs")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            val got = price(
                cart,
                code = c.optString("coupon").ifEmpty { null },
                method = Method.valueOf(c.optString("method", "standard").uppercase()),
                state = c.optString("state", "CA"),
                bugs = bugs,
                times = c.optInt("times", 1),
            )
            val expect = c.getJSONObject("expect")
            for (key in expect.keys()) {
                val actual: Any? = when (got) {
                    is Priced.CodeRefused -> if (key == "coupon_error") got.message else "(refused: ${got.message})"
                    is Priced.Order -> when (key) {
                        "merch" -> got.merch; "discount" -> got.discount; "shipping" -> got.shipping
                        "tax" -> got.tax; "gift" -> got.gift; "total" -> got.total; "grams" -> got.grams
                        else -> "(no field $key)"
                    }
                }
                checked++
                if (expect.get(key) != actual) failures += "${c.getString("name")}: $key expected ${expect.get(key)}, got $actual"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
        assertTrue("checked nothing", checked > 0)
    }

    @Test
    fun moneyReadsAsDollars() {
        assertEquals("$0.00", money(0))
        assertEquals("$5.99", money(599))
        assertEquals("$1,234.56", money(123456))
        assertEquals("-$6.40", money(-640))
    }
}

class CatalogTest {
    /** The catalog here is the catalog in the spec: catalog.json is written by spec/pricing.py. */
    @Test
    fun matchesTheSpec() {
        val spec = JSONObject(specFile("catalog.json").readText())
        val products = spec.getJSONArray("products")
        assertEquals(products.length(), CATALOG.size)
        for (i in 0 until products.length()) {
            val p = products.getJSONObject(i)
            val mine = product(p.getString("id"))
            assertEquals(p.getString("name"), mine.name)
            assertEquals(p.getString("id"), p.optInt("cents", 0), if (mine.digital) 0 else mine.cents)
            assertEquals(p.getString("id"), p.getInt("grams"), mine.grams)
            assertEquals(p.getString("id"), p.getBoolean("digital"), mine.digital)
        }
        val gifts = spec.getJSONObject("gift_amounts")
        assertEquals(gifts.length(), GIFT_AMOUNTS.size)
        for (k in gifts.keys()) assertEquals(gifts.getInt(k), GIFT_AMOUNTS[k])
        val sur = spec.getJSONArray("surcharge")
        assertEquals(sur.length(), SURCHARGE.size)
        for (i in 0 until sur.length()) {
            val s = sur.getJSONObject(i)
            assertEquals(s.getInt("cents"), SURCHARGE[s.getString("id") to s.getString("variant")])
        }
    }
}
