"""The shop's pricing rules, as a reference both apps are checked against.

Money is integer cents and weight integer grams throughout; nothing is a
float. `python3 pricing.py` prints every case in pricing-cases.json with its
computed result and fails if a case's expected values disagree, so the spec's
worked examples cannot drift from the rules.
"""
import json
import os
import sys

CATALOG = {
    # id: (name, cents, grams, digital)
    "tee": ("Logo Tee", 2400, 200, False),
    "hoodie": ("Map Before You Tap Hoodie", 5800, 700, False),
    "hat": ("Morpho Dad Hat", 2600, 150, False),
    "mug": ("Wait For It Mug", 1600, 400, False),
    "tumbler": ("No sleep() Tumbler", 3200, 450, False),
    "magnet": ("Works On My Emulator Magnet", 599, 20, False),
    "pins": ("@e1 Enamel Pin Set", 1200, 50, False),
    "stickers": ("CHALLENGES Sticker Pack", 749, 10, False),
    "case": ("Real Device Verified Phone Case", 2900, 60, False),
    "beanie": ("Headless Beanie", 2200, 120, False),
    "tote": ("Leave Nothing On The Device Tote", 1900, 250, False),
    "socks": ("Zero Runtime Dependencies Socks", 1399, 80, False),
    "giftcard": ("Gift Card", None, 0, True),
}
SURCHARGE = {("tee", "XXL"): 200}
GIFT_AMOUNTS = {"25": 2500, "50": 5000}

# Demo rates, not tax advice: thousandths of a percent, and whether shipping is taxed.
TAX = {"CA": (7250, False), "NY": (8875, True), "OR": (0, False), "TX": (6250, True), "WA": (6500, True)}
PICKUP_STATE = "CA"

FREE_SHIPPING_AT = 7500
STANDARD = (599, 150)   # base up to 1000 g, then per started 500 g
EXPRESS = (1499, 250)

COUPONS = {
    "WELCOME10": {"percent": 10},
    "MOBIUM5": {"off": 500, "min": 3000},
    "SHIPFREE": {"free_standard": True},
    "EXPIRED20": {"expired": True},
}


# The pricing bugs of the bug switch (SPEC.md, "The bug switch"). The app's
# other bugs live in its screens, not in its arithmetic.
PRICING_BUGS = {"tax-before-discount", "free-shipping-before-discount", "free-shipping-over",
                "round-down", "expired-accepted", "express-free", "xxl-no-surcharge"}


def unit_price(line, bugs=frozenset()):
    pid, variant = line["id"], line.get("variant")
    if pid == "giftcard":
        return GIFT_AMOUNTS[variant]
    if "xxl-no-surcharge" in bugs:
        return CATALOG[pid][1]
    return CATALOG[pid][1] + SURCHARGE.get((pid, variant), 0)


def half_up(numerator, denominator):
    return (numerator * 2 + denominator) // (denominator * 2)


def shipping_cost(method, grams):
    if method == "pickup":
        return 0
    base, per = STANDARD if method == "standard" else EXPRESS
    over = max(0, grams - 1000)
    return base + per * ((over + 499) // 500)


def price(cart, coupon=None, method="standard", state="CA", bugs=frozenset(), times=1):
    """Returns the order's figures, or {"coupon_error": ...} for a coupon refused.

    `times` is how often the code was applied: 1, unless the coupon-twice bug
    let it be applied again."""
    bugs = frozenset(bugs)
    unknown = bugs - PRICING_BUGS
    if unknown:
        raise ValueError(f"not a pricing bug: {sorted(unknown)}")
    rnd = (lambda n, d: n // d) if "round-down" in bugs else half_up
    merch = sum(unit_price(l, bugs) * l["qty"] for l in cart if not CATALOG[l["id"]][3])
    gift = sum(unit_price(l, bugs) * l["qty"] for l in cart if CATALOG[l["id"]][3])
    grams = sum(CATALOG[l["id"]][2] * l["qty"] for l in cart)
    physical = merch > 0

    discount, free_standard = 0, False
    if coupon:
        c = COUPONS.get(coupon.strip().upper())
        if c is None:
            return {"coupon_error": "That code isn't valid."}
        if c.get("expired") and "expired-accepted" in bugs:
            c = {"percent": 20}
        if c.get("expired"):
            return {"coupon_error": "That code has expired."}
        if not physical:
            return {"coupon_error": "Codes don't apply to gift cards."}
        if merch < c.get("min", 0):
            return {"coupon_error": f"Add ${(c['min'] - merch) / 100:.2f} more to use this code."}
        if "percent" in c:
            discount = rnd(merch * c["percent"], 100)
        discount += min(c.get("off", 0), merch)
        discount = min(discount * times, merch)
        free_standard = c.get("free_standard", False)

    goods = merch - discount
    if not physical:
        method, shipping = None, 0
    else:
        shipping = shipping_cost(method, grams)
        judged = merch if "free-shipping-before-discount" in bugs else goods
        over_line = judged > FREE_SHIPPING_AT if "free-shipping-over" in bugs else judged >= FREE_SHIPPING_AT
        if method == "standard" and (over_line or free_standard):
            shipping = 0
        if method == "express" and free_standard and "express-free" in bugs:
            shipping = 0
    tax_state = PICKUP_STATE if method == "pickup" else state
    rate, ships_taxed = TAX[tax_state]
    base = (merch if "tax-before-discount" in bugs else goods) + (shipping if ships_taxed else 0)
    tax = rnd(base * rate, 100000)
    return {"merch": merch, "discount": discount, "shipping": shipping, "tax": tax,
            "gift": gift, "total": goods + shipping + tax + gift, "grams": grams}


def write_catalog(path):
    """catalog.json: the table above, for each app's unit tests to compare its own against."""
    out = [{"id": pid, "name": n, "cents": c, "grams": g, "digital": d} for pid, (n, c, g, d) in CATALOG.items()]
    with open(path, "w") as f:
        json.dump({"products": out, "surcharge": [{"id": i, "variant": v, "cents": c} for (i, v), c in SURCHARGE.items()],
                   "gift_amounts": GIFT_AMOUNTS}, f, indent=1, ensure_ascii=False)
        f.write("\n")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    if sys.argv[1:] == ["--catalog"]:
        write_catalog(os.path.join(here, "catalog.json"))
        return
    cases = json.load(open(os.path.join(here, "pricing-cases.json")))
    bad = 0
    for case in cases:
        got = price(case["cart"], case.get("coupon"), case.get("method", "standard"), case.get("state", "CA"),
                    case.get("bugs", []), case.get("times", 1))
        want = case.get("expect", {})
        wrong = {k: (v, got.get(k)) for k, v in want.items() if got.get(k) != v}
        print(f"{'ok ' if not wrong else 'BAD'} {case['name']}: {json.dumps(got)}")
        for k, (w, g) in wrong.items():
            print(f"      {k}: expected {w}, computed {g}")
        bad += bool(wrong)
    if not cases:
        sys.exit("no cases")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
