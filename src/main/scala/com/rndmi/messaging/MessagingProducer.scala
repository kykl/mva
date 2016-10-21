package com.rndmi.messaging

import java.util.concurrent.TimeUnit

import com.google.common.base.Charsets
import com.google.protobuf.ByteString
import io.bigfast.messaging.MessagingGrpc.{MessagingBlockingStub, MessagingStub}
import io.bigfast.messaging._
import io.grpc.stub.{MetadataUtils, StreamObserver}
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata}

import scala.io.Source

/**
  * Created by andy on 10/19/16.
  */
object MessagingProducer {
  // Hardcoded from rndmi internal auth
  val userId = "18128"
  val subscribeTopic = "server2Client"
  val publishTopic = "client2Server"

  def main(args: Array[String]): Unit = {
    val messagingProducer = MessagingProducer(host = "messaging.rndmi.com")
    print(s"Running stuff")

    try {
      while (true) {
        messagingProducer.responseObserver.onNext(
          UntypedMessage(topicId = MessagingProducer.publishTopic)
        )
        Thread.sleep(1000)
      }
    }
    finally {
      messagingProducer.shutdown()
    }
  }

  def apply(host: String = "localhost", port: Int = 8443): MessagingProducer = {
    val builder = ManagedChannelBuilder.forAddress(host, port)
    val channel = builder.build()

    // Set up metadata from hidden auth file
    val authLines = Source.fromFile(s"client-auth-$userId.pem").getLines()
    val authorization = authLines.next()
    val session = authLines.next()
    val metadata = new Metadata()
    metadata.put(
      Metadata.Key.of("AUTHORIZATION", Metadata.ASCII_STRING_MARSHALLER),
      authorization
    )
    metadata.put(
      Metadata.Key.of("X-AUTHENTICATION", Metadata.ASCII_STRING_MARSHALLER),
      session
    )

    // Set up stubs
    val blockingStub = MetadataUtils.attachHeaders(
      MessagingGrpc.blockingStub(channel),
      metadata
    )
    val asyncStub = MetadataUtils.attachHeaders(
      MessagingGrpc.stub(channel),
      metadata
    )
    new MessagingProducer(channel, blockingStub, asyncStub)
  }

  def encodeAsByteString(dataString: String): ByteString = {
    val byteString = dataString.getBytes(Charsets.ISO_8859_1)
    ByteString.copyFrom(byteString)
  }

  def encodeAsByteString(dataBytes: Array[Byte]): ByteString = {
    ByteString.copyFrom(dataBytes)
  }

  def decodeAsDataString(byteString: ByteString): String = {
    val messageByteString = byteString.toByteArray
    new String(messageByteString, Charsets.ISO_8859_1)
  }
}


class MessagingProducer private(channel: ManagedChannel, blockingStub: MessagingBlockingStub, asyncStub: MessagingStub) {

  val responseObserver = asyncStub.publishGlobalUntyped(
    new StreamObserver[Empty] {
      override def onError(t: Throwable): Unit = {
        println(s"responseObserver.onError(${t.getMessage})")
        throw t
      }

      override def onCompleted(): Unit = {
        println(s"responseObserver.onCompleted()")
      }

      override def onNext(value: Empty): Unit = {
        println(s"Got Empty message for some reason")
      }
    }
  )

  val requestObserver = asyncStub.subscribeTopicUntyped(
    Topic(MessagingProducer.subscribeTopic),
    new StreamObserver[UntypedMessage] {
      override def onError(t: Throwable): Unit = {
        println(s"requestObserver.onError(${t.getMessage})")
        throw t
      }

      override def onCompleted(): Unit = {
        println(s"requestObserver.onCompleted()")
      }

      override def onNext(value: UntypedMessage): Unit = {
        println(s"Got message back ${value.toString}")
      }
    }
  )

  def shutdown(): Unit = {
    responseObserver.onCompleted()
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }
}