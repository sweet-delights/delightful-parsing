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
package sweet.delights.parsing.annotations

import scala.annotation.StaticAnnotation

/**
  * Specifies a condition on a field to be parsed. This annotation is
  * only taken into account for repeatable types. The function provided
  * is given the occurence index, starting from 0, in which the field
  * is being parsed. If the function applied on the index return true,
  * then the field is parsed. Otherwise, the field is ignored.
  *
  * This annotation is an experiment and may be removed in the future.
  *
  * @param func a function on a index
  */
case class Conditional(func: Int => Boolean) extends StaticAnnotation
