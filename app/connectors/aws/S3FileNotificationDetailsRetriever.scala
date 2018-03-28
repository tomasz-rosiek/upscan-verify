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

import cats.{Id, Monad}
import com.amazonaws.services.s3.AmazonS3
import config.ServiceConfiguration
import model.{S3ObjectLocation, UploadedFile}
import services.FileNotificationDetailsRetriever

class S3FileNotificationDetailsRetriever[F[_]: Monad] @Inject()(s3Client: AmazonS3, config: ServiceConfiguration)
    extends FileNotificationDetailsRetriever[F] {

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): F[UploadedFile] =
    implicitly[Monad[F]].pure(UploadedFile(objectLocation))

}