package com.ricardorlg.devicefarm.tractor.runner

import arrow.core.Either
import arrow.core.computations.either
import arrow.fx.coroutines.parSequence
import arrow.fx.coroutines.parTupledN
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorController
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Paths
import java.time.LocalDateTime

class DeviceFarmTractorRunner(
    private val controller: IDeviceFarmTractorController,
    private val logger : IDeviceFarmTractorLogging
){

    suspend fun runTests(
        projectName: String,
        devicePoolName: String,
        appPath: String,
        appUploadType: UploadType,
        testProjectPath: String,
        testSpecPath: String,
        captureVideo: Boolean = true,
        runName: String = "",
        testReportsBaseDirectory: String = "",
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
                .testSpecArn(testSpecUpload.arn())
                .type(TestType.APPIUM_NODE)
                .build()

            val run = !controller.scheduleRunAndWait(
                appArn = appUpload.arn(),
                runConfiguration = runConfiguration,
                devicePoolArn = devicePool.arn(),
                executionConfiguration = executionConfiguration,
                runName = runName.ifBlank { generateRunName() },
                projectArn = project.arn(),
                testConfiguration = testConfiguration
            )
            val extraSteps = mutableListOf<suspend ()->Unit>()
            if (downloadReports && testReportsBaseDirectory.isNotBlank())
                extraSteps.add { controller.downloadAllTestReportsOfTestRun(run, Paths.get(testReportsBaseDirectory)) }
            if (cleanStateAfterRun)
                extraSteps.add {  controller.deleteUploads(appUpload, testUpload, testSpecUpload)}
            extraSteps.parSequence()
            run
        }
        return when (result) {
            is Either.Left -> {
                logger.logError(result.a.cause, result.a.message)
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