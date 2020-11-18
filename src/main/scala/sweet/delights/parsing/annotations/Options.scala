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
  * Case class level options for parsing.
  *
  * @param trim if true, strings are trimmed otherwise left unchanged
  * @param debug if true, print debugging info to std otherwise nothing
  */
case class Options(trim: Boolean = false, debug: Boolean = false) extends StaticAnnotation
