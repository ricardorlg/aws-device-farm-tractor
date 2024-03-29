package io.github.ricardorlg.devicefarm.tractor.runner

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.none
import arrow.core.some
import arrow.fx.coroutines.parZip
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorController
import io.github.ricardorlg.devicefarm.tractor.model.APP_PERFORMANCE_MONITORING_PARAMETER_KEY
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.github.ricardorlg.devicefarm.tractor.model.TestExecutionType
import io.github.ricardorlg.devicefarm.tractor.utils.HelperMethods.uploadType
import kotlinx.coroutines.Dispatchers
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Paths
import java.time.LocalDateTime

class DeviceFarmTractorRunner(
    private val controller: IDeviceFarmTractorController,
) {

    suspend fun runTests(
        projectName: String,
        devicePoolName: String,
        appPath: String,
        testExecutionType: TestExecutionType,
        testProjectPath: String,
        testSpecPath: String,
        captureVideo: Boolean = true,
        runName: String = "",
        testReportsBaseDirectory: String = "",
        downloadReports: Boolean = true,
        cleanStateAfterRun: Boolean = true,
        meteredTests: Boolean = true,
        disablePerformanceMonitoring: Boolean = false,
    ): Run {
        val result = either<DeviceFarmTractorError, Run> {
            val appUploadType = appPath.uploadType().bind()
            val project = controller.findOrCreateProject(projectName).bind()
            val devicePool = controller.findOrUseDefaultDevicePool(project.arn(), devicePoolName).bind()
            val runConfiguration = ScheduleRunConfiguration
                .builder()
                .billingMethod(BillingMethod.METERED.takeIf { meteredTests } ?: BillingMethod.UNMETERED)
                .build()
            val executionConfiguration = ExecutionConfiguration
                .builder()
                .videoCapture(captureVideo)
                .build()

            val (appUpload, testUpload, testSpecUpload) = parZip(
                Dispatchers.IO,
                fa = {
                    if (testExecutionType == TestExecutionType.MOBILE_NATIVE) {
                        controller.uploadArtifactToDeviceFarm(
                            project.arn(),
                            appPath,
                            appUploadType
                        ).bind()
                            .some()
                    } else none()
                },
                fb = {
                    controller.uploadArtifactToDeviceFarm(
                        project.arn(),
                        testProjectPath,
                        UploadType.APPIUM_NODE_TEST_PACKAGE
                    ).bind()
                },
                fc = {
                    controller.uploadArtifactToDeviceFarm(
                        project.arn(),
                        testSpecPath,
                        UploadType.APPIUM_NODE_TEST_SPEC
                    ).bind()
                }
            ) { a, b, c ->
                Triple(a, b, c)
            }

            val testConfiguration = ScheduleRunTest
                .builder()
                .testPackageArn(testUpload.arn())
                .testSpecArn(testSpecUpload.arn())
                .apply {
                    if (testExecutionType == TestExecutionType.MOBILE_WEB)
                        type(TestType.APPIUM_WEB_NODE)
                    if (testExecutionType == TestExecutionType.MOBILE_NATIVE)
                        type(TestType.APPIUM_NODE)
                    if (disablePerformanceMonitoring) {
                        parameters(mutableMapOf(APP_PERFORMANCE_MONITORING_PARAMETER_KEY to "false"))
                    }
                }
                .build()

            val run = controller.scheduleRunAndWait(
                appArn = appUpload.map { it.arn() }.orNull().orEmpty(),
                runConfiguration = runConfiguration,
                devicePoolArn = devicePool.arn(),
                executionConfiguration = executionConfiguration,
                runName = runName.ifBlank { generateRunName() },
                projectArn = project.arn(),
                testConfiguration = testConfiguration
            ).bind()

            if (downloadReports && testReportsBaseDirectory.isNotBlank()) {
                controller.downloadAllEvidencesOfTestRun(run, Paths.get(testReportsBaseDirectory))
            }
            if (cleanStateAfterRun)
                appUpload.fold(
                    ifEmpty = { controller.deleteUploads(testUpload, testSpecUpload) },
                    ifSome = { controller.deleteUploads(it, testUpload, testSpecUpload) }
                )
            run
        }
        return when (result) {
            is Either.Left -> {
                throw result.value
            }
            is Either.Right -> result.value
        }
    }

    @Suppress("unused")
    suspend fun getDeviceResultsTable(run: Run): String {
        return controller.getDeviceResultsTable(run)
    }

    private fun generateRunName(): String {
        val date = LocalDateTime.now()
        return "Test_Run_${date.year}_${date.monthValue}_${date.dayOfMonth}_${date.hour}_${date.minute}"
    }

}