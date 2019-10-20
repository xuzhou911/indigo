import cats.syntax.functor._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.Json

sealed trait ScalaMessage
case object Noop extends ScalaMessage

object ScalaMessage {

  implicit val encodeEvent: Encoder[ScalaMessage] = Encoder.instance {
    case Noop => Json.fromString("noop")
  }

  implicit val decodeEvent: Decoder[ScalaMessage] =
    List[Decoder[ScalaMessage]](
      // Decoder[Doubled].widen
    ).reduceLeft(_ or _)

}
