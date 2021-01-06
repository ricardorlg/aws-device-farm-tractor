package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.Artifact
import software.amazon.awssdk.services.devicefarm.model.ListArtifactsRequest

class DefaultDeviceFarmArtifactsHandler(private val deviceFarmClient: DeviceFarmClient) : IDeviceFarmArtifactsHandler {
    override suspend fun getArtifacts(runArn: String): Either<DeviceFarmTractorError, List<Artifact>> {
        return if (runArn.isBlank()) {
            Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_RUN_ARN))
        } else {
            Either.catch {
                deviceFarmClient
                    .listArtifactsPaginator(
                        ListArtifactsRequest
                            .builder()
                            .arn(runArn)
                            .build()
                    ).artifacts()
                    .toList()
            }.mapLeft {
                ErrorFetchingArtifacts(ERROR_FETCHING_ARTIFACTS.format(runArn), it)
            }
        }
    }
}