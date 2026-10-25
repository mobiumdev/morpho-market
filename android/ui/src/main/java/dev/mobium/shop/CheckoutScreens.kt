@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.mobium.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun methodLabel(m: Method?) = when (m) {
    Method.STANDARD -> "Standard, 3–5 days"
    Method.EXPRESS -> "Express, 1–2 days"
    Method.PICKUP -> "Pickup: San Francisco store"
    null -> "Delivered by email"
}

private fun physical(shop: Shop) = shop.cart.any { !product(it.id).digital }

/** The summary rows, each value with its id, as on every screen that shows the money. */
@Composable
fun OrderSummary(o: Priced.Order, code: String?, shipping: Boolean, taxKnown: Boolean, totalLabel: String) {
    @Composable
    fun row(label: String, value: String, tag: String, bold: Boolean = false) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, fontWeight = if (bold) FontWeight.Bold else null)
            Spacer(Modifier.weight(1f))
            Text(value, Modifier.testTag(tag), fontWeight = if (bold) FontWeight.Bold else null)
        }
    }
    Column {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        row("Merchandise", money(o.merch), "sumMerch")
        if (o.discount > 0) row("Discount ($code)", money(-o.discount), "sumDiscount")
        if (o.gift > 0) row("Gift cards", money(o.gift), "sumGift")
        if (o.merch > 0 && shipping) row("Shipping", if (o.shipping == 0) "Free" else money(o.shipping), "sumShipping")
        if (taxKnown) row("Tax", money(o.tax), "sumTax")
        else row("Tax", "Calculated with your address", "sumTax")
        val total = if (taxKnown) o.total else o.total - o.tax
        row(totalLabel, money(total), "sumTotal", bold = true)
    }
}

@Composable
fun DeliveryScreen(shop: Shop, checkout: CheckoutState, onContinue: () -> Unit) {
    val lines = shop.lines()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How should it reach you?", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            Method.entries.forEach { m ->
                val cost = (price(lines, shop.code, m, "CA", shop.pricingBugs, shop.codeTimes) as? Priced.Order)?.shipping ?: 0
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = checkout.method == m, onClick = { checkout.method = m }, role = Role.RadioButton)
                        .testTag("ship_${m.name.lowercase()}")
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = checkout.method == m, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(methodLabel(m), Modifier.weight(1f))
                    Text(if (cost == 0) "Free" else money(cost), Modifier.testTag("shipPrice_${m.name.lowercase()}"))
                }
            }
        }
        val o = price(lines, shop.code, checkout.method, "CA", shop.pricingBugs, shop.codeTimes)
        if (o is Priced.Order) OrderSummary(o, shop.code, shipping = true, taxKnown = false, totalLabel = "Before tax")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag("continueDelivery")) { Text("Continue") }
    }
}

@Composable
private fun Field(
    tag: String, label: String, value: String, error: String?, onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false,
) {
    // Enter is Done and gives up focus, so the keyboard goes as it does on iOS.
    // Under a remote it moves on to what is next, as a TV's forms do: given up,
    // focus started again at the top of the screen after every field.
    val focus = LocalFocusManager.current
    val remote = LocalRemote.current
    Column {
        RemoteField(Modifier.fillMaxWidth()) { m ->
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (remote) focus.moveFocus(FocusDirection.Down) else focus.clearFocus()
                }),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().then(m).dpadLeaves().testTag("field_$tag"),
            )
        }
        if (error != null) Text(error, Modifier.testTag("error_$tag"), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AddressScreen(shop: Shop, checkout: CheckoutState, onContinue: () -> Unit) {
    val method = if (physical(shop)) checkout.method else null
    val full = method == Method.STANDARD || method == Method.EXPRESS
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var stateOpen by remember { mutableStateOf(false) }
    val a = checkout.address
    fun set(f: (Address) -> Address) { checkout.address = f(checkout.address) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("addressForm"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (full) "Where should we send it?" else "Who is it for?", style = MaterialTheme.typography.titleMedium)
        Field("name", "Full name", a.name, errors["name"], { v -> set { it.copy(name = v) } })
        if (full) {
            Field("street", "Street address", a.street, errors["street"], { v -> set { it.copy(street = v) } })
            Field("city", "City", a.city, errors["city"], { v -> set { it.copy(city = v) } })
            Box {
                OutlinedButton(onClick = { stateOpen = true }, modifier = Modifier.testTag("statePicker")) {
                    Text(if (a.state.isEmpty()) "Choose a state" else "State: ${a.state}")
                }
                DropdownMenu(expanded = stateOpen, onDismissRequest = { stateOpen = false },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                    TAX.keys.forEach { st ->
                        DropdownMenuItem(text = { Text(st) }, onClick = { set { it.copy(state = st) }; stateOpen = false },
                            modifier = Modifier.testTag("state_$st"))
                    }
                }
            }
            errors["state"]?.let { Text(it, Modifier.testTag("error_state"), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            Text("We ship to CA, NY, OR, TX and WA.", style = MaterialTheme.typography.bodySmall)
            Field("zip", "ZIP code", a.zip, errors["zip"], { v -> set { it.copy(zip = v.filter { c -> c.isDigit() }.take(5)) } },
                keyboard = KeyboardType.Number)
        }
        Field("email", "Email for the receipt", a.email, errors["email"], { v -> set { it.copy(email = v) } },
            keyboard = KeyboardType.Email)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            errors = addressErrors(checkout.address, method)
            if (errors.isEmpty()) onContinue()
        }, modifier = Modifier.fillMaxWidth().testTag("continueBtn")) { Text("Continue") }
    }
}

@Composable
fun PaymentScreen(checkout: CheckoutState, onContinue: () -> Unit) {
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    val c = checkout.card
    fun set(f: (Card) -> Card) { checkout.card = f(checkout.card) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Test cards only: nothing is charged.", style = MaterialTheme.typography.bodySmall)
        Field("cardNumber", "Card number", c.number, errors["cardNumber"],
            { v -> set { it.copy(number = groupCard(digitsOf(v).take(16))) } }, keyboard = KeyboardType.Number)
        Field("cardExpiry", "Expiry (MM/YY)", c.expiry, errors["cardExpiry"],
            { v -> set { it.copy(expiry = slashExpiry(digitsOf(v).take(4))) } }, keyboard = KeyboardType.Number)
        Field("cardCvc", "Security code", c.cvc, errors["cardCvc"],
            { v -> set { it.copy(cvc = digitsOf(v).take(3)) } }, keyboard = KeyboardType.NumberPassword, secret = true)
        Field("cardName", "Name on card", c.name, errors["cardName"], { v -> set { it.copy(name = v) } })
        Button(onClick = {
            errors = cardErrors(checkout.card)
            if (errors.isEmpty()) onContinue()
        }, modifier = Modifier.fillMaxWidth().testTag("continuePayment")) { Text("Review order") }
    }
}

@Composable
fun ReviewScreen(shop: Shop, checkout: CheckoutState, onPlaced: (Order) -> Unit) {
    val scope = rememberCoroutineScope()
    val method = if (physical(shop)) checkout.method else null
    val a = checkout.address
    val o = price(shop.lines(), shop.code, checkout.method, a.state.ifEmpty { "CA" }, shop.pricingBugs, shop.codeTimes)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shop.cart, key = { it.key }) { l ->
            Row(Modifier.fillMaxWidth().testTag("reviewLine_${l.key}")) {
                Text("${l.label} × ${l.qty}", Modifier.weight(1f))
                Text(money(unitPrice(l.asLine(), shop.pricingBugs) * l.qty))
            }
        }
        item {
            Text(methodLabel(method), Modifier.testTag("reviewDelivery"), fontWeight = FontWeight.SemiBold)
            val where = when (method) {
                Method.STANDARD, Method.EXPRESS -> "${a.name}, ${a.street}, ${a.city}, ${a.state} ${a.zip}"
                else -> a.name
            }
            Text(where, Modifier.testTag("reviewAddress"))
            Text("Receipt to ${a.email}", style = MaterialTheme.typography.bodySmall)
            Text("Card ending ${digitsOf(checkout.card.number).takeLast(4)}", Modifier.testTag("reviewCard"))
        }
        item {
            if (o is Priced.Order) OrderSummary(o, shop.code, shipping = true, taxKnown = true, totalLabel = "Total")
            Text("Tax rates here are demo rates.", Modifier.testTag("taxNote"), style = MaterialTheme.typography.bodySmall)
        }
        item {
            checkout.paymentError?.let {
                Text(it, Modifier.testTag("paymentError"), color = MaterialTheme.colorScheme.error)
            }
            val busy = checkout.placing > 0
            Button(
                onClick = {
                    // What is placed is the cart as it is at the tap.
                    val lines = shop.cart.toList()
                    val code = shop.code
                    val times = shop.codeTimes
                    val m = checkout.method
                    val address = checkout.address
                    val card = checkout.card.number
                    checkout.paymentError = null
                    checkout.placing++
                    scope.launch {
                        delay(placingMillis(card))
                        val declined = cardOutcome(card, shop.bugs)
                        checkout.placing--
                        if (declined != null) {
                            checkout.paymentError = declined
                        } else {
                            onPlaced(shop.place(lines, code, times, m, address))
                        }
                    }
                },
                enabled = !busy || shop.bug("double-order"),
                modifier = Modifier.fillMaxWidth().testTag("placeOrder"),
            ) { Text("Place order") }
            if (busy) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag("placingOrder"))
                    Spacer(Modifier.width(8.dp))
                    Text("Placing your order…")
                }
            }
        }
    }
}

@Composable
fun ConfirmationScreen(shop: Shop, number: String, onKeepShopping: () -> Unit) {
    val o = shop.orders.firstOrNull { it.number == number } ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        MobiumMark(Modifier.size(120.dp))
        Text("Order ${o.number} placed", Modifier.testTag("orderNumber"), style = MaterialTheme.typography.titleLarge)
        Text(money(o.total), Modifier.testTag("confirmTotal"), style = MaterialTheme.typography.titleMedium)
        Text("A receipt is on its way to ${o.address.email}.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onKeepShopping, modifier = Modifier.testTag("keepShopping")) { Text("Keep shopping") }
    }
}

@Composable
fun OrdersScreen(shop: Shop, onOpen: (String) -> Unit) {
    if (shop.orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No orders yet", Modifier.testTag("noOrders"), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("orderList"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${shop.orders.size} order" + if (shop.orders.size == 1) "" else "s", Modifier.testTag("orderCount")) }
        items(shop.orders, key = { it.number }) { o ->
            Card(Modifier.fillMaxWidth().testTag("order_${o.number}").clickable { onOpen(o.number) }) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(o.number, style = MaterialTheme.typography.titleSmall)
                        Text("${o.lines.sumOf { it.qty }} items · ${methodLabel(o.method)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(o.total), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ReceiptScreen(shop: Shop, number: String) {
    val o = shop.orders.firstOrNull { it.number == number } ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(o.number, Modifier.testTag("receiptNumber"), style = MaterialTheme.typography.titleLarge)
        o.lines.forEach { l -> Text("${l.label} × ${l.qty}") }
        Text(methodLabel(o.method), Modifier.testTag("receiptDelivery"))
        OrderSummary(Priced.Order(o.merch, o.discount, o.shipping, o.tax, o.gift, o.total, 0), o.code,
            shipping = true, taxKnown = true, totalLabel = "Total")
    }
}
