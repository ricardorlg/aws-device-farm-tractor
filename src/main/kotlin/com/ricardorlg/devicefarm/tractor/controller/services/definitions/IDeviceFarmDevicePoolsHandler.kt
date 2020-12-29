package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.DevicePool

interface IDeviceFarmDevicePoolsHandler {
    suspend fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>>
}