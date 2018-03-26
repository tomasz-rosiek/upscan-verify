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

package services

import cats.{Id, ~>}
import model._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import cats.implicits._

class ScanUploadedFilesFlowSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit def idToFuture: Id ~> Future =
    new (Id ~> Future) {
      override def apply[A](fa: Id[A]) = Future(fa)
    }

  val parser = new MessageParser[Id] {
    override def parse(message: Message) = message.body match {
      case "VALID-BODY" => FileUploadEvent(S3ObjectLocation("bucket", message.id))
      case _            => throw new Exception("Invalid body")
    }
  }

  val fileDetailsRetriever = new FileNotificationDetailsRetriever[Id] {
    override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation) = UploadedFile(objectLocation)
  }

  "ScanUploadedFilesFlow" should {
    "get messages from the queue consumer, and scan and postprocess valid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage = Message("ID", "VALID-BODY", "RECEIPT-1")

      val queueConsumer = mock[QueueConsumer[Id]]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage))

      val scanningService = mock[ScanningService[Id]]
      Mockito
        .when(scanningService.scan(any()))
        .thenReturn(FileIsClean(S3ObjectLocation("bucket", "ID")))

      val scanningResultHandler = mock[ScanningResultHandler[Id]]
      Mockito.when(scanningResultHandler.handleScanningResult(any())).thenReturn(Future.successful(()))

      val queueOrchestrator =
        new ScanUploadedFilesFlow(
          queueConsumer,
          new MessageProcessingFlow(parser, fileDetailsRetriever, scanningService, scanningResultHandler))

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("scanning result handler is called")
      Mockito
        .verify(scanningResultHandler)
        .handleScanningResult(FileIsClean(S3ObjectLocation("bucket", "ID")))

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage)
    }

    "get messages from the queue consumer, and perform scanning for valid messages and ignore invalid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage1  = Message("ID1", "VALID-BODY", "RECEIPT-1")
      val invalidMessage = Message("ID2", "INVALID-BODY", "RECEIPT-2")
      val validMessage2  = Message("ID3", "VALID-BODY", "RECEIPT-3")

      val queueConsumer = mock[QueueConsumer[Id]]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage1, invalidMessage, validMessage2))
      Mockito
        .when(queueConsumer.confirm(any()))
        .thenReturn(Future.successful(()))
        .thenReturn(Future.successful(()))

      val scanningService = mock[ScanningService[Id]]
      Mockito
        .when(scanningService.scan(UploadedFile(S3ObjectLocation("bucket", "ID1"))))
        .thenReturn(FileIsClean(S3ObjectLocation("bucket", "ID1")))

      Mockito
        .when(scanningService.scan(UploadedFile(S3ObjectLocation("bucket", "ID3"))))
        .thenReturn(Future.successful(FileIsInfected(S3ObjectLocation("bucket", "ID3"), "infection")))

      val scanningResultHandler = mock[ScanningResultHandler[Id]]
      Mockito
        .when(scanningResultHandler.handleScanningResult(any()))
        .thenReturn(Future.successful(()))
        .thenReturn(Future.successful(()))

      val queueOrchestrator =
        new ScanUploadedFilesFlow(
          queueConsumer,
          new MessageProcessingFlow(parser, fileDetailsRetriever, scanningService, scanningResultHandler))

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("notification service is called only for valid messages")
      Mockito
        .verify(scanningResultHandler)
        .handleScanningResult(FileIsClean(S3ObjectLocation("bucket", "ID1")))
      Mockito
        .verify(scanningResultHandler)
        .handleScanningResult(FileIsInfected(S3ObjectLocation("bucket", "ID3"), "infection"))
      Mockito.verifyNoMoreInteractions(scanningResultHandler)

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage1)
      Mockito.verify(queueConsumer).confirm(validMessage2)

      And("invalid messages are not confirmed")
      Mockito.verifyNoMoreInteractions(queueConsumer)
    }

    "do not confirm valid messages for which scanning has failed" in {

      Given("there are only valid messages in a message queue")
      val validMessage1 = Message("ID1", "VALID-BODY", "RECEIPT-1")
      val validMessage2 = Message("ID2", "VALID-BODY", "RECEIPT-2")
      val validMessage3 = Message("ID3", "VALID-BODY", "RECEIPT-3")

      val queueConsumer = mock[QueueConsumer[Id]]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage1, validMessage2, validMessage3))

      val scanningService = mock[ScanningService[Id]]
      Mockito
        .when(scanningService.scan(UploadedFile(S3ObjectLocation("bucket", "ID1"))))
        .thenReturn(FileIsClean(S3ObjectLocation("bucket", "ID1")))

      Mockito
        .when(scanningService.scan(UploadedFile(S3ObjectLocation("bucket", "ID2"))))
        .thenThrow(new Exception("Planned exception"))

      Mockito
        .when(scanningService.scan(UploadedFile(S3ObjectLocation("bucket", "ID3"))))
        .thenReturn(Future.successful(FileIsInfected(S3ObjectLocation("bucket", "ID3"), "infection")))

      val scanningResultHandler = mock[ScanningResultHandler[Id]]

      val queueOrchestrator =
        new ScanUploadedFilesFlow(
          queueConsumer,
          new MessageProcessingFlow(parser, fileDetailsRetriever, scanningService, scanningResultHandler))

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("scanning handler is called only for successfully scanned messages")
      Mockito
        .verify(scanningResultHandler)
        .handleScanningResult(FileIsClean(S3ObjectLocation("bucket", "ID1")))
      Mockito
        .verify(scanningResultHandler)
        .handleScanningResult(FileIsInfected(S3ObjectLocation("bucket", "ID3"), "infection"))
      Mockito.verifyNoMoreInteractions(scanningResultHandler)

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage1)
      Mockito.verify(queueConsumer).confirm(validMessage3)

      And("invalid messages are not confirmed")
      Mockito.verifyNoMoreInteractions(queueConsumer)

    }
  }

}
