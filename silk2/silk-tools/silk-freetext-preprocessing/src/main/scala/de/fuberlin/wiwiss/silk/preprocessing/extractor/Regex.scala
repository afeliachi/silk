package de.fuberlin.wiwiss.silk.preprocessing.extractor

import de.fuberlin.wiwiss.silk.preprocessing.entity.{Property, Entity}
import scala.collection.mutable.HashSet
import de.fuberlin.wiwiss.silk.preprocessing.transformer.Transformer
import de.fuberlin.wiwiss.silk.preprocessing.dataset.Dataset

/**
 * Created with IntelliJ IDEA.
 * User: Petar
 * Date: 21/01/14
 * Time: 14:05
 * To change this template use File | Settings | File Templates.
 */
case class Regex(override  val id: String,
                 override val propertyToExtractFrom: String,
                 override val transformers:List[Transformer],
                 override val param: String) extends ManualExtractor{
  private[this] val compiledRegex = param.r



  def solvePath(s: String) = {
    val regex = "(.*)Extractor".r.findAllIn(s)
    if(regex.hasNext) regex.subgroups(0) else ""
  }

  override def apply(dataset:Dataset, findNewProperty: String => String):Traversable[Entity]= {

    val filteredEntities = dataset.filter(propertyToExtractFrom)

    val newProperty = findNewProperty(solvePath(id))

    for(entity <- filteredEntities) yield {
      val extractedProperties = for(property <- entity.properties) yield{
        val values = compiledRegex.findAllIn(property.value)
        val value = if(values.hasNext) values.next() else ""
        new Property(newProperty, value)
      }
      new Entity(entity.uri, extractedProperties)
    }
  }
}
