package io.github.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.DevicePool

class MockedDeviceFarmDevicePoolsHandler(
    private val fetchDevicePoolsImpl: (String) -> Either<DeviceFarmTractorError, List<DevicePool>> = { fail("Not implemented") }
) : IDeviceFarmDevicePoolsHandler {
    override fun fetchDevicePools(projectArn: String): Either<DeviceFarmTractorError, List<DevicePool>> {
        return fetchDevicePoolsImpl(projectArn)
    }
}