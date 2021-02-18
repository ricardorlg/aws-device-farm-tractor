package io.github.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import io.github.ricardorlg.devicefarm.tractor.model.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.CreateProjectRequest
import software.amazon.awssdk.services.devicefarm.model.Project

internal class DefaultDeviceFarmProjectsHandler(private val deviceFarmClient: DeviceFarmClient) : IDeviceFarmProjectsHandler {
    override suspend fun listProjects(): Either<DeviceFarmTractorError, List<Project>> {
        return Either.catch {
            deviceFarmClient
                .listProjectsPaginator()
                .projects()
                .toList()
        }.mapLeft {
            ErrorFetchingProjects(ERROR_MESSAGE_FETCHING_AWS_PROJECTS, it)
        }
    }

    override suspend fun createProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return if (projectName.isBlank()) {
            Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_PROJECT_NAME))
        } else Either.catch {
            deviceFarmClient
                .createProject(
                    CreateProjectRequest
                        .builder()
                        .name(projectName)
                        .build()
                )
                .project()
        }.mapLeft {
            ErrorCreatingProject("$ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT $projectName", it)
        }
    }
}