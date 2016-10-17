package com.rndmi.messaging

import java.util.concurrent.TimeUnit

import com.google.common.base.Charsets
import com.google.protobuf.ByteString
import io.bigfast.messaging.Channel.{Message, Subscription}
import io.bigfast.messaging.MessagingGrpc
import io.bigfast.messaging.MessagingGrpc._
import io.bigfast.playerstateaction.PlayerStateAction
import io.grpc._
import io.grpc.stub.{MetadataUtils, StreamObserver}

import scala.io.Source

/**
  * MessagingClient
  * Reference Scala implementation
  * Uses netty (not realistic in Android/mobile)
  * Create channel (privileged)
  * Subscribe to channel (privileged)
  * Connect to bidirectional stream
  * Send and receive the same message twice
  */

object MessagingClient {
  // Hardcoded from rndmi internal auth
  val userId = "18127"
  val incomingChannel = "client2Server"
  val outgoingChannel = "server2Client"

  def main(args: Array[String]): Unit = {
    MessagingClient(host = "messaging.rndmi.com")
    print(s"Running stuff")

    while (true) {
      print(".")
      Thread.sleep(5000)
    }
  }

  def apply(host: String = "localhost", port: Int = 8443): MessagingClient = {
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
    new MessagingClient(channel, blockingStub, asyncStub)
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

class MessagingClient private(channel: ManagedChannel, blockingStub: MessagingBlockingStub, asyncStub: MessagingStub) {

  val requestObserver = asyncStub.channelMessageStream(

    new StreamObserver[Message] {
      println(s"Creating a new bi-directional stream")

      override def onError(t: Throwable): Unit = {
        println(t)
        shutdown()
      }

      override def onCompleted(): Unit = {
        println("Completed Stream")
        shutdown()
      }

      override def onNext(message: Message): Unit = {
        handleMessage(message)
      }
    })

  Thread.sleep(3000)

  println(s"Subscribing to inbound channel")
  blockingStub.subscribeChannel(Subscription.Add(
    MessagingClient.incomingChannel,
    MessagingClient.userId
  ))

  Thread.sleep(1000)

  requestObserver.onNext(new Message(userId = "18128", channelId = "client2Server"))

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  private def handleMessage(message: Message): Unit = {
    requestObserver.onNext(message.withChannelId(MessagingClient.outgoingChannel))
  }
}