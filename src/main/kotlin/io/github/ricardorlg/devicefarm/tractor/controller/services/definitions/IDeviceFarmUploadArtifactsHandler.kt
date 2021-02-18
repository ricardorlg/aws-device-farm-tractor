package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.DeleteUploadResponse
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadType
import java.io.File

interface IDeviceFarmUploadArtifactsHandler {
    suspend fun createUpload(
        projectArn: String,
        uploadType: UploadType,
        artifactName: String
    ): Either<DeviceFarmTractorError, Upload>

    suspend fun uploadArtifactToS3(artifact: File, awsUpload: Upload): Either<DeviceFarmTractorError, Unit>

    suspend fun fetchUpload(uploadArn: String):Either<DeviceFarmTractorError,Upload>

    suspend fun deleteUpload(uploadArn: String):Either<DeviceFarmTractorError,DeleteUploadResponse>
}