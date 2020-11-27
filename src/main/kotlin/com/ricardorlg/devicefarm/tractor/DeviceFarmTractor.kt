package com.ricardorlg.devicefarm.tractor

import arrow.core.Either
import arrow.core.computations.either
import mu.KotlinLogging
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*

sealed class DeviceFarmExceptions(message: String, cause: Throwable) : Throwable(message, cause) {
    class ErrorFetchingProjects(message: String, cause: Throwable) : DeviceFarmExceptions(message, cause)
    class ErrorCreatingProject(message: String, cause: Throwable) : DeviceFarmExceptions(message, cause)
}

class DeviceFarmTractor(private val deviceFarmClient: DeviceFarmClient = DeviceFarmClient.create()) {
    private val logger = KotlinLogging.logger("Device Farm Tractor")
    private suspend fun listProjects(nextToken: String = "") =
        Either.catch {
            val request = if (nextToken.isEmpty())
                ListProjectsRequest.builder().build()
            else
                ListProjectsRequest.builder().nextToken(nextToken).build()

            deviceFarmClient
                .listProjects(request)
                .run {
                    projects() to this
                        .nextToken()
                        .orEmpty()
                }
        }.mapLeft {
            logger.error { it }
            DeviceFarmExceptions.ErrorFetchingProjects(ERROR_MESSAGE_FETCHING_USER_PROJECTS, it)
        }


    private suspend fun createProject(projectName: String) = Either.catch {
        deviceFarmClient
            .createProject(
                CreateProjectRequest
                    .builder()
                    .name(projectName)
                    .build()
            )
            .project()
    }.mapLeft {
        logger.error { it }
        DeviceFarmExceptions.ErrorCreatingProject(ERROR_PREFIX_MESSAGE_CRETING_NEW_PROJECT + projectName, it)
    }

    suspend fun findOrCreateProject(projectName: String): Either<Throwable, Project> {
        return either {
            var (projects, nextToken) = !listProjects()
            var project = projects.find { it.name() == projectName }
            while (project == null && nextToken.isNotEmpty()) {
                val nextProjects = !listProjects(nextToken)
                projects = nextProjects.first
                nextToken = nextProjects.second
                project = projects.find { it.name() == projectName }
            }
            project ?: !createProject(projectName)
        }
    }
}
