package sweet.delights.parsing.annotations

import scala.annotation.StaticAnnotation

// TODO try to generalize on something other than index
case class Conditional(func: Int => Boolean) extends StaticAnnotation
