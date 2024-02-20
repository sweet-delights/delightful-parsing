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

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.{
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  MonthDay,
  OffsetDateTime,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZoneId,
  ZoneOffset,
  ZonedDateTime
}
import java.util.regex.Pattern
import shapeless.ops.hlist.{LeftFolder, Mapper, Reverse, ToTraversable, Zip}
import shapeless.{:+:, ::, AllTypeAnnotations, Annotation, CNil, Coproduct, Generic, HList, HNil, Lazy, Poly1, Poly2}
import sweet.delights.parsing.annotations.{
  Conditional,
  Format,
  FormatParam,
  Ignore,
  IgnoreParam,
  Length,
  LengthParam,
  Lenient,
  Options,
  ParseFunc,
  Regex,
  Repetition,
  TrailingSkip,
  TrueIf
}
import sweet.delights.typeclass.Default

import scala.annotation.StaticAnnotation
import scala.util.Try

/**
 * Parser is a typeclass that parses a line containing fixed-width columns.
 *
 * @tparam T
 */
trait Parser[T] {

  def parse(ctx: Context): (Option[T], Context)

}

/**
 * Parser companion object.
 */
object Parser {

  implicit lazy val charParser: Parser[Char] = new Parser[Char] {
    def parse(ctx: Context): (Option[Char], Context) = {
      val (parsed, _) = stringParser.parse(ctx.withAnnotations(Length(1) :: ctx.annotations))
      (parsed.map(_.charAt(0)), ctx += 1)
    }
  }

  implicit lazy val stringParser: Parser[String] = new Parser[String] {
    def parse(ctx: Context): (Option[String], Context) = {
      val skip = toSkip(ctx)

      val res =
        if (skip) Some((None, ctx))
        else {
          val opt = {
            // @Length
            for {
              length <- ctx.getAnnotation[Length]
              parsed = ctx.line.substring(ctx.offset, ctx.offset + length.value)
            } yield {
              (Some(parsed), ctx += length.value)
            }
          }.orElse {
            // @LengthParam
            for {
              lengthParam <- ctx.getAnnotation[LengthParam]
              length = ctx.getParameterOrFail[Int](lengthParam.value)
              parsed = ctx.line.substring(ctx.offset, ctx.offset + length)
            } yield {
              (Some(parsed), ctx += length)
            }
          }.orElse {
            // @Regex
            for {
              regex <- ctx.getAnnotation[Regex]
            } yield {
              val pattern = Pattern.compile(regex.value)
              val matcher = pattern.matcher(ctx.line)

              if (matcher.find(ctx.offset)) {
                val start = matcher.start()
                val end = matcher.`end`()
                val length = end - start
                val parsed = ctx.line.substring(start, end)
                (Some(parsed), ctx += length)
              } else {
                throw new IllegalArgumentException(s"Could not parse anything with regular expression: ${regex.value}")
              }
            }
          }

          opt
            .map { case (some, next) =>
              if (ctx.options.trim) (some.map(_.trim).filterNot(_.isEmpty), next) else (some, next)
            }
            .map { case (some, next) =>
              ctx.getAnnotation[TrailingSkip] match {
                case Some(skip) => (some, next += skip.value)
                case None => (some, next)
              }
            }
        }

      if (ctx.debug || ctx.options.debug) res.foreach { case (some, next) =>
        println(
          s"ctx: idx = ${ctx.idx}, offset = ${ctx.offset}, length = ${next.offset - ctx.offset}, skip = ${skip}, res = ${some}, params = ${ctx.parameters}, options = ${ctx.options}, annotations = ${ctx.annotations}"
        )
      }

      res.getOrElse {
        throw new IllegalArgumentException("Could not find any of @Length, @LengthParam or @Regex")
      }
    }
  }

  implicit lazy val booleanParser: Parser[Boolean] = new Parser[Boolean] {
    def parse(ctx: Context): (Option[Boolean], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      val eval = for {
        truth <- next.getAnnotation[TrueIf]
        str <- parsed
      } yield str == truth.value

      val res = eval.orElse(parseSimpleType(parsed, ctx)(_.toBoolean))
      (res, next)
    }
  }

  implicit lazy val byteParser: Parser[Byte] = new Parser[Byte] {
    def parse(ctx: Context): (Option[Byte], Context) = parseSimpleType(ctx)(_.toByte)
  }

  implicit lazy val shortParser: Parser[Short] = new Parser[Short] {
    def parse(ctx: Context): (Option[Short], Context) = parseSimpleType(ctx)(_.toShort)
  }

  implicit lazy val intParser: Parser[Int] = new Parser[Int] {
    def parse(ctx: Context): (Option[Int], Context) = parseSimpleType(ctx)(_.toInt)
  }

  implicit lazy val longParser: Parser[Long] = new Parser[Long] {
    def parse(ctx: Context): (Option[Long], Context) = parseSimpleType(ctx)(_.toLong)
  }

  implicit lazy val floatParser: Parser[Float] = new Parser[Float] {
    def parse(ctx: Context): (Option[Float], Context) = parseSimpleType(ctx)(_.toFloat)
  }

  implicit lazy val doubleParser: Parser[Double] = new Parser[Double] {
    def parse(ctx: Context): (Option[Double], Context) = parseSimpleType(ctx)(_.toDouble)
  }

  implicit lazy val durationParser: Parser[Duration] = new Parser[Duration] {
    def parse(ctx: Context): (Option[Duration], Context) = parseSimpleType(ctx)(Duration.parse)
  }

  implicit lazy val instantParser: Parser[Instant] = new Parser[Instant] {
    def parse(ctx: Context): (Option[Instant], Context) = parseSimpleType(ctx)(Instant.parse)
  }

  implicit lazy val localDateParser: Parser[LocalDate] = new Parser[LocalDate] {
    def parse(ctx: Context): (Option[LocalDate], Context) = {
      parseJavaTime(ctx)(LocalDate.parse(_), LocalDate.parse(_, _))
    }
  }

  implicit lazy val localTimeParser: Parser[LocalTime] = new Parser[LocalTime] {
    def parse(ctx: Context): (Option[LocalTime], Context) = {
      parseJavaTime(ctx)(LocalTime.parse(_), LocalTime.parse(_, _))
    }
  }

  implicit lazy val localDateTimeParser: Parser[LocalDateTime] = new Parser[LocalDateTime] {
    def parse(ctx: Context): (Option[LocalDateTime], Context) = {
      parseJavaTime(ctx)(LocalDateTime.parse(_), LocalDateTime.parse(_, _))
    }
  }

  implicit lazy val monthDayParser: Parser[MonthDay] = new Parser[MonthDay] {
    def parse(ctx: Context): (Option[MonthDay], Context) = {
      parseJavaTime(ctx)(MonthDay.parse(_), MonthDay.parse(_, _))
    }
  }

  implicit lazy val zoneOffsetParser: Parser[ZoneOffset] = new Parser[ZoneOffset] {
    def parse(ctx: Context): (Option[ZoneOffset], Context) = parseSimpleType(ctx)(ZoneOffset.of)
  }

  implicit lazy val zoneIdParser: Parser[ZoneId] = new Parser[ZoneId] {
    def parse(ctx: Context): (Option[ZoneId], Context) = parseSimpleType(ctx)(ZoneId.of)
  }

  implicit lazy val offsetDateTimeParser: Parser[OffsetDateTime] = new Parser[OffsetDateTime] {
    def parse(ctx: Context): (Option[OffsetDateTime], Context) = {
      parseJavaTime(ctx)(OffsetDateTime.parse(_), OffsetDateTime.parse(_, _))
    }
  }

  implicit lazy val offsetTimeParser: Parser[OffsetTime] = new Parser[OffsetTime] {
    def parse(ctx: Context): (Option[OffsetTime], Context) = {
      parseJavaTime(ctx)(OffsetTime.parse(_), OffsetTime.parse(_, _))
    }
  }

  implicit lazy val periodParser: Parser[Period] = new Parser[Period] {
    def parse(ctx: Context): (Option[Period], Context) = parseSimpleType(ctx)(Period.parse)
  }

  implicit lazy val yearParser: Parser[Year] = new Parser[Year] {
    def parse(ctx: Context): (Option[Year], Context) = {
      parseJavaTime(ctx)(Year.parse(_), Year.parse(_, _))
    }
  }

  implicit lazy val yearMonthParser: Parser[YearMonth] = new Parser[YearMonth] {
    def parse(ctx: Context): (Option[YearMonth], Context) = {
      parseJavaTime(ctx)(YearMonth.parse(_), YearMonth.parse(_, _))
    }
  }

  implicit lazy val zonedDateTimeParser: Parser[ZonedDateTime] = new Parser[ZonedDateTime] {
    def parse(ctx: Context): (Option[ZonedDateTime], Context) = {
      parseJavaTime(ctx)(ZonedDateTime.parse(_), ZonedDateTime.parse(_, _))
    }
  }

  implicit lazy val optionStringParser: Parser[Option[String]] = new Parser[Option[String]] {
    def parse(ctx: Context): (Option[Option[String]], Context) = {
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else {
        val (parsed, next) = optionParser[String].parse(ctx)
        (parsed.map(_.filterNot(_.trim.isEmpty)), next)
      }
    }
  }

  implicit def optionParser[T](implicit parser: Parser[T]): Parser[Option[T]] = new Parser[Option[T]] {
    def parse(ctx: Context): (Option[Option[T]], Context) = {
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else {
        val (parsed, next) = parser.parse(ctx)
        (Option(parsed).filterNot(_.isEmpty), next)
      }
    }
  }

  implicit def listParser[T](implicit parser: Parser[T]): Parser[List[T]] = new Parser[List[T]] {
    def parse(ctx: Context): (Option[List[T]], Context) = {
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else {
        val repetition = ctx.getAnnotationOrFail[Repetition]
        val (parsed, next) = (0 until repetition.value).foldLeft((List.empty[T], ctx)) { case ((list, curr), i) =>
          parser.parse(curr.withIndex(i)) match {
            case (Some(parsed), next) => (parsed :: list, next)
            case (None, next) => (list, next)
          }
        }

        (Option(parsed.reverse).filterNot(_.isEmpty), next.withIndex(-1))
      }
    }
  }

  implicit val hnilParser: Parser[HNil] = new Parser[HNil] {
    def parse(ctx: Context): (Option[HNil], Context) = ???
  }
  implicit def hlistParser[H, T <: HList](implicit
    hser: Lazy[Parser[H]],
    tser: Parser[T]
  ): Parser[H :: T] = new Parser[H :: T] {
    def parse(ctx: Context): (Option[H :: T], Context) = ???
  }

  implicit val cnilParser: Parser[CNil] = new Parser[CNil] {
    def parse(ctx: Context): (Option[CNil], Context) = ???
  }
  implicit def coproductParser[L, R <: Coproduct](implicit
    lser: Lazy[Parser[L]],
    rser: Parser[R]
  ): Parser[L :+: R] = new Parser[L :+: R] {
    def parse(ctx: Context): (Option[L :+: R], Context) = ???
  }

  object collector extends Poly1 {
    implicit def annotationsCase[AL <: HList](implicit
      toTraversable: ToTraversable.Aux[AL, List, StaticAnnotation]
    ): Case.Aux[AL, List[StaticAnnotation]] = at { annotations =>
      annotations.toList[StaticAnnotation]
    }
  }

  object rightFold extends Poly2 {
    implicit val caseConditional: Case.Aux[Conditional, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseFormat: Case.Aux[Format, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseFormatParam: Case.Aux[FormatParam, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseIgnore: Case.Aux[Ignore, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseIgnoreParam: Case.Aux[IgnoreParam, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseLength: Case.Aux[Length, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseLengthParam: Case.Aux[LengthParam, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseLenient: Case.Aux[Lenient, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseParseFunc: Case.Aux[ParseFunc[_], List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseRegex: Case.Aux[Regex, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseRepetition: Case.Aux[Repetition, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseTrailingSkip: Case.Aux[TrailingSkip, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseTrueIf: Case.Aux[TrueIf, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseAnnotations: Case.Aux[List[StaticAnnotation], List[List[StaticAnnotation]], List[List[StaticAnnotation]]] = at((c, acc) => c :: acc)
  }

  object leftFolder extends Poly2 {
    implicit def caseAt[F, HL <: HList](implicit
      parser: Parser[F]
    ): Case.Aux[(HL, Context), (F, List[StaticAnnotation]), (F :: HL, Context)] = at { case ((acc, ctx), (default, annotations)) =>
      val (parsed, next) = parser.parse(ctx.withAnnotations(annotations))
      (parsed.getOrElse(default) :: acc, next)
    }
  }

  object rightFolder extends Poly2 {
    implicit def caseAt[F, HL <: HList](implicit
      parser: Parser[F]
    ): Case.Aux[(F, List[StaticAnnotation]), (HL, Context), (F :: HL, Context)] = at { case ((default, annotations), (acc, ctx)) =>
      val (parsed, next) = parser.parse(ctx.withAnnotations(annotations))
      (parsed.getOrElse(default) :: acc, next)
    }
  }

  // putting everything together
  implicit def genericParser[T, HL <: HList, AL <: HList, ML <: HList, ZL <: HList, Out, LL <: HList](implicit
    gen: Generic.Aux[T, HL],
    parser: Lazy[Parser[HL]],
    default: Lazy[Default[HL]],
    options: Annotation[Options, T],
    annotations: AllTypeAnnotations.Aux[T, AL],
    mapper: Mapper.Aux[collector.type, AL, ML],
    zipper: Zip.Aux[HL :: ML :: HNil, ZL],
    folder: LeftFolder.Aux[ZL, (HNil, Context), leftFolder.type, Out],
    ev: Out <:< (LL, Context),
    reverse: Reverse.Aux[LL, HL]
  ): Parser[T] = new Parser[T] {
    def parse(ctx: Context): (Option[T], Context) = {
      val opts = options()
      val annots = annotations().map(collector) // ML
      val instance = default.value.get // HL
      val zipped = zipper(instance :: annots :: HNil) // ZL = (HL, ML)
      val (reversed, next) = ev(zipped.foldLeft((HNil: HNil, ctx.withOptions(opts)))(leftFolder)) // Out
      val result = reversed.reverse // HL
      val typed = gen.from(result) // T
      (Some(typed), next)
    }
  }

  private lazy val formatterCache: scala.collection.mutable.Map[String, DateTimeFormatter] = scala.collection.mutable.Map.empty

  private def parseJavaTime[T](ctx: Context)(
    parse: String => T,
    parseFormat: (String, DateTimeFormatter) => T
  ): (Option[T], Context) = {
    val (parsed, next) = stringParser.parse(ctx)
    val format = next
      .getAnnotation[Format]
      .map(_.value)
      .orElse(next.getAnnotation[FormatParam].map(_.value).map(next.getParameterOrFail[String]))

    format match {
      case Some(fmt) =>
        val formatter = formatterCache.getOrElseUpdate(
          fmt, {
            new DateTimeFormatterBuilder()
              .parseCaseInsensitive()
              .appendPattern(fmt)
              .toFormatter
          }
        )(parseSimpleType(parsed, ctx)(parseFormat(_, formatter)), next)

      case None =>
        (parseSimpleType(parsed, ctx)(parse), next)
    }
  }

  private def parseSimpleType[T](ctx: Context)(parse: String => T): (Option[T], Context) = {
    val (parsed, next) = stringParser.parse(ctx)
    (parseSimpleType[T](parsed, next)(parse), next)
  }

  private def parseSimpleType[T](opt: Option[String], ctx: Context)(parse: String => T): Option[T] = {
    val parseFunc = ctx
      .getAnnotation[ParseFunc[_]]
      .map(_.value.asInstanceOf[String => Option[T]])

    def p(str: String): T = parseFunc.flatMap(f => f(str)).getOrElse(parse(str))

    val lenient = ctx.getAnnotation[Lenient].isDefined
    if (lenient) opt.flatMap(str => Try(p(str)).toOption)
    else opt.map(p)
  }

  private def toSkip(ctx: Context): Boolean =
    ctx
      .getAnnotation[Conditional]
      .map(!_.func(ctx.idx))
      .orElse(ctx.getAnnotation[Ignore].map(_ => true))
      .orElse(ctx.getAnnotation[IgnoreParam].map(a => ctx.getParameterOrFail[Boolean](a.value)))
      .getOrElse(false)

  def apply[T](implicit parser: Parser[T]): Parser[T] = parser

  def parse[T](line: String)(implicit parser: Parser[T]): Option[T] = parse[T](Map.empty[String, Any])(line)

  def parse[T](params: Map[String, Any])(line: String)(implicit parser: Parser[T]): Option[T] = {
    val ctx = Context(line, 0, Nil, params, Options(false, false), -1, params.get("debug").contains(true))
    parser.parse(ctx)._1
  }

}
