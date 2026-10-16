# Evidence: the bug matrix on both platforms

Each folder holds one run of the suite per bug, and one with no bug on:

- `android-bug-matrix/`: an Android 15 emulator (Pixel 7 profile).
- `ios-bug-matrix/`: an iPhone 17 Pro simulator, iOS 26.5.

Both ran on 2026-10-14 and 2026-10-15, with Mobium at `2e37123`, using
`tests/matrix.sh`.

| File | What it is |
|---|---|
| `<bug>/junit.xml` | The suite's JUnit result with that bug on: which tests failed, and why |
| `none/junit.xml` | The control, no bug on: 25 of 25 pass on both |
| `SCORE.txt` | `python3 tests/score.py` over the folder: which tests catch each bug, and any bug no test catches |

Both scores read "16 of 16 bugs caught", and the two `SCORE.txt` files are
identical: the same tests catch each bug on Android and iOS.

Screenshots and console logs from these runs are not kept here. To reproduce
a run, see "The bug switch" in the [README](../README.md).
