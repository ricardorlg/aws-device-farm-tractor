package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.fx.coroutines.Duration
import arrow.fx.coroutines.seconds
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.Project
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadType

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
        delaySpaceInterval:Duration = 2.seconds,
        maximumNumberOfRetries: Int = 100
    ): Either<DeviceFarmTractorError, Upload>
}