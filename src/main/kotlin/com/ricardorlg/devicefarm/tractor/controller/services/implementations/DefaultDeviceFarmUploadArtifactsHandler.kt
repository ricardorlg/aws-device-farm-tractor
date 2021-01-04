package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import arrow.core.left
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmUploadArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.CreateUploadRequest
import software.amazon.awssdk.services.devicefarm.model.GetUploadRequest
import software.amazon.awssdk.services.devicefarm.model.Upload
import software.amazon.awssdk.services.devicefarm.model.UploadType
import java.io.File

class DefaultDeviceFarmUploadArtifactsHandler(
    private val deviceFarmClient: DeviceFarmClient,
    private val httpClient: HttpHandler
) : IDeviceFarmUploadArtifactsHandler {

    override suspend fun createUpload(
        projectArn: String,
        uploadType: UploadType,
        artifactName: String
    ): Either<DeviceFarmTractorError, Upload> {
        return when {
            projectArn.isBlank() -> {
                Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_PROJECT_ARN))
            }
            artifactName.isBlank() -> {
                Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_FILENAME))
            }
            else -> {
                Either.catch {
                    deviceFarmClient
                        .createUpload(
                            CreateUploadRequest
                                .builder()
                                .projectArn(projectArn)
                                .type(uploadType)
                                .name(artifactName)
                                .contentType(AWS_UPLOAD_CONTENT_TYPE)
                                .build()
                        )
                        .upload()
                }.mapLeft {
                    ErrorCreatingUpload(ERROR_CREATING_AWS_UPLOAD.format(projectArn), it)
                }
            }
        }
    }

    override suspend fun uploadArtifactToS3(artifact: File, awsUpload: Upload): Either<DeviceFarmTractorError, Unit> {
        return Either.catch {
            val request = Request(Method.PUT, awsUpload.url())
                .header("Content-Type", awsUpload.contentType())
                .body(artifact.inputStream())
            httpClient(request)
        }.fold(
            ifRight = {
                Either.conditionally(it.status == Status.OK,
                    ifFalse = {
                        val msg = ARTIFACT_UPLOAD_TO_S3_NOT_SUCCESSFULLY.format(
                            artifact.nameWithoutExtension,
                            it.status
                        )
                        ErrorUploadingArtifact(msg, IllegalStateException(msg))
                    },
                    ifTrue = {})
            },
            ifLeft = { error ->
                ErrorUploadingArtifact(
                    ERROR_UPLOADING_ARTIFACT_TO_S3.format(artifact.nameWithoutExtension),
                    error
                ).left()
            }
        )
    }

    override suspend fun fetchUpload(uploadArn: String): Either<DeviceFarmTractorError, Upload> {
        return if (uploadArn.isBlank()) {
            DeviceFarmTractorErrorIllegalArgumentException(EMPTY_UPLOAD_ARN).left()
        } else {
            Either.catch {
                deviceFarmClient
                    .getUpload(
                        GetUploadRequest
                            .builder()
                            .arn(uploadArn)
                            .build()
                    ).upload()
            }.mapLeft {
                ErrorFetchingUpload(ERROR_FETCHING_UPLOAD_FROM_AWS.format(uploadArn), it)
            }
        }
    }
}