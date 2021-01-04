package com.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.DevicePool

class MockedDeviceFarmDevicePoolsHandler(
    private val fetchDevicePoolsImpl: (String) -> Either<DeviceFarmTractorError, List<DevicePool>> = { fail("Not implemented") }
) : IDeviceFarmDevicePoolsHandler {
    override suspend fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>> {
        return fetchDevicePoolsImpl(projectArn)
    }
}