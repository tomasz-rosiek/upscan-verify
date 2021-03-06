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

import java.io.ByteArrayInputStream
import java.util

import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{CopyObjectResult, ObjectMetadata, S3Object}
import config.ServiceConfiguration
import model.S3ObjectLocation
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{doThrow, verify, verifyNoMoreInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertions, GivenWhenThen, Matchers}
import sun.security.util.PropertyExpander.ExpandException
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class S3FileManagerSpec extends UnitSpec with Matchers with Assertions with GivenWhenThen with MockitoSugar {
  private val configuration = mock[ServiceConfiguration]
  when(configuration.outboundBucket).thenReturn("outboundBucket")
  when(configuration.quarantineBucket).thenReturn("quarantineBucket")

  "S3FileManager" should {
    "allow to copy file from inbound bucket to outbound bucket" in {

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.copyObject(any(), any(), any(), any())).thenReturn(new CopyObjectResult())
      val fileManager = new S3FileManager(s3client, configuration)

      When("copying the file is requested")
      Await.result(fileManager.copyToOutboundBucket(S3ObjectLocation("inboundBucket", "file")), 2.seconds)

      Then("the S3 copy method of AWS client should be called")
      verify(s3client).copyObject("inboundBucket", "file", "outboundBucket", "file")
      verifyNoMoreInteractions(s3client)

    }

    "return error if copying the file failed" in {

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.copyObject(any(), any(), any(), any())).thenThrow(new RuntimeException("exception"))
      val fileManager = new S3FileManager(s3client, configuration)

      When("copying the file is requested")
      val result = Await.ready(fileManager.copyToOutboundBucket(S3ObjectLocation("inboundBucket", "file")), 2.seconds)

      Then("error is returned")

      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[RuntimeException]
      }
    }

    "allow to delete file" in {

      val s3client: AmazonS3 = mock[AmazonS3]
      val fileManager        = new S3FileManager(s3client, configuration)

      When("deleting the file is requested")
      Await.result(fileManager.delete(S3ObjectLocation("inboundBucket", "file")), 2.seconds)

      Then("the S3 copy method of AWS client should be called")
      verify(s3client).deleteObject("inboundBucket", "file")
      verifyNoMoreInteractions(s3client)

    }

    "return error if deleting the file failed" in {

      val s3client: AmazonS3 = mock[AmazonS3]
      Given("deleting file would fail")
      doThrow(new RuntimeException("exception")).when(s3client).deleteObject(any(), any())
      val fileManager = new S3FileManager(s3client, configuration)

      When("deleting the file is requested")
      val result = Await.ready(fileManager.delete(S3ObjectLocation("inboundBucket", "file")), 2.seconds)

      Then("error is returned")
      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[RuntimeException]
      }
    }

    "return bytes of a successfully retrieved file" in {
      val fileLocation           = S3ObjectLocation("inboundBucket", "file")
      val byteArray: Array[Byte] = "Hello World".getBytes

      val s3Object = new S3Object()
      s3Object.setObjectContent(new ByteArrayInputStream(byteArray))

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.getObject(fileLocation.bucket, fileLocation.objectKey)).thenReturn(s3Object)

      Given("a valid file location")
      val fileManager = new S3FileManager(s3client, configuration)

      When("the bytes are requested")
      val result = Await.result(fileManager.getBytes(fileLocation), 2.seconds)

      Then("expected byte array is returned")
      result shouldBe byteArray
    }

    "return error if file retrieval fails" in {
      val fileLocation = S3ObjectLocation("inboundBucket", "file")

      val s3client: AmazonS3 = mock[AmazonS3]
      Mockito
        .doThrow(new RuntimeException("exception"))
        .when(s3client)
        .getObject(fileLocation.bucket, fileLocation.objectKey)

      Given("a call to the S3 client errors")
      val fileManager = new S3FileManager(s3client, configuration)

      When("the bytes are requested")
      val result = Await.ready(fileManager.getBytes(fileLocation), 2.seconds)

      Then("error is returned")
      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[RuntimeException]
      }
    }

    "return successful if copy of file metadata and content to quarantine bucket succeeds" in {
      Given("a valid file location and details of an error")
      val fileLocation = S3ObjectLocation("inboundBucket", "file")

      val userMetadata = new util.HashMap[String, String]()
      userMetadata.put("callbackUrl", "http://some.callback.url")
      val fileMetadata = new ObjectMetadata()
      fileMetadata.setUserMetadata(userMetadata)

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.getObjectMetadata(any(), any())).thenReturn(fileMetadata)

      val fileManager = new S3FileManager(s3client, configuration)

      When("a call to copy to quarantine is made")
      val result = Await.result(fileManager.writeToQuarantineBucket(fileLocation, "This is a dirty file"), 2.seconds)

      Then("the original object metadata should be retrieved")
      verify(s3client).getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)

      And("a new S3 object with details set as contents and object metadata set should be created")
      verify(s3client).putObject(any(), any(), any(), any())

      And("the service should return success")
      result shouldBe ()
    }

    "return failure if retrieval of file metadata fail for copying to quarantine bucket" in {
      Given("a file location which errors retrieving metadata")
      val fileLocation = S3ObjectLocation("inboundBucket", "file")

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.getObjectMetadata(any(), any())).thenThrow(new SdkClientException("This is a metadata exception"))

      val fileManager = new S3FileManager(s3client, configuration)

      When("a call to copy to quarantine is made")
      val result = Await.ready(fileManager.writeToQuarantineBucket(fileLocation, "This is a dirty file"), 2.seconds)

      Then("the original object metadata should be requested from S3")
      verify(s3client).getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)

      Then("error is returned")
      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[SdkClientException]
        error.getMessage shouldBe "This is a metadata exception"
      }
    }

    "return failure if put to quarantine bucket fails" in {
      Given("a valid file location and details of an error")
      val fileLocation = S3ObjectLocation("inboundBucket", "file")

      val userMetadata = new util.HashMap[String, String]()
      userMetadata.put("callbackUrl", "http://some.callback.url")
      val fileMetadata = new ObjectMetadata()
      fileMetadata.setUserMetadata(userMetadata)

      val s3client: AmazonS3 = mock[AmazonS3]
      when(s3client.getObjectMetadata(any(), any())).thenReturn(fileMetadata)
      when(s3client.putObject(any(), any(), any(), any())).thenThrow(new SdkClientException("This is a put exception"))

      val fileManager = new S3FileManager(s3client, configuration)

      When("a call to copy to quarantine is made")
      val result = Await.ready(fileManager.writeToQuarantineBucket(fileLocation, "This is a dirty file"), 2.seconds)

      Then("the original object metadata should be retrieved")
      verify(s3client).getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)

      And("a new S3 object with details set as contents and object metadata set should be created")
      verify(s3client).putObject(any(), any(), any(), any())

      And("error is returned")
      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[SdkClientException]
        error.getMessage shouldBe "This is a put exception"
      }
    }
  }
}
