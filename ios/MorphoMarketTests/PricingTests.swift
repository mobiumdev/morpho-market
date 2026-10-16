import XCTest
@testable import Morpho_Market

private func specJSON(_ name: String) throws -> Any {
    let bundle = Bundle(for: PricingTests.self)
    let url = try XCTUnwrap(bundle.url(forResource: name, withExtension: "json"), "missing \(name).json in the test bundle")
    return try JSONSerialization.jsonObject(with: Data(contentsOf: url))
}

final class PricingTests: XCTestCase {
    /// Every case in the spec's table, the bug cases included, against this implementation.
    func testEverySpecCase() throws {
        let cases = try XCTUnwrap(try specJSON("pricing-cases") as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty, "no cases")
        var failures: [String] = []
        var checked = 0
        for c in cases {
            let cart = (c["cart"] as! [[String: Any]]).map {
                Line(id: $0["id"] as! String, variant: $0["variant"] as? String, qty: $0["qty"] as! Int)
            }
            let got = price(
                cart,
                code: c["coupon"] as? String,
                method: Method(rawValue: (c["method"] as? String) ?? "standard")!,
                state: (c["state"] as? String) ?? "CA",
                bugs: Set((c["bugs"] as? [String]) ?? []),
                times: (c["times"] as? Int) ?? 1
            )
            for (key, want) in c["expect"] as! [String: Any] {
                let actual: Any
                switch got {
                case let .codeRefused(message):
                    actual = key == "coupon_error" ? message : "(refused: \(message))"
                case let .order(o):
                    switch key {
                    case "merch": actual = o.merch
                    case "discount": actual = o.discount
                    case "shipping": actual = o.shipping
                    case "tax": actual = o.tax
                    case "gift": actual = o.gift
                    case "total": actual = o.total
                    case "grams": actual = o.grams
                    default: actual = "(no field \(key))"
                    }
                }
                checked += 1
                if "\(want)" != "\(actual)" {
                    failures.append("\(c["name"]!): \(key) expected \(want), got \(actual)")
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: "\n"))
        XCTAssertGreaterThan(checked, 0, "checked nothing")
    }

    func testMoneyReadsAsDollars() {
        XCTAssertEqual(money(0), "$0.00")
        XCTAssertEqual(money(599), "$5.99")
        XCTAssertEqual(money(123456), "$1,234.56")
        XCTAssertEqual(money(-640), "-$6.40")
    }

    /// The catalog here is the catalog in the spec: catalog.json is written by spec/pricing.py.
    func testCatalogMatchesTheSpec() throws {
        let spec = try XCTUnwrap(try specJSON("catalog") as? [String: Any])
        let products = spec["products"] as! [[String: Any]]
        XCTAssertEqual(products.count, catalog.count)
        for p in products {
            let mine = product(p["id"] as! String)
            XCTAssertEqual(p["name"] as? String, mine.name)
            XCTAssertEqual((p["cents"] as? Int) ?? 0, mine.digital ? 0 : mine.cents, mine.id)
            XCTAssertEqual(p["grams"] as? Int, mine.grams, mine.id)
            XCTAssertEqual(p["digital"] as? Bool, mine.digital, mine.id)
        }
        let gifts = spec["gift_amounts"] as! [String: Int]
        XCTAssertEqual(gifts, giftAmounts)
        let sur = spec["surcharge"] as! [[String: Any]]
        XCTAssertEqual(sur.count, surcharge.count)
        for s in sur {
            XCTAssertEqual(s["cents"] as? Int, surcharge[SurchargeKey(id: s["id"] as! String, variant: s["variant"] as! String)])
        }
    }
}
