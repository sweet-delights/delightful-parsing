[![Build Status](https://travis-ci.com/sweet-delights/delightful-parsing.svg?branch=master)](https://travis-ci.com/sweet-delights/delightful-parsing)
[![Maven Central](https://img.shields.io/maven-central/v/org.sweet-delights/delightful-parsing_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/org.sweet-delights/delightful-parsing_2.13)

`delightful-parsing` is a library for parsing fixed-width columns from a string. It is highly inspired by the project
[Apache Daffodil](https://daffodil.apache.org/). The differences are:
1) For now, a much smaller scope (ie fixed-width strings)
2) Defining the parsing specification with Scala case classes and type annotations, instead of XSD

This library is built for Scala 2.12.12 and 2.13.3

### SBT
```scala
libraryDependencies += "org.sweet-delights" %% "delightful-parsing" % "0.0.1"
```

### Maven
```xml
<dependency>
  <groupId>org.sweet-delights</groupId>
  <artifactId>delightful-parsing_2.12</artifactId>
  <version>0.0.1</version>
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
such as `Int`, `Double` etc.

The support for additional types is done via implentations of [`Parser[T]`](src/main/scala/sweet/delights/parsing/Parser.scala).

## Limitations

- case classes MUST be decorated with the [`Options`](src/main/scala/sweet/delights/parsing/annotations/Options.scala) annotation
- all fields of a case class MUST be annotated with applicable annotations

## Acknowledgments

- [Apache Daffodil](https://daffodil.apache.org/) for inspiration
- the [`shapeless`](https://github.com/milessabin/shapeless) library
- the [The Type Astronaut's Guide to Shapeless](https://underscore.io/books/shapeless-guide/) book
- [StackOverflow](https://stackoverflow.com/questions/64688798/hlist-foldleft-with-tuple-as-zero)
