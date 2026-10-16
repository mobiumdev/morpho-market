package dev.mobium.shop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

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

    fun go(t: Tab) {
        tab = t
        stack.clear()
        stack.add(t.root)
    }

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var shop: Shop
    private val nav = Nav()
    private val checkout = CheckoutState()
    private var linkError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shop = Shop(getSharedPreferences("shop", MODE_PRIVATE))
        intent?.data?.let(::handleLink)
        setContent { App(shop, nav, checkout, linkError, onLinkErrorSeen = { linkError = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(::handleLink)
    }

    /**
     * mobiumshop://bugs?on=a,b&off=c (off=all turns every bug off), and
     * mobiumshop://reset. A link naming something that is not a bug is refused
     * whole, with a dialog, before anything changes.
     */
    private fun handleLink(uri: Uri) {
        if (uri.scheme != "mobiumshop") return
        when (uri.host) {
            "bugs" -> {
                fun ids(param: String) = uri.getQueryParameter(param)
                    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                val on = ids("on")
                var off = ids("off")
                if ("all" in off) off = (off - "all") + BUG_IDS
                val unknown = (on + off) - BUG_IDS
                if (unknown.isNotEmpty()) {
                    linkError = "Not a bug: ${unknown.sorted().joinToString(", ")}"
                    return
                }
                if (on.isEmpty() && off.isEmpty()) {
                    linkError = "The link names no bug: add on= or off="
                    return
                }
                shop.setBugs(on, off)
            }
            "reset" -> {
                shop.reset()
                checkout.clear()
                nav.go(Tab.SHOP)
            }
            else -> linkError = "Not a link this app knows: $uri"
        }
    }
}

private val Morpho = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun App(shop: Shop, nav: Nav, checkout: CheckoutState, linkError: String?, onLinkErrorSeen: () -> Unit) {
    // Back from payment: the lost-delivery bug forgets the delivery chosen.
    fun back() {
        if (nav.top == Screen.Payment && shop.bug("lost-delivery")) checkout.method = Method.STANDARD
        nav.pop()
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Morpho, secondary = Color(0xFF00838F))) {
        BackHandler(enabled = nav.stack.size > 1) { back() }
        Scaffold(
            modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
            topBar = {
                TopAppBar(
                    title = {
                        val title = when (val s = nav.top) {
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
                        Text(
                            title,
                            maxLines = 1,
                            // A long press on the store's name opens the bug switch, for a person.
                            modifier = Modifier.testTag("screenTitle").let {
                                if (nav.top == Screen.Catalog) it.combinedClickable(
                                    onClick = {},
                                    onLongClick = { nav.push(Screen.Bugs) },
                                ) else it
                            },
                        )
                    },
                    navigationIcon = {
                        if (nav.stack.size > 1) {
                            IconButton(onClick = { back() }, modifier = Modifier.testTag("backBtn")) {
                                Text("←", style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { contentDescription = "Back" })
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        val glyph = when (t) { Tab.SHOP -> "◆"; Tab.CART -> "🛒"; Tab.ORDERS -> "☰" }
                        NavigationBarItem(
                            selected = nav.tab == t,
                            onClick = { nav.go(t) },
                            label = { Text(t.label) },
                            icon = {
                                if (t == Tab.CART) {
                                    BadgedBox(badge = {
                                        // Material 3 clears the icon's semantics, badge and all: the
                                        // count is read from the item's description below.
                                        if (shop.badge > 0) Badge { Text("${shop.badge}") }
                                    }) { Text(glyph) }
                                } else {
                                    Text(glyph)
                                }
                            },
                            // Every tab is named in its description, as iOS names it in its label,
                            // so `label=Orders` finds it on both.
                            modifier = Modifier.testTag("tab_${t.name.lowercase()}").semantics {
                                contentDescription = if (t == Tab.CART) {
                                    "Cart, ${shop.badge} item" + if (shop.badge == 1) "" else "s"
                                } else t.label
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val s = nav.top) {
                    Screen.Catalog -> CatalogScreen(shop) { nav.push(Screen.ProductPage(it)) }
                    is Screen.ProductPage -> ProductScreen(shop, s.id, onViewCart = { nav.go(Tab.CART) })
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
        if (linkError != null) {
            AlertDialog(
                onDismissRequest = onLinkErrorSeen,
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                title = { Text("Link refused") },
                text = { Text(linkError, Modifier.testTag("linkError")) },
                confirmButton = { TextButton(onClick = onLinkErrorSeen, Modifier.testTag("linkErrorOk")) { Text("OK") } },
            )
        }
    }
}
