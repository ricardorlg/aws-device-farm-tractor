package com.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import arrow.core.right
import arrow.fx.coroutines.Duration
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorController
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path

class MockedDeviceFarmController(
    private val findOrCreateProjectImpl: (String) -> Either<DeviceFarmTractorError, Project> = { fail("Not implemented") },
    private val findOrUseDefaultDevicePoolImpl: (String, String) -> Either<DeviceFarmTractorError, DevicePool> = { _, _ ->
        fail(
            "Not implemented"
        )
    },
    private val uploadArtifactToDeviceFarmImpl: suspend (String, String, UploadType) -> Either<DeviceFarmTractorError, Upload> = { _, _, _ ->
        fail(
            "Not implemented"
        )
    },
    private val deleteUploadsImpl: (List<Upload>) -> Unit = { _ -> fail("Not implemented") },
    private val scheduleRunAndWaitImpl: (String, ScheduleRunConfiguration, String, ExecutionConfiguration, String, String, ScheduleRunTest) -> Either<DeviceFarmTractorError, Run> = { _, _, _, _, _, _, _ ->
        fail(
            "Not implemented"
        )
    },
    private val downloadAllTestReportsOfTestRunImpl: (Run, Path) -> Unit = { _, _ -> fail("Not implemented") }
) : IDeviceFarmTractorController {
    override suspend fun findOrCreateProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return findOrCreateProjectImpl(projectName)
    }

    override suspend fun findOrUseDefaultDevicePool(
        projectArn: String,
        devicePoolName: String
    ): Either<DeviceFarmTractorError, DevicePool> {
        return findOrUseDefaultDevicePoolImpl(projectArn, devicePoolName)
    }

    override suspend fun uploadArtifactToDeviceFarm(
        projectArn: String,
        artifactPath: String,
        uploadType: UploadType,
        delaySpaceInterval: Duration,
        maximumNumberOfRetries: Int
    ): Either<DeviceFarmTractorError, Upload> {
        return uploadArtifactToDeviceFarmImpl(projectArn, artifactPath, uploadType)
    }

    override suspend fun deleteUploads(vararg uploads: Upload) {
        return deleteUploadsImpl(uploads.toList())
    }

    override suspend fun scheduleRunAndWait(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest,
        delaySpaceInterval: Duration
    ): Either<DeviceFarmTractorError, Run> {
        return scheduleRunAndWaitImpl(
            appArn,
            runConfiguration,
            devicePoolArn,
            executionConfiguration,
            runName,
            projectArn,
            testConfiguration
        )
    }

    override suspend fun downloadAllEvidencesOfTestRun(
        run: Run,
        destinyDirectory: Path,
        delayForDownload: kotlin.time.Duration
    ) {
        downloadAllTestReportsOfTestRunImpl(run, destinyDirectory)
    }

    override suspend fun downloadAWSDeviceFarmArtifacts(
        artifacts: List<Artifact>,
        deviceName: String,
        path: Path,
        artifactType: ArtifactType
    ): Either<DeviceFarmTractorError, Unit> {
        return Unit.right()
    }

    override suspend fun getDeviceResultsTable(run: Run): String {
        TODO("Not yet implemented")
    }

}