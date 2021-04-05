package io.github.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import io.github.ricardorlg.devicefarm.tractor.model.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.ListDevicePoolsRequest

internal class DefaultDeviceFarmDevicePoolsHandler(private val deviceFarmClient: DeviceFarmClient) :
    IDeviceFarmDevicePoolsHandler {
    override fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>> {
        return if (projectArn.isBlank()) {
            Either.Left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_PROJECT_ARN))
        } else {
            Either.catch {
                deviceFarmClient
                    .listDevicePoolsPaginator(
                        ListDevicePoolsRequest
                            .builder()
                            .arn(projectArn)
                            .build()
                    ).devicePools()
                    .toList()
            }.mapLeft {
                ErrorFetchingDevicePools(ERROR_MESSAGE_FETCHING_DEVICE_POOLS, it)
            }
        }
    }
}