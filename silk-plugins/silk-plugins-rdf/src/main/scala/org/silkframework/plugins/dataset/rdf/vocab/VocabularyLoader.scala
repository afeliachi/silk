package org.silkframework.plugins.dataset.rdf.vocab

import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.silkframework.dataset.rdf.{BlankNode, RdfNode, SparqlEndpoint}
import org.silkframework.rule.vocab._

import scala.collection.immutable.SortedMap

private class VocabularyLoader(endpoint: SparqlEndpoint) {

  def retrieveVocabulary(uri: String): Vocabulary = {
    val classes = retrieveClasses(uri)
    Vocabulary(
      info = GenericInfo(uri, None, None),
      classes = classes,
      properties = retrieveProperties(uri, classes)
    )
  }

  def retrieveClasses(uri: String): Traversable[VocabularyClass] = {
    val classQuery =
      s"""
         | PREFIX owl: <http://www.w3.org/2002/07/owl#>
         | PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         | PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
         |
         | SELECT * WHERE {
         |   GRAPH <$uri> {
         |     { ?c a owl:Class }
         |     UNION
         |     { ?c a rdfs:Class }
         |     OPTIONAL { ?c rdfs:label ?label }
         |     OPTIONAL { ?c rdfs:comment ?desc }
         |     OPTIONAL { ?c skos:definition ?desc }
         |     OPTIONAL { ?c rdfs:subClassOf ?parent }
         |   }
         | }
         | ORDER BY ?c
      """.stripMargin

    val resultsPerClass = new SequentialGroup(endpoint.select(classQuery).bindings)
    for((classUri, bindings) <- resultsPerClass) yield {
      val labels = collectValues("label", bindings)
      val descriptions = collectValues("desc", bindings)
      val parents = collectValues("parent", bindings)
      VocabularyClass(
        info =
          GenericInfo(
            uri = classUri,
            label = labels.headOption,
            description = descriptions.headOption
          ),
        parentClasses = parents
      )
    }
  }

  private def collectValues(varName: String, bindings: Traversable[SortedMap[String, RdfNode]]): Seq[String] = {
    bindings.flatMap(_.get(varName).map(_.value)).toSeq.distinct
  }

  class SequentialGroup(bindings: Traversable[SortedMap[String, RdfNode]]) extends Traversable[(String, Traversable[SortedMap[String, RdfNode]])] {

    override def foreach[U](emit: ((String, Traversable[SortedMap[String, RdfNode]])) => U): Unit = {
      var currentUri: Option[String] = None
      var groupedBindings: Vector[SortedMap[String, RdfNode]] = Vector.empty
      for(binding <- bindings if !binding("c").isInstanceOf[BlankNode]) {
        val uri = binding("c").value
        if(!currentUri.contains(uri)) {
          emitIfExists(emit, currentUri, groupedBindings)
          currentUri = Some(uri)
          groupedBindings = Vector.empty
        }
        groupedBindings :+= binding
      }
      emitIfExists(emit, currentUri, groupedBindings)
    }

    private def emitIfExists[U](emit: ((String, Traversable[SortedMap[String, RdfNode]])) => U,
                                currentUri: Option[String],
                                groupedBindings: Vector[SortedMap[String, RdfNode]]) = {
      currentUri foreach { uri =>
        emit((uri, groupedBindings))
      }
    }
  }

  val propertyClasses = Set(
    RDF.Property.getURI,
    OWL.DatatypeProperty.getURI,
    OWL.ObjectProperty.getURI
  )

  def retrieveProperties(uri: String, classes: Traversable[VocabularyClass]): Traversable[VocabularyProperty] = {
    val propertyQuery = propertiesOfClassQuery(uri)

    val classMap = classes.map(c => (c.info.uri, c)).toMap
    def getClass(uri: String) = classMap.getOrElse(uri, VocabularyClass(GenericInfo(uri), Seq()))
    val result = endpoint.select(propertyQuery).bindings
    val propertiesGrouped = result.groupBy(_("p"))

    for((propertyResource, bindings) <- propertiesGrouped if !propertyResource.isInstanceOf[BlankNode]) yield {
      val info =
        GenericInfo(
          uri = propertyResource.value,
          label = firstValue("label", bindings),
          description = firstValue("desc", bindings)
        )
      val classes = bindings.flatMap(_.get("class"))
      val propertyType = classes.toSeq.
          map(_.value).
          filter(propertyClasses). // Only interested in any of the property classes
          map(PropertyType.uriToTypeMap). // convert to PropertyType instance
          sortWith(_.preference > _.preference).headOption.getOrElse(BasePropertyType) // take the most preferred one
      VocabularyProperty(
        info = info,
        domain = firstValue("domain", bindings).map(getClass),
        range = firstValue("range", bindings).map(getClass),
        propertyType = propertyType
      )
    }
  }

  private def propertiesOfClassQuery(uri: String) = {
    s"""
       | PREFIX owl: <http://www.w3.org/2002/07/owl#>
       | PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       | PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       | PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
       |
       | SELECT * WHERE {
       |   GRAPH <$uri> {
       |     { ?p a rdf:Property }
       |     UNION
       |     { ?p a owl:ObjectProperty }
       |     UNION
       |     { ?p a owl:DatatypeProperty }
       |
       |     ?p a ?class
       |     OPTIONAL { ?p rdfs:label ?label }
       |     OPTIONAL { ?p rdfs:comment ?desc }
       |     OPTIONAL { ?p skos:definition ?def }
       |     OPTIONAL { ?p rdfs:domain ?domain }
       |     OPTIONAL { ?p rdfs:range ?range }
       |   }
       | }
      """.stripMargin
  }

  private def firstValue(variable: String, bindings: Traversable[SortedMap[String, RdfNode]]): Option[String] = {
    bindings.flatMap(_.get(variable)).headOption.map(_.value)
  }

}