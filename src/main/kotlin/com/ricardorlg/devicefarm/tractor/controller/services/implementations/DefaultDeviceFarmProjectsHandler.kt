package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.ERROR_MESSAGE_FETCHING_AWS_PROJECTS
import com.ricardorlg.devicefarm.tractor.model.ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmListingProjectsError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmProjectCreationError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.CreateProjectRequest
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmProjectsHandler(private val deviceFarmClient: DeviceFarmClient) : IDeviceFarmProjectsHandler {
    override suspend fun listProjects(): Either<DeviceFarmTractorError, List<Project>> {
        return Either.catch {
            deviceFarmClient
                .listProjectsPaginator()
                .projects()
                .toList()
        }.mapLeft {
            DeviceFarmListingProjectsError(ERROR_MESSAGE_FETCHING_AWS_PROJECTS, it)
        }
    }

    override suspend fun createProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return Either.catch {
            deviceFarmClient
                .createProject(
                    CreateProjectRequest
                        .builder()
                        .name(projectName)
                        .build()
                )
                .project()
        }.mapLeft {
            DeviceFarmProjectCreationError("$ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT $projectName", it)
        }
    }
}