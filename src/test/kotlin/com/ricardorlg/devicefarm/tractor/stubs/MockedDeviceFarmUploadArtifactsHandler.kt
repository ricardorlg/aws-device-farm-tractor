package com.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmUploadArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadType
import java.io.File

class MockedDeviceFarmUploadArtifactsHandler(
    private val createUploadImpl: (String, UploadType, String) -> Either<DeviceFarmTractorError, Upload> = { _, _, _ ->
        fail(
            "Not implemented"
        )
    },
    private val uploadArtifactImpl: suspend (File, Upload) -> Either<DeviceFarmTractorError, Unit> = { _, _ -> fail("Not implemented") },
    private val fetchUploadImpl: suspend (String) -> Either<DeviceFarmTractorError, Upload> = { fail("Not implemented") }
) : IDeviceFarmUploadArtifactsHandler {
    override suspend fun createUpload(
        projectArn: String,
        uploadType: UploadType,
        artifactName: String
    ): Either<DeviceFarmTractorError, Upload> {
        return createUploadImpl(projectArn, uploadType, artifactName)
    }

    override suspend fun uploadArtifactToS3(artifact: File, awsUpload: Upload): Either<DeviceFarmTractorError, Unit> {
        return uploadArtifactImpl(artifact, awsUpload)
    }

    override suspend fun fetchUpload(uploadArn: String): Either<DeviceFarmTractorError, Upload> {
        return fetchUploadImpl(uploadArn)
    }
}