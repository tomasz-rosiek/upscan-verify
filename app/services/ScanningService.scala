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

import cats.Monad
import model.{S3ObjectLocation, UploadedFile}

sealed trait ScanningResult {
  def location: S3ObjectLocation
}
case class FileIsClean(location: S3ObjectLocation) extends ScanningResult
case class FileIsInfected(location: S3ObjectLocation, details: String) extends ScanningResult

trait ScanningService[F[_]] {
  def scan(notification: UploadedFile): F[ScanningResult]
}

class MockScanningService[F[_]: Monad] extends ScanningService[F] {
  override def scan(notification: UploadedFile): F[ScanningResult] =
    implicitly[Monad[F]].pure(FileIsClean(notification.location))
}
