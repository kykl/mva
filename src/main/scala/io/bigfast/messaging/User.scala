package io.bigfast.messaging

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator._
import io.bigfast.messaging.Channel.Message
import io.bigfast.messaging.Channel.Subscription.{Add, Remove}
import io.grpc.stub.StreamObserver

/*
User represents the Actor for a basic user in the messaging system
It's only responsibility is to relay messages from subscribed topics to the streamObserver
It has 2 auxiliary functions that allow it to subscribe and unsubscribe from a topic on command
 */
object User {
  def props(name: String, mediator: ActorRef, streamObserver: StreamObserver[Message]): Props = Props(classOf[User], name, mediator, streamObserver)
}

class User(name: String, mediator: ActorRef, streamObserver: StreamObserver[Message]) extends Actor with ActorLogging {
  override def postStop(): Unit = {
    log.info(s"Actor for user $name shutting down!")
    super.postStop()
  }

  override def preStart(): Unit = {
    log.info(s"Actor for user $name booting up - registering with mediator")
    mediator ! Put(self)
    super.preStart()
  }

  def receive = {
    case message: Message                    =>
      streamObserver.onNext(message)
    case subscriptionAdd: Add                =>
      mediator ! Subscribe(subscriptionAdd.channelId, self)
    case subscriptionRemove: Remove          =>
      mediator ! Unsubscribe(subscriptionRemove.channelId, self)
    case subscriptionAdded: SubscribeAck     =>
      log.info(s"Successfully subscribed $name to ${subscriptionAdded.subscribe.topic}")
    case subscriptionRemoved: UnsubscribeAck =>
      log.info(s"Successfully unsubscribed $name from ${subscriptionRemoved.unsubscribe.topic}")
  }
}
