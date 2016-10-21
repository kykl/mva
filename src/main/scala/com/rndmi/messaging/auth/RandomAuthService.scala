package com.rndmi.messaging.auth

import java.util.logging.Logger

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import io.bigfast.messaging.MessagingServer._
import io.bigfast.messaging.auth.AuthService
import io.grpc.Metadata
import spray.json.{JsonParser, ParserInput}

/**
  * Created by andy on 9/28/16.
  */
class RandomAuthService extends AuthService {

  import RandomAuthService._

  override def doAuth(metadata: Metadata) = {

    val authorization = metadata.get[String](authorizationKey)
    val session = metadata.get[String](sessionKey)

    logger.info(s"Checking auth for $authorization, $session")

    val httpResponse = http.singleRequest(
      HttpRequest(
        uri = "https://dev-api.rndmi.com:443/v1/profiles/me?fields=userId"
      ).withHeaders(
        AuthorizationHeader(authorization),
        SessionHeader(session)
      )
    )(materializer)

    httpResponse flatMap { response =>
      val responseEntity = response.entity
      val eventualRandomResponse = unmarshaller.apply(responseEntity)

      logger.info(s"Parsed this response: $eventualRandomResponse")
      eventualRandomResponse
    } map { resp =>
      resp.data.userId.toString
    }
  }
}

object RandomAuthService extends JsonSupport {
  val authorizationKey = Metadata.Key.of("AUTHORIZATION", Metadata.ASCII_STRING_MARSHALLER)
  val sessionKey = Metadata.Key.of("X-AUTHENTICATION", Metadata.ASCII_STRING_MARSHALLER)
  val http = Http()
  implicit val materializer = ActorMaterializer()

  val logger = Logger.getLogger(this.getClass.getName)

  val unmarshaller: Unmarshaller[HttpEntity, RandomResponse] = {
    Unmarshaller.byteArrayUnmarshaller mapWithCharset { (data, charset) =>
      JsonParser(ParserInput(data)).asJsObject.convertTo[RandomResponse]
    }
  }
}
