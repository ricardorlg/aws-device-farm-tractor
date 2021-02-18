package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DELETE_UPLOAD_MESSAGE
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.captureStandardOut
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.fail
import software.amazon.awssdk.services.devicefarm.model.DeleteUploadResponse
import software.amazon.awssdk.services.devicefarm.model.Upload

class WhenDeletingUploadsFromDeviceFarm : StringSpec({

    val deviceFarmProjectsHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val runScheduleHandler = MockedDeviceFarmRunsHandler()
    val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler()

    "It should delete all the provided uploads"{
        //GIVEN
        val uploads = (1..10)
            .map {
                Upload
                    .builder()
                    .name("upload_test_$it")
                    .arn("arn:test:upload:$it")
                    .build()
            }
        val expectedOutputs = uploads
            .map {
                DELETE_UPLOAD_MESSAGE.format(it.name(), it.arn())
            }

        val mockedDeleteResponse = DeleteUploadResponse.builder().build()

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            deleteUploadImpl = { Either.right(mockedDeleteResponse) }
        )

        //WHEN
        val lastOutputs = captureStandardOut {
            io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runScheduleHandler,
                downloadArtifactsHandler
            ).deleteUploads(*uploads.toTypedArray())
        }.lines()
            .filter(String::isNotBlank)
            .map(String::trim)

        //THEN
        lastOutputs shouldContainExactlyInAnyOrder expectedOutputs
    }

    "It should delete uploads in parallel and keep deleting even if some operation fails"{
        //GIVEN
        val uploads = (1..10)
            .map {
                Upload
                    .builder()
                    .name("upload_test_$it")
                    .arn("arn:test:upload:$it")
                    .build()
            }
        val uploadToFail = uploads.random()
        val error = DeviceFarmTractorGeneralError(RuntimeException("test error"))
        val expectedOutputs = uploads
            .filter { it != uploadToFail }
            .map {
                DELETE_UPLOAD_MESSAGE.format(it.name(), it.arn())
            }

        val mockedDeleteResponse = DeleteUploadResponse.builder().build()

        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            deleteUploadImpl = { uploadArn ->
                synchronized(this) {
                    Either.conditionally(uploadArn != uploadToFail.arn(),
                        ifTrue = { mockedDeleteResponse },
                        ifFalse = { error }
                    )
                }
            }
        )

        //WHEN
        val lastOutputs = captureStandardOut {
            io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runScheduleHandler,
                downloadArtifactsHandler
            ).deleteUploads(*uploads.toTypedArray())
        }.lines()
            .filter(String::isNotBlank)
            .map(String::trim)

        //THEN
        lastOutputs shouldContainExactlyInAnyOrder expectedOutputs
    }

    "It should never try to delete when no uploads are provided"{
        //GIVEN
        val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler(
            deleteUploadImpl = { fail("this should never been called") }
        )

        //WHEN
        val lastOutputs = captureStandardOut {
            io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runScheduleHandler,
                downloadArtifactsHandler
            ).deleteUploads()
        }.lines()
            .filter(String::isNotBlank)
            .map(String::trim)

        //THEN
        lastOutputs.shouldBeEmpty()
    }
})