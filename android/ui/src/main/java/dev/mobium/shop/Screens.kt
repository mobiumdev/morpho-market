@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.mobium.shop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobium.shop.ui.R
import kotlinx.coroutines.delay

/** A product's photograph (res/drawable-nodpi/product_<id>.jpg). Decorative:
 *  no description, so the name beside it stays the one thing a screen reader
 *  and a test find. */
@Composable
fun ProductImage(id: String, modifier: Modifier = Modifier) {
    Image(painterResource(PRODUCT_IMAGES.getValue(id)), contentDescription = null,
        contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(12.dp)))
}

private val PRODUCT_IMAGES = mapOf(
    "tee" to R.drawable.product_tee, "hoodie" to R.drawable.product_hoodie,
    "hat" to R.drawable.product_hat, "beanie" to R.drawable.product_beanie,
    "socks" to R.drawable.product_socks, "mug" to R.drawable.product_mug,
    "tumbler" to R.drawable.product_tumbler, "case" to R.drawable.product_case,
    "tote" to R.drawable.product_tote, "magnet" to R.drawable.product_magnet,
    "pins" to R.drawable.product_pins, "stickers" to R.drawable.product_stickers,
    "giftcard" to R.drawable.product_giftcard,
)

/** The morpho, drawn: the brand mark, where no product is shown. */
@Composable
fun Butterfly(color: Long, modifier: Modifier = Modifier) {
    val c = Color(color)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val wing = minOf(w, h) * 0.42f
        rotate(-20f, Offset(cx, h * 0.45f)) {
            drawOval(c, Offset(cx - wing, h * 0.12f), Size(wing, wing * 0.9f))
        }
        rotate(20f, Offset(cx, h * 0.45f)) {
            drawOval(c, Offset(cx, h * 0.12f), Size(wing, wing * 0.9f))
        }
        drawOval(c.copy(alpha = 0.75f), Offset(cx - wing * 0.8f, h * 0.48f), Size(wing * 0.8f, wing * 0.65f))
        drawOval(c.copy(alpha = 0.75f), Offset(cx, h * 0.48f), Size(wing * 0.8f, wing * 0.65f))
        drawRoundRect(Color(0xFF263238), Offset(cx - w * 0.015f, h * 0.18f), Size(w * 0.03f, h * 0.62f))
    }
}

private enum class Sort(val id: String, val label: String) {
    FEATURED("featured", "Featured"),
    PRICE_ASC("price_asc", "Price: low to high"),
    PRICE_DESC("price_desc", "Price: high to low"),
    NAME("name", "Name"),
}

/** What a product's card says it costs: a gift card, from its smaller amount. */
private fun listPrice(p: Product) = if (p.digital) GIFT_AMOUNTS.values.min() else p.cents

private fun priceText(p: Product) = if (p.digital) "${money(2500)} or ${money(5000)}" else money(p.cents)

@Composable
fun CatalogScreen(shop: Shop, columns: Int = 2, imageHeight: Dp = 110.dp, focusOn: String? = null,
    onOpen: (String) -> Unit) {
    val returnFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var sort by remember { mutableStateOf(Sort.FEATURED) }
    var sortOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(shop.bug("slow-catalog") && !shop.catalogShown) }
    // A lazy grid keeps its first visible item in place when the list changes,
    // so a new sort or filter would open partway down: start it at the top.
    val grid = rememberLazyGridState()
    LaunchedEffect(sort, category, query) { grid.scrollToItem(0) }
    LaunchedEffect(loading) {
        if (loading) delay(8000)
        loading = false
        shop.catalogShown = true
    }

    // The card for focusOn takes focus once it is laid out; a filter can have
    // hidden it, in which case focus stays where the platform puts it.
    LaunchedEffect(focusOn, loading) {
        if (focusOn != null && !loading) {
            // A lazy grid composes only what is on screen: bring the card in, give
            // it a frame to be laid out, then focus it. Coming back, the filters
            // are the defaults, so its place is its place in the catalog.
            grid.scrollToItem(CATALOG.indexOfFirst { it.id == focusOn }.coerceAtLeast(0))
            withFrameNanos { }
            runCatching { returnFocus.requestFocus() }
        }
    }

    val shown = CATALOG
        .filter { category == null || it.category == category }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .let { list ->
            when (sort) {
                Sort.FEATURED -> list
                Sort.NAME -> list.sortedBy { it.name.lowercase() }
                Sort.PRICE_ASC, Sort.PRICE_DESC -> {
                    val sorted = if (shop.bug("sort-as-text")) list.sortedBy { money(listPrice(it)) }
                    else list.sortedBy { listPrice(it) }
                    if (sort == Sort.PRICE_DESC) sorted.reversed() else sorted
                }
            }
        }

    Column(Modifier.fillMaxSize()) {
        RemoteField(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) { m ->
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search") },
                modifier = Modifier.fillMaxWidth().then(m).dpadLeaves().testTag("searchField"),
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") },
                modifier = Modifier.testTag("category_all"))
            Category.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) },
                    modifier = Modifier.testTag("category_${c.id}"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} item" + if (shown.size == 1) "" else "s", Modifier.testTag("resultCount"))
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortOpen = true }, modifier = Modifier.testTag("sortMenu")) {
                    Text("Sort: ${sort.label}")
                }
                // A menu is a window of its own, and a test tag reaches it only if said again here.
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortOpen = false },
                            modifier = Modifier.testTag("sort_${s.id}"))
                    }
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("catalogLoading"))
            }
            shown.isEmpty() -> Text("No products match \"${query.trim()}\".",
                Modifier.padding(24.dp).testTag("noResults"))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("catalogList"),
            ) {
                items(shown, key = { it.id }) { p ->
                    val card = Modifier.fillMaxWidth().testTag("product_${p.id}")
                    Card((if (p.id == focusOn) card.focusRequester(returnFocus) else card).clickable { onOpen(p.id) }) {
                        ProductImage(p.id, Modifier.fillMaxWidth().height(imageHeight))
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall)
                            Text(priceText(p), Modifier.testTag("price_${p.id}"),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (p.outOfStock) {
                                Text("Out of stock", Modifier.testTag("stock_${p.id}"),
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun optionPrefix(p: Product) = when (p.optionName) {
    "Size" -> "size"; "Model" -> "model"; "Amount" -> "amount"; else -> "option"
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun ProductScreen(shop: Shop, id: String, wide: Boolean = false, onViewCart: () -> Unit) {
    val focus = LocalFocusManager.current
    val p = product(id)
    var variant by remember { mutableStateOf<String?>(null) }
    var qty by remember { mutableIntStateOf(1) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableIntStateOf(0) }

    val needsVariant = p.variants.isNotEmpty()
    val outOfStock = p.outOfStock || (variant != null && variant in p.outOfStockVariants)
    val canAdd = (!needsVariant || variant != null) && (!outOfStock || shop.bug("out-of-stock-addable"))
    val shownPrice = when {
        p.digital && variant != null -> money(GIFT_AMOUNTS.getValue(variant!!))
        p.digital -> priceText(p)
        else -> money(unitPrice(Line(p.id, variant, 1), shop.pricingBugs))
    }

    // The notice stays while its View cart has focus: a remote takes a few
    // presses to reach it, and four seconds ran out on the way.
    var viewCartFocused by remember { mutableStateOf(false) }
    LaunchedEffect(notice, viewCartFocused) {
        if (notice > 0 && !viewCartFocused) {
            delay(4000)
            notice = 0
        }
    }

    // The details, once: below the photograph on a phone, beside it on a TV.
    val details: @Composable ColumnScope.() -> Unit = {
        Text(p.name, Modifier.testTag("productTitle"), style = MaterialTheme.typography.headlineSmall)
        Text(shownPrice, Modifier.testTag("productPrice"), style = MaterialTheme.typography.titleLarge)
        Text(p.blurb, style = MaterialTheme.typography.bodyMedium)
        if (p.outOfStock) {
            Text("Out of stock", Modifier.testTag("productStock"), color = MaterialTheme.colorScheme.error)
        }
        if (needsVariant) {
            Text(p.optionName!!, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.variants.forEach { v ->
                    FilterChip(selected = variant == v.id, onClick = { variant = v.id }, label = { Text(v.label) },
                        modifier = Modifier.testTag("${optionPrefix(p)}_${v.id}"))
                }
            }
            if (variant != null && variant in p.outOfStockVariants) {
                Text("Out of stock in ${p.variants.first { it.id == variant }.label}",
                    Modifier.testTag("variantStock"), color = MaterialTheme.colorScheme.error)
            }
        }
        if (p.digital) {
            RemoteField(Modifier.fillMaxWidth()) { m ->
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    label = { Text("Recipient email") },
                    singleLine = true,
                    isError = emailError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().then(m).dpadLeaves().testTag("giftEmail"),
                )
            }
            emailError?.let { Text(it, Modifier.testTag("giftEmailError"), color = MaterialTheme.colorScheme.error) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quantity")
            OutlinedButton(onClick = { qty-- }, enabled = qty > 1, modifier = Modifier.testTag("qtyMinus")) { Text("−") }
            Text("$qty", Modifier.testTag("qtyValue"), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { qty++ }, enabled = qty < shop.maxQty, modifier = Modifier.testTag("qtyPlus")) { Text("+") }
        }
        Button(
            onClick = {
                if (p.digital && !EMAIL.matches(email.trim())) {
                    emailError = "Enter a valid email address."
                    return@Button
                }
                shop.add(p.id, variant, qty, if (p.digital) email.trim() else null)
                notice++
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth().testTag("addToCart"),
        ) { Text("Add to cart") }
        Spacer(Modifier.height(64.dp))
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ProductImage(p.id, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = details)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductImage(p.id, Modifier.fillMaxWidth().height(180.dp))
                details()
            }
        }
        if (notice > 0) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Added to cart", Modifier.weight(1f).testTag("addedNotice"),
                        color = MaterialTheme.colorScheme.inverseOnSurface)
                    TextButton(onClick = onViewCart, modifier = Modifier.testTag("viewCart")
                        .onFocusChanged { viewCartFocused = it.isFocused }) {
                        Text("View cart", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, tag: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else null)
        Spacer(Modifier.weight(1f))
        Text(value, Modifier.testTag(tag), fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
fun CartScreen(shop: Shop, onKeepShopping: () -> Unit, onCheckout: () -> Unit) {
    var confirm by remember { mutableStateOf<CartLine?>(null) }
    var codeText by remember { mutableStateOf("") }
    // Apply puts the keyboard away, as a store's code field does.
    val focus = LocalFocusManager.current

    LazyColumn(Modifier.fillMaxSize().testTag("cartList"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shop.cart.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Your cart is empty", Modifier.testTag("emptyCart"), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onKeepShopping, modifier = Modifier.testTag("keepShopping")) { Text("Keep shopping") }
                }
            }
        }
        items(shop.cart, key = { it.key }) { line ->
            val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                if (v == SwipeToDismissBoxValue.EndToStart) confirm = line
                false
            })
            SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = false, backgroundContent = {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }) {
                Card(Modifier.fillMaxWidth().testTag("line_${line.key}")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(line.id, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line.label, style = MaterialTheme.typography.titleSmall)
                            Text(money(unitPrice(line.asLine(), shop.pricingBugs)) + " each",
                                style = MaterialTheme.typography.bodySmall)
                            Text(money(unitPrice(line.asLine(), shop.pricingBugs) * line.qty),
                                Modifier.testTag("lineTotal_${line.key}"), fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(onClick = {
                            if (line.qty <= 1) confirm = line else shop.setQty(line.key, line.qty - 1)
                        }, modifier = Modifier.testTag("lineMinus_${line.key}")) { Text("−") }
                        Text("${line.qty}", Modifier.padding(horizontal = 8.dp).testTag("lineQty_${line.key}"))
                        OutlinedButton(onClick = { shop.setQty(line.key, line.qty + 1) },
                            enabled = line.qty < shop.maxQty,
                            modifier = Modifier.testTag("linePlus_${line.key}")) { Text("+") }
                    }
                }
            }
        }
        if (shop.cart.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteField(Modifier.weight(1f)) { m ->
                            OutlinedTextField(value = codeText, onValueChange = { codeText = it }, singleLine = true,
                                label = { Text("Discount code") },
                                // Done applies it: on a TV, the keyboard's ✓ is how a remote gets there.
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); shop.applyCode(codeText) }),
                                modifier = Modifier.fillMaxWidth().then(m).dpadLeaves().testTag("codeField"))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { focus.clearFocus(); shop.applyCode(codeText) }, modifier = Modifier.testTag("applyCode")) { Text("Apply") }
                    }
                    shop.codeMessage?.let { m ->
                        Text(m.text, Modifier.testTag("codeMessage"),
                            color = if (m.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    if (shop.code != null) {
                        TextButton(onClick = { shop.removeCode(); codeText = "" }, modifier = Modifier.testTag("removeCode")) {
                            Text("Remove code ${shop.code}")
                        }
                    }
                }
            }
        }
        item {
            val order = price(shop.lines(), shop.code, bugs = shop.pricingBugs, times = shop.codeTimes)
            if (order is Priced.Order) {
                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Merchandise", money(order.merch), "sumMerch")
                    if (order.discount > 0) SummaryRow("Discount (${shop.code})", money(-order.discount), "sumDiscount")
                    if (order.gift > 0) SummaryRow("Gift cards", money(order.gift), "sumGift")
                    if (order.merch > 0) {
                        SummaryRow("Shipping", "Calculated at checkout", "sumShipping")
                        SummaryRow("Tax", "Calculated at checkout", "sumTax")
                    }
                    SummaryRow("Estimated total", money(order.merch - order.discount + order.gift), "sumTotal", bold = true)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onCheckout, enabled = shop.cart.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("checkoutBtn")) { Text("Checkout") }
                }
            }
        }
    }

    confirm?.let { line ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text("Remove ${line.label}?") },
            confirmButton = {
                TextButton(onClick = { shop.remove(line.key); confirm = null }, modifier = Modifier.testTag("removeConfirm")) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }, modifier = Modifier.testTag("removeCancel")) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun BugsScreen(shop: Shop) {
    LazyColumn(Modifier.fillMaxSize().testTag("bugList"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Deliberate bugs, for testing. Each stays on until turned off.",
                style = MaterialTheme.typography.bodyMedium)
        }
        items(BUGS, key = { it.id }) { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.id, style = MaterialTheme.typography.titleSmall)
                    Text(b.what, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = shop.bug(b.id),
                    onCheckedChange = { on -> if (on) shop.setBugs(setOf(b.id), emptySet()) else shop.setBugs(emptySet(), setOf(b.id)) },
                    modifier = Modifier.testTag("bug_${b.id}"),
                )
            }
        }
        item {
            OutlinedButton(onClick = { shop.reset() }, modifier = Modifier.fillMaxWidth().testTag("resetAll")) {
                Text("Reset the store (bugs stay)")
            }
        }
    }
}
