package io.bigfast.messaging

import java.io.File
import java.util.logging.Logger

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, SendToAll}
import akka.cluster.seed.ZookeeperClusterSeed
import com.typesafe.config.ConfigFactory
import io.bigfast.messaging.auth.{AuthService, HeaderServerInterceptor}
import io.grpc.Context.CancellationListener
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
  val mediator = DistributedPubSub(system).mediator
  private val logger = Logger.getLogger(classOf[MessagingServer].getName)
  private val port = 8443
  ZookeeperClusterSeed(system).join()

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

    override def untypedChannelSubscribe(subscription: Subscription, responseObserver: StreamObserver[UntypedMessage]): Unit = {
      val channelId = subscription.channelId

      val userId = Await.result(HeaderServerInterceptor.userIdKey.get(), 2.seconds)

      val rpcContext = Context.current().withCancellation()

      println(s"Creating actor for $userId on channel $channelId")
      val subscriber = system.actorOf(
        Subscriber.props(subscription, mediator, responseObserver, rpcContext),
        Subscriber.path(subscription)
      )
      rpcContext.addListener(
        new CancellationListener() {
          println(s"Adding cancellation listening in case client disconnects")

          override def cancelled(context: Context): Unit = {
            logger.info(s"Client $userId disconnected - removing subscription to $channelId")
            subscriber ! subscription
          }
        },
        executionContext
      )
    }

    override def untypedGlobalPublish(responseObserver: StreamObserver[Empty]): StreamObserver[UntypedMessage] = {
      val eventualUserId = HeaderServerInterceptor.userIdKey.get()

      val eventualStream = eventualUserId map { userId =>
        logger.info(s"Returning publishing stream for $userId")
        new StreamObserver[UntypedMessage] {
          override def onError(t: Throwable): Unit = {
            logger.warning(s"untypedGlobalPublish.onError(${t.getMessage}) for $userId")
          }

          override def onCompleted(): Unit = {
            logger.info(s"untypedGlobalPublish.onCompleted() for $userId")
            responseObserver.onCompleted()
          }

          override def onNext(untypedMessage: UntypedMessage): Unit = {
            mediator ! Publish(
              untypedMessage.channelId,
              UntypedMessage(
                userId = untypedMessage.userId,
                content = untypedMessage.content
              )
            )
          }
        }
      } recover {
        case e: Throwable =>
          logger.warning(s"untypedGlobalPublish error: ${e.getMessage}")
          throw e
      }

      // Block here since you need to return the stream observer
      Await.result(eventualStream, 2.seconds)
    }

    override def shutdownSubscriber(subscription: Subscription): Future[Empty] = eventualPrivilegeCheck(subscription) { subscription =>
      mediator ! SendToAll(s"/user/${Subscriber.path(subscription)}", subscription)
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
  }

}
