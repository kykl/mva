package io.bigfast.messaging

import java.io.File
import java.util.UUID
import java.util.logging.Logger

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.typesafe.config.ConfigFactory
import io.bigfast.messaging.Channel.Message
import io.bigfast.messaging.Channel.Subscription.{Add, Remove}
import io.bigfast.messaging.auth.{AuthService, HeaderServerInterceptor}
import io.grpc._
import io.grpc.stub.StreamObserver

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


/**
  * MessagingServer
  * Allowed injection of authentication mechanism
  * 2 types of endpoints - user and privileged
  */

object MessagingServer {
  implicit val executionContext = ExecutionContext.global
  // Start Akka Cluster
  val systemName = "DistributedMessaging"
  implicit val system = ActorSystem(systemName)
  val joinAddress = Cluster(system).selfAddress
  val mediator = DistributedPubSub(system).mediator
  private val logger = Logger.getLogger(classOf[MessagingServer].getName)
  private val port = 8443
  Cluster(system).join(joinAddress)

  def main(args: Array[String]): Unit = {
    val server = new MessagingServer
    server.start()
    server.blockUntilShutdown()
  }
}

class MessagingServer {
  self =>

  import MessagingServer._

  // Start Auth Service
  val authServiceClassName = ConfigFactory.load().getString("auth.service")
  implicit val authService: AuthService = getClass.getClassLoader.loadClass(authServiceClassName).newInstance().asInstanceOf[AuthService]

  private[this] var server: Server = _

  private def start(): Unit = {
    val certFile = new File("/etc/secrets/cert-chain")
    val privateKey = new File("/etc/secrets/private-key")
    server = ServerBuilder
      .forPort(MessagingServer.port)
      .useTransportSecurity(certFile, privateKey)
      .addService(
        ServerInterceptors.intercept(
          MessagingGrpc.bindService(new ChatImpl, executionContext),
          new HeaderServerInterceptor
        )
      )
      .build
      .start

    logger.info("Server started, listening on " + MessagingServer.port)
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("*** shutting down gRPC server since JVM is shutting down")
        self.stop()
        logger.info("*** server shut down")
      }
    })
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class ChatImpl extends MessagingGrpc.Messaging {

    override def channelMessageStream(responseObserver: StreamObserver[Message]): StreamObserver[Message] = {
      val userId: String = Await.result(HeaderServerInterceptor.userIdKey.get(), 2.seconds)
      logger.info(s"Creating stream for userId: $userId")
      val userActor = system.actorOf(User.props(userId, mediator, responseObserver))

      new StreamObserver[Channel.Message] {
        override def onError(t: Throwable): Unit = {
          responseObserver.onError(t)
          logger.warning(s"channelMessageStream.onError(${t.getMessage}) for user $userId")
          userActor ! PoisonPill
          throw t
        }

        override def onCompleted(): Unit = {
          logger.info(s"Shutting down channelMessageStream for $userId")
          userActor ! PoisonPill
          responseObserver.onCompleted()
        }

        override def onNext(message: Message): Unit = {
          mediator ! Publish(message.channelId.toString, message)
        }
      }
    }

    override def createChannel(request: Empty): Future[Channel] =
      eventualPrivilegeCheck(request) { request =>
        val channelId = UUID.randomUUID().toString
        val channel = Channel(channelId)
        logger.info(s"Create channel ${channel.id}")
        channel
      }

    override def subscribeChannel(request: Add): Future[Empty] =
      eventualPrivilegeCheck(request) { request =>
        logger.info(s"Subscribe to channel ${request.channelId} for user ${request.userId}")
        val adminTopic = User.adminTopic(request.userId.toString)
        mediator ! Publish(adminTopic, Add(request.channelId, request.userId))
        Empty.defaultInstance
      }

    private def eventualPrivilegeCheck[A, B](request: A)(process: A => B) = {
      HeaderServerInterceptor.privilegedKey.get() map { privileged =>
        if (privileged) process(request)
        else throw new Exception("Not Privileged")
      } recover {
        case e =>
          logger.warning(s"HeaderServerInterceptor hit error ${e.printStackTrace()}")
          throw e
      }
    }

    override def unsubscribeChannel(request: Remove): Future[Empty] =
      eventualPrivilegeCheck(request) { request =>
        logger.info(s"Unsubscribe from channel ${request.channelId} for user ${request.userId}")
        val adminTopic = User.adminTopic(request.userId.toString)
        mediator ! Publish(adminTopic, Remove(request.channelId, request.userId))
        Empty.defaultInstance
      }
  }

}
