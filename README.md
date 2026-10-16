# Morpho Market

A store for [Mobium](https://github.com/mobiumdev/mobium) merch, built
natively twice, in **SwiftUI** for iOS and **Jetpack Compose** for Android.
It is an *app under test*: a full checkout with discount codes, tax and
shipping, and a switch of **deliberate bugs** that a test suite can be
scored against.

Nothing is for sale. The catalog, prices and orders are local, and there is
no network, so a run is deterministic and works offline.

## Contents

- [Why it exists](#why-it-exists)
- [What is in it](#what-is-in-it)
- [Build and install](#build-and-install)
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
| `android/` | The Compose app (`dev.mobium.shop`), with unit tests over the shared cases |
| `ios/` | The SwiftUI app (`dev.mobium.shop`), an XcodeGen project, with unit tests over the same cases |
| `tests/` | One Mobium test suite for both platforms (`shop.test.json`, 14 tests; `checkout.test.json`, 11 tests), the configs, and the bug matrix (`matrix.sh`, `score.py`) |
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
./gradlew :app:testDebugUnitTest :app:assembleRelease
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

## Run the tests

Install [Mobium](https://github.com/mobiumdev/mobium) (`go install
github.com/mobiumdev/mobium/cmd/mobium@latest`), then from the repository
root:

```sh
mobium test --config tests/mobium.config.json                         # Android, emulator-5554
MOBIUM_IOS_DEVICE=<udid> mobium test --config tests/ios.config.json   # iOS simulator
```

Edit `tests/mobium.config.json` to name another Android device.

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
that appears after 8 seconds, fails every test on both. The JUnit results and
scores are in [evidence/](evidence/README.md).

## License

Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE).
