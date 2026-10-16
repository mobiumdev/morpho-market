package dev.mobium.shop.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.mobium.shop.CheckoutState
import dev.mobium.shop.LocalRemote
import dev.mobium.shop.Nav
import dev.mobium.shop.Screen
import dev.mobium.shop.Shop
import dev.mobium.shop.ShopContent
import dev.mobium.shop.ShopLayout
import dev.mobium.shop.Tab
import dev.mobium.shop.followLink
import dev.mobium.shop.goBack
import dev.mobium.shop.screenTitle

class TvActivity : ComponentActivity() {
    private lateinit var shop: Shop
    private val nav = Nav()
    private val checkout = CheckoutState()
    private var linkError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shop = Shop(getSharedPreferences("shop", MODE_PRIVATE))
        intent?.data?.let(::handleLink)
        setContent { TvApp(shop, nav, checkout, linkError, onLinkErrorSeen = { linkError = null }) }
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

/** A 1080p screen read from across a room: five columns, two panes for a product. */
private val TvLayout = ShopLayout(catalogColumns = 5, catalogImageHeight = 150.dp, wideProduct = true, restoreFocus = true)

/**
 * Focus is the only cursor a remote has, so it has to be seen from the sofa:
 * every Material control and clickable card draws its focus state far stronger
 * than a phone's.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val TvFocus = RippleConfiguration(
    color = Morpho,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.2f, focusedAlpha = 0.35f, hoveredAlpha = 0.1f, pressedAlpha = 0.4f),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvApp(shop: Shop, nav: Nav, checkout: CheckoutState, linkError: String?, onLinkErrorSeen: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Morpho, secondary = Color(0xFF00838F))) {
        CompositionLocalProvider(LocalRippleConfiguration provides TvFocus, LocalRemote provides true) {
            // The remote's Back is the phone's back arrow.
            BackHandler(enabled = nav.stack.size > 1) { goBack(shop, nav, checkout) }
            Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
                    TopBar(shop, nav)
                    // The catalog and a product use the width; a form reads better narrow.
                    val wide = nav.top == Screen.Catalog || nav.top is Screen.ProductPage
                    Box(Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
                        Box(if (wide) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxHeight()) {
                            ShopContent(shop, nav, checkout, TvLayout)
                        }
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
}

/** The screen's title, and the three tabs: the phone's bottom bar, across the top. */
@Composable
private fun TopBar(shop: Shop, nav: Nav) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(screenTitle(nav.top), Modifier.testTag("screenTitle"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        Tab.entries.forEach { t ->
            val label = if (t == Tab.CART && shop.badge > 0) "Cart (${shop.badge})" else t.label
            // Every tab is named in its description, as on the phone, so `label=Orders` finds it.
            val tabModifier = Modifier.testTag("tab_${t.name.lowercase()}").semantics {
                contentDescription = if (t == Tab.CART) {
                    "Cart, ${shop.badge} item" + if (shop.badge == 1) "" else "s"
                } else t.label
            }
            if (nav.tab == t) {
                Button(onClick = { nav.go(t) }, modifier = tabModifier) { Text(label) }
            } else {
                OutlinedButton(onClick = { nav.go(t) }, modifier = tabModifier,
                    colors = ButtonDefaults.outlinedButtonColors()) { Text(label) }
            }
        }
    }
}
