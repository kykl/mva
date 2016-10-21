package io.bigfast.messaging.auth

import io.grpc.Context.Key
import io.grpc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by andy on 9/19/16.
  */
class HeaderServerInterceptor(implicit authService: AuthService, implicit val executionContext: ExecutionContext) extends ServerInterceptor {

  override def interceptCall[RespT, ReqT](call: ServerCall[RespT, ReqT], requestHeaders: Metadata, next: ServerCallHandler[RespT, ReqT]) = {
    val context = Context.current().withValue(
      HeaderServerInterceptor.userIdKey,
      authService.doAuth(requestHeaders)
    )
    Contexts.interceptCall(
      context,
      call,
      requestHeaders,
      next
    )
  }
}

object HeaderServerInterceptor {
  val userIdKey: Key[Future[String]] = Context.key("userId")
}