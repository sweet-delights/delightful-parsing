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
 * Specifies a parameterized width for a field to be parsed. The parameter name can be provided by calling:
 * ```scala
 * Parser.parse[T](params: Map[String, Any]) _
 * ```
 *
 * @param value
 *   the name of the parameter.
 */
case class LengthParam(value: String) extends StaticAnnotation
