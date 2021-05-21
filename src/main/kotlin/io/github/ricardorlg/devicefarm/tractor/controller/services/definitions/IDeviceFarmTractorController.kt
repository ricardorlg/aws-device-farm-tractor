package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Path
import kotlin.time.Duration

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
        delaySpaceInterval: Duration = Duration.seconds(2),
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
        delaySpaceInterval: Duration = Duration.seconds(10)
    ): Either<DeviceFarmTractorError, Run>

    suspend fun downloadAllEvidencesOfTestRun(
        run: Run,
        destinyDirectory: Path,
        delayForDownload: Duration = Duration.seconds(20)
    )

    suspend fun downloadAWSDeviceFarmArtifacts(
        artifacts: List<Artifact>,
        deviceName: String,
        path: Path,
        artifactType: ArtifactType
    ): Either<DeviceFarmTractorError, Unit>

    suspend fun getDeviceResultsTable(run: Run): String
}