[![Build Status](https://travis-ci.com/sweet-delights/delightful-parsing.svg?branch=master)](https://travis-ci.com/sweet-delights/delightful-parsing)
[![Maven Central](https://img.shields.io/maven-central/v/org.sweet-delights/delightful-parsing_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/org.sweet-delights/delightful-parsing_2.13)

`delightful-parsing` is a library for parsing fixed-width columns from a string. It is highly inspired by the project
[Apache Daffodil](https://daffodil.apache.org/). The differences are:
1) For now, a much smaller scope (ie fixed-width strings)
2) Defining the parsing specification with Scala case classes and type annotations, instead of XSD

This library is built for Scala 2.11.12, 2.12.12 and 2.13.3

### SBT
```scala
libraryDependencies += "org.sweet-delights" %% "delightful-parsing" % "0.2.0"
```

### Maven
```xml
<dependency>
  <groupId>org.sweet-delights</groupId>
  <artifactId>delightful-parsing_2.12</artifactId>
  <version>0.2.0</version>
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

### `@Options`

Speficies some parsing options like trimming what is consumed. For now, this annotation is mandantory for nodes (case classes).
Example:
```scala
import sweet.delights.parsing.annotations.Options

@Options(trim = true)
case class Foo()
```

### `@Length(Int)`

Specifies the number of characters to be consumed explicitly. Applicable only to leaf types. Example:
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

### `@LengthParam(String)`

Specifies the length of characters to be consumed via a parameter to be provided when calling `Parser.parser[T]`. Applicable
to leaf types only. Example:
```scala
import sweet.delights.parsing.annotations.{LengthParam, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @LengthParam("myStrSize")
)

val line = "ABCDE"
Parser.parse[Foo](Map("myStrSize" -> 5))(line)
```

### `@Regex(String)`

Specifies characters to be consumed thanks to a regular expression. Applicable of leaf types only. Example:
```scala
import sweet.delights.parsing.annotations.{Regex, Options}

@Options(trim = true)
case class Foo(
  str: String @Regex("""\w{5}""")
)
```

### `@Repetition(Int)`

Specifies the number of repetitions for a list. Example:
```scala
import sweet.delights.parsing.annotations.{Length, Repetition, Options}

@Options(trim = true)
case class Foo(
  strs: List[String] @Repetition(2) @Length(5),
  bars: List[Bar]    @Repetition(3)
)

@Options(trim = true)
case class Bar(
  list: String @Length(1)
)
```

`strs` is a repeatable leaf field. As such, is requires `@Length` in addition to `@Repetition`.

`bars` is a repeatable node field. Only `@Repetition` is required.

### `@Conditional(Int => Boolean)`

Experimental. TODO.

### `@TrailingSkip(Int)`

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

Parser.parse[Foo]("AB C")
// res0: Foo(
//   str1 = "AA",
//   str2 = "BB",
//   str3 = "CC"
// )
```

### `@Debug(String)`

Makes `Parser` to print debugging information to stdout, after a successful parsing. Example:
```scala
import sweet.delights.parsing.annotations.{Debug, Length, Options}
import sweet.delights.parsing.Parser

@Options(trim = true)
case class Foo(
  str: String @Length(1) @Debug("Hello World!")
)

Parser.parse[Foo]("A")
// ctx: idx = -1, offset = 1, params = Map(), options = Options(true), annotations = List(Length(1), Debug(Hello World!))
// res0: Foo(
//   str = "A"
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
