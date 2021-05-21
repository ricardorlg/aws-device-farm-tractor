package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.Project

interface IDeviceFarmProjectsHandler {
    fun listProjects(): Either<DeviceFarmTractorError, List<Project>>
    fun createProject(projectName: String): Either<DeviceFarmTractorError, Project>
}