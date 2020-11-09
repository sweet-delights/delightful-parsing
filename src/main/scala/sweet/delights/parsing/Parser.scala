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

import java.util.regex.Pattern

import shapeless.{:+:, ::, Annotation, CNil, Coproduct, Generic, HList, HNil, Lazy, Poly1, Poly2}
import shapeless.ops.hlist.{LeftFolder, Mapper, Reverse, RightFolder, Zip}
import sweet.delights.parsing.annotations.{Conditional, Length, LengthParam, Options, Regex, Repetition, TrailingSkip}
import sweet.delights.typeclass.Default

import scala.annotation.StaticAnnotation

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
      (parsed.map(_.charAt(0)), ctx.incOffset(1))
    }
  }

  implicit lazy val stringParser: Parser[String] = new Parser[String] {
    def parse(ctx: Context): (Option[String], Context) = {
      val opt = {
        // @Length
        for {
          length <- ctx.getAnnotation[Length]
          parsed = ctx.line.substring(ctx.offset, ctx.offset + length.value)
        } yield {
          (Some(parsed), ctx.incOffset(length.value))
        }
      }.orElse {
          // @LengthParam
          for {
            lengthParam <- ctx.getAnnotation[LengthParam]
            length = ctx
              .getParameter[Int](lengthParam.value)
              .getOrElse(
                throw new IllegalArgumentException(s"""Could not find parameter "${lengthParam.value}"""")
              )
            parsed = ctx.line.substring(ctx.offset, ctx.offset + length)
          } yield {
            (Some(parsed), ctx.incOffset(length))
          }
        }
        .orElse {
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
              (Some(parsed), ctx.incOffset(length))
            } else {
              throw new IllegalArgumentException(s"Could not parse anything with regular expression: ${regex.value}")
            }
          }
        }

      opt
        .map {
          case (some, next) => if (ctx.options.trim) (some.map(_.trim), next) else (some, next)
        }
        .map {
          case (some, next) =>
            ctx.getAnnotation[Conditional] match {
              case Some(cond) => if (cond.func(ctx.idx)) (some, next) else (None, ctx)
              case None => (some, next)
            }
        }
        .map {
          case (some, next) =>
            ctx.getAnnotation[TrailingSkip] match {
              case Some(skip) => (some, next.incOffset(skip.value))
              case None => (some, next)
            }
        }
        .getOrElse {
          throw new IllegalArgumentException("Could not find any of @Length, @LengthParam or @Regex")
        }
    }
  }

  implicit lazy val byteParser: Parser[Byte] = new Parser[Byte] {
    def parse(ctx: Context): (Option[Byte], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toByte), next)
    }
  }

  implicit lazy val shortParser: Parser[Short] = new Parser[Short] {
    def parse(ctx: Context): (Option[Short], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toShort), next)
    }
  }

  implicit lazy val intParser: Parser[Int] = new Parser[Int] {
    def parse(ctx: Context): (Option[Int], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toInt), next)
    }
  }

  implicit lazy val longParser: Parser[Long] = new Parser[Long] {
    def parse(ctx: Context): (Option[Long], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toLong), next)
    }
  }

  implicit lazy val floatParser: Parser[Float] = new Parser[Float] {
    def parse(ctx: Context): (Option[Float], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toFloat), next)
    }
  }

  implicit lazy val doubleParser: Parser[Double] = new Parser[Double] {
    def parse(ctx: Context): (Option[Double], Context) = {
      val (parsed, next) = stringParser.parse(ctx)
      (parsed.map(_.trim.toDouble), next)
    }
  }

  implicit lazy val optionStringParser: Parser[Option[String]] = new Parser[Option[String]] {
    def parse(ctx: Context): (Option[Option[String]], Context) = {
      val (parsed, next) = optionParser[String].parse(ctx)
      (parsed.map(_.filterNot(_.trim.isEmpty)), next)
    }
  }

  implicit def optionParser[T](implicit parser: Parser[T]): Parser[Option[T]] = new Parser[Option[T]] {
    def parse(ctx: Context): (Option[Option[T]], Context) = {
      val (parsed, next) = parser.parse(ctx.withIndex(0))
      (Option(parsed).filterNot(_.isEmpty), next.withIndex(-1))
    }
  }

  implicit def listParser[T](implicit parser: Parser[T]): Parser[List[T]] = new Parser[List[T]] {
    def parse(ctx: Context): (Option[List[T]], Context) = {
      val repetition = ctx.getAnnotationOrFail[Repetition]
      val (parsed, next) = (0 until repetition.value).foldLeft((List.empty[T], ctx)) {
        case ((list, curr), i) =>
          parser.parse(curr.withIndex(i)) match {
            case (Some(parsed), next) => (parsed :: list, next)
            case (None, next) => (list, next)
          }
      }

      (Option(parsed.reverse).filterNot(_.isEmpty), next.withIndex(-1))
    }
  }

  implicit val hnilParser: Parser[HNil] = new Parser[HNil] {
    def parse(ctx: Context): (Option[HNil], Context) = ???
  }
  implicit def hlistParser[H, T <: HList](
    implicit
    hser: Lazy[Parser[H]],
    tser: Parser[T]
  ): Parser[H :: T] = new Parser[H :: T] {
    def parse(ctx: Context): (Option[H :: T], Context) = ???
  }

  implicit val cnilParser: Parser[CNil] = new Parser[CNil] {
    def parse(ctx: Context): (Option[CNil], Context) = ???
  }
  implicit def coproductParser[L, R <: Coproduct](
    implicit
    lser: Lazy[Parser[L]],
    rser: Parser[R]
  ): Parser[L :+: R] = new Parser[L :+: R] {
    def parse(ctx: Context): (Option[L :+: R], Context) = ???
  }

  object collector extends Poly1 {
    implicit def annotationsCase[AL <: HList](
      implicit folder: RightFolder.Aux[AL, List[StaticAnnotation], rightFold.type, List[StaticAnnotation]]
    ): Case.Aux[AL, List[StaticAnnotation]] = at { annotations =>
      annotations.foldRight(List.empty[StaticAnnotation])(rightFold)
    }
  }

  object rightFold extends Poly2 {
    implicit val caseConditional: Case.Aux[Conditional, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseLength: Case.Aux[Length, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseLengthParam: Case.Aux[LengthParam, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseRegex: Case.Aux[Regex, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseRepetition: Case.Aux[Repetition, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseTrailingSkip: Case.Aux[TrailingSkip, List[StaticAnnotation], List[StaticAnnotation]] = at((c, acc) => c :: acc)
    implicit val caseAnnotations: Case.Aux[List[StaticAnnotation], List[List[StaticAnnotation]], List[List[StaticAnnotation]]] = at((c, acc) => c :: acc)
  }

  object leftFolder extends Poly2 {
    implicit def caseAt[F, HL <: HList](
      implicit parser: Parser[F]
    ): Case.Aux[(HL, Context), (F, List[StaticAnnotation]), (F :: HL, Context)] = at {
      case ((acc, ctx), (default, annotations)) =>
        val (parsed, next) = parser.parse(ctx.withAnnotations(annotations))
        (parsed.getOrElse(default) :: acc, next)
    }
  }

  // putting everything together
  implicit def genericParser[T, HL <: HList, AL <: HList, ML <: HList, ZL <: HList, Out, LL <: HList](
    implicit
    default: Default[T],
    gen: Generic.Aux[T, HL],
    parser: Lazy[Parser[HL]],
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
      val instance = gen.to(default.get) // HL
      val zipped = zipper(instance :: annots :: HNil) // ZL = (HL, ML)
      val (reversed, next) = ev(zipped.foldLeft((HNil: HNil, ctx.withOptions(opts)))(leftFolder)) // Out
      val result = reversed.reverse // HL
      val typed = gen.from(result) // T
      (Some(typed), next)
    }
  }

  def apply[T](implicit parser: Parser[T]): Parser[T] = parser

  def parse[T](line: String)(implicit parser: Parser[T]): Option[T] = parse[T](Map.empty[String, Any])(line)

  def parse[T](params: Map[String, Any])(line: String)(implicit parser: Parser[T]): Option[T] = {
    val ctx = Context(line, 0, Nil, params, Options(false), -1)
    parser.parse(ctx)._1
  }
}
