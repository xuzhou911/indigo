port module App.ScalaJSMailbox exposing (receive, send)

import App.Msg exposing (..)
import Debug exposing (log)
import Json.Decode as Decode exposing (..)
import Json.Encode as Encode


port sendToScalaJS : ForScala -> Cmd msg


port receiveFromScalaJS : (String -> msg) -> Sub msg


type alias ForScala =
    Encode.Value


send : SendToScala -> Cmd msg
send toSend =
    case toSend of
        LogIt s ->
            sendToScalaJS <| logItEncoder s


receive : Sub FromScala
receive =
    receiveFromScalaJS decodeScalaMessage


decodeScalaMessage : String -> FromScala
decodeScalaMessage msg =
    case Decode.decodeString decoders msg of
        Err e ->
            (log <| Debug.toString e)
                Ignore

        Ok result ->
            result



-- Encoders


logItEncoder : String -> ForScala
logItEncoder s =
    Encode.object
        [ ( "message", Encode.string s ) ]



-- Decoders


decoders : Decoder FromScala
decoders =
    Decode.oneOf
        [ ignoreDecoder
        ]


ignoreDecoder : Decode.Decoder FromScala
ignoreDecoder =
    Decode.string
        |> andThen ignoreStringDecoder


ignoreStringDecoder : String -> Decode.Decoder FromScala
ignoreStringDecoder s =
    case s of
        "" ->
            Decode.succeed Ignore

        "noop" ->
            Decode.succeed Ignore

        "ignore" ->
            Decode.succeed Ignore

        _ ->
            Decode.fail "Tried to decide if this action should be ignored, but couldn't."
