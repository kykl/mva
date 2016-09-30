package com.rndmi.messaging.auth

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import io.bigfast.messaging.MessagingServer._
import io.bigfast.messaging.auth.AuthService
import io.grpc.Metadata
import spray.json.{DefaultJsonProtocol, JsonParser, ParserInput}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by andy on 9/28/16.
  */
class RandomAuthService extends AuthService {

  import RandomAuthService._

  implicit val rdf = randomDataFormat
  implicit val rrf = randomResponseFormat

  override def doAuth(metadata: Metadata) = Try {

    val authorization = metadata.get[String](RandomAuthService.authorizationKey)
    val session = metadata.get[String](RandomAuthService.sessionKey)

    println(s"Checking auth for $authorization, $session")

    val request = http.singleRequest(
      HttpRequest(
        uri = "https://dev-api.rndmi.com:443/v1/profiles/me?fields=null"
      ).withHeaders(
        AuthorizationHeader(authorization),
        SessionHeader(session)
      )
    )(materializer) flatMap { response =>
      val responseEntity = response.entity
      val eventualRandomResponse = unmarshaller.apply(responseEntity)

      println(s"Parsed this response: $eventualRandomResponse")
      eventualRandomResponse

    }

    val awaitedResponse = Await.result(request, 5.seconds)

    println(s"Awaited and got $awaitedResponse")

    (awaitedResponse.data.userId.toString, true)
  }
}

object RandomAuthService extends JsonSupport {
  val authorizationKey = Metadata.Key.of("AUTHORIZATION", Metadata.ASCII_STRING_MARSHALLER)
  val sessionKey = Metadata.Key.of("X-AUTHENTICATION", Metadata.ASCII_STRING_MARSHALLER)
  val http = Http()
  implicit val materializer = ActorMaterializer()

  implicit val unmarshaller: Unmarshaller[HttpEntity, RandomResponse] = {
    Unmarshaller.byteArrayUnmarshaller.mapWithCharset { (data, charset) =>
      JsonParser(ParserInput(data)).asJsObject.convertTo[RandomResponse]
    }
  }

}

final case class RandomData(userId: Long)

final case class RandomResponse(code: Long, data: RandomData)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val randomDataFormat = jsonFormat1(RandomData)
  implicit val randomResponseFormat = jsonFormat2(RandomResponse)
}