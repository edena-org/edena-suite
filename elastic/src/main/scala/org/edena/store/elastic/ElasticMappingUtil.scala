package org.edena.store.elastic

import org.edena.core.store.EdenaDataStoreException

import scala.util.Try

/**
 * Utilities for parsing a raw Elastic index mapping (as returned by `getMappings`: either the
 * mappings object containing "properties" or the bare properties map itself) into the field
 * metadata the store/query layer needs.
 *
 * @since 2026
 * @author
 *   Peter Banda
 */
object ElasticMappingUtil {

  /**
   * Collects all (multi-level) nested field paths, i.e. nodes mapped with `type: "nested"`, e.g.
   * Set("chunks", "a.b"). Mirrors the semantics of `ElasticReadonlyStore.extractNestedFieldNames`
   * but sources the information from a live index mapping instead of hand-written field
   * definitions.
   *
   * Note: a nested node carries BOTH "type" -> "nested" AND "properties" (children), whereas a
   * plain object node has "properties" only, so the type must be checked independently of the
   * presence of children.
   */
  def extractNestedPaths(mapping: Map[String, Any]): Set[String] = {
    def collect(
      props: Map[String, Any],
      parentPath: String
    ): Set[String] =
      props.flatMap { case (fieldName, definition) =>
        definition match {
          case defMap: Map[String @unchecked, Any @unchecked] =>
            val path = pathOf(parentPath, fieldName)

            val selfSet: Set[String] =
              if (defMap.get("type").contains("nested")) Set(path) else Set.empty

            val childSet = defMap.get("properties") match {
              case Some(subProps: Map[String @unchecked, Any @unchecked]) =>
                collect(subProps, path)
              case _ => Set.empty[String]
            }

            selfSet ++ childSet

          case _ => Set.empty[String]
        }
      }.toSet

    collect(rootProperties(mapping), "")
  }

  /**
   * Collects the `dense_vector` leaf paths together with their dimensions ("dims"), e.g.
   * Map("chunks.embedding" -> 1536). Entries without a parsable "dims" value are skipped.
   */
  def extractDenseVectorDims(mapping: Map[String, Any]): Map[String, Int] = {
    def collect(
      props: Map[String, Any],
      parentPath: String
    ): Map[String, Int] =
      props.flatMap { case (fieldName, definition) =>
        definition match {
          case defMap: Map[String @unchecked, Any @unchecked] =>
            val path = pathOf(parentPath, fieldName)

            val selfEntry: Map[String, Int] =
              if (defMap.get("type").contains("dense_vector"))
                defMap
                  .get("dims")
                  .flatMap(dims => Try(dims.toString.toInt).toOption)
                  .map(dims => Map(path -> dims))
                  .getOrElse(Map.empty)
              else
                Map.empty

            val childEntries = defMap.get("properties") match {
              case Some(subProps: Map[String @unchecked, Any @unchecked]) =>
                collect(subProps, path)
              case _ => Map.empty[String, Int]
            }

            selfEntry ++ childEntries

          case _ => Map.empty[String, Int]
        }
      }

    collect(rootProperties(mapping), "")
  }

  /**
   * Selects the mapping for a requested index name from a `getMappings` result (concrete index
   * name -> mapping). An exact name match wins (a concrete index); otherwise a single entry is
   * accepted (an alias resolving to one index). Multiple entries — an alias spanning several
   * concrete indices — fail, since picking one arbitrarily would silently build the store/schema
   * from the wrong index's mapping.
   */
  def selectIndexMapping(
    requestedIndexName: String,
    indexMappings: Map[String, Map[String, Any]]
  ): (String, Map[String, Any]) =
    indexMappings.get(requestedIndexName).map((requestedIndexName, _)).getOrElse {
      if (indexMappings.size > 1)
        throw new EdenaDataStoreException(
          s"The index name '$requestedIndexName' resolves to multiple indices (${indexMappings.keys.toSeq.sorted
              .mkString(", ")}) — likely a multi-index alias, which is not supported for mapping-driven dynamic stores."
        )

      indexMappings.headOption.getOrElse(
        throw new EdenaDataStoreException(
          s"No mapping found for the index '$requestedIndexName'."
        )
      )
    }

  // the mapping may come wrapped ({"properties" -> {...}}) or as the bare properties map
  private def rootProperties(mapping: Map[String, Any]): Map[String, Any] =
    mapping.get("properties") match {
      case Some(props: Map[String @unchecked, Any @unchecked]) => props
      case _                                                   => mapping
    }

  private def pathOf(
    parentPath: String,
    fieldName: String
  ): String =
    if (parentPath.isEmpty) fieldName else s"$parentPath.$fieldName"
}
