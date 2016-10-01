package com.rndmi.messaging.auth

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * SprayJson types and implicit conversions
  * Required for parsing auth endpoint json
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val randomDataFormat = jsonFormat1(RandomData)
  implicit val randomResponseFormat = jsonFormat2(RandomResponse)
}

final case class RandomData(userId: Long)

final case class RandomResponse(code: Long, data: RandomData)

