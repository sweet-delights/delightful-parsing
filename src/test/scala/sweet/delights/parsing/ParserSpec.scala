// This file is part of delightful-parsing.
//
// delightful-parsing is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package sweet.delights.parsing

import org.specs2.mutable.Specification
import sweet.delights.parsing.annotations.{Length, LengthParam, Options, Regex, Repetition}
import sweet.delights.parsing.Parser._

class ParserSpec extends Specification {

  @Options(trim = true)
  case class Foo(
    opt: Option[String] @Length(3),
    str: String         @Regex("""\w{3}"""),
    integer: String     @LengthParam("intSize"),
    more: List[Bar]     @Repetition(2)
  )

  @Options(trim = true)
  case class Bar(
    list: List[String] @Repetition(2) @Length(5)
  )

  "Parser" should {
    val foo = Foo(
      opt = Some("opt"),
      str = "str",
      integer = "integer",
      List(
        Bar(List("AAAAA", "BBBBB")),
        Bar(List("CCCCC", "DDDDD"))
      )
    )

    "parse a line" in {
      val line = "optstrintegerAAAAABBBBBCCCCCDDDDD"
      val parsed = parse[Foo](Map("intSize" -> 7))(line)
      parsed mustEqual foo
    }
  }
}
