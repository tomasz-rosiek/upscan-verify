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

import model.UploadedFile

import scala.concurrent.Future

sealed trait ScanningResult
case object FileIsClean extends ScanningResult
case class FileIsInfected(details: String) extends ScanningResult

trait ScanningService {
  def scan(notification: UploadedFile): Future[ScanningResult]
}

class MockScanningService extends ScanningService {
  override def scan(notification: UploadedFile) = Future.successful(FileIsClean)
}
