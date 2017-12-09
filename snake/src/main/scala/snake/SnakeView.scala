package snake

import com.purplekingdomgames.indigo.gameengine.scenegraph._
import com.purplekingdomgames.indigo.gameengine.scenegraph.datatypes.Point

import scala.language.implicitConversions

object SnakeView {

  implicit def snakePointToYInvertedPoint(snakePoint: SnakePoint): Point =
    Point(snakePoint.x * 16, (15 - snakePoint.y) * 16)

  def updateView(model: SnakeModel): SceneGraphUpdate[SnakeEvent] =
    SceneGraphUpdate(
      SceneGraphRootNode(
        game = gameLayer(model, if(model.running) model.staticAssets.snakeAlive else model.staticAssets.snakeDead),
        lighting = SceneGraphLightingLayer.empty,
        ui = SceneGraphUiLayer.empty
      ),
      Nil
    )

  def gameLayer(currentState: SnakeModel, snakeAsset: Graphic[SnakeEvent]): SceneGraphGameLayer[SnakeEvent] =
    SceneGraphGameLayer()
      .addChild(currentState.staticAssets.outerWalls)
      .addChild(currentState.staticAssets.apple.moveTo(currentState.apple.x * 16, (15 - currentState.apple.y) *  16))
      .addChildren(currentState.snake.givePath.map(pt => snakeAsset.moveTo(pt)))

}