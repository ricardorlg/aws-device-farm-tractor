package com.ricardorlg.devicefarm.tractor

import arrow.core.Either
import arrow.core.computations.either
import arrow.fx.coroutines.parTupledN
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorController
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path
import java.time.LocalDateTime

class DeviceFarmTractorRunner(
    private val controller: IDeviceFarmTractorController,
    deviceFarmTractorLogger: IDeviceFarmTractorLogging
) : IDeviceFarmTractorLogging by deviceFarmTractorLogger {

    suspend fun runTests(
        projectName: String,
        devicePoolName: String,
        appPath: String,
        appUploadType: UploadType,
        testProjectPath: String,
        testSpecPath: String,
        captureVideo: Boolean = true,
        runName: String,
        downloadReports: Boolean = true,
        cleanStateAfterRun: Boolean = true
    ): Run {
        val result = either<DeviceFarmTractorError, Run> {
            val project = !controller.findOrCreateProject(projectName)
            val devicePool = !controller.findOrUseDefaultDevicePool(project.arn(), devicePoolName)
            val (appUpload, testUpload, testSpecUpload) = parTupledN(
                fa = {
                    !controller.uploadArtifactToDeviceFarm(
                        project.arn(),
                        appPath,
                        appUploadType
                    )
                },
                fb = {
                    !controller.uploadArtifactToDeviceFarm(
                        project.arn(),
                        testProjectPath,
                        UploadType.APPIUM_NODE_TEST_PACKAGE
                    )
                },
                fc = {
                    !controller.uploadArtifactToDeviceFarm(
                        project.arn(),
                        testSpecPath,
                        UploadType.APPIUM_NODE_TEST_SPEC
                    )
                }
            )
            val runConfiguration = ScheduleRunConfiguration
                .builder()
                .build()
            val executionConfiguration = ExecutionConfiguration
                .builder()
                .videoCapture(captureVideo)
                .build()
            val testConfiguration = ScheduleRunTest
                .builder()
                .testPackageArn(testUpload.arn())
                .testSpecArn(testUpload.arn())
                .type(TestType.APPIUM_NODE)
                .build()

            val run = !controller.scheduleRunAndWait(
                appUpload.arn(),
                runConfiguration,
                devicePool.arn(),
                executionConfiguration,
                runName.ifBlank { generateRunName() },
                project.arn(),
                testConfiguration
            )
            if (downloadReports)
                controller.downloadAllTestReportsOfTestRun(run, Path.of(""))
            if (cleanStateAfterRun)
                controller.deleteUploads(appUpload, testUpload, testSpecUpload)
            run
        }
        return when (result) {
            is Either.Left -> {
                logError(result.a.cause, result.a.message)
                throw result.a
            }
            is Either.Right -> result.b
        }
    }

    private fun generateRunName(): String {
        val date = LocalDateTime.now()
        return "Test_Run_${date.year}_${date.monthValue}_${date.dayOfMonth}_${date.hour}_${date.minute}"
    }

}