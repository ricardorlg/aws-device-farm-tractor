package io.github.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.Project

class MockedDeviceFarmProjectsHandler(
    private val listProjectsImpl: () -> Either<DeviceFarmTractorError, List<Project>> = { fail("Not implemented") },
    private val createProjectImpl: (String) -> Either<DeviceFarmTractorError, Project> = { fail("Not implemented") }

) : IDeviceFarmProjectsHandler {
    override fun listProjects(): Either<DeviceFarmTractorError, List<Project>> {
        return listProjectsImpl()
    }

    override fun createProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return createProjectImpl(projectName)
    }
}