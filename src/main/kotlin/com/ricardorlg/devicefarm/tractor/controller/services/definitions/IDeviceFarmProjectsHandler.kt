package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.Project

interface IDeviceFarmProjectsHandler {
    suspend fun listProjects(): Either<DeviceFarmTractorError, List<Project>>
    suspend fun createProject(projectName: String): Either<DeviceFarmTractorError, Project>
}