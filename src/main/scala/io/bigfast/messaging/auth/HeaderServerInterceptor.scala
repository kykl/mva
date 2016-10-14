package io.bigfast.messaging.auth

import io.grpc.Context.Key
import io.grpc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by andy on 9/19/16.
  */
class HeaderServerInterceptor(implicit authService: AuthService, implicit val executionContext: ExecutionContext) extends ServerInterceptor {

  override def interceptCall[RespT, ReqT](call: ServerCall[RespT, ReqT], requestHeaders: Metadata, next: ServerCallHandler[RespT, ReqT]) = {
    val eventualUserPrivilege =
      for {
        (uid: String, priv: Boolean) <- authService.doAuth(requestHeaders)
      } yield {
        (uid, priv)
      }

    val eventualUserId = eventualUserPrivilege map (_._1)
    val eventualPrivilege = eventualUserPrivilege map (_._2)


    val context = Context.current().withValues(
      HeaderServerInterceptor.userIdKey,
      eventualUserId,
      HeaderServerInterceptor.privilegedKey,
      eventualPrivilege
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
  val privilegedKey: Key[Future[Boolean]] = Context.key("isPrivileged")
}