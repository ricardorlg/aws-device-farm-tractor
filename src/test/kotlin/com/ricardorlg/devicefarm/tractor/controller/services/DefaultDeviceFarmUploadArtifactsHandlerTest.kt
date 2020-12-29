package com.ricardorlg.devicefarm.tractor.controller.services

import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmUploadArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*


class DefaultDeviceFarmUploadArtifactsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val httpClient: HttpHandler = { Response(status = Status.OK) }
    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
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
        response shouldBeRight expectedUpload
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmCreateUploadError>()
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArgumentError>()
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArgumentError>()
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
            .url("testurl")
            .contentType(AWS_UPLOAD_CONTENT_TYPE)
            .build()
        //WHEN
        val response =
            DefaultDeviceFarmUploadArtifactsHandler(dfClient, httpClient).uploadArtifactToS3(artifact, awsUpload)

        //THEN
        response shouldBeRight Unit
    }
    "When uploading artifact to S3, it should return a left when upload result has not OK status" {
        //GIVEN
        val artifact = tempfile()
        val awsUpload = Upload
            .builder()
            .url("testurl")
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmUploadFailedError>()
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
            .url("testurl")
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmUploadArtifactError>()
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
        response shouldBeRight expectedUpload
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArgumentError>()
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
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmGetUploadError>()
            it shouldHaveMessage ERROR_FETCHING_UPLOAD_FROM_AWS.format(uploadARN)
            it.cause shouldBe expectedError
        }

        verify {
            dfClient.getUpload(GetUploadRequest.builder().arn(uploadARN).build())
        }
        confirmVerified(dfClient)
    }

    afterTest {
        clearMocks(dfClient)
    }
})
