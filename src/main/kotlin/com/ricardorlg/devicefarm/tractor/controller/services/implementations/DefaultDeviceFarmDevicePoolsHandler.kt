package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.EMPTY_PROJECT_ARN
import com.ricardorlg.devicefarm.tractor.model.ERROR_MESSAGE_FETCHING_DEVICE_POOLS
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmIllegalArgumentError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmListingDevicePoolsError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.ListDevicePoolsRequest

class DefaultDeviceFarmDevicePoolsHandler(private val deviceFarmClient: DeviceFarmClient) :
    IDeviceFarmDevicePoolsHandler {
    override suspend fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>> {
        return if (projectArn.isBlank()) {
            Either.left(DeviceFarmIllegalArgumentError(EMPTY_PROJECT_ARN))
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
                DeviceFarmListingDevicePoolsError(ERROR_MESSAGE_FETCHING_DEVICE_POOLS, it)
            }
        }
    }
}