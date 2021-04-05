package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.DevicePool

interface IDeviceFarmDevicePoolsHandler {
    fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>>
}