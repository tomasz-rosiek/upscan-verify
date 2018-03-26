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

import cats.implicits._
import cats.FlatMap
import model.Message

class MessageProcessingFlow[F[_]: FlatMap](
  parser: MessageParser[F],
  fileDetailsRetriever: FileNotificationDetailsRetriever[F],
  scanningService: ScanningService[F],
  scanningResultHandler: ScanningResultHandler[F]
) {
  def flow(message: Message): F[Unit] =
    for {
      parsedMessage  <- parser.parse(message)
      uploadedFile   <- fileDetailsRetriever.retrieveUploadedFileDetails(parsedMessage.location)
      scanningResult <- scanningService.scan(uploadedFile)
      _              <- scanningResultHandler.handleScanningResult(scanningResult)
    } yield ()
}
