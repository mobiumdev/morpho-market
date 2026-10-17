package dev.mobium.shop

// The bug switch (SPEC.md, "The bug switch"). Every bug is off by default,
// looks like an ordinary defect, and stays on across launches until turned off.

data class Bug(val id: String, val what: String)

val BUGS: List<Bug> = listOf(
    Bug("tax-before-discount", "Tax is charged on merchandise before the code."),
    Bug("free-shipping-before-discount", "The \$75 free-shipping line is judged before the code."),
    Bug("free-shipping-over", "\$75.00 exactly does not ship free."),
    Bug("round-down", "Percentages and tax are truncated, not rounded."),
    Bug("coupon-twice", "The same code can be applied twice."),
    Bug("expired-accepted", "EXPIRED20 gives 20% off."),
    Bug("express-free", "SHIPFREE frees express too."),
    Bug("xxl-no-surcharge", "The XXL tee costs the same as the others."),
    Bug("out-of-stock-addable", "Out-of-stock items can be added to the cart."),
    Bug("stale-badge", "The cart badge does not drop when items are removed."),
    Bug("qty-eleven", "The quantity stepper goes to 11."),
    Bug("declined-succeeds", "The declined card places an order."),
    Bug("double-order", "Place order can be tapped twice while placing."),
    Bug("sort-as-text", "Sorting by price compares prices as text."),
    Bug("slow-catalog", "The catalog appears after 8 seconds."),
    Bug("lost-delivery", "Going back from payment resets delivery to standard."),
    Bug("stale-code-message", "A code refused after another refused code keeps the first one's message."),
)

val BUG_IDS: Set<String> = BUGS.map { it.id }.toSet()
