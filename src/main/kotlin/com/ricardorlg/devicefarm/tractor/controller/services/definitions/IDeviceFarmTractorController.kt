package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import arrow.fx.coroutines.Duration as ArrowDuration
import arrow.fx.coroutines.minutes
import arrow.fx.coroutines.seconds
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.seconds as kotlinSeconds

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
        delaySpaceInterval: ArrowDuration = 2.seconds,
        maximumNumberOfRetries: Int = 3600
    ): Either<DeviceFarmTractorError, Upload>

    suspend fun deleteUploads(vararg uploads: Upload)

    suspend fun scheduleRunAndWait(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest,
        delaySpaceInterval: ArrowDuration = 1.minutes
    ): Either<DeviceFarmTractorError, Run>

    suspend fun downloadAllTestReportsOfTestRun(
        run: Run,
        destinyDirectory: Path,
        delayForDownload: Duration = 15.kotlinSeconds
    )

    suspend fun downloadCustomerArtifacts(
        job: Job,
        path: Path,
        delayForDownload: Duration
    ): Either<DeviceFarmTractorError, Unit>
}