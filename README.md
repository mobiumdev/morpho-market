# Morpho Market

A store for [Mobium](https://github.com/mobiumdev/mobium) merch, built
natively twice, in **SwiftUI** for iOS and **Jetpack Compose** for Android,
and for TVs: **Fire TV**, Android TV and Google TV, driven with a remote. It
is an *app under test*: a full checkout with discount codes, tax and
shipping, and a switch of **deliberate bugs** that a test suite can be
scored against.

Nothing is for sale. The catalog, prices and orders are local, and there is
no network, so a run is deterministic and works offline.

## See it run

One Mobium test, checkout test **A** from `tests/checkout.test.json`, on
both platforms: two Logo Tees and a mug, the code WELCOME10, standard
shipping to New York, the spec's test card. It asserts every line of the
summary and places the order. The same test file drives both apps.
Shown at double speed.

| Android (Jetpack Compose) | iOS (SwiftUI) |
|---|---|
| <img src="docs/media/android-checkout.gif" width="270" alt="Checkout test A on an Android 15 emulator, from the catalog to the order placed"> | <img src="docs/media/ios-checkout.gif" width="270" alt="Checkout test A on an iPhone 17 Pro simulator, from the catalog to the order placed"> |
| [Full run, MP4](docs/media/android-checkout.mp4) | [Full run, MP4](docs/media/ios-checkout.mp4) |

The screens the test passes through, Android above and iOS below:

| Catalog | Product | Cart | Placed |
|---|---|---|---|
| <img src="docs/media/android-catalog.png" width="180" alt="Android catalog with product photographs"> | <img src="docs/media/android-product.png" width="180" alt="Android product page: Logo Tee, $24.00"> | <img src="docs/media/android-cart.png" width="180" alt="Android cart with two tees and a mug"> | <img src="docs/media/android-placed.png" width="180" alt="Android: order MS-000001 placed, $69.23"> |
| <img src="docs/media/ios-catalog.png" width="180" alt="iOS catalog with product photographs"> | <img src="docs/media/ios-product.png" width="180" alt="iOS product page: Logo Tee, $24.00"> | <img src="docs/media/ios-cart.png" width="180" alt="iOS cart: WELCOME10 applied, $57.60 before shipping and tax"> | <img src="docs/media/ios-placed.png" width="180" alt="iOS: order MS-000001 placed, $69.23"> |

**On a TV**, the same checkout with the remote (`tests-tv/tv.test.json`):
every move a D-pad press, every choice a select, and a check after each press
of where focus went. Shown at double speed.

<img src="docs/media/tv-checkout.gif" width="480" alt="The TV checkout driven with a remote on an Android TV emulator, from the catalog to the order placed">

[Full run, MP4](docs/media/tv-checkout.mp4)

| Catalog | Product | Cart | Review | Placed |
|---|---|---|---|---|
| <img src="docs/media/tv-catalog.png" width="170" alt="TV catalog in five columns"> | <img src="docs/media/tv-product.png" width="170" alt="TV product page: the photograph beside size M chosen and quantity 2"> | <img src="docs/media/tv-cart.png" width="170" alt="TV cart with WELCOME10 applied"> | <img src="docs/media/tv-review.png" width="170" alt="TV review: total $69.23, focus on Place order"> | <img src="docs/media/tv-placed.png" width="170" alt="TV: order MS-000001 placed, $69.23"> |

The product photographs were generated with an open model; how, and every
prompt, is in [docs/product-images.md](docs/product-images.md).

Recorded on an Android 15 emulator, an iPhone 17 Pro simulator (iOS 26.5)
and an Android TV emulator (Android 14, 1080p), with Mobium at `74ce9e9`.

## Contents

- [See it run](#see-it-run)
- [Why it exists](#why-it-exists)
- [What is in it](#what-is-in-it)
- [Build and install](#build-and-install)
- [On a TV](#on-a-tv)
- [Run the tests](#run-the-tests)
- [The bug switch](#the-bug-switch)
- [Results](#results)
- [License](#license)

## Why it exists

Mobium drives mobile apps for AI agents and people. Its own app under test,
[MobiumApp](https://github.com/mobiumdev/mobium-app), is React Native, and
its demo is Flutter. SwiftUI and Jetpack Compose had been met only in other
people's apps, where nothing can be changed or switched on. Morpho Market is
a native pair of our own, so both toolkits have a positive control.

A checkout is what tests most often cover in the field, and it puts most of
what a tool has to handle on one path: lazy lists, menus, pickers, steppers,
sheets, alerts, the keyboard over a form, disabled buttons, a slow step, a
password-like field, and arithmetic a test must check.

Building it found five defects in Mobium, all fixed: mobiumdev/mobium #213
to #217, the last reproduced and confirmed in #218.

## What is in it

| Path | What it is |
|---|---|
| [SPEC.md](SPEC.md) | The spec: catalog, pricing rules with worked examples, screens, ids, and the bug switch |
| `spec/` | The pricing rules as a reference (`pricing.py`), the catalog, and the shared pricing cases |
| `android/` | Four Gradle modules: `shared`, the store's logic (catalog, pricing, checkout, the bug switch), whose unit tests run the shared cases; `ui`, the screens and product photographs; `app`, the phone app (`dev.mobium.shop`); and `tv`, the TV app (`dev.mobium.shop.tv`) |
| `ios/` | The SwiftUI app (`dev.mobium.shop`), an XcodeGen project, with unit tests over the same cases |
| `tests/` | One Mobium test suite for both phone platforms (`shop.test.json`, 14 tests; `checkout.test.json`, 11 tests), the configs, and the bug matrix (`matrix.sh`, `score.py`) |
| `tests-tv/` | The TV's test, driven with the remote (`tv.test.json`), and its config. A folder of its own: `mobium test` reads every test file under the folder it is given |
| `evidence/` | The bug matrix's results on both platforms; see [evidence/README.md](evidence/README.md) |

Both apps use the same accessibility ids, the same text and the same prices,
so one test file drives both. No third-party dependencies in either app:
Jetpack and Apple frameworks only. Money is integer cents everywhere.

## Build and install

**The pricing reference** (Python 3):

```sh
python3 spec/pricing.py     # checks the spec's worked examples against the rules
```

**Android** (JDK 17 or later, the Android SDK, an emulator or phone):

```sh
cd android
./gradlew :shared:testDebugUnitTest :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key: an app under test, installed
by hand, never published.

**iOS** (Xcode 26 and [XcodeGen](https://github.com/yonaskolb/XcodeGen), an
iOS simulator):

```sh
cd ios
xcodegen generate
xcodebuild test -project MorphoMarket.xcodeproj -scheme MorphoMarket \
  -destination 'platform=iOS Simulator,id=<udid>'
xcodebuild -project MorphoMarket.xcodeproj -scheme MorphoMarket -configuration Release \
  -sdk iphonesimulator -destination id=<udid> -derivedDataPath build build
xcrun simctl install <udid> "build/Build/Products/Release-iphonesimulator/Morpho Market.app"
```

For an iPhone, set `DEVELOPMENT_TEAM` in `ios/project.yml` to your team id
before `xcodegen generate`.

**A TV** (the same toolchain as Android; a Fire TV, an Android TV, or Google's
Android TV emulator image, `system-images;android-34;android-tv;arm64-v8a`):

```sh
cd android
./gradlew :tv:assembleRelease
adb install -r tv/build/outputs/apk/release/tv-release.apk
```

On a Fire TV, open Settings > Device & Software (My Fire TV on older Fire
OS) > About and select the device's name seven times; then turn on ADB
Debugging under Developer Options, and `adb connect <tv-address>:5555` before
installing. The app runs on Android 7.1 and later,
which is Fire OS 6, 7 and 8. On the Android TV emulator it is listed under
Installed Apps with its banner. It has not yet been run on a real Fire TV.

## On a TV

The TV app is the same store: the same catalog, prices, codes, bug switch and
screens, from the same `shared` and `ui` modules, so a price or a bug cannot
differ between the phone and the TV. What changes is what a remote needs:

- **Focus is the cursor.** Every control shows clearly which one has it, and
  the D-pad moves it. Back on the remote is the phone's back arrow.
- **A 1080p layout:** the catalog in five columns, a product's photograph
  beside its details, and forms centered at a readable width.
- **Back from a product returns focus to its card**, not to the search field
  at the top.
- **A text field opens on select.** Focus passes over a field without
  opening the keyboard, and select opens it. A field that opened its keyboard
  the moment focus arrived took every key after it, and the remote could not
  leave.
- **Done moves to the next field** in a form, instead of sending focus back
  to the top of the screen.
- **"Added to cart" stays while its View cart has focus.** It is gone after
  four seconds on a phone; a remote took longer than that to reach it.

The phone app shares those screens and behaves exactly as before: each change
applies only where a remote drives the app.

## Run the tests

Install [Mobium](https://github.com/mobiumdev/mobium) (`go install
github.com/mobiumdev/mobium/cmd/mobium@latest`), then from the repository
root:

```sh
mobium test --config tests/mobium.config.json                            # Android, emulator-5554
MOBIUM_IOS_DEVICE=<udid> mobium test --config tests/ios.config.json      # iOS simulator
MOBIUM_TV_DEVICE=<serial> mobium test --config tests-tv/tv.config.json   # a TV, by its adb serial
```

Edit `tests/mobium.config.json` to name another Android device. The TV's
test is the phone suite's checkout test A, driven as a person on a sofa
would: every move is a D-pad press, every choice a select, and after each
press the test checks where focus went. Text is filled once a field is open,
as a TV's keyboard would type it.

## The bug switch

Sixteen deliberate bugs, each a plausible mistake in a checkout: a code
applied twice, tax taken before the discount, a declined card that places an
order, a price sort that compares text, and more. [SPEC.md](SPEC.md) lists
them all, with what each breaks. They are switched by a deep link:

```sh
mobium open "mobiumshop://bugs?on=sort-as-text"   # one bug on
mobium open "mobiumshop://bugs?off=all"           # every bug off
```

**The bug matrix** runs the suite with no bug on, then with each bug on
alone, and scores which tests catch each one. With no bug on, every test
must pass, or no other row means anything.

```sh
sh tests/matrix.sh out/android && python3 tests/score.py out/android
CONFIG=tests/ios.config.json DEVICE=<udid> MOBIUM_IOS_DEVICE=<udid> sh tests/matrix.sh out/ios
python3 tests/score.py out/ios
```

`MOBIUM` names a `mobium` binary other than the one on `PATH`.

## Results

On an Android 15 emulator and an iPhone 17 Pro simulator (iOS 26.5), with
Mobium at `2e37123`:

| | Android | iOS |
|---|---|---|
| The suite, no bug on | 25 of 25 | 25 of 25 |
| Bugs caught | **16 of 16** | **16 of 16** |

The same tests catch each bug on both platforms. `slow-catalog`, a catalog
that appears after 8 seconds, fails every test on both.

**On a TV**, the one test (Android TV emulator, Android 14): with no bug on
it passes, and it catches 3 of the 16 bugs (`slow-catalog`, `stale-badge`,
`tax-before-discount`), exactly the bugs the phone's checkout test A catches
on its own. The other bugs are caught on the phones by tests the TV does not
have yet. The JUnit results and scores are in [evidence/](evidence/README.md).

## License

Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE).
