package indigojs.delegates

import scala.scalajs.js.annotation._
import scala.scalajs.js.JSConverters._
import scala.scalajs.js
import indigo.shared.Outcome

@SuppressWarnings(Array("org.wartremover.warts.Any"))
@JSExportTopLevel("Outcome")
final class OutcomeDelegate(_state: js.Object, _globalEvents: js.UndefOr[js.Array[GlobalEventDelegate]]) {

  @JSExport
  val state = _state
  @JSExport
  val globalEvents = _globalEvents.toOption match {
      case Some(e) => e
      case None => new js.Array()
  }

  @JSExport
  def addEvent(event: GlobalEventDelegate): OutcomeDelegate =
    addEvents(js.Array(event))

  @JSExport
  def addEvents(events: js.Array[GlobalEventDelegate]): OutcomeDelegate =
    new OutcomeDelegate(state, Some(globalEvents.concat(events)).orUndefined)

  def toInternal: Outcome[js.Object] =
    new Outcome(state, globalEvents.toList.map(_.toInternal))

}

@SuppressWarnings(Array("org.wartremover.warts.Any"))
@JSExportTopLevel("OutcomeHelper")
object OutcomeDelegame {

  @JSExport
  def of(state: js.Object): OutcomeDelegate = {
    val arr: js.Array[GlobalEventDelegate] = new js.Array()
    new OutcomeDelegate(state, Some(arr).orUndefined)
  }

}
