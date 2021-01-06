package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.Artifact

interface IDeviceFarmArtifactsHandler {
    suspend fun getArtifacts(runArn: String): Either<DeviceFarmTractorError, List<Artifact>>
}