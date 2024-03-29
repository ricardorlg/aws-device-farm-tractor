package io.github.ricardorlg.devicefarm.tractor.controller.services

import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmUploadArtifactsHandler
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*


class DefaultDeviceFarmUploadArtifactsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val httpClient: HttpHandler = { Response(status = Status.OK) }
    val projectArn = "arn:aws:device_farm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val artifactName = "testArtifact"
    val commonUploadType = UploadType.ANDROID_APP
    val uploadARN = "test_upload_arn"

    "When creating AWS device farm upload, tt should return the created AWS Upload as a right" {
        //GIVEN
        val expectedUpload = Upload
            .builder()
            .name(artifactName)
            .build()
        every { dfClient.createUpload(any<CreateUploadRequest>()) } returns CreateUploadResponse
            .builder()
            .upload(expectedUpload)
            .build()

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).createUpload(
            projectArn,
            commonUploadType,
            artifactName
        )

        //THEN
        response.shouldBeRight().shouldBe(expectedUpload)
        verify {
            dfClient.createUpload(
                CreateUploadRequest
                    .builder()
                    .projectArn(projectArn)
                    .name(artifactName)
                    .type(commonUploadType)
                    .contentType(AWS_UPLOAD_CONTENT_TYPE)
                    .build()
            )
        }
        confirmVerified(dfClient)
    }
    "When creating AWS device farm upload, any error should be returned as a left" {
        //GIVEN
        val expectedError = RuntimeException("Test error")
        every { dfClient.createUpload(any<CreateUploadRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).createUpload(
            projectArn,
            commonUploadType,
            artifactName
        )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorCreatingUpload>()
            it shouldHaveMessage ERROR_CREATING_AWS_UPLOAD.format(projectArn)
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.createUpload(
                CreateUploadRequest
                    .builder()
                    .projectArn(projectArn)
                    .name(artifactName)
                    .type(commonUploadType)
                    .contentType(AWS_UPLOAD_CONTENT_TYPE)
                    .build()
            )
        }
        confirmVerified(dfClient)
    }
    "When creating AWS device farm upload, project ARN is mandatory, a left should be returned if not provided" {
        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).createUpload(
            "",
            commonUploadType,
            artifactName
        )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_PROJECT_ARN
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
        verify {
            dfClient
                .createUpload(any<CreateUploadRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }
    "When creating AWS device farm upload, artifact name is mandatory, a left should be returned if not provided" {
        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).createUpload(
            projectArn,
            commonUploadType,
            ""
        )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_FILENAME
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
        verify {
            dfClient
                .createUpload(any<CreateUploadRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }
    "When uploading artifact to S3, it should return a right when upload result has OK status" {
        //GIVEN
        val artifact = tempfile()
        val awsUpload = Upload
            .builder()
            .url("test_url")
            .contentType(AWS_UPLOAD_CONTENT_TYPE)
            .build()
        //WHEN
        val response =
            DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).uploadArtifactToS3(artifact, awsUpload)

        //THEN
        response.shouldBeRight().shouldBe(Unit)
    }
    "When uploading artifact to S3, it should return a left when upload result has not OK status" {
        //GIVEN
        val artifact = tempfile()
        val awsUpload = Upload
            .builder()
            .url("test_url")
            .contentType(AWS_UPLOAD_CONTENT_TYPE)
            .build()
        val httpClientNonOkStatusResponse: HttpHandler = {
            Response(status = Status.FORBIDDEN)
        }
        //WHEN
        val response =
            DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClientNonOkStatusResponse).uploadArtifactToS3(
                artifact,
                awsUpload
            )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorUploadingArtifact>()
            it shouldHaveMessage ARTIFACT_UPLOAD_TO_S3_NOT_SUCCESSFULLY.format(
                artifact.nameWithoutExtension,
                Status.FORBIDDEN
            )
            it.cause.shouldBeInstanceOf<IllegalStateException>()
        }
    }
    "When uploading artifact to S3, any error should be returned as a left" {
        //GIVEN
        val artifact = tempfile()
        val awsUpload = Upload
            .builder()
            .url("test_url")
            .contentType(AWS_UPLOAD_CONTENT_TYPE)
            .build()
        val expectedError = RuntimeException("Test error")
        val errorHttpClient: HttpHandler = {
            throw expectedError
        }
        //WHEN
        val response =
            DefaultDeviceFarmUploadArtifactsHandler(dfClient, errorHttpClient).uploadArtifactToS3(
                artifact,
                awsUpload
            )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorUploadingArtifact>()
            it shouldHaveMessage ERROR_UPLOADING_ARTIFACT_TO_S3.format(
                artifact.nameWithoutExtension
            )
            it.cause shouldBe expectedError
        }
    }
    "When fetching an AWS device farm upload, it should return the upload as a right" {
        //GIVEN
        val expectedUpload = Upload
            .builder()
            .arn(uploadARN)
            .status(UploadStatus.SUCCEEDED)
            .build()

        every { dfClient.getUpload(any<GetUploadRequest>()) } returns GetUploadResponse
            .builder()
            .upload(expectedUpload)
            .build()

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).fetchUpload(uploadARN)

        //THEN
        response.shouldBeRight() shouldBe expectedUpload
        verify {
            dfClient.getUpload(
                GetUploadRequest
                    .builder()
                    .arn(uploadARN)
                    .build()
            )
        }
        confirmVerified(dfClient)
    }
    "When fetching an AWS device farm upload, it should return an error as a left if upload ARN is empty" {
        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).fetchUpload("")

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_UPLOAD_ARN
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
        verify {
            dfClient.getUpload(any<GetUploadRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }
    "When fetching an AWS device farm upload, any error should be returned as a left" {
        //GIVEN
        val expectedError = RuntimeException("Test error")
        every { dfClient.getUpload(any<GetUploadRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).fetchUpload(uploadARN)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorFetchingUpload>()
            it shouldHaveMessage ERROR_FETCHING_UPLOAD_FROM_AWS.format(uploadARN)
            it.cause shouldBe expectedError
        }

        verify {
            dfClient.getUpload(GetUploadRequest.builder().arn(uploadARN).build())
        }
        confirmVerified(dfClient)
    }
    "When deleting an Upload it should return a DeleteUploadResponse as a right"{
        //GIVEN
        val expectedResponse = DeleteUploadResponse
            .builder()
            .applyMutation {
                it.sdkHttpResponse(
                    SdkHttpResponse
                        .builder()
                        .statusCode(200)
                        .build()
                )
            }
            .build()

        every { dfClient.deleteUpload(any<DeleteUploadRequest>()) } returns expectedResponse

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).deleteUpload(uploadARN)

        //THEN
        response.shouldBeRight() shouldBe expectedResponse
        verify {
            dfClient.deleteUpload(DeleteUploadRequest.builder().arn(uploadARN).build())
        }
        confirmVerified(dfClient)
    }
    "When deleting an Upload any error should be returned as a left"{
        //GIVEN
        val expectedError = RuntimeException("test error")

        every { dfClient.deleteUpload(any<DeleteUploadRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).deleteUpload(uploadARN)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorDeletingUpload>()
            it shouldHaveMessage ERROR_DELETING_UPLOAD.format(uploadARN)
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.deleteUpload(DeleteUploadRequest.builder().arn(uploadARN).build())
        }
        confirmVerified(dfClient)
    }
    "When deleting an Upload if non-200 response is received then an error should be returned as a left"{
        //GIVEN
        val codeError = 400
        val expectedResponse = DeleteUploadResponse
            .builder()
            .applyMutation {
                it.sdkHttpResponse(
                    SdkHttpResponse
                        .builder()
                        .statusCode(codeError)
                        .build()
                )
            }
            .build()

        every { dfClient.deleteUpload(any<DeleteUploadRequest>()) } returns expectedResponse

        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).deleteUpload(uploadARN)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorDeletingUpload>()
            it shouldHaveMessage DELETE_UPLOAD_INVALID_RESPONSE_CODE.format(codeError)
        }
        verify {
            dfClient.deleteUpload(DeleteUploadRequest.builder().arn(uploadARN).build())
        }
        confirmVerified(dfClient)
    }
    "When deleting an Upload if no arn is provided a DeviceFarmTractorErrorIllegalArgumentException should be returned as a left"{
        //WHEN
        val response = DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).deleteUpload("  ")

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_UPLOAD_ARN
        }
        verify {
            dfClient.deleteUpload(any<DeleteUploadRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }

    afterTest {
        clearMocks(dfClient)
    }
})
