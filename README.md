[![Build Status](https://travis-ci.com/sweet-delights/delightful-parsing.svg?branch=master)](https://travis-ci.com/sweet-delights/delightful-parsing)
[![Maven Central](https://img.shields.io/maven-central/v/org.sweet-delights/delightful-parsing_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/org.sweet-delights/delightful-parsing_2.13)

`delightful-parsing` is a library for parsing fixed-width columns from a string. It is highly inspired by the project
[Apache Daffodil](https://daffodil.apache.org/). The differences are:
1) For now, a much smaller scope (ie fixed-width strings)
2) Defining the parsing specification with Scala case classes and type annotations, instead of XSD

This library is built for Scala 2.11.12, 2.12.12 and 2.13.3

### SBT
```scala
libraryDependencies += "org.sweet-delights" %% "delightful-parsing" % "0.6.1" // check latest version above
```

### Maven
```xml
<dependency>
  <groupId>org.sweet-delights</groupId>
  <artifactId>delightful-parsing_2.12</artifactId>
  <version>0.5.0</version>
</dependency>
```

## [License](LICENSE.md)

All files in `delightful-parsing` are under the GNU Lesser General Public License version 3.
Please read files [`COPYING`](COPYING) and [`COPYING.LESSER`](COPYING.LESSER) for details.

## How to parse a string having fixed-width columns?

*Step 1*: decorate a case class with `delightful-parsing` [annotations](src/main/scala/sweet/delights/parsing/annotations).
Example:
```scala
import sweet.delights.parsing.annotations.{Length, LengthParam, Options, Regex, Repetition}

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
```

*Step 2*: parse!
```scala
import sweet.delights.parsing.Parser._

val line = "optstrintegerAAAAABBBBBCCCCCDDDDD"
val parsed = parse[Foo](Map("intSize" -> 7))(line)
println(parsed)
// Foo(
//   opt = Some("opt"),
//   str = "str",
//   integer = "integer",
//   List(
//     Bar(List("AAAAA", "BBBBB")),
//     Bar(List("CCCCC", "DDDDD"))
//   )
// )
```

## Supported types

By default, [`Parser`](src/main/scala/sweet/delights/parsing/Parser.scala) is able to parse strings and basic types
such as `Int`, `Double`, `String`, `Option[T]`, `List[T]` etc.

The support for additional types is done via implentations of [`Parser[T]`](src/main/scala/sweet/delights/parsing/Parser.scala).

## Definitions

### Node

Considering a case class, any field that has a reference to another case classe is a ***node field***.

A ***node type*** is the type of a node field.

### Leaf

Any field that is NOT a node is a ***leaf field***.
 
A ***leaf type*** is the type of a leaf field.

Types `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double` and `String` are leaves.

### Optional and repeatable types

A node or leaf type `T` can be optional (i.e. `Option[T]`) or repeatable (i.e. `List[T]`).

## Annotations

### Why type annotations?

The choice of type annotations (i.e. annotations "*on the right*") rather than variable annotations (i.e. annotations
"*on the left*") is purely for readability purposes. As such, it is subjective and opiniated.

### Case class annotations

#### `@Options`

Speficies some parsing options like trimming what is consumed. For now, this annotation is mandantory for nodes (case classes).
Example:
```scala
import sweet.delights.parsing.annotations.Options

@Options(trim = true)
case class Foo()
```

### Type annotations

#### `@Conditional(Int => Boolean)`

Experimental. TODO.

#### `@Format(String)` & `@FormatParam(String)`

Specifies a format to parse a certain leaf type. For now, leaf types supported are `java.time.{LocalDate, LocalTime, LocalDateTime,
ZonedDateTime}`. Example:

```scala
import java.time.LocalDate
import sweet.delights.parsing.annotations.{Length, Options, Format}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  date: LocalDate @Length(6) @Format("yyMMdd")
)

Parser.parse[Foo]("200101")
// res0: Foo(
//   date = LocalDate.of(2020, 1, 1)
// )
```

The format can be provided through a parameter by using the `@FormatParam(String)` annotation.

```scala
import java.time.LocalDate
import sweet.delights.parsing.annotations.{Length, Options, FormatParam}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  date: LocalDate @Length(6) @FormatParam("dateFormat")
)

Parser.parse[Foo](Map("dateFormat" -> "yyMMdd"))("200101")
// res0: Foo(
//   date = LocalDate.of(2020, 1, 1)
// )
```

#### `@Ignore(Boolean) & @IgnoreParam(String)`

Specified whether the parsing of a field should be bypassed (ignored) or not. Applicable only to leaf types.
Example:

```scala
import sweet.delights.parsing.annotations.{Ignore, Length, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @Length(5) @Ignore(true),
  opt: Option[String] @Length(2)
)

Parser.parse[Foo]("XX")
// res0: Foo(
//   str = "",
//   opt = Some("XX")
// )
```

The parsing of `str` is skipped completely. The field is assigned a default value.

Ignoring a field can be set through a parameter by using the `@IgnoreParam(String)` annotation.

```scala
import sweet.delights.parsing.annotations.{IgnoreParam, Length, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @Length(5) @IgnoreParam("ignoreMe"),
  opt: Option[String] @Length(2)
)

Parser.parse[Foo](Map("ignoreMe" -> true))("XX")
// res0: Foo(
//   str = "",
//   opt = Some("XX")
// )
```

#### `@Length(Int) & @LengthParam(String)`

Specifies the number of characters to be consumed explicitly. Example:

```scala
import sweet.delights.parsing.annotations.{Length, Options}

@Options(trim = true)
case class Foo(
  str: String @Length(5),
  opt: Option[String] @Length(2)
)
```

The field `str` consumes 5 characters from the input string. As the trimming option is activated, the final length of
`str` may be less than 5.

The field `opt` consumes 2 characters. In addition to the behavior above, as this is an optional field, if the trimmed
string is empty, then `opt` becomes `None`.

The length can be provided through a parameter by using the `@LengthParam` annotation.

```scala
import sweet.delights.parsing.annotations.{LengthParam, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @LengthParam("myStrSize")
)

Parser.parse[Foo](Map("myStrSize" -> 5))("ABCDE")
// res0: Foo(
//   str = "ABCDE"
// )
```

#### `@Lenient`

Specifies to ignore any exceptions raised during the parsing of a leaf field. Example:

```scala
import sweet.delights.parsing.annotations.{Length, Lenient, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  integer: Int        @Length(5) @Lenient,
  option: Option[Int] @Length(5) @Lenient
)

Parser.parse[Foo](Map("myStrSize" -> 5))("xxxxxXXXXX")
// res0: Foo(
//   integer = 0,
//   option = None
// )
```

NB:
- the default value of an integer is `0`
- the default value of an `Option` is `None`

#### `@Regex(String)`

Specifies characters to be consumed thanks to a regular expression. Applicable of leaf types only. Example:
```scala
import sweet.delights.parsing.annotations.{Regex, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @Regex("""\w{5}""")
)

Parser.parse[Foo]("ABCDEF")
// res0: Foo(
//   str = "ABCDE"
// )
```

#### `@Repetition(Int)`

Specifies the number of repetitions for a list. Example:
```scala
import sweet.delights.parsing.annotations.{Length, Repetition, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  strs: List[String] @Repetition(2) @Length(5),
  bars: List[Bar]    @Repetition(3)
)

@Options(trim = true)
case class Bar(
  str: String @Length(1)
)

Parser.parse[Foo]("ABCDEFGHIJKLM")
// res0: Foo(
//   strs = List("ABCDE", "FGHIJ"),
//   bars = List(
//     Bar(str = "K"),
//     Bar(str = "L"),
//     Bar(str = "M")
//   )
// )
```

`strs` is a repeatable leaf field. As such, is requires `@Length` in addition to `@Repetition`.

`bars` is a repeatable node field. Only `@Repetition` is required.

#### `@TrailingSkip(Int)`

Specifies a number of characters to be skipped after a field is parsed successfully. Example:
```scala
import sweet.delights.parsing.annotations.{Length, Options, TrailingSkip}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str1: String @Length(1),
  str2: String @Length(1) @TrailingSkip(1),
  str3: String @Length(1)
)

Parser.parse[Foo]("AB_D")
// res0: Foo(
//   str1 = "A",
//   str2 = "B",
//   str3 = "D"
// )
```

#### `@TrueIf`

For a `Boolean` field, specifies a string that should be matched to evaluate the field to `true`. Example:

```scala
import sweet.delights.parsing.annotations.{Length, Options, TrueIf}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  bool: Boolean @Length(3) @TrueIf("Yes") 
)

Parser.parse[Foo]("Yes")
// res0: Foo(
//   bool = true
// )

Parser.parse[Foo]("xxx")
// res1: Foo(
//   bool = false
// )
```

## Limitations

- case classes MUST be decorated with the [`Options`](src/main/scala/sweet/delights/parsing/annotations/Options.scala) annotation
- all fields of a case class MUST be annotated with applicable annotations

## Acknowledgments

- [Apache Daffodil](https://daffodil.apache.org/) for inspiration
- the [`shapeless`](https://github.com/milessabin/shapeless) library
- the [The Type Astronaut's Guide to Shapeless](https://underscore.io/books/shapeless-guide/) book
- [StackOverflow](https://stackoverflow.com/questions/64688798/hlist-foldleft-with-tuple-as-zero)
