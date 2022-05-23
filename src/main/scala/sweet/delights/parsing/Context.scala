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

import sweet.delights.parsing.annotations.Options

import scala.annotation.StaticAnnotation
import scala.reflect.{classTag, ClassTag}

/**
 * Parsing context used internally by [[Parser]].
 *
 * @param line
 *   string being parsed
 * @param offset
 *   position in line
 * @param annotations
 *   list of annotations in context
 * @param parameters
 *   list of input parameters
 * @param options
 *   optional parsing features
 * @param idx
 *   index if current context inside a lift or option
 */
case class Context(
  line: String,
  offset: Int,
  annotations: List[StaticAnnotation],
  parameters: Map[String, Any],
  options: Options,
  idx: Int, // index if curr context inside a list or option
  debug: Boolean
) {
  def +=(inc: Int): Context = this.copy(offset = offset + inc)

  def withAnnotations(annotations: List[StaticAnnotation]): Context = this.copy(annotations = annotations)

  def withIndex(idx: Int): Context = this.copy(idx = idx)

  def withOptions(options: Options): Context = this.copy(options = options)

  def getParameterOrFail[T: ClassTag](param: String): T =
    getParameter[T](param).getOrElse(throw new IllegalArgumentException(s"Parameter ${param} not found"))

  def getParameter[T: ClassTag](param: String): Option[T] =
    parameters.get(param).map(_.asInstanceOf[T])

  def getAnnotationOrFail[T <: StaticAnnotation: ClassTag]: T =
    getAnnotation[T].getOrElse(throw new IllegalArgumentException(s"Annotation @${classTag[T].runtimeClass.getSimpleName} not found"))

  def getAnnotation[T <: StaticAnnotation: ClassTag]: Option[T] =
    annotations
      .find(classTag[T].runtimeClass.isInstance(_))
      .map(_.asInstanceOf[T])
}
