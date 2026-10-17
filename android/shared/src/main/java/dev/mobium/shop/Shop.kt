package dev.mobium.shop

import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** What `adb logcat -s MorphoMarket` shows: the process and each reset. */
const val LOG_TAG = "MorphoMarket"

/** A line in the cart. A gift card keeps its recipient, which pricing never reads. */
data class CartLine(val id: String, val variant: String?, val qty: Int, val email: String? = null) {
    val key: String get() = if (variant == null) id else "${id}_$variant"
    val label: String
        get() {
            val p = product(id)
            val v = p.variants.firstOrNull { it.id == variant }?.label ?: return p.name
            return "${p.name} ($v)"
        }
    fun asLine() = Line(id, variant, qty)
}

/** A message under the code field, and whether it is an error. */
data class CodeMessage(val text: String, val error: Boolean)

/**
 * The store's state: the cart, the code, the bugs and what has been spent.
 * Everything here persists across launches, so a test starts from
 * `mobiumshop://reset` rather than from whatever the last one left.
 */
class Shop(private val prefs: SharedPreferences) {
    val cart = mutableStateListOf<CartLine>()
    var code by mutableStateOf<String?>(null)
        private set
    var codeTimes by mutableIntStateOf(1)
        private set
    var codeMessage by mutableStateOf<CodeMessage?>(null)
    var bugs by mutableStateOf(emptySet<String>())
        private set
    var spent by mutableStateOf(emptySet<String>())
        private set
    var badge by mutableIntStateOf(0)
        private set
    /** Newest first. */
    val orders = mutableStateListOf<Order>()
    private var orderCounter = 0

    /**
     * The slow-catalog bug delays the catalog's first showing after the store
     * is made or reset, so every test that starts from a reset meets it.
     */
    var catalogShown = false
    /** Counts resets, so a catalog already on screen loads again after one. */
    var resets by mutableIntStateOf(0)
        private set

    /** This process, shown on the bug screen and logged, to tell a relaunch from a reset. */
    val pid: Int = Process.myPid()

    init {
        load()
        Log.i(LOG_TAG, "process $pid: store made")
    }

    fun bug(id: String) = id in bugs
    val pricingBugs: Set<String> get() = bugs intersect PRICING_BUGS
    val maxQty: Int get() = if (bug("qty-eleven")) 11 else 10
    val itemCount: Int get() = cart.sumOf { it.qty }

    fun lines(): List<Line> = cart.map { it.asLine() }

    fun add(id: String, variant: String?, qty: Int, email: String? = null) {
        val i = cart.indexOfFirst { it.id == id && it.variant == variant }
        if (i >= 0) {
            cart[i] = cart[i].copy(qty = minOf(cart[i].qty + qty, maxQty), email = email ?: cart[i].email)
        } else {
            cart.add(CartLine(id, variant, minOf(qty, maxQty), email))
        }
        changed()
    }

    fun setQty(key: String, qty: Int) {
        val i = cart.indexOfFirst { it.key == key }
        if (i < 0) return
        if (qty <= 0) cart.removeAt(i) else cart[i] = cart[i].copy(qty = minOf(qty, maxQty))
        changed()
    }

    fun remove(key: String) = setQty(key, 0)

    fun applyCode(raw: String) {
        val c = normalizeCode(raw)
        val applied = code
        when {
            c.isEmpty() -> codeMessage = CodeMessage("Enter a code.", true)
            applied == c && bug("coupon-twice") && codeTimes < 2 -> {
                codeTimes = 2
                codeMessage = CodeMessage(appliedMessage(c), false)
            }
            applied == c -> codeMessage = CodeMessage("$c is already applied.", true)
            applied != null -> codeMessage = CodeMessage("Remove $applied to use another code.", true)
            c in spent -> codeMessage = CodeMessage("That code has already been used.", true)
            else -> when (val r = price(lines(), c, bugs = pricingBugs)) {
                // stale-code-message: a refusal over a refusal leaves the first one up.
                is Priced.CodeRefused -> if (!(bug("stale-code-message") && codeMessage?.error == true)) {
                    codeMessage = CodeMessage(r.message, true)
                }
                is Priced.Order -> {
                    code = c
                    codeTimes = 1
                    codeMessage = CodeMessage(appliedMessage(c), false)
                }
            }
        }
        save()
    }

    fun removeCode() {
        code = null
        codeTimes = 1
        codeMessage = null
        save()
    }

    private fun appliedMessage(c: String) = when (c) {
        "WELCOME10" -> "WELCOME10 applied: 10% off merchandise."
        "MOBIUM5" -> "MOBIUM5 applied: \$5.00 off."
        "SHIPFREE" -> "SHIPFREE applied: free standard shipping."
        "EXPIRED20" -> "EXPIRED20 applied: 20% off merchandise."
        else -> "$c applied."
    }

    /** The cart moved: a code that no longer qualifies comes off, saying why; the badge follows. */
    private fun changed() {
        val c = code
        if (c != null) {
            val r = price(lines(), c, bugs = pricingBugs)
            if (r is Priced.CodeRefused) {
                code = null
                codeTimes = 1
                codeMessage = CodeMessage(r.message, true)
            }
        }
        val n = itemCount
        if (!(bug("stale-badge") && n < badge)) badge = n
        save()
    }

    /**
     * Places an order for [lines] as they were when Place order was tapped,
     * which is what lets the double-order bug place the same cart twice.
     */
    fun place(lines: List<CartLine>, codeAtTap: String?, times: Int, method: Method, address: Address): Order {
        val physical = lines.any { !product(it.id).digital }
        val o = price(lines.map { it.asLine() }, codeAtTap, method, address.state.ifEmpty { "CA" }, pricingBugs, times)
            as Priced.Order
        orderCounter++
        val order = Order(
            number = "MS-%06d".format(orderCounter),
            lines = lines,
            method = if (physical) method else null,
            address = address,
            code = codeAtTap,
            merch = o.merch, discount = o.discount, shipping = o.shipping, tax = o.tax, gift = o.gift, total = o.total,
        )
        orders.add(0, order)
        if (codeAtTap == "WELCOME10") spent = spent + "WELCOME10"
        cart.clear()
        code = null
        codeTimes = 1
        codeMessage = null
        changed()
        return order
    }

    fun setBugs(on: Set<String>, off: Set<String>) {
        bugs = (bugs - off) + on
        save()
    }

    /** Cart, code, orders and spent codes; the bugs stay as they are. */
    fun reset() {
        cart.clear()
        code = null
        codeTimes = 1
        codeMessage = null
        spent = emptySet()
        badge = 0
        orders.clear()
        orderCounter = 0
        catalogShown = false
        resets += 1
        save()
        Log.i(LOG_TAG, "process $pid: reset")
    }

    private fun save() {
        val lines = JSONArray(cart.map { lineJson(it) })
        prefs.edit()
            .putString("cart", lines.toString())
            .putString("code", code)
            .putInt("codeTimes", codeTimes)
            .putStringSet("bugs", bugs)
            .putStringSet("spent", spent)
            .putInt("badge", badge)
            .putString("orders", JSONArray(orders.map { orderJson(it) }).toString())
            .putInt("orderCounter", orderCounter)
            .apply()
    }

    private fun load() {
        val lines = JSONArray(prefs.getString("cart", "[]"))
        for (i in 0 until lines.length()) cart.add(lineFrom(lines.getJSONObject(i)))
        code = prefs.getString("code", null)
        codeTimes = prefs.getInt("codeTimes", 1)
        bugs = prefs.getStringSet("bugs", emptySet())!!.toSet()
        spent = prefs.getStringSet("spent", emptySet())!!.toSet()
        badge = prefs.getInt("badge", itemCount)
        val os = JSONArray(prefs.getString("orders", "[]"))
        for (i in 0 until os.length()) orders.add(orderFrom(os.getJSONObject(i)))
        orderCounter = prefs.getInt("orderCounter", 0)
    }

    private fun lineJson(l: CartLine) = JSONObject().put("id", l.id).put("variant", l.variant ?: JSONObject.NULL)
        .put("qty", l.qty).put("email", l.email ?: JSONObject.NULL)

    private fun lineFrom(o: JSONObject) = CartLine(
        o.getString("id"),
        if (o.isNull("variant")) null else o.getString("variant"),
        o.getInt("qty"),
        if (o.isNull("email")) null else o.getString("email"),
    )

    private fun orderJson(o: Order): JSONObject {
        val a = o.address
        return JSONObject()
            .put("number", o.number)
            .put("lines", JSONArray(o.lines.map { lineJson(it) }))
            .put("method", o.method?.name ?: JSONObject.NULL)
            .put("address", JSONObject().put("name", a.name).put("street", a.street).put("city", a.city)
                .put("state", a.state).put("zip", a.zip).put("email", a.email))
            .put("code", o.code ?: JSONObject.NULL)
            .put("merch", o.merch).put("discount", o.discount).put("shipping", o.shipping)
            .put("tax", o.tax).put("gift", o.gift).put("total", o.total)
    }

    private fun orderFrom(j: JSONObject): Order {
        val a = j.getJSONObject("address")
        val ls = j.getJSONArray("lines")
        return Order(
            number = j.getString("number"),
            lines = (0 until ls.length()).map { lineFrom(ls.getJSONObject(it)) },
            method = if (j.isNull("method")) null else Method.valueOf(j.getString("method")),
            address = Address(a.getString("name"), a.getString("street"), a.getString("city"),
                a.getString("state"), a.getString("zip"), a.getString("email")),
            code = if (j.isNull("code")) null else j.getString("code"),
            merch = j.getInt("merch"), discount = j.getInt("discount"), shipping = j.getInt("shipping"),
            tax = j.getInt("tax"), gift = j.getInt("gift"), total = j.getInt("total"),
        )
    }
}
