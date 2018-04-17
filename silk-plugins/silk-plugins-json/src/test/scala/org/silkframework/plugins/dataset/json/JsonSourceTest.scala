package org.silkframework.plugins.dataset.json

import org.scalatest.{FlatSpec, MustMatchers}
import org.silkframework.entity.{EntitySchema, Path}
import org.silkframework.runtime.resource.{ClasspathResourceLoader, InMemoryResourceManager}
import org.silkframework.util.Uri

import scala.io.Codec

/**
  * Created on 12/22/16.
  */
class JsonSourceTest extends FlatSpec with MustMatchers {
  behavior of "Json Source"

  private def jsonSource: JsonSource = {
    val resources = ClasspathResourceLoader("org/silkframework/plugins/dataset/json/")
    val source = new JsonSource(resources.get("example.json"), "", "#id", Codec.UTF8)
    source
  }

  it should "not return an entity for an empty JSON array" in {
    val resourceManager = InMemoryResourceManager()
    val resource = resourceManager.get("test.json")
    resource.writeString(
      """
        |{"data":[]}
      """.stripMargin)
    val source = new JsonSource(resource, "data", "http://blah", Codec.UTF8)
    val entities = source.retrieve(EntitySchema.empty)
    entities mustBe empty
  }

  it should "not return entities for valid paths" in {
    val resourceManager = InMemoryResourceManager()
    val resource = resourceManager.get("test.json")
    resource.writeString(
      """
        |{"data":{"entities":[{"id":"ID"}]}}
      """.stripMargin)
    val source = new JsonSource(resource, "data/entities", "http://blah/{id}", Codec.UTF8)
    val entities = source.retrieve(EntitySchema.empty)
    entities.size mustBe 1
    entities.head.uri mustBe "http://blah/ID"
  }

  it should "return peak results" in {
    val result = jsonSource.peak(EntitySchema(Uri(""), typedPaths = IndexedSeq(Path.parse("/persons/phoneNumbers/number").asStringTypedPath)), 3).toSeq
    result.size mustBe 1
    result.head.values mustBe IndexedSeq(Seq("123", "456", "789"))
  }

  it should "return peak results with sub path set" in {
    val result = jsonSource.peak(EntitySchema(Uri(""), typedPaths = IndexedSeq(Path.parse("/number").asStringTypedPath),
      subPath = Path.parse("/persons/phoneNumbers")), 3).toSeq
    result.size mustBe 3
    result.map(_.values) mustBe Seq(IndexedSeq(Seq("123")), IndexedSeq(Seq("456")), IndexedSeq(Seq("789")))
  }
}
