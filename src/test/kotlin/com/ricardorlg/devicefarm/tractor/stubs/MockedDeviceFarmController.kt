package com.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import arrow.fx.coroutines.Duration
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorController
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path

class MockedDeviceFarmController(
    private val findOrCreateProjectImpl: () -> Either<DeviceFarmTractorError, Project> = { fail("Not implemented") }
) : IDeviceFarmTractorController {
    override suspend fun findOrCreateProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        TODO("Not yet implemented")
    }

    override suspend fun findOrUseDefaultDevicePool(
        projectArn: String,
        devicePoolName: String
    ): Either<DeviceFarmTractorError, DevicePool> {
        TODO("Not yet implemented")
    }

    override suspend fun uploadArtifactToDeviceFarm(
        projectArn: String,
        artifactPath: String,
        uploadType: UploadType,
        delaySpaceInterval: Duration,
        maximumNumberOfRetries: Int
    ): Either<DeviceFarmTractorError, Upload> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteUploads(vararg uploads: Upload) {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override suspend fun downloadAllTestReportsOfTestRun(run: Run, destinyDirectory: Path) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadCustomerArtifacts(job: Job, path: Path): Either<DeviceFarmTractorError, Unit> {
        TODO("Not yet implemented")
    }

}