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

import javax.inject.Inject

import model.{FileUploadEvent, Message, S3ObjectLocation}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import services._

import scala.util.{Failure, Success, Try}

class S3EventParser @Inject() extends MessageParser[Try] {

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

  override def parse(message: Message): Try[FileUploadEvent] =
    for {
      json               <- Try(Json.parse(message.body))
      deserializedJson   <- asTry(json.validate[S3EventNotification])
      interpretedMessage <- interpretS3EventMessage(deserializedJson)
    } yield interpretedMessage

  private def asTry[T](input: JsResult[T]): Try[T] =
    input.fold(
      errors => Failure(new Exception(s"Cannot parse the message ${errors.toString()}")),
      result => Success(result)
    )

  private def interpretS3EventMessage(result: S3EventNotification): Try[FileUploadEvent] =
    result.records match {
      case S3EventNotificationRecord(_, "aws:s3", _, _, "ObjectCreated:Post", s3Details) :: Nil =>
        Success(FileUploadEvent(S3ObjectLocation(s3Details.bucketName, s3Details.objectKey)))
      case _ => Failure(new Exception(s"Unexpected number of records in event ${result.records.toString}"))
    }

}
