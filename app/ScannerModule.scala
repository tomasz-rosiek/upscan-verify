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

import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import connectors.aws.{S3EventParser, S3FileManager, S3FileNotificationDetailsRetriever, SqsQueueConsumer}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import services._

import scala.concurrent.Future

class ScannerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration].eagerly(),
      bind[FileNotificationDetailsRetriever[Future]].to[S3FileNotificationDetailsRetriever[Future]],
      bind[MessageParser[Future]].to[S3EventParser[Future]],
      bind[QueueConsumer[Future]].to[SqsQueueConsumer[Future]],
      bind[PollingJob].to[ScanUploadedFilesFlow],
      bind[ContinousPoller].toSelf.eagerly(),
      bind[ScanningService[Future]].to[MockScanningService[Future]],
      bind[FileManager[Future]].to[S3FileManager[Future]],
      bind[VirusNotifier[Future]].to[LoggingVirusNotifier[Future]]
    )
}
