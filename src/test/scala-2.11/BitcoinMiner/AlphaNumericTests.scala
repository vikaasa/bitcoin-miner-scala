package BitcoinMiner

import org.scalatest._

class AlphaNumericTests extends FlatSpec {
  "An AlphaNumeric number" should "be constructed from string representation of the Alphanumeric(base 36)" in {
    assert(new AlphaNumeric("0").toString() == "00000000000000000000000000000000")
    assert(new AlphaNumeric("-1").toString() == "-0000000000000000000000000000001")
  }

  it should "throw an exception when a non alphanumeric characters are passed to its constructor" in {
    intercept[IllegalArgumentException] {
      new AlphaNumeric("0000000000000000000/000000000000")
    }
  }

  it should "support + - operations with Integers, BigInt and other AlphaNumerics" in {
    assert(new AlphaNumeric("1") + 1 == new AlphaNumeric("2"))
    assert(new AlphaNumeric("-1") + 2 == new AlphaNumeric("1"))
    assert(new AlphaNumeric("z") + 1 == new AlphaNumeric("10"))
    assert(new AlphaNumeric("-3") + 1 == new AlphaNumeric("-2"))
    assert(new AlphaNumeric("z") + new AlphaNumeric("1") == new AlphaNumeric("10"))
    assert(new AlphaNumeric("z") + BigInt(1) == new AlphaNumeric("10"))

    assert(new AlphaNumeric("1")- 1 == new AlphaNumeric("0"))
    assert(new AlphaNumeric("-1") - 2 == new AlphaNumeric("-3"))
    assert(new AlphaNumeric("10") - 1 == new AlphaNumeric("z"))
    assert(new AlphaNumeric("-3") - 1 == new AlphaNumeric("-4"))
    assert(new AlphaNumeric("10") - new AlphaNumeric("1") == new AlphaNumeric("z"))
    assert(new AlphaNumeric("10") - BigInt(1) == new AlphaNumeric("z"))
  }

  it should "support <, <=, >, >=, == operations with other AlphaNumerics" in {
    assert(new AlphaNumeric("2") == new AlphaNumeric("2"))


    assert(new AlphaNumeric("2") <= new AlphaNumeric("2"))
    assert(new AlphaNumeric("1") <= new AlphaNumeric("2"))

    assert(new AlphaNumeric("2") >= new AlphaNumeric("2"))
    assert(new AlphaNumeric("3") >= new AlphaNumeric("2"))

    assert(new AlphaNumeric("3") > new AlphaNumeric("2"))

    assert(new AlphaNumeric("1") < new AlphaNumeric("2"))

  }
}