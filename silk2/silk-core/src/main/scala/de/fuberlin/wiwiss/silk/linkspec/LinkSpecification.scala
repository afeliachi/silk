package de.fuberlin.wiwiss.silk.linkspec

import condition.Blocking
import de.fuberlin.wiwiss.silk.output.Output
import input.{Input, TransformInput, Transformer, PathInput}
import de.fuberlin.wiwiss.silk.instance.Path
import xml.Node
import java.io.InputStream
import de.fuberlin.wiwiss.silk.util.{ValidatingXMLReader, SourceTargetPair}

/**
 * @param id The id which identifies this link specification. May only contain alphanumeric characters (a - z, 0 - 9).
 */
//TODO make LinkSpecification self contained by including a link to the prefixes?
//TODO move blocking to configuration
case class LinkSpecification(val id : String, val linkType : String, val datasets : SourceTargetPair[DatasetSpecification],
                             val blocking : Option[Blocking], val condition : LinkCondition,
                             val filter : LinkFilter, val outputs : Traversable[Output])
{
  require(id.forall(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')),
          "A link specification ID may only contain alphanumeric characters (a - z, 0 - 9). The following id is not valid: '" + id + "'")
}

object LinkSpecification
{
  private val schemaLocation = "de/fuberlin/wiwiss/silk/linkspec/LinkSpecificationLanguage.xsd"

  def load(prefixes : Map[String, String]) =
  {
    new ValidatingXMLReader(node => fromXML(node, prefixes), schemaLocation)
  }

  def fromXML(node : Node, prefixes : Map[String, String]) : LinkSpecification =
  {
    new LinkSpecification(
      node \ "@id" text,
      resolveQualifiedName(node \ "LinkType" text, prefixes),
      new SourceTargetPair(DatasetSpecification.fromXML(node \ "SourceDataset" head),
                           DatasetSpecification.fromXML(node \ "TargetDataset" head)),
      (node \ "Blocking").headOption.map(blockingNode => readBlocking(blockingNode)),
      readLinkCondition(node \ "LinkCondition" head, prefixes),
      LinkFilter.fromXML(node \ "Filter" head),
      (node \ "Outputs" \ "Output").map(Output.fromXML)
    )
  }

  private def readLinkCondition(node : Node, prefixes : Map[String, String]) =
  {
    LinkCondition(readOperators(node.child, prefixes).headOption)
  }

  private def readOperators(nodes : Seq[Node], prefixes : Map[String, String]) : Traversable[Operator] =
  {
    nodes.collect
    {
      case node @ <Aggregate>{_*}</Aggregate> => readAggregation(node, prefixes)
      case node @ <Compare>{_*}</Compare> => readComparison(node, prefixes)
    }
  }

  private def readAggregation(node : Node, prefixes : Map[String, String]) : Aggregation =
  {
    val requiredStr = node \ "@required" text
    val weightStr = node \ "@weight" text

    val aggregator = Aggregator(node \ "@type" text, readParams(node))

    new Aggregation(
      if(requiredStr.isEmpty) false else requiredStr.toBoolean,
      if(weightStr.isEmpty) 1 else weightStr.toInt,
      readOperators(node.child, prefixes),
      aggregator
    )
  }

  private def readComparison(node : Node, prefixes : Map[String, String]) : Comparison =
  {
    val requiredStr = node \ "@required" text
    val weightStr = node \ "@weight" text
    val metric = Metric(node \ "@metric" text, readParams(node))
    val inputs = readInputs(node.child, prefixes)

    new Comparison(
      if(requiredStr.isEmpty) false else requiredStr.toBoolean,
      if(weightStr.isEmpty) 1 else weightStr.toInt,
      SourceTargetPair(inputs(0), inputs(1)),
      metric
    )
  }

  private def readBlocking(node : Node) : Blocking =
  {
    new Blocking(
      (node \ "@blocks").headOption.map(_.text.toInt).getOrElse(1000),
      (node \ "@overlap").headOption.map(_.text.toDouble).getOrElse(0.4)
    )
  }

  private def readInputs(nodes : Seq[Node], prefixes : Map[String, String]) : Seq[Input] =
  {
    nodes.collect {
      case p @ <Input/> =>
      {
        val pathStr = p \ "@path" text
        val path = Path.parse(pathStr, prefixes)
        PathInput(path)
      }
      case p @ <TransformInput>{_*}</TransformInput> =>
      {
        val transformer = Transformer(p \ "@function" text, readParams(p))
        TransformInput(readInputs(p.child, prefixes), transformer)
      }
    }
  }

  private def readParams(element : Node) : Map[String, String] =
  {
    element \ "Param" map(p => (p \ "@name" text, p \ "@value" text)) toMap
  }

  private def resolveQualifiedName(name : String, prefixes : Map[String, String]) =
  {
    if(name.startsWith("<") && name.endsWith(">"))
    {
      name.substring(1, name.length - 1)
    }
    else
    {
      name.split(":", 2) match
      {
        case Array(prefix, suffix) => prefixes.get(prefix) match
        {
          case Some(resolvedPrefix) => resolvedPrefix + suffix
          case None => throw new IllegalArgumentException("Unknown prefix: " + prefix)
        }
        case _ => throw new IllegalArgumentException("No prefix found in " + name)
      }
    }
  }
}
