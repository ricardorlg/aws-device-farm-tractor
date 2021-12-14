package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmIllegalArtifactExtension
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import io.github.ricardorlg.devicefarm.tractor.model.UploadFailureError
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import software.amazon.awssdk.services.devicefarm.model.DeviceFarmException
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadStatus
import software.amazon.awssdk.services.devicefarm.model.UploadType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WhenUploadingArtifactsToDeviceFarm : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val runScheduleHandler = MockedDeviceFarmRunsHandler()
    val artifactsHandler = MockedDeviceFarmArtifactsHandler()
    val projectArn = "arn:aws:device_farm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
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
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { Either.Right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response.shouldBeRight() shouldBe expectedUpload

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
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { Either.Right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        val expectedErrorMessage =
            "The artifact ${file.name} upload AWS status was ${expectedUpload.status()}, message = ${expectedUpload.message()}"
        response.shouldBeLeft() should {
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
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { Either.Right(expectedUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType,
            10.milliseconds,
            5
        )

        //THEN
        val expectedErrorMessage =
            "The artifact ${file.name} upload AWS status was ${expectedUpload.status()}, message = ${expectedUpload.message()}"
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<UploadFailureError>()
            it shouldHaveMessage expectedErrorMessage
        }
    }

    "It should return the last DeviceFarmTractorError returned when fetching upload is still failing after the maximum number of retries"{
        //GIVEN
        val artifactPath = tempfile("test_artifact", ".apk").absolutePath
        val initialUpload = Upload
            .builder()
            .status(UploadStatus.INITIALIZED)
            .arn(uploadARN)
            .name(artifactName)
            .build()

        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { Either.Left(expectedError) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType,
            2.milliseconds,
            5
        )

        //THEN
        response.shouldBeLeft() shouldBe expectedError
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
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ ->
                delay(50)
                Either.Left(expectedError)
            },
            fetchUploadImpl = { Either.Right(currentUpload) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType,
            10.milliseconds,
        )

        //THEN
        response.shouldBeLeft() shouldBe expectedError
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
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { responses.next() }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn = projectArn,
            artifactPath = artifactPath,
            uploadType = defaultUploadType,
            delaySpaceInterval = 5.milliseconds
        )

        //THEN
        response.shouldBeRight() shouldBe expectedUpload
    }

    "It should stop fetching upload status when a DeviceFarmException happens in a a retry"{
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

        val uploadFailure = DeviceFarmTractorGeneralError(DeviceFarmException.builder().message("test error").build())

        val responses = iterator {
            yield(initialUpload.right())
            yield(processingUpload.right())
            yield(uploadFailure.left())
            fail("DeviceFarmException just happens, it should stop fetching data")
        }

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.Right(initialUpload) },
            uploadArtifactImpl = { _, _ -> Either.Right(Unit) },
            fetchUploadImpl = { responses.next() }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn = projectArn,
            artifactPath = artifactPath,
            uploadType = defaultUploadType,
            delaySpaceInterval = 5.milliseconds
        )

        //THEN
        response.shouldBeLeft() shouldBe uploadFailure

    }

    "It should return a DeviceFarmTractorError when creating the upload fails"{
        //GIVEN
        val artifactPath = tempfile("test_artifact", ".apk").absolutePath
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))


        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            createUploadImpl = { _, _, _ -> Either.Left(expectedError) },
            uploadArtifactImpl = { _, _ -> fail("this should never been called") },
            fetchUploadImpl = { fail("this should never been called") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response.shouldBeLeft() shouldBe expectedError
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
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response.shouldBeLeft() should {
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
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).uploadArtifactToDeviceFarm(
            projectArn,
            artifactPath,
            defaultUploadType
        )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorGeneralError>()
        }
    }
})