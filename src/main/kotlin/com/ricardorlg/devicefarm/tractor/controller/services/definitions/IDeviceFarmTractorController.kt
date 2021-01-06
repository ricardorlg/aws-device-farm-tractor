package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import arrow.fx.coroutines.Duration
import arrow.fx.coroutines.seconds
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path

interface IDeviceFarmTractorController {
    suspend fun findOrCreateProject(projectName: String): Either<DeviceFarmTractorError, Project>

    suspend fun findOrUseDefaultDevicePool(
        projectArn: String,
        devicePoolName: String = ""
    ): Either<DeviceFarmTractorError, DevicePool>

    suspend fun uploadArtifactToDeviceFarm(
        projectArn: String,
        artifactPath: String,
        uploadType: UploadType,
        delaySpaceInterval: Duration = 2.seconds,
        maximumNumberOfRetries: Int = 3600
    ): Either<DeviceFarmTractorError, Upload>

    suspend fun scheduleRunAndWait(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest,
        delaySpaceInterval: Duration = 10.seconds
    ): Either<DeviceFarmTractorError, Run>

    suspend fun downloadCustomerArtifacts(
        job: Job,
        path: Path
    ): Either<DeviceFarmTractorError, Unit>
}