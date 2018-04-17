/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.silkframework.entity.rdf


import org.scalatest.{FlatSpec, Matchers}
import org.silkframework.config.Prefixes
import org.silkframework.entity.Restriction.{Condition, Or}
import org.silkframework.entity.{Path, Restriction}


class SparqlRestrictionParserTest extends FlatSpec with Matchers {
  implicit val prefixes: Prefixes = Map(
    "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "dbpedia" -> "http://dbpedia.org/ontology/",
    "lgdo" -> "http://linkedgeodata.org/ontology/"
  )

  val restrictionConverter = new SparqlRestrictionParser

  "SparqlRestrictionParser" should "parse an empty pattern" in {
    val sparqlRestriction1 = SparqlRestriction.fromSparql("a", "")
    val sparqlRestriction2 = SparqlRestriction.fromSparql("a", ". ")
    val restriction = Restriction(None)

    restrictionConverter(sparqlRestriction1) should equal(restriction)
    restrictionConverter(sparqlRestriction2) should equal(restriction)
  }

  "SparqlRestrictionParser" should "convert simple patterns" in {
    val sparqlRestriction = SparqlRestriction.fromSparql("a", "?a rdf:type dbpedia:Settlement")
    val restriction = Restriction(Some(Condition(Path.parse("?a/rdf:type"), prefixes.resolve("dbpedia:Settlement"))))

    restrictionConverter(sparqlRestriction) should equal(restriction)
  }

  "SparqlRestrictionParser" should "convert simple patterns with full URIs" in {
    val sparqlRestriction = SparqlRestriction.fromSparql("a", "?a rdf:type <http://unknown.org/Settlement>")
    val restriction = Restriction(Some(Condition(Path.parse("?a/rdf:type"), "http://unknown.org/Settlement")))

    restrictionConverter(sparqlRestriction) should equal(restriction)
  }

  "SparqlRestrictionParser" should "convert simple patterns with any variable name" in {
    val sparqlRestriction = SparqlRestriction.fromSparql("x", "?x rdf:type dbpedia:Settlement")

    val restriction = Restriction(Some(Condition(Path.parse("?x/rdf:type"), prefixes.resolve("dbpedia:Settlement"))))

    restrictionConverter(sparqlRestriction) should equal(restriction)
  }

  "SparqlRestrictionParser" should "convert simple patterns with type alias" in {
    val sparqlRestriction = SparqlRestriction.fromSparql("a", "?a a dbpedia:Settlement")

    val restriction = Restriction(Some(Condition(Path.parse("?a/rdf:type"), prefixes.resolve("dbpedia:Settlement"))))

    restrictionConverter(sparqlRestriction) should equal(restriction)
  }

  "SparqlRestrictionParser" should "convert union patterns" in {
    val sparqlRestriction = SparqlRestriction.fromSparql("b", """
       {{
        { ?b rdf:type lgdo:City }}
       UNION
       { ?b rdf:type lgdo:Town }
       UNION
       {  { ?b rdf:type lgdo:Village }
       } }
    """)

    val restriction = Restriction(Some(Or(
      Condition(Path.parse("?b/rdf:type"), prefixes.resolve("lgdo:City")) ::
      Condition(Path.parse("?b/rdf:type"), prefixes.resolve("lgdo:Town")) ::
      Condition(Path.parse("?b/rdf:type"), prefixes.resolve("lgdo:Village")) :: Nil
    )))

    restrictionConverter(sparqlRestriction) should equal(restriction)
  }
}