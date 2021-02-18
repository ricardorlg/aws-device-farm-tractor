package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import arrow.fx.coroutines.seconds
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path
import kotlin.time.Duration
import arrow.fx.coroutines.Duration as ArrowDuration
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
        delaySpaceInterval: ArrowDuration = 10.seconds
    ): Either<DeviceFarmTractorError, Run>

    suspend fun downloadAllEvidencesOfTestRun(
        run: Run,
        destinyDirectory: Path,
        delayForDownload: Duration = 20.kotlinSeconds
    )

    suspend fun downloadAWSDeviceFarmArtifacts(
        artifacts: List<Artifact>,
        deviceName:String,
        path: Path,
        artifactType: ArtifactType
    ): Either<DeviceFarmTractorError, Unit>

    suspend fun getDeviceResultsTable(run: Run):String
}