# Evidence: the bug matrix on both platforms

Each folder holds one run of the suite per bug, and one with no bug on:

- `android-bug-matrix/`: an Android 15 emulator (Pixel 7 profile).
- `ios-bug-matrix/`: an iPhone 17 Pro simulator, iOS 26.5.
- `tv-bug-matrix/`: the TV app's one test (`tests-tv/tv.test.json`) on an
  Android TV emulator, Android 14.

The phone matrices ran on 2026-10-14 and 2026-10-15 with Mobium at `2e37123`;
the TV's on 2026-10-16 with Mobium at `74ce9e9`. All used `tests/matrix.sh`.
`stale-code-message`, the seventeenth bug, came later: its row on each phone
ran on 2026-10-17 with Mobium at `bfcb09f`, the same suite run by
[mobium-roundtrip](https://github.com/mobiumdev/mobium-roundtrip)'s
`tools/morpho_run.sh`, and its control passed 25 of 25 on both. The TV has
not been run against it.

| File | What it is |
|---|---|
| `<bug>/junit.xml` | The suite's JUnit result with that bug on: which tests failed, and why |
| `none/junit.xml` | The control, no bug on: 25 of 25 pass on both phones, 1 of 1 on the TV |
| `SCORE.txt` | `python3 tests/score.py` over the folder: which tests catch each bug, and any bug no test catches |

Both phone scores read "17 of 17 bugs caught", and the two `SCORE.txt` files
are identical: the same tests catch each bug on Android and iOS. The TV's
reads "3 of 16", the bugs there were then: its one test catches the bugs
the phone's checkout test A catches.

Screenshots and console logs from these runs are not kept here. To reproduce
a run, see "The bug switch" in the [README](../README.md).
