/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors.aws

import cats.MonadError
import cats.implicits._
import model.{FileUploadEvent, Message, S3ObjectLocation}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import services._

import scala.util.Try

class S3EventParser[F[_]](implicit monadError: MonadError[F, Throwable]) extends MessageParser[F] {

  case class S3EventNotification(records: Seq[S3EventNotificationRecord])

  case class S3EventNotificationRecord(
    eventVersion: String,
    eventSource: String,
    awsRegion: String,
    eventTime: String,
    eventName: String,
    s3: S3Details)

  case class S3Details(bucketName: String, objectKey: String)

  implicit val s3reads: Reads[S3Details] =
    ((JsPath \ "bucket" \ "name").read[String] and
      (JsPath \ "object" \ "key").read[String])(S3Details.apply _)

  implicit val reads: Reads[S3EventNotificationRecord] = Json.reads[S3EventNotificationRecord]

  implicit val messageReads: Reads[S3EventNotification] =
    (JsPath \ "Records").read[Seq[S3EventNotificationRecord]].map(S3EventNotification)

  override def parse(message: Message): F[FileUploadEvent] =
    messageToJson(message)
      .flatMap(parseJson)
      .flatMap(interpretS3EventMessage)

  private def messageToJson(message: Message): F[JsValue] = monadError.fromTry(Try(Json.parse(message.body)))

  private def parseJson(json: JsValue): F[S3EventNotification] =
    json
      .validate[S3EventNotification]
      .fold(
        errors => monadError.raiseError(new Exception(s"Cannot parse the message ${errors.toString()}")),
        result => monadError.pure(result)
      )

  private def interpretS3EventMessage(result: S3EventNotification): F[FileUploadEvent] =
    result.records match {
      case S3EventNotificationRecord(_, "aws:s3", _, _, "ObjectCreated:Post", s3Details) :: Nil =>
        monadError.pure(FileUploadEvent(S3ObjectLocation(s3Details.bucketName, s3Details.objectKey)))
      case _ =>
        monadError.raiseError(new Exception(s"Unexpected number of records in event ${result.records.toString}"))
    }

}
