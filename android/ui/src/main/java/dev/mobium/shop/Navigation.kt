package dev.mobium.shop

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed interface Screen {
    data object Catalog : Screen
    data class ProductPage(val id: String) : Screen
    data object Cart : Screen
    data object Orders : Screen
    data object Bugs : Screen
    data object Delivery : Screen
    data object AddressForm : Screen
    data object Payment : Screen
    data object Review : Screen
    data class Confirmation(val number: String) : Screen
    data class Receipt(val number: String) : Screen
}

enum class Tab(val label: String, val root: Screen) {
    SHOP("Shop", Screen.Catalog), CART("Cart", Screen.Cart), ORDERS("Orders", Screen.Orders)
}

/** Where the app is: a tab, and a stack of screens on it. Back pops the stack. */
class Nav {
    var tab by mutableStateOf(Tab.SHOP)
        private set
    val stack = mutableStateListOf<Screen>(Screen.Catalog)
    val top: Screen get() = stack.last()

    /** The product last opened, which a TV gives focus back to on the way out. */
    var lastProduct by mutableStateOf<String?>(null)
        private set

    fun go(t: Tab) {
        tab = t
        stack.clear()
        stack.add(t.root)
    }

    fun push(s: Screen) {
        if (s is Screen.ProductPage) lastProduct = s.id
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/** A screen's title, as either app's bar shows it. */
fun screenTitle(s: Screen): String = when (s) {
    Screen.Catalog -> "Morpho Market"
    is Screen.ProductPage -> product(s.id).name
    Screen.Cart -> "Cart"
    Screen.Orders -> "Orders"
    Screen.Bugs -> "Bugs"
    Screen.Delivery -> "Delivery"
    Screen.AddressForm -> "Address"
    Screen.Payment -> "Payment"
    Screen.Review -> "Review"
    is Screen.Confirmation -> "Thank you"
    is Screen.Receipt -> s.number
}

/** Back, with the lost-delivery bug: back from payment forgets the delivery chosen. */
fun goBack(shop: Shop, nav: Nav, checkout: CheckoutState) {
    if (nav.top == Screen.Payment && shop.bug("lost-delivery")) checkout.method = Method.STANDARD
    nav.pop()
}

/**
 * mobiumshop://bugs?on=a,b&off=c (off=all turns every bug off), and
 * mobiumshop://reset. A link naming something that is not a bug is refused
 * whole, before anything changes: the answer is what to tell the person, or
 * null when the link was followed.
 */
fun followLink(uri: Uri, shop: Shop, nav: Nav, checkout: CheckoutState): String? {
    if (uri.scheme != "mobiumshop") return null
    return when (uri.host) {
        "bugs" -> {
            fun ids(param: String) = uri.getQueryParameter(param)
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            val on = ids("on")
            var off = ids("off")
            if ("all" in off) off = (off - "all") + BUG_IDS
            val unknown = (on + off) - BUG_IDS
            when {
                unknown.isNotEmpty() -> "Not a bug: ${unknown.sorted().joinToString(", ")}"
                on.isEmpty() && off.isEmpty() -> "The link names no bug: add on= or off="
                else -> {
                    shop.setBugs(on, off)
                    null
                }
            }
        }
        "reset" -> {
            shop.reset()
            checkout.clear()
            nav.go(Tab.SHOP)
            null
        }
        else -> "Not a link this app knows: $uri"
    }
}

/** What a screen of one size wants: the phone's are the defaults. */
data class ShopLayout(
    val catalogColumns: Int = 2,
    val catalogImageHeight: Dp = 110.dp,
    /** The product page as two panes, the photograph beside the details. */
    val wideProduct: Boolean = false,
    /**
     * Back from a product gives its card focus again. A remote's only cursor is
     * focus, and without this it went to the search field, the first thing
     * that takes it, whose keyboard opens on focus.
     */
    val restoreFocus: Boolean = false,
)

/** The screen on top of the stack, the same in both apps. */
@Composable
fun ShopContent(shop: Shop, nav: Nav, checkout: CheckoutState, layout: ShopLayout = ShopLayout()) {
    Box(Modifier.fillMaxSize()) {
        when (val s = nav.top) {
            Screen.Catalog -> CatalogScreen(shop, layout.catalogColumns, layout.catalogImageHeight,
                focusOn = if (layout.restoreFocus) nav.lastProduct else null) {
                nav.push(Screen.ProductPage(it))
            }
            is Screen.ProductPage -> ProductScreen(shop, s.id, wide = layout.wideProduct, onViewCart = { nav.go(Tab.CART) })
            Screen.Cart -> CartScreen(shop, onKeepShopping = { nav.go(Tab.SHOP) }, onCheckout = {
                nav.push(if (shop.cart.any { !product(it.id).digital }) Screen.Delivery else Screen.AddressForm)
            })
            Screen.Orders -> OrdersScreen(shop) { nav.push(Screen.Receipt(it)) }
            Screen.Bugs -> BugsScreen(shop)
            Screen.Delivery -> DeliveryScreen(shop, checkout) { nav.push(Screen.AddressForm) }
            Screen.AddressForm -> AddressScreen(shop, checkout) { nav.push(Screen.Payment) }
            Screen.Payment -> PaymentScreen(checkout) { nav.push(Screen.Review) }
            Screen.Review -> ReviewScreen(shop, checkout) { order ->
                checkout.clear()
                nav.go(Tab.SHOP)
                nav.push(Screen.Confirmation(order.number))
            }
            is Screen.Confirmation -> ConfirmationScreen(shop, s.number) { nav.go(Tab.SHOP) }
            is Screen.Receipt -> ReceiptScreen(shop, s.number)
        }
    }
}
