package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.left
import arrow.core.right
import com.jakewharton.picnic.TextBorder
import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import software.amazon.awssdk.services.devicefarm.model.ExecutionResult
import software.amazon.awssdk.services.devicefarm.model.Job
import software.amazon.awssdk.services.devicefarm.model.Run

class WhenFetchingResultsTable : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectsHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val artifactsHandler = MockedDeviceFarmArtifactsHandler()

    val run = Run
        .builder()
        .arn("arn:test:run")
        .name("test run")
        .build()

    "It should return a string table with the results of the run per each device" {
        //region GIVEN
        val jobs = (1..10)
            .map { job ->
                Job
                    .builder()
                    .arn("arn:test:job:$job")
                    .name("test job $job")
                    .result(ExecutionResult.values().random())
                    .device {
                        it.name("Test device $job")
                            .arn("arn:test:device:$job")
                    }.build()
            }

        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { jobs.right() }
        )

        val expectedResultTable = table {
            cellStyle {
                border = true
            }
            header {
                row("Device", "Result")
            }
            jobs
                .forEach { job ->
                    row(job.device().name(), job.result().name)
                }
        }.renderText(border = TextBorder.ASCII)
        //endregion

        //region WHEN
        val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            deviceFarmTractorLogging = logger,
            deviceFarmProjectsHandler = deviceFarmProjectsHandler,
            deviceFarmDevicePoolsHandler = devicePoolsHandler,
            deviceFarmUploadArtifactsHandler = uploadArtifactsHandler,
            deviceFarmRunsHandler = runHandler,
            deviceFarmArtifactsHandler = artifactsHandler
        ).getDeviceResultsTable(run)
        //endregion

        //region THEN
        response shouldBe expectedResultTable
        //endregion
    }

    "It should return an empty string when there is an error fetching the associated jobs of the run"{
        //region GIVEN
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { DeviceFarmTractorGeneralError(RuntimeException("test error")).left() }
        )
        //endregion

        //region WHEN
        val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            deviceFarmTractorLogging = logger,
            deviceFarmProjectsHandler = deviceFarmProjectsHandler,
            deviceFarmDevicePoolsHandler = devicePoolsHandler,
            deviceFarmUploadArtifactsHandler = uploadArtifactsHandler,
            deviceFarmRunsHandler = runHandler,
            deviceFarmArtifactsHandler = artifactsHandler
        ).getDeviceResultsTable(run)
        //endregion

        //region THEN
        response.shouldBeEmpty()
        //endregion
    }
})
