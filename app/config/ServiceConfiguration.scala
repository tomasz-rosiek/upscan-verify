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

package config

import javax.inject.Inject
import play.api.Configuration
import scala.concurrent.duration._

trait ServiceConfiguration {
  def quarantineBucket: String

  def retryInterval: FiniteDuration

  def inboundQueueUrl: String

  def accessKeyId: String

  def secretAccessKey: String

  def sessionToken: Option[String]

  def outboundBucket: String

  def awsRegion: String

  def useContainerCredentials: Boolean

}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def inboundQueueUrl: String =
    getRequired(configuration.getString(_), "aws.sqs.queue.inbound")

  override def awsRegion = getRequired(configuration.getString(_), "aws.s3.region")

  override def useContainerCredentials = configuration.getBoolean("aws.useContainerCredentials").getOrElse(true)

  override def accessKeyId = getRequired(configuration.getString(_), "aws.accessKeyId")

  override def secretAccessKey = getRequired(configuration.getString(_), "aws.secretAccessKey")

  override def sessionToken = configuration.getString("aws.sessionToken")

  override def retryInterval = getRequired(configuration.getMilliseconds, "aws.sqs.retry.interval").milliseconds

  def getRequired[T](function: String => Option[T], key: String) =
    function(key).getOrElse(throw new IllegalStateException(s"Configuration key not found: $key"))

  override def outboundBucket = getRequired(configuration.getString(_), "aws.s3.bucket.outbound")

  override def quarantineBucket: String = getRequired(configuration.getString(_), "aws.s3.bucket.quarantine")
}
