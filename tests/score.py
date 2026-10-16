"""Score the bug matrix: which tests catch each bug, and which bugs nothing catches.

Reads <matrix>/<bug>/junit.xml as tests/matrix.sh leaves them. With no bug
on ("none") every test must pass, or no other row means anything, and the
script fails. A bug no test catches is a gap in the suite, and is listed.

usage: python3 tests/score.py MATRIX_DIR
"""
import os
import sys
import xml.etree.ElementTree as ET


def failures(junit):
    root = ET.parse(junit).getroot()
    cases = list(root.iter("testcase"))
    failed = [f"{c.get('classname').split('.', 1)[-1]} › {c.get('name')}" for c in cases
              if c.find("failure") is not None or c.find("error") is not None]
    return len(cases), failed


def main(matrix):
    rows = sorted(d for d in os.listdir(matrix) if os.path.isfile(os.path.join(matrix, d, "junit.xml")))
    if "none" not in rows:
        sys.exit("no 'none' run: the control is missing")
    total, control = failures(os.path.join(matrix, "none", "junit.xml"))
    print(f"none: {total} tests, {len(control)} failed")
    for f in control:
        print(f"  CONTROL FAILED: {f}")
    missed = []
    for bug in (r for r in rows if r != "none"):
        n, failed = failures(os.path.join(matrix, bug, "junit.xml"))
        if n != total:
            print(f"{bug}: ran {n} tests, not {total}")
        if failed:
            print(f"{bug}: caught by {len(failed)}")
            for f in failed:
                print(f"    {f}")
        else:
            missed.append(bug)
            print(f"{bug}: NOT CAUGHT")
    print(f"\n{len(rows) - 1 - len(missed)} of {len(rows) - 1} bugs caught" +
          (f"; not caught: {', '.join(missed)}" if missed else ""))
    sys.exit(1 if control or missed else 0)


if __name__ == "__main__":
    main(sys.argv[1])
