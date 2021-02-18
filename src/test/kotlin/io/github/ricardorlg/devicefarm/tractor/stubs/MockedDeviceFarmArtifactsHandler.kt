package io.github.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmArtifactsHandler
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import org.junit.jupiter.api.fail
import software.amazon.awssdk.services.devicefarm.model.Artifact

class MockedDeviceFarmArtifactsHandler(
    private val getArtifactsImpl: () -> Either<DeviceFarmTractorError, List<Artifact>> = { fail("Not implemented") }
) : IDeviceFarmArtifactsHandler {
    override suspend fun getArtifacts(runArn: String): Either<DeviceFarmTractorError, List<Artifact>> {
        return getArtifactsImpl()
    }
}