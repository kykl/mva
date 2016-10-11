package io.bigfast.messaging

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe}
import io.bigfast.messaging.Channel.Message
import io.grpc.stub.StreamObserver

/*
User represents the Actor for a basic user in the messaging system
It's only responsibility is to relay messages from subscribed topics to the streamObserver
It has 2 auxiliary functions that allow it to subscribe and unsubscribe from a topic on command
 */
object User {
  def props(name: String, mediator: ActorRef, streamObserver: StreamObserver[Message]): Props = Props(classOf[User], name, mediator, streamObserver)

  def adminTopic(name: String) = s"admin-$name"
}

class User(name: String, mediator: ActorRef, streamObserver: StreamObserver[Message]) extends Actor with ActorLogging {
  mediator ! Subscribe(User.adminTopic(name), self)

  def receive = {
    case message: Message =>
      log.info(message.toString)
      streamObserver.onNext(message)
    case subscriptionAdd: Channel.Subscription.Add =>
      mediator ! Subscribe(subscriptionAdd.channelId, self)
    case subscriptionRemove: Channel.Subscription.Remove =>
      mediator ! Unsubscribe(subscriptionRemove.channelId, self)
    case subscriptionAdded: SubscribeAck =>
      log.info(s"Successfully subscribed $name to ${subscriptionAdded.subscribe.topic}")
  }
}
