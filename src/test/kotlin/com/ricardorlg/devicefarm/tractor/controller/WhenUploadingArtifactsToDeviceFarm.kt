package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.milliseconds
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmIllegalArtifactExtension
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import com.ricardorlg.devicefarm.tractor.model.UploadFailureError
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmLogging
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmUploadArtifactsHandler
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadStatus
import software.amazon.awssdk.services.devicefarm.model.UploadType

class WhenUploadingArtifactsToDeviceFarm : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val artifactName = "testArtifact"
    val defaultUploadType = UploadType.ANDROID_APP
    val uploadARN = "test_upload_arn"

    "It should return the upload, if its ready to be used"{
        //GIVEN
        val artifactPath = tempfile("test_artifact", ".apk").absolutePath
        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val expectedUpload = Upload
            .builder()
            .status(UploadStatus.SUCCEEDED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.right(Unit) },
            fetchUploadImpl = { Either.right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response shouldBeRight expectedUpload

    }

    "It should return an UploadFailureError if the upload status is not succeeded"{
        //GIVEN
        val file = tempfile("test_artifact", ".apk")
        val artifactPath = file.absolutePath
        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val expectedUpload = Upload
            .builder()
            .status(UploadStatus.FAILED)
            .message("failed status")
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.right(Unit) },
            fetchUploadImpl = { Either.right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        val expectedErrorMessage =
            "The artifact ${file.nameWithoutExtension} AWS status was ${expectedUpload.status()} message = ${expectedUpload.message()}"
        response shouldBeLeft {
            it.shouldBeInstanceOf<UploadFailureError>()
            it shouldHaveMessage expectedErrorMessage
        }
    }

    "It should return an UploadFailureError, if the upload status is not succeeded before the maximum number of retries"{
        //GIVEN
        val file = tempfile("test_artifact", ".apk")
        val artifactPath = file.absolutePath
        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val expectedUpload = Upload
            .builder()
            .status(UploadStatus.PROCESSING)
            .message("upload is being processed")
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.right(Unit) },
            fetchUploadImpl = { Either.right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType,
            10.milliseconds,
            5
        )

        //THEN
        val expectedErrorMessage =
            "The artifact ${file.nameWithoutExtension} AWS status was ${expectedUpload.status()} message = ${expectedUpload.message()}"
        response shouldBeLeft {
            it.shouldBeInstanceOf<UploadFailureError>()
            it shouldHaveMessage expectedErrorMessage
        }
    }

    "It should return a DeviceFarmTractorError when uploading the artifact to s3 fails, no matters the upload status"{
        //GIVEN
        val file = tempfile("test_artifact", ".apk")
        val artifactPath = file.absolutePath
        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val currentUpload = Upload
            .builder()
            .status(UploadStatus.PROCESSING)
            .message("upload is being processed")
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.right(initialUpload) },
            uploadArtifactImpl = { _, _ ->
                delay(50)
                Either.left(expectedError)
            },
            fetchUploadImpl = { Either.right(currentUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType,
            10.milliseconds,
        )

        //THEN
        response shouldBeLeft expectedError
    }

    "It should keep fetching upload status even if an error happens in a a retry"{
        //GIVEN
        val artifactPath = tempfile("test_artifact", ".apk").absolutePath

        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val processingUpload = Upload
            .builder()
            .status(UploadStatus.PROCESSING)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val uploadFailure = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val expectedUpload = Upload
            .builder()
            .status(UploadStatus.SUCCEEDED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val responses = iterator {
            yield(initialUpload.right())
            yield(processingUpload.right())
            yield(uploadFailure.left())
            yield(expectedUpload.right())
        }

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.right(Unit) },
            fetchUploadImpl = { responses.next() }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn = projectArn,
            artifactPath = artifactPath,
            uploadType = defaultUploadType,
            delaySpaceInterval = 5.milliseconds
        )

        //THEN
        response shouldBeRight expectedUpload

    }

    "It should return a DeviceFarmTractorError when creating the upload fails"{
        //GIVEN
        val artifactPath = tempfile("test_artifact", ".apk").absolutePath
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))


        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.left(expectedError) },
            uploadArtifactImpl = { _, _ -> fail("this should never been called") },
            fetchUploadImpl = { fail("this should never been called") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response shouldBeLeft expectedError
    }

    "It should return a DeviceFarmIllegalArtifactExtension when the upload file doesn't satisfy the upload type"{
        //GIVEN
        val file = tempfile("test_artifact", ".ipa")
        val artifactPath = file.absolutePath
        val expectedErrorMessage =
            "the file ${file.nameWithoutExtension} is not a valid Android app. It should has .apk extension"

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> fail("this should never been called") },
            uploadArtifactImpl = { _, _ -> fail("this should never been called") },
            fetchUploadImpl = { fail("this should never been called") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension>()
            it shouldHaveMessage expectedErrorMessage
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }

    }

    "It should return a DeviceFarmTractorError when the artifact to upload doesn't exists"{
        //GIVEN
        val artifactPath = "path/to/nonexisting/file"

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> fail("this should never been called") },
            uploadArtifactImpl = { _, _ -> fail("this should never been called") },
            fetchUploadImpl = { fail("this should never been called") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorGeneralError>()
        }
    }
})