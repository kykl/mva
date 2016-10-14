package com.rndmi.messaging

import java.util.concurrent.TimeUnit

import com.google.common.base.Charsets
import com.google.protobuf.ByteString
import io.bigfast.messaging.Channel.{Message, Subscription}
import io.bigfast.messaging.MessagingGrpc._
import io.bigfast.messaging.{Empty, MessagingGrpc}
import io.bigfast.playerstateaction.PlayerStateAction
import io.bigfast.playerstateaction.PlayerStateAction.{GameState, Position, Velocity}
import io.grpc._
import io.grpc.stub.{MetadataUtils, StreamObserver}

import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

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

  def main(args: Array[String]): Unit = {
    val messagingClient = MessagingClient(host = "messaging.rndmi.com")

    Try {
      messagingClient.connectStream
    } match {
      case Success(_)         =>
        println("Completed test")
      case Failure(exception) =>
        println(exception)
        messagingClient.shutdown()
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
  def connectStream: StreamObserver[Message] = {
    val r = new StreamObserver[Message] {
      override def onError(t: Throwable): Unit = {
        println(t)
      }

      override def onCompleted(): Unit = {
        println("Completed Stream")
      }

      override def onNext(message: Message): Unit = {
        val rawString = MessagingClient.decodeAsDataString(message.content)
        println(s"Client Receive Message: $rawString")

        val playerStateAction = PlayerStateAction.parseFrom(message.content.toByteArray)
        println("Got this player state action")
        println(playerStateAction.toString)
      }
    }

    val requestObserver = asyncStub.channelMessageStream(r)

    println(s"Testing channel Create")
    val chatChannel = blockingStub.createChannel(Empty())
    println(s"Created channel with id ${chatChannel.id}")

    println(s"Subscribing to channel ${chatChannel.id}")
    blockingStub.subscribeChannel(Subscription.Add(
      chatChannel.id,
      MessagingClient.userId
    ))
    Thread.sleep(Random.nextInt(1000) + 500)


    println(s"Testing messaging of complex data")

    val stateAction1 = PlayerStateAction(
      channelId = "channel1",
      userId = "user1",
      timestamp = System.currentTimeMillis(),
      playId = "play1",
      playerState = Some(GameState(
        Some(Position(3F, 1F, 2F)),
        Some(Velocity(1F, 3F, 1F))
      ))
    )
    requestObserver.onNext(
      Message(
        channelId = chatChannel.id,
        userId = MessagingClient.userId,
        content = MessagingClient.encodeAsByteString(stateAction1.toByteArray)
      )
    )
    Thread.sleep(Random.nextInt(1000) + 500)

    val stateAction2 = PlayerStateAction(
      channelId = "channel1",
      userId = "user1",
      timestamp = System.currentTimeMillis(),
      playId = "play2",
      playerState = Some(GameState(
        Some(Position(0F, 1F, 2F)),
        Some(Velocity(3F, 2F, 1F))
      ))
    )

    requestObserver.onNext(
      Message(
        channelId = chatChannel.id,
        userId = MessagingClient.userId,
        content = MessagingClient.encodeAsByteString(stateAction2.toByteArray)
      )
    )
    Thread.sleep(Random.nextInt(1000) + 500)

    requestObserver.onCompleted()

    r
  }

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }
}