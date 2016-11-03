package com.rndmi.messaging

import java.util.concurrent.TimeUnit

import com.google.common.base.Charsets
import com.google.protobuf.ByteString
import io.bigfast.messaging.MessagingGrpc._
import io.bigfast.messaging._
import io.grpc._
import io.grpc.stub.{MetadataUtils, StreamObserver}

import scala.io.Source

/**
  * MessagingConsumer
  * Reference Scala implementation
  * Uses netty (not realistic in Android/mobile)
  * Create channel (privileged)
  * Subscribe to channel (privileged)
  * Connect to bidirectional stream
  * Send and receive the same message twice
  */

object MessagingConsumer {
  // Hardcoded from rndmi internal auth
  val userId = "18127"
  val subscribeTopic = "client2Server"
  val publishTopic = "server2Client"

  def main(args: Array[String]): Unit = {
    val mc = MessagingConsumer(host = args(0))
    print(s"Running stuff")

    try {
      while (true) {
        print(".")
        Thread.sleep(5000)
      }
    }
    finally {
      mc.shutdown()
    }
  }

  def apply(host: String = "localhost", port: Int = 8443): MessagingConsumer = {
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
    new MessagingConsumer(channel, blockingStub, asyncStub)
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

class MessagingConsumer private(channel: ManagedChannel, blockingStub: MessagingBlockingStub, asyncStub: MessagingStub) {

  val responseObserver = asyncStub.publishGlobalUntyped(
    new StreamObserver[Empty] {
      println(s"Got publishing observer")
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
    Topic(MessagingConsumer.subscribeTopic),
    new StreamObserver[UntypedMessage] {
      println(s"Got subscribing observer")

      override def onError(t: Throwable): Unit = {
        println(s"requestObserver.onError(${t.getMessage})")
        throw t
      }

      override def onCompleted(): Unit = {
        println(s"requestObserver.onCompleted()")
      }

      override def onNext(value: UntypedMessage): Unit = {
        println(s"Got message and will send back ${value.toString}")
        handleMessage(value)
      }
    }
  )

  def shutdown(): Unit = {
    unsubscribe(Topic(MessagingConsumer.subscribeTopic))
    responseObserver.onCompleted()
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def unsubscribe(topic: Topic) = {
    blockingStub.shutdownSubscribe(topic)
  }

  private def handleMessage(message: UntypedMessage): Unit = {
    responseObserver.onNext(message.withTopicId(MessagingConsumer.publishTopic))
  }
}