package io.bigfast.messaging

import java.io.File
import java.util.logging.Logger

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, SendToAll}
import akka.cluster.seed.ZookeeperClusterSeed
import com.typesafe.config.ConfigFactory
import io.bigfast.messaging.Subscriber.ShutdownSubscribe
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

    override def subscribeTopicUntyped(topic: Topic, responseObserver: StreamObserver[UntypedMessage]): Unit = {

      val rpcContext = Context.current().withCancellation()
      val process = processEventualUser(topic, responseObserver) { userId =>
        println(s"Creating actor for $userId on channel ${topic.id}")
        val subscriber = system.actorOf(
          Subscriber.props(userId, topic, mediator, responseObserver, rpcContext),
          Subscriber.path(topic.id, userId)
        )
        rpcContext.addListener(
          new CancellationListener() {
            println(s"Registering cancellation listener for $userId@${topic.id}")
            override def cancelled(context: Context): Unit = {
              logger.info(s"Client $userId disconnected - removing subscription to ${topic.id}")
              subscriber ! ShutdownSubscribe
            }
          },
          executionContext
        )
      }

      // Block here since you have to wait before killing the context
      Await.ready(process, 2.seconds)
    }

    override def publishGlobalUntyped(responseObserver: StreamObserver[Empty]): StreamObserver[UntypedMessage] = {
      val eventualStream = processEventualUser(responseObserver) { userId =>
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
              untypedMessage.topicId,
              UntypedMessage(
                userId = userId,
                content = untypedMessage.content
              )
            )
          }
        }
      }

      // Block here since you need to return the stream observer
      Await.result(eventualStream, 2.seconds)
    }

    private def processEventualUser[A, B](request: A*)(process: String => B) = {
      val eventualUser = HeaderServerInterceptor.userIdKey.get() map { userId =>
        process(userId)
      }
      eventualUser onFailure {
        case e =>
          logger.warning(s"HeaderServerInterceptor hit error ${e.printStackTrace()}")
          throw e
      }
      eventualUser
    }

    override def createTopic(request: Empty): Future[Topic] = {
      processEventualUser(request) { userId =>
        Topic(java.util.UUID.randomUUID.toString)
      }
    }

    override def shutdownSubscribe(topic: Topic): Future[Empty] =
      processEventualUser(topic) { userId =>
        mediator ! SendToAll(s"/user/${Subscriber.path(topic.id, userId)}", ShutdownSubscribe)
        Empty.defaultInstance
      }
  }

}
