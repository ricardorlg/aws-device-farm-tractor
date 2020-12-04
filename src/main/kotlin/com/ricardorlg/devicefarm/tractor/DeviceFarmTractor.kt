package com.ricardorlg.devicefarm.tractor

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.rightIfNotNull
import mu.KotlinLogging
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*

sealed class DeviceFarmExceptions(message: String, cause: Throwable) : Throwable(message, cause) {
    class ErrorFetchingProjects(message: String, cause: Throwable) : DeviceFarmExceptions(message, cause)
    class ErrorCreatingProject(message: String, cause: Throwable) : DeviceFarmExceptions(message, cause)
    class EmptyProjectArnException : IllegalStateException(EMPTY_PROJECT_ARN)
    class ErrorFetchingDevicePools(message: String, cause: Throwable) : DeviceFarmExceptions(message, cause)
    class ProjectDoesNotHaveDevicePools(message: String) : IllegalStateException(message)
    class DevicePoolNotFoundException(message: String) : NoSuchElementException(message)
}

class DeviceFarmTractor(private val deviceFarmClient: DeviceFarmClient = DeviceFarmClient.create()) {
    private val logger = KotlinLogging.logger("Device Farm Tractor")

    private suspend fun listProjects(nextToken: String = ""): Either<Throwable, Pair<List<Project>, String>> {
        return Either.catch {
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
    }

    private suspend fun createProject(projectName: String): Either<Throwable, Project> {
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
            logger.error { it }
            DeviceFarmExceptions.ErrorCreatingProject(ERROR_PREFIX_MESSAGE_CRETING_NEW_PROJECT + projectName, it)
        }
    }

    private suspend fun fetchDevicePools(
        projectArn: String,
        nextToken: String = ""
    ): Either<Throwable, Pair<List<DevicePool>, String>> {
        return Either.catch {
            val request = if (nextToken.isEmpty())
                ListDevicePoolsRequest.builder().arn(projectArn).build()
            else
                ListDevicePoolsRequest.builder().arn(projectArn).nextToken(nextToken).build()
            deviceFarmClient
                .listDevicePools(request)
                .run {
                    devicePools() to this
                        .nextToken()
                        .orEmpty()
                }
        }.mapLeft {
            logger.error { it }
            DeviceFarmExceptions.ErrorFetchingDevicePools(ERROR_MESSAGE_FETCHING_DEVICE_POOLS, it)
        }
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

    suspend fun findOrUseDefaultDevicePool(
        projectArn: String,
        devicePoolName: String = ""
    ): Either<Throwable, DevicePool> {
        return if (projectArn.isEmpty()) {
            logger.error { EMPTY_PROJECT_ARN }
            Either.left(DeviceFarmExceptions.EmptyProjectArnException())
        } else
            either {
                if (devicePoolName.isEmpty()) {
                    val devicePools = fetchDevicePools(projectArn).bind().first
                    !devicePools
                        .firstOrNull()
                        .rightIfNotNull {
                            val msg = "El proyecto $projectArn no tiene device pools asociados"
                            logger.error { msg }
                            DeviceFarmExceptions.ProjectDoesNotHaveDevicePools(msg)
                        }
                } else {
                    var (devicePools, nextToken) = !fetchDevicePools(projectArn)
                    var devicePool = devicePools.find { it.name() == devicePoolName }
                    while (devicePool == null && nextToken.isNotEmpty()) {
                        val nextDevicePools = !fetchDevicePools(projectArn, nextToken)
                        devicePools = nextDevicePools.first
                        nextToken = nextDevicePools.second
                        devicePool = devicePools.find { it.name() == devicePoolName }
                    }
                    !devicePool
                        .rightIfNotNull {
                            DeviceFarmExceptions
                                .DevicePoolNotFoundException("El devicePool $devicePoolName no esta asociado al proyecto $projectArn")
                        }
                }
            }
    }
}
