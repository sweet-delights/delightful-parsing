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

import java.time.LocalDate

import org.specs2.mutable.Specification
import sweet.delights.parsing.annotations.{Conditional, Format, Length, LengthParam, Options, Regex, Repetition, TrailingSkip}
import sweet.delights.parsing.Parser._

class ParserSpec extends Specification {

  lazy val cond = (i: Int) => i >= 0

  @Options(trim = true)
  case class Foo(
    opt: Option[String] @Length(3),
    str: String         @Regex("""\w{3}""") @TrailingSkip(1),
    integer: String     @LengthParam("intSize"),
    more: List[Bar]     @Repetition(2),
    date: LocalDate     @Length(6) @Format("yyMMdd")
  )

  @Options(trim = true)
  case class Bar(
    list: List[String] @Repetition(2) @Length(5) @Conditional(cond)
  )

  "Parser" should {
    val foo = Foo(
      opt = Some("opt"),
      str = "str",
      integer = "integer",
      more = List(
        Bar(List("AAAAA", "BBBBB")),
        Bar(List("CCCCC", "DDDDD"))
      ),
      date = LocalDate.of(2020, 1, 1)
    )

    "parse a line" in {
      val line = "optstr_integerAAAAABBBBBCCCCCDDDDD200101"
      val parsed = parse[Foo](Map("intSize" -> 7))(line)
      parsed must beSome
      parsed.get mustEqual foo
    }
  }
}
