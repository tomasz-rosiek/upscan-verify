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

import javax.inject.Inject

import cats.FlatMap
import model.S3ObjectLocation
import cats._
import cats.implicits._
import scala.concurrent.ExecutionContext

class ScanningResultHandler[F[_]: FlatMap] @Inject()(fileManager: FileManager[F], virusNotifier: VirusNotifier[F])(
  implicit ec: ExecutionContext) {

  def handleScanningResult(result: ScanningResult): F[Unit] =
    result match {
      case FileIsClean(file)             => handleClean(file)
      case FileIsInfected(file, details) => handleInfected(file, details)
    }

  private def handleInfected(file: S3ObjectLocation, details: String) =
    for {
      _ <- virusNotifier.notifyFileInfected(file, details)
      _ <- fileManager.delete(file)
    } yield ()

  private def handleClean(file: S3ObjectLocation) =
    for {
      _ <- fileManager.copyToOutboundBucket(file)
      _ <- fileManager.delete(file)
    } yield ()
}
