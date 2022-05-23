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

import shapeless3.deriving.{AllTypeAnnotations, Annotation}
import sweet.delights.parsing.annotations.*
import sweet.delights.typeclass.Default
import sweet.delights.typeclass.Default.*

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.*
import java.util.regex.Pattern

import scala.annotation.StaticAnnotation
import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.*
import scala.util.Try

import scala.language.postfixOps

/**
 * Parser is a typeclass that parses a line containing fixed-width columns.
 *
 * @tparam T
 */
trait Parser[T]:
  def parse(ctx: Context): (Option[T], Context)

/**
 * Parser companion object.
 */
object Parser:

  given Parser[Char] with
    def parse(ctx: Context): (Option[Char], Context) =
      val (parsed, _) = stringParser.parse(ctx.withAnnotations(Length(1) :: ctx.annotations))
      (parsed.map(_.charAt(0)), ctx += 1)

  given stringParser: Parser[String] with
    def parse(ctx: Context): (Option[String], Context) =
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

  given Parser[Boolean] with
    def parse(ctx: Context): (Option[Boolean], Context) =
      val (parsed, next) = stringParser.parse(ctx)
      val eval = for {
        truth <- next.getAnnotation[TrueIf]
        str <- parsed
      } yield str == truth.value

      val res = eval.orElse(parseSimpleType(ctx, parsed)(_.toBoolean))
      (res, next)

  given Parser[Byte] with
    def parse(ctx: Context): (Option[Byte], Context) = parseSimpleType(ctx)(_.toByte)

  given Parser[Short] with
    def parse(ctx: Context): (Option[Short], Context) = parseSimpleType(ctx)(_.toShort)

  given Parser[Int] with
    def parse(ctx: Context): (Option[Int], Context) = parseSimpleType(ctx)(_.toInt)

  given Parser[Long] with
    def parse(ctx: Context): (Option[Long], Context) = parseSimpleType(ctx)(_.toLong)

  given Parser[Float] with
    def parse(ctx: Context): (Option[Float], Context) = parseSimpleType(ctx)(_.toFloat)

  given Parser[Double] with
    def parse(ctx: Context): (Option[Double], Context) = parseSimpleType(ctx)(_.toDouble)

  given Parser[Duration] with
    def parse(ctx: Context): (Option[Duration], Context) = parseSimpleType(ctx)(Duration.parse)

  given Parser[Instant] with
    def parse(ctx: Context): (Option[Instant], Context) = parseSimpleType(ctx)(Instant.parse)

  given Parser[LocalDate] with
    def parse(ctx: Context): (Option[LocalDate], Context) =
      parseJavaTime(ctx)(LocalDate.parse(_), LocalDate.parse(_, _))

  given Parser[LocalTime] with
    def parse(ctx: Context): (Option[LocalTime], Context) =
      parseJavaTime(ctx)(LocalTime.parse(_), LocalTime.parse(_, _))

  given Parser[LocalDateTime] with
    def parse(ctx: Context): (Option[LocalDateTime], Context) =
      parseJavaTime(ctx)(LocalDateTime.parse(_), LocalDateTime.parse(_, _))

  given Parser[MonthDay] with
    def parse(ctx: Context): (Option[MonthDay], Context) =
      parseJavaTime(ctx)(MonthDay.parse(_), MonthDay.parse(_, _))

  given Parser[ZoneOffset] with
    def parse(ctx: Context): (Option[ZoneOffset], Context) = parseSimpleType(ctx)(ZoneOffset.of)

  given Parser[ZoneId] with
    def parse(ctx: Context): (Option[ZoneId], Context) = parseSimpleType(ctx)(ZoneId.of)

  given Parser[OffsetDateTime] with
    def parse(ctx: Context): (Option[OffsetDateTime], Context) =
      parseJavaTime(ctx)(OffsetDateTime.parse(_), OffsetDateTime.parse(_, _))

  given Parser[OffsetTime] with
    def parse(ctx: Context): (Option[OffsetTime], Context) =
      parseJavaTime(ctx)(OffsetTime.parse(_), OffsetTime.parse(_, _))

  given Parser[Period] with
    def parse(ctx: Context): (Option[Period], Context) = parseSimpleType(ctx)(Period.parse)

  given Parser[Year] with
    def parse(ctx: Context): (Option[Year], Context) =
      parseJavaTime(ctx)(Year.parse(_), Year.parse(_, _))

  given Parser[YearMonth] with
    def parse(ctx: Context): (Option[YearMonth], Context) =
      parseJavaTime(ctx)(YearMonth.parse(_), YearMonth.parse(_, _))

  given Parser[ZonedDateTime] with
    def parse(ctx: Context): (Option[ZonedDateTime], Context) =
      parseJavaTime(ctx)(ZonedDateTime.parse(_), ZonedDateTime.parse(_, _))

  given Parser[Option[String]] with
    def parse(ctx: Context): (Option[Option[String]], Context) =
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else
        val (parsed, next) = optionParser[String].parse(ctx)
        (parsed.map(_.filterNot(_.trim.isEmpty)), next)

  given optionParser[T](using parser: Parser[T]): Parser[Option[T]] with
    def parse(ctx: Context): (Option[Option[T]], Context) =
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else
        val (parsed, next) = parser.parse(ctx)
        (Option(parsed).filterNot(_.isEmpty), next)

  given listParser[T](using parser: Parser[T]): Parser[List[T]] with
    def parse(ctx: Context): (Option[List[T]], Context) =
      val skip = toSkip(ctx)

      if (skip) (None, ctx)
      else
        val repetition = ctx.getAnnotationOrFail[Repetition]
        val (parsed, next) = (0 until repetition.value).foldLeft((List.empty[T], ctx)) { case ((list, curr), i) =>
          parser.parse(curr.withIndex(i)) match
            case (Some(parsed), next) => (parsed :: list, next)
            case (None, next) => (list, next)
        }

        (Option(parsed.reverse).filterNot(_.isEmpty), next.withIndex(-1))

  private lazy val formatterCache: scala.collection.mutable.Map[String, DateTimeFormatter] = scala.collection.mutable.Map.empty

  private def parseJavaTime[T](ctx: Context)(
    parse: String => T,
    parseFormat: (String, DateTimeFormatter) => T
  ): (Option[T], Context) =
    val (parsed, next) = stringParser.parse(ctx)
    val format = next
      .getAnnotation[Format]
      .map(_.value)
      .orElse(next.getAnnotation[FormatParam].map(_.value).map(next.getParameterOrFail[String]))

    format match
      case Some(fmt) =>
        val formatter = formatterCache.getOrElseUpdate(
          fmt, {
            new DateTimeFormatterBuilder()
              .parseCaseInsensitive()
              .appendPattern(fmt)
              .toFormatter
          }
        )
        (parseSimpleType(ctx, parsed)(parseFormat(_, formatter)), next)

      case None =>
        (parseSimpleType(ctx, parsed)(parse), next)

  private def parseSimpleType[T](ctx: Context)(parse: String => T): (Option[T], Context) =
    val (parsed, next) = stringParser.parse(ctx)
    (parseSimpleType[T](next, parsed)(parse), next)

  private def parseSimpleType[T](ctx: Context, opt: Option[String])(parse: String => T): Option[T] =
    val parseFunc = ctx
      .getAnnotation[ParseFunc[_]]
      .map(_.value.asInstanceOf[String => Option[T]])

    def p(str: String): T = parseFunc.flatMap(f => f(str)).getOrElse(parse(str))

    val lenient = ctx.getAnnotation[Lenient].isDefined
    if (lenient) opt.flatMap(str => Try(p(str)).toOption)
    else opt.map(p)

  private def toSkip(ctx: Context): Boolean =
    ctx
      .getAnnotation[Conditional]
      .map(!_.func(ctx.idx))
      .orElse(ctx.getAnnotation[Ignore].map(_ => true))
      .orElse(ctx.getAnnotation[IgnoreParam].map(a => ctx.getParameterOrFail[Boolean](a.value)))
      .getOrElse(false)

  private inline def summonAll[T <: Tuple]: List[Parser[_]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[Parser[t]] :: summonAll[ts]

  private def deriveSum[T](s: Mirror.SumOf[T], elems: List[Parser[_]]): Parser[T] = ???

  private def deriveProduct[T](
    p: Mirror.ProductOf[T],
    elems: List[Parser[_]]
  )(using
    options: Annotation[Options, T],
    annotations: AllTypeAnnotations[T],
    default: Default[T]
  ): Parser[T] =
    def toListOfAnnotations(annotations: Tuple): List[List[StaticAnnotation]] = annotations match
      case EmptyTuple => Nil
      case (t: Tuple) *: ts => t.toList.asInstanceOf[List[StaticAnnotation]] :: toListOfAnnotations(ts)
      case _ => throw new IllegalArgumentException(s"${annotations} is not a tuple of tuples")

    def toTuple(list: List[?]): Tuple = list.foldLeft(EmptyTuple: Tuple) { case (ts, e) =>
      e *: ts
    }

    val opts = options()
    val annots = toListOfAnnotations(annotations())
    val instance = default.get.asInstanceOf[Product]

    val parser = elems.zipWithIndex
      .map(_.swap)
      .zip(annots)

    (ctx: Context) =>
      val (parsed, next) = parser.foldLeft((List.empty[Any], ctx.withOptions(opts))) { case ((t, curr), ((idx, p), as)) =>
        val (opt, next) = p.parse(curr.withAnnotations(as))
        (opt.getOrElse(instance.productElement(idx)) :: t, next)
      }

      val tuple = toTuple(parsed)
      (Some(p.fromProduct(tuple)), next)

  inline given derived[T](using m: Mirror.Of[T]): Parser[T] =
    val elemsParsers = summonAll[m.MirroredElemTypes]
    inline m match
      case s: Mirror.SumOf[T] => deriveSum(s, elemsParsers)
      case p: Mirror.ProductOf[T] => deriveProduct(p, elemsParsers)

  def apply[T](using parser: Parser[T]): Parser[T] = parser

  def parse[T](line: String)(using parser: Parser[T]): Option[T] = parse[T](Map.empty[String, Any])(line)

  def parse[T](params: Map[String, Any])(line: String)(using parser: Parser[T]): Option[T] =
    val ctx = Context(line, 0, Nil, params, Options(), -1, params.get("debug").contains(true))
    parser.parse(ctx)._1
