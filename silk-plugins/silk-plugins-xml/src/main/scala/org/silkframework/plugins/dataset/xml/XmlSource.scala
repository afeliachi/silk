package org.silkframework.plugins.dataset.xml

import java.net.URLEncoder
import java.util.logging.{Level, Logger}

import org.silkframework.config.{DefaultConfig, Prefixes}
import org.silkframework.dataset._
import org.silkframework.entity._
import org.silkframework.runtime.resource.Resource
import org.silkframework.runtime.validation.ValidationException
import org.silkframework.util.Uri

import scala.xml.XML

class XmlSource(file: Resource, basePath: String, uriPattern: String) extends DataSource
    with PathCoverageDataSource with ValueCoverageDataSource with PeakDataSource {

  private val logger = Logger.getLogger(getClass.getName)

  private val uriRegex = "\\{([^\\}]+)\\}".r
  private val maxFileSizeForPeak = DefaultConfig.instance().getInt(MAX_SIZE_CONFIG_KEY)

  override def retrieveTypes(limit: Option[Int]): Traversable[(String, Double)] = {
    val xml = XML.load(file.load)
    for (path <- XmlTraverser(xml).collectPaths(onlyLeafNodes = false)) yield {
      (path.serialize(Prefixes.empty), 1.0 / path.operators.size)
    }
  }

  override def retrievePaths(t: Uri, depth: Int, limit: Option[Int]): IndexedSeq[Path] = {
    // At the moment we just generate paths from the first xml node that is found
    val xml = loadXmlNodes(t.uri)
    if (xml.isEmpty) {
      throw new ValidationException(s"There are no XML nodes at the given path ${t.toString} in resource ${file.name}")
    } else {
      xml.head.collectPaths(onlyLeafNodes = true).toIndexedSeq
    }
  }

  override def retrieve(entitySchema: EntitySchema, limit: Option[Int] = None): Traversable[Entity] = {
    logger.log(Level.FINE, "Retrieving data from XML.")

    val nodes = loadXmlNodes(entitySchema.typeUri.uri)
    val subTypeEntities = if(entitySchema.subPath.operators.nonEmpty) {
      nodes.flatMap(_.evaluatePath(entitySchema.subPath))
    } else { nodes }
    new Entities(subTypeEntities, entitySchema)
  }

  override def retrieveByUri(entitySchema: EntitySchema, entities: Seq[Uri]): Seq[Entity] = {
    throw new UnsupportedOperationException("Retrieving single entities from XML is currently not supported")
  }

  /**
    * Returns the XML nodes found at the base path and
    *
    * @return
    */
  private def loadXmlNodes(typeUri: String): Seq[XmlTraverser] = {
    // If a type URI is provided, we use it as path. Otherwise we are using the base Path (which is deprecated)
    val pathStr = if (typeUri.isEmpty) basePath else typeUri
    // Load XML
    val xml = XML.load(file.load)
    val rootTraverser = XmlTraverser(xml)
    // Move to base path
    rootTraverser.evaluatePath(Path.parse(pathStr))
  }

  private class Entities(xml: Seq[XmlTraverser], entityDesc: EntitySchema) extends Traversable[Entity] {
    def foreach[U](f: Entity => U) {
      // Enumerate entities
      for ((traverser, index) <- xml.zipWithIndex) {
        val uri =
          if (uriPattern.isEmpty) {
            traverser.node.label + index
          } else {
            uriRegex.replaceAllIn(uriPattern, m => {
              val pattern = m.group(1)
              val value = traverser.evaluatePathAsString(Path.parse(pattern)).mkString("")
              URLEncoder.encode(value, "UTF8")
            })
          }

        val values = for (typedPath <- entityDesc.typedPaths) yield traverser.evaluatePathAsString(typedPath.path)
        f(new Entity(uri, values, entityDesc))
      }
    }
  }

  override def combinedPath(typeUri: String, inputPath: Path): Path = {
    val typePath = Path.parse(typeUri)
    Path(typePath.operators ++ inputPath.operators)
  }

  override def convertToIdPath(path: Path): Option[Path] = {
    Some(Path(path.operators ::: List(ForwardOperator("#id"))))
  }

  override def peak(entitySchema: EntitySchema, limit: Int): Traversable[Entity] = {
    peakWithMaximumFileSize(file, entitySchema, limit)
  }
}


