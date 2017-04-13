package com.purplekingdomgames.indigo.gameengine.assets

import com.purplekingdomgames.indigo.gameengine.PowerOfTwo
import com.purplekingdomgames.indigo.gameengine.assets.TextureAtlas.supportedSizes
import com.purplekingdomgames.indigo.gameengine.scenegraph.datatypes.Point
import com.purplekingdomgames.indigo.util.Logger

object TextureAtlas {

  import TextureAtlasFunctions._

  val MaxTextureSize: PowerOfTwo = PowerOfTwo._4096

  val supportedSizes: Set[PowerOfTwo] = PowerOfTwo.all

  val create: List[ImageRef] => TextureAtlas =
    inflateAndSortByPowerOfTwo andThen groupTexturesIntoAtlasBuckets(MaxTextureSize) andThen convertToAtlas

  def lookUp(name: String, textureAtlas: TextureAtlas): Unit = ()

  val identity: TextureAtlas = TextureAtlas(Map(), Map())

}

// Output
case class TextureAtlas(atlases: Map[AtlasId, Atlas], legend: Map[String, AtlasIndex]) {
  def +(other: TextureAtlas): TextureAtlas = TextureAtlas(
    this.atlases ++ other.atlases,
    this.legend ++ other.legend
  )

  def lookUpByName(name: String): Option[AtlasLookupResult] =
    legend.get(name).flatMap { i =>
      atlases.get(i.id).map { a =>
        AtlasLookupResult(a, i.offset)
      }
    }

}
case class AtlasId(id: String)
case class AtlasIndex(id: AtlasId, offset: Point)
case class Atlas(/*TODO: image data??*/)
case class AtlasLookupResult(atlas: Atlas, offset: Point)

object TextureAtlasFunctions {

  /**
    * Type fails all over the place, no guarantee that this list is in the right order...
    * so instead of just going through the set until we find a bigger value, we have to filter and fold all
    */
  def pickPowerOfTwoSizeFor(supportedSizes: Set[PowerOfTwo], width: Int, height: Int): PowerOfTwo =
    supportedSizes
      .filter(s => s.value >= width && s.value >= height)
      .foldLeft(PowerOfTwo.Max)(PowerOfTwo.min)

  def isTooBig(max: PowerOfTwo, width: Int, height: Int): Boolean = if(width > max.value || height > max.value) true else false

//  def filterIfTooLarge(max: PowerOfTwo): List[ImageRef] => List[ImageRef] = images =>
//    images.flatMap { i =>
//      if(TextureAtlasFunctions.isTooBig(max, i.width, i.height)) {
//        Logger.info(s"Image ${i.name} is too large and will not be added to the texture atlas - may cause performance penalties")
//        Nil
//      } else List(i)
//    }

  val inflateAndSortByPowerOfTwo: List[ImageRef] => List[TextureDetails] = images =>
    images.map(i => TextureDetails(i, TextureAtlasFunctions.pickPowerOfTwoSizeFor(supportedSizes, i.width, i.height))).sortBy(_.size.value).reverse

  def groupTexturesIntoAtlasBuckets(max: PowerOfTwo): List[TextureDetails] => List[List[TextureDetails]] = list => {

    val runningTotal: List[TextureDetails] => Int = _.map(_.size.value).sum

    def rec(remaining: List[TextureDetails], current: List[TextureDetails], rejected: List[TextureDetails], acc: List[List[TextureDetails]], maximum: PowerOfTwo): List[List[TextureDetails]] = {
      (remaining, rejected) match {
        case (Nil, Nil) =>
          current :: acc

        case (Nil, x :: xs) =>
          rec(x :: xs, Nil, Nil, current :: acc, maximum)

        case (x :: xs, _) if x.size >= maximum =>
          rec(xs, current, rejected, List(x) :: acc, maximum)

        case (x :: xs, _) if runningTotal(current) + x.size.value > maximum.value =>
          rec(xs, current, x :: rejected, acc, maximum)

        case (x :: xs, _) =>
          rec(xs, x :: current, rejected, acc, maximum)

      }
    }

    rec(list, Nil, Nil, Nil, max)
  }

  val convertTextureDetailsToTree: TextureDetails => AtlasQuadTree = textureDetails => {
    AtlasQuadNode(textureDetails.size, AtlasTexture(textureDetails.imageRef))
  }

  val convertToTextureAtlas: (AtlasId, List[TextureDetails]) => TextureAtlas = (atlasId, list) =>
    list.map(convertTextureDetailsToTree).foldLeft(AtlasQuadTree.identity)(_ + _) match {
      case AtlasQuadEmpty(_) => TextureAtlas.identity
      case n: AtlasQuadNode =>
        val textureMap = n.toTextureMap

        val legend: Map[String, AtlasIndex] =
          textureMap.textureCoords.foldLeft(Map.empty[String, AtlasIndex])((m, t) => m ++ Map(t.imageRef.name -> AtlasIndex(atlasId, t.coords)))

        val atlas = Atlas()

        TextureAtlas(
          atlases = Map(
            atlasId -> atlas
          ),
          legend = legend
        )
    }

  val combineTextureAtlases: List[TextureAtlas] => TextureAtlas = list =>
    list.foldLeft(TextureAtlas.identity)(_ + _)

  val convertToAtlas: List[List[TextureDetails]] => TextureAtlas = list =>
    combineTextureAtlases(list.zipWithIndex.map(p => convertToTextureAtlas(AtlasId("atlas_" + p._2), p._1)))

  def mergeTrees(a: AtlasQuadTree, b: AtlasQuadTree, max: PowerOfTwo): Option[AtlasQuadTree] =
    (a, b) match {
      case (AtlasQuadEmpty(_), AtlasQuadEmpty(_)) =>
        Some(a)

      case (AtlasQuadNode(_, _), AtlasQuadEmpty(_)) =>
        Some(a)

      case (AtlasQuadEmpty(_), AtlasQuadNode(_, _)) =>
        Some(b)

      case (AtlasQuadNode(_, _), AtlasQuadNode(sizeB, _)) if a.canAccommodate(sizeB) =>
        mergeTreeBIntoA(a, b)

      case (AtlasQuadNode(sizeA, _), AtlasQuadNode(_, _)) if b.canAccommodate(sizeA) =>
        mergeTreeBIntoA(b, a)

      case (AtlasQuadNode(sizeA, _), AtlasQuadNode(sizeB, _)) if sizeA >= sizeB && sizeA.doubled <= max =>
        mergeTreeBIntoA(createEmptyTree(calculateSizeNeededToHouseAB(sizeA, sizeB)), a).flatMap { c =>
          mergeTreeBIntoA(c, b)
        }

      case (AtlasQuadNode(sizeA, _), AtlasQuadNode(sizeB, _)) if sizeB >= sizeA && sizeB.doubled <= max =>
        mergeTreeBIntoA(createEmptyTree(calculateSizeNeededToHouseAB(sizeA, sizeB)), b).flatMap { c =>
          mergeTreeBIntoA(c, a)
        }

      case _ =>
        Logger.info("Could not merge trees")
        None
    }

  def mergeTreeBIntoA(a: AtlasQuadTree, b: AtlasQuadTree): Option[AtlasQuadTree] =
    if (!a.canAccommodate(b.size) && !b.canAccommodate(a.size)) None
    else Option {
      if(a.canAccommodate(b.size)) a.insert(b) else b.insert(a)
    }

  def calculateSizeNeededToHouseAB(sizeA: PowerOfTwo, sizeB: PowerOfTwo): PowerOfTwo =
    if(sizeA >= sizeB) sizeA.doubled else sizeB.doubled

  def createEmptyTree(size: PowerOfTwo): AtlasQuadNode = AtlasQuadNode(size, AtlasQuadDivision.empty(size.halved))

}


// Input
case class ImageRef(name: String, width: Int, height: Int)

case class TextureDetails(imageRef: ImageRef, size: PowerOfTwo)

case class TextureMap(size: Int, textureCoords: List[TextureAndCoords])
case class TextureAndCoords(imageRef: ImageRef, coords: Point)

// Intermediate tree structure
sealed trait AtlasQuadTree {
  val size: PowerOfTwo
  def canAccommodate(requiredSize: PowerOfTwo): Boolean
  def insert(tree: AtlasQuadTree): AtlasQuadTree

  def +(other: AtlasQuadTree): AtlasQuadTree = AtlasQuadTree.append(this, other)

  def toTextureCoordsList(offset: Point): List[TextureAndCoords]
}

// Oh look! It's a monoid...
object AtlasQuadTree {

  def identity: AtlasQuadTree = AtlasQuadEmpty(PowerOfTwo._1)

  def append(first: AtlasQuadTree, second: AtlasQuadTree): AtlasQuadTree =
    TextureAtlasFunctions.mergeTrees(first, second, PowerOfTwo.Max).getOrElse(first)

}

case class AtlasQuadNode(size: PowerOfTwo, atlas: AtlasSum) extends AtlasQuadTree {
  def canAccommodate(requiredSize: PowerOfTwo): Boolean =
    if(size < requiredSize) false
    else atlas.canAccommodate(requiredSize)

  def insert(tree: AtlasQuadTree): AtlasQuadTree = this.copy ( atlas =
    atlas match {
      case AtlasTexture(_) => this.atlas

      case d @ AtlasQuadDivision(AtlasQuadEmpty(s), _, _, _) if s === tree.size =>
        d.copy(q1 = tree)
      case d @ AtlasQuadDivision(_, AtlasQuadEmpty(s), _, _) if s === tree.size =>
        d.copy(q2 = tree)
      case d @ AtlasQuadDivision(_, _, AtlasQuadEmpty(s), _) if s === tree.size =>
        d.copy(q3 = tree)
      case d @ AtlasQuadDivision(_, _, _, AtlasQuadEmpty(s)) if s === tree.size =>
        d.copy(q4 = tree)

      case d @ AtlasQuadDivision(AtlasQuadEmpty(s), _, _, _) if s > tree.size =>
        d.copy(q1 = TextureAtlasFunctions.createEmptyTree(s).insert(tree))
      case d @ AtlasQuadDivision(_, AtlasQuadEmpty(s), _, _) if s > tree.size =>
        d.copy(q2 = TextureAtlasFunctions.createEmptyTree(s).insert(tree))
      case d @ AtlasQuadDivision(_, _, AtlasQuadEmpty(s), _) if s > tree.size =>
        d.copy(q3 = TextureAtlasFunctions.createEmptyTree(s).insert(tree))
      case d @ AtlasQuadDivision(_, _, _, AtlasQuadEmpty(s)) if s > tree.size =>
        d.copy(q4 = TextureAtlasFunctions.createEmptyTree(s).insert(tree))

      case d @ AtlasQuadDivision(AtlasQuadNode(_, _), _, _, _) if d.q1.canAccommodate(tree.size) =>
        d.copy(q1 = d.q1.insert(tree))
      case d @ AtlasQuadDivision(_, AtlasQuadNode(_, _), _, _) if d.q2.canAccommodate(tree.size) =>
        d.copy(q2 = d.q2.insert(tree))
      case d @ AtlasQuadDivision(_, _, AtlasQuadNode(_, _), _) if d.q3.canAccommodate(tree.size) =>
        d.copy(q3 = d.q3.insert(tree))
      case d @ AtlasQuadDivision(_, _, _, AtlasQuadNode(_, _)) if d.q4.canAccommodate(tree.size) =>
        d.copy(q4 = d.q4.insert(tree))

      case _ =>
        Logger.info("Unexpected failure to insert tree")
        this.atlas
    }
  )

  def toTextureCoordsList(offset: Point): List[TextureAndCoords] =
    atlas match {
      case AtlasTexture(imageRef) =>
        List(TextureAndCoords(imageRef, offset))

      case AtlasQuadDivision(q1, q2, q3, q4) =>
        q1.toTextureCoordsList(offset) ++
          q2.toTextureCoordsList(offset + size.toPoint.withY(0)) ++
          q3.toTextureCoordsList(offset + size.toPoint.withX(0)) ++
          q4.toTextureCoordsList(offset + size.toPoint)
    }


  def toTextureMap: TextureMap =
    TextureMap(size.value, toTextureCoordsList(Point.zero))
}

case class AtlasQuadEmpty(size: PowerOfTwo) extends AtlasQuadTree {
  def canAccommodate(requiredSize: PowerOfTwo): Boolean = size >= requiredSize
  def insert(tree: AtlasQuadTree): AtlasQuadTree = this

  def toTextureCoordsList(offset: Point): List[TextureAndCoords] = Nil
}

sealed trait AtlasSum {
  def canAccommodate(requiredSize: PowerOfTwo): Boolean
}

case class AtlasTexture(imageRef: ImageRef) extends AtlasSum {
  def canAccommodate(requiredSize: PowerOfTwo): Boolean = false
}

case class AtlasQuadDivision(q1: AtlasQuadTree, q2: AtlasQuadTree, q3: AtlasQuadTree, q4: AtlasQuadTree) extends AtlasSum {
  def canAccommodate(requiredSize: PowerOfTwo): Boolean =
    q1.canAccommodate(requiredSize) || q2.canAccommodate(requiredSize) || q3.canAccommodate(requiredSize) || q4.canAccommodate(requiredSize)
}

object AtlasQuadDivision {
  def empty(size: PowerOfTwo): AtlasQuadDivision = AtlasQuadDivision(AtlasQuadEmpty(size), AtlasQuadEmpty(size), AtlasQuadEmpty(size), AtlasQuadEmpty(size))
}