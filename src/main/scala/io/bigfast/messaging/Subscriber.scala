package io.bigfast.messaging

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator._
import io.bigfast.messaging.Subscriber.ShutdownSubscribe
import io.grpc.Context.CancellableContext
import io.grpc.stub.StreamObserver

/*
Subscriber represents the Actor for a basic user in the messaging system
It's only responsibility is to relay messages from a single topic to the streamObserver
It has 1 auxiliary function to shut down both the stream and itself upon command
 */
object Subscriber {
  def props(
             userId: String,
             topic: Topic,
             mediator: ActorRef,
             streamObserver: StreamObserver[UntypedMessage],
             rpcContext: CancellableContext
           ): Props =
    Props(classOf[Subscriber], userId, topic.id, mediator, streamObserver, rpcContext)

  def path(channelId: String, userId: String) = s"$userId@$channelId"

  case object ShutdownSubscribe

}

class Subscriber(
                  userId: String,
                  channelId: String,
                  mediator: ActorRef,
                  streamObserver: StreamObserver[UntypedMessage],
                  rpcContext: CancellableContext
                ) extends Actor with ActorLogging {
  log.info(s"Actor for user $userId booting up - registering with mediator")
  mediator ! Put(self)
  mediator ! Subscribe(channelId, self)

  override def postStop(): Unit = {
    log.info(s"Actor for user $userId shutting down!")
    if (!rpcContext.isCancelled) streamObserver.onCompleted()
    super.postStop()
  }

  def receive = {
    case message: UntypedMessage             =>
      streamObserver.onNext(message)
    case ShutdownSubscribe                   =>
      mediator ! Unsubscribe(channelId, self)
    case subscriptionAdded: SubscribeAck     =>
      log.info(s"Successfully subscribed $userId to ${subscriptionAdded.subscribe.topic}")
    case subscriptionRemoved: UnsubscribeAck =>
      log.info(s"Successfully unsubscribed $userId from ${subscriptionRemoved.unsubscribe.topic}")
      context.stop(self)
  }
}
