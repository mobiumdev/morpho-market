# Morpho Market: the spec

**Morpho Market**, a store for Mobium merch, built twice: once in **SwiftUI** for iOS and once in
**Jetpack Compose** for Android. Both builds use the same ids, the same text and
the same prices, so one Mobium check drives both and branches only where the
platforms differ (MobiumTV's `tv/` and `tvos/` are the model). It is an app
under test: a control for what a real checkout puts in front of a tool. It
has a switch of deliberate bugs, so a test can be shown to catch them.

## Why this app, and why this stack

- **Mobium's own apps are cross-platform.** MobiumApp is React Native, and
  its demo is Flutter. SwiftUI and Compose were met only in other people's
  apps (Ice Cubes: CHALLENGES 213–217; Seal: 176–178), where nothing can be
  changed or switched on. A native pair of our own gives a positive control
  in both toolkits.
- **Checkout is what tests most often cover in the field**, and it combines
  everything Mobium has had to learn one piece at a time: lazy lists, menus,
  pickers, steppers, sheets, alerts, the keyboard over a form, a disabled
  button, a slow step, a password-like field, and arithmetic a test must
  assert.
- **A third build later, not now**: Compose Multiplatform on iOS draws its
  own screen and publishes semantics, as Flutter does. That is a new
  hierarchy shape worth its own measurement, once the native pair is done.

**Constraints.** No third-party dependencies in either app: Jetpack and Apple
frameworks only. No network: catalog, prices and orders are local, so a run
is deterministic and works offline. Money is integer cents everywhere, and
nothing is a float.

| | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose (Material 3) |
| Language | Swift 6 | Kotlin 2 |
| Minimum | iOS 17 | API 26 |
| Measured on | iPhone 17 Pro simulator (26.5), the iPhone 15 Plus | the `mobium-test` AVD (API 35), API 37, the Pixel 8 Pro |
| Bundle / package | `dev.mobium.shop` | `dev.mobium.shop` |
| Storage | `UserDefaults` | `SharedPreferences` (no DataStore: one dependency fewer) |

## The catalog

Thirteen products. `spec/pricing.py` holds the same table, and is the
reference.

| id | Name | Price | Weight | Category | Options and edges |
|---|---|---|---|---|---|
| `tee` | Logo Tee | $24.00 | 200 g | Apparel | size XS–XXL; **XXL +$2.00** |
| `hoodie` | Map Before You Tap Hoodie | $58.00 | 700 g | Apparel | size S–XL; **XL out of stock** |
| `hat` | Morpho Dad Hat | $26.00 | 150 g | Apparel | |
| `beanie` | Headless Beanie | $22.00 | 120 g | Apparel | **out of stock** |
| `socks` | Zero Runtime Dependencies Socks | $13.99 | 80 g | Apparel | |
| `mug` | Wait For It Mug | $16.00 | 400 g | Drinkware | |
| `tumbler` | No sleep() Tumbler | $32.00 | 450 g | Drinkware | |
| `case` | Real Device Verified Phone Case | $29.00 | 60 g | Accessories | model: iPhone 15, iPhone 17 Pro, Pixel 7, Pixel 8 Pro |
| `tote` | Leave Nothing On The Device Tote | $19.00 | 250 g | Accessories | |
| `pins` | @e1 Enamel Pin Set | $12.00 | 50 g | Accessories | |
| `magnet` | Works On My Emulator Magnet | $5.99 | 20 g | Accessories | |
| `stickers` | CHALLENGES Sticker Pack | $7.49 | 10 g | Accessories | |
| `giftcard` | Gift Card | $25 or $50 | none | Gift Cards | **digital**: no shipping, no tax, no codes; a recipient email |

Each item has a photograph, bundled in both apps and loaded locally
(`android/app/src/main/res/drawable-nodpi/product_<id>.jpg`,
`ios/MorphoMarket/Products/product_<id>.jpg`). They were generated with
Z-Image-Turbo (Apache-2.0); the prompts are in `docs/product-images.md`. A
photograph is decorative: it has no accessibility label, so the item's name
stays the one thing a screen reader and a test find. The morpho butterfly,
drawn in code, remains the brand mark on the order-placed screen.

## Pricing

In this order:

1. **Merchandise** is the sum of price × quantity over physical items. Gift
   cards are kept apart.
2. **A code** applies to merchandise only. A percentage is rounded half up
   to the cent, and a fixed amount never exceeds the merchandise. One code
   per order.
3. **Shipping** depends on the method and the total weight:
   - **Standard**: $5.99 up to 1 kg, plus $1.50 for each started 500 g
     above that. **Free when merchandise after the code is $75.00 or more**,
     or with `SHIPFREE`.
   - **Express**: $14.99 up to 1 kg, plus $2.50 for each started 500 g
     above that. Never free.
   - **Pickup**: free, at the San Francisco store, so no address is asked.
   - An order of gift cards alone has no shipping step.
4. **Tax** is the state's rate × (merchandise after the code, plus shipping
   where the state taxes shipping), rounded half up to the cent. Pickup is
   taxed at the store's state, CA. These are **demo rates**, labeled as
   such in the app:

   | State | Rate | Shipping taxed |
   |---|---|---|
   | CA | 7.25% | no |
   | NY | 8.875% | yes |
   | OR | 0% | — |
   | TX | 6.25% | yes |
   | WA | 6.5% | yes |

   Only these five states are offered. The picker is a menu, and its
   window is the case CHALLENGES 225 describes.
5. **Total** = merchandise − code + shipping + tax + gift cards.

### Codes

Codes are matched ignoring case and surrounding spaces. Each message below
is the exact text, and tests assert it.

| Code | Effect | Refused with |
|---|---|---|
| `WELCOME10` | 10% off merchandise | — |
| `MOBIUM5` | $5.00 off, merchandise of $30.00 or more | "Add $X.XX more to use this code." |
| `SHIPFREE` | free standard shipping (not express) | — |
| `EXPIRED20` | — | "That code has expired." |
| anything else | — | "That code isn't valid." |
| any code, gift cards alone | — | "Codes don't apply to gift cards." |
| a second code | — | "Remove WELCOME10 to use another code." |

A code that stops qualifying (an item removed takes `MOBIUM5` below $30)
is removed, with the minimum message shown in its place.

### Worked examples

From `spec/pricing-cases.json`; `python3 spec/pricing.py` checks all 17.
A, B and D were worked out by hand before the code existed.

| Case | Cart | Code, method, state | Total |
|---|---|---|---|
| A | 2 × Logo Tee M, Wait For It Mug | WELCOME10, standard, NY | **$69.23** (discount $6.40, shipping $5.99, tax $5.64) |
| B | Hoodie L, Socks, Magnet ($77.98) | MOBIUM5, standard, CA | **$84.26**: the code takes it under $75, so shipping is $5.99 |
| C1 | Tee, Tumbler, Tote | standard, OR | **$75.00**: exactly the line, so shipping is free |
| C2 | Tumbler, Case, Socks | standard, OR | **$80.98**: $74.99, one cent short |
| D | 3 × Tumbler (1,350 g) | express, TX | **$120.58** (shipping $17.49, tax on it too) |
| H | 5 × Sticker Pack ($37.45) | `welcome10 `, pickup | discount **$3.75**: 3.745 rounds half up |
| M | Hoodie M | pickup, address in OR | tax **$4.21**: taxed at CA, and 4.205 rounds up |

## Screens

One flow, a tab bar of three: **Shop**, **Cart** (badge) and **Orders**.

1. **Catalog** (`catalogList`): a lazy two-column grid, longer than the
   screen. It has a search field (`searchField`), category chips
   (`category_all`, `category_apparel`, …) and a sort menu (`sortMenu`:
   Featured, Price low to high, Price high to low, Name). Each item is
   `product_<id>`, with its price in `price_<id>`. Out of stock shows a label
   and stays tappable.
2. **Product** (`productTitle`, `productPrice`): option chips (`size_M`,
   `model_pixel8pro`, `amount_25`), a quantity stepper (`qtyMinus`,
   `qtyValue`, `qtyPlus`, 1–10), and **Add to cart** (`addToCart`). The
   button is disabled until a required option is chosen, and disabled for
   out of stock. Adding shows a snackbar or banner, "Added to cart"
   (`addedNotice`), with **View cart** (`viewCart`). The gift card has
   a recipient email field (`giftEmail`), checked on add.
3. **Cart** (`cartList`): each line is `line_<id>_<variant>` with its own
   stepper. Swipe to delete, and a stepper taken to 0, both ask "Remove
   Logo Tee (M)?" with Remove and Cancel: two buttons, the case
   `app_alert` accept and dismiss are for. The code field (`codeField`),
   **Apply** (`applyCode`), the code's message (`codeMessage`) and
   **Remove code** (`removeCode`) sit here. Applied, a code says so:
   "WELCOME10 applied: 10% off merchandise.", "MOBIUM5 applied: $5.00
   off.", "SHIPFREE applied: free standard shipping." Applied again, it
   says "WELCOME10 is already applied." The summary rows are
   `sumMerch`, `sumDiscount`, `sumShipping` ("Calculated at checkout" until
   then), `sumTax`, `sumGift` and `sumTotal`. **Checkout** (`checkoutBtn`)
   is disabled on an empty cart, which says "Your cart is empty"
   (`emptyCart`).
4. **Checkout, delivery** comes first (changed 2026-10-14, see below), then
   **the address** (`addressForm`): name, street, city, state picker
   (`statePicker`, items `state_CA` …), ZIP (5 digits) and email. Pickup
   asks only a name and an email, and so do gift cards alone. Each field is
   `field_<name>`, with its error in `error_<name>`. The form is long
   enough that the keyboard covers **Continue** (`continueBtn`). Gift cards
   alone skip to payment.
5. **Delivery**, before the address: three radios, `ship_standard`,
   `ship_express` and `ship_pickup`, each with its price for this cart
   (`shipPrice_standard` …); **Continue** is `continueDelivery`. Tax waits
   for the address. Delivery came after the address in the first draft,
   which asked a pickup for an address it then did not use.
6. **Checkout, payment**: card number (`cardNumber`, grouped in fours as
   typed, Luhn-checked), expiry (`cardExpiry`, MM/YY, not past by the
   device's date), CVC (`cardCvc`, secure) and name on card. The test
   cards:

   | Number | Outcome |
   |---|---|
   | 4242 4242 4242 4242 | approved |
   | 4000 0000 0000 0002 | "Your card was declined." |
   | 4000 0000 0000 9995 | "Insufficient funds." |
   | 4000 0000 0000 0119 | approved after **6 seconds** |

7. **Review**: every line, the address or "Pickup: San Francisco store", and
   the summary. **Place order** (`placeOrder`) shows a spinner
   (`placingOrder`) for 1.5 s (6 for the slow card), and is disabled while
   it runs.
8. **Confirmation**: "Order MS-000001 placed" (`orderNumber`, counting up
   per install), the total (`confirmTotal`) and **Keep shopping**. The
   cart is emptied. `WELCOME10` is spent per install: used again, it says
   "That code has already been used."
9. **Orders**: a list of past orders (`order_MS-000001`), each opening its
   receipt with the same summary rows.

Back works from every screen. On iOS that means the navigation bar's back
button, since iOS has no back button.

### Ids and text

- **Ids are identical on both platforms**: `accessibilityIdentifier` in
  SwiftUI, and `Modifier.testTag` with `testTagsAsResourceId = true` in
  Compose. That makes Compose report a **bare** resource id, the shape React
  Native has (CHALLENGES 55). That is deliberate: it is how Compose apps
  that set test tags look.
- Every price is text that reads as money ("$24.00"), and the Cart tab's
  label says the count ("Cart, 3 items"). The badge has no id: Material 3
  clears a navigation item's icon semantics, badge and all, so on Android the
  label is the only way anything, a screen reader included, reads the count.
- A popup (a menu, a dialog) is a window of its own in Compose. A test tag
  inside one is exposed only if `testTagsAsResourceId` is set on the popup
  too. Setting it on the screen alone left every menu item and dialog button
  without its id, which is what a Compose app that sets test tags meets.
- All copy is American English.

## The bug switch

Every bug is plausible, invisible in the UI and off by default. A link turns
them on, and they persist across launches until turned off:

```
mobiumshop://bugs?on=tax-before-discount,coupon-twice
mobiumshop://bugs?off=all
mobiumshop://reset          cart, code, orders and spent codes cleared; bugs kept
```

Reset leaves the bugs alone on purpose: a test resets in its `beforeEach`, and a
bug matrix that switched a bug on before the run must still have it on. A link
naming something that is not a bug is refused with a dialog ("Not a bug:
x", `linkError`), never ignored.

Mobium reaches the link with `app_open_url` on both platforms. The reset
link is also the reset for a real iPhone, which has no clear-data. A hidden
**Bugs** screen (a long press on the Shop title) lists each bug with a switch
(`bug_<id>`), for a person.

| Bug | What goes wrong | Caught by |
|---|---|---|
| `tax-before-discount` | tax on merchandise before the code | A: tax $6.21, not $5.64 |
| `free-shipping-before-discount` | the $75 line judged before the code | B: shipping $0, total $78.27, not $84.26 |
| `free-shipping-over` | $75.00 exactly is not free (`>` for `≥`) | C1 |
| `round-down` | percentages and tax truncated | H: $3.74; M: $4.20 |
| `coupon-twice` | the same code applies twice | A: discount $12.80 |
| `expired-accepted` | `EXPIRED20` gives 20% off | J |
| `express-free` | `SHIPFREE` frees express too | K |
| `xxl-no-surcharge` | the XXL tee is $24.00 | N |
| `out-of-stock-addable` | Add to cart enabled for the beanie | a disabled-button check |
| `stale-badge` | the badge does not drop when a line is removed | badge after delete |
| `qty-eleven` | the stepper goes to 11 | the stepper's limit |
| `declined-succeeds` | the declined card places an order | the declined card |
| `double-order` | Place order is not disabled while placing; two taps, two orders | Orders count |
| `sort-as-text` | price sort compares "$5.99" > "$24.00" as text | sort order |
| `slow-catalog` | the catalog appears after 8 s | a test that does not wait |
| `lost-delivery` | going back from payment resets delivery to standard | the review's shipping |

A test suite is scored by which of these bugs it catches: each must fail at
least one test, and with none on, every test must pass.

## What it is a control for

| Mobium rule or CHALLENGES | Where it is met here |
|---|---|
| Lazy lists: a row not in the tree until scrolled to | catalog grid, cart |
| Menus close by a touch outside (220–221); an Android popup is a window (225) | sort menu, state picker |
| `app_check` on radios | delivery method |
| An action waits for a target to be enabled | Add to cart, Checkout, Place order |
| A target under the keyboard is refused (107) | Continue on the address form |
| A target under a dialog is refused (105) | the remove alert |
| A sheet or screen in front is not an Alert (217) | gift card sheet on iOS |
| `wait_for` text, exact (233) | every summary row; "MS-000001" |
| Redaction (43) | **Measured 2026-10-14 on Android**: the security code (`KeyboardType.NumberPassword`) is a password field, and Mobium masks it ("not echoed", `role=password`). The **card number is not**: `map` lists the field by its digits, and a fill's result repeats all 16. Whether Mobium should treat a card number as secret is Mobium's decision; the app only makes the case |
| Swipe gestures | swipe to delete |
| A spinner waited out (223) | placing an order; `slow-catalog` |

## Tests that come with it

- **Unit tests in each app** run `spec/pricing-cases.json` against that
  platform's pricing code. Two implementations, one table: if either
  drifts, its own CI fails.
- **`mobium test` files** (`tests/*.test.json`): browse, add, codes, the
  three delivery methods, the four cards, orders. Written against the ids,
  so one file runs on both platforms.
- **A bug matrix**: every bug × the suite. Each bug must fail at least one
  test, and with no bugs on, everything passes, or the matrix means nothing.

## Build order

1. **Android, Compose**: catalog, product, cart, codes, pricing and its unit
   tests, and the bug switch. Then a `mobium test` file and the bug matrix
   on the emulator.
2. **The same in SwiftUI**, the same test file unchanged.
3. Checkout (address, delivery, payment, review, orders) on both.
4. On the real devices: the Pixel and the iPhone. A checkout on someone's
   phone uses test cards and a local store, so nothing leaves the app.
5. Later: Compose Multiplatform as a third build. A gray-box hook for
   "placing" is out of scope, since the library is React Native's.

## Open questions

1. **Real merch**: are any of these meant to exist (print on demand)? That
   would change prices and names; nothing here assumes so.
2. **Card number redaction**: decide in Mobium, not here. The app only
   makes the case.
