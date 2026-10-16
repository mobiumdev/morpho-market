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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

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

    private fun handleLink(uri: Uri) {
        followLink(uri, shop, nav, checkout)?.let { linkError = it }
    }
}

private val Morpho = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun App(shop: Shop, nav: Nav, checkout: CheckoutState, linkError: String?, onLinkErrorSeen: () -> Unit) {
    fun back() = goBack(shop, nav, checkout)
    MaterialTheme(colorScheme = lightColorScheme(primary = Morpho, secondary = Color(0xFF00838F))) {
        BackHandler(enabled = nav.stack.size > 1) { back() }
        Scaffold(
            modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
            topBar = {
                TopAppBar(
                    title = {
                        val title = screenTitle(nav.top)
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
                ShopContent(shop, nav, checkout)
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
