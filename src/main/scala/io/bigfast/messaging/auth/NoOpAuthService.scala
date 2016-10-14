package io.bigfast.messaging.auth

import io.grpc.Metadata

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by andy on 9/28/16.
  */
class NoOpAuthService(implicit val executionContext: ExecutionContext) extends AuthService {
  override def doAuth(metadata: Metadata): Future[(String, Boolean)] = Future {
    val userId = metadata.get[String](NoOpAuthService.userKey)
    (userId, false)
  }
}

object NoOpAuthService {
  val userKey = Metadata.Key.of("X-USER", Metadata.ASCII_STRING_MARSHALLER)
}