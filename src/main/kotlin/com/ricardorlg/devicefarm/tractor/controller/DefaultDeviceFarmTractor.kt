package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.rightIfNotNull
import com.ricardorlg.devicefarm.tractor.model.DEVICE_POOL_NOT_FOUND
import com.ricardorlg.devicefarm.tractor.model.PROJECT_DOES_NOT_HAVE_DEVICE_POOLS
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmDevicePoolNotFound
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmProjectDoesNotHaveDevicePools
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import com.ricardorlg.devicefarm.tractor.utils.fold
import mu.KotlinLogging
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmTractor(
    deviceFarmProjectsHandler: IDeviceFarmProjectsHandler,
    deviceFarmDevicePoolsHandler: IDeviceFarmDevicePoolsHandler
) :
    IDeviceFarmTractorController,
    IDeviceFarmProjectsHandler by deviceFarmProjectsHandler,
    IDeviceFarmDevicePoolsHandler by deviceFarmDevicePoolsHandler {

    private val logger = KotlinLogging.logger("Device Farm Tractor")

    override suspend fun findOrCreateProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return either {
            logger.info { "I will try to find the project $projectName or I will create it if not found" }
            val projects = !listProjects()
            val maybeProject = projects.find { it.name().equals(projectName, true) }
            maybeProject.fold(
                ifNone = {
                    logger.info { "I didn't find the project $projectName, I will create it" }
                    !createProject(projectName)
                },
                ifPresent = {
                    logger.info { "I found the project $projectName, I will use it" }
                    it
                }
            )
        }
    }

    override suspend fun findOrUseDefaultDevicePool(
        projectArn: String,
        devicePoolName: String
    ): Either<DeviceFarmTractorError, DevicePool> {
        return either {
            val devicePools = !fetchDevicePools(projectArn)
            if (devicePoolName.isBlank()) {
                logger.info { "No device pool name was provided, I will use the first associated device pool of current project" }
                devicePools
                    .firstOrNull()
                    .rightIfNotNull {
                        DeviceFarmProjectDoesNotHaveDevicePools(PROJECT_DOES_NOT_HAVE_DEVICE_POOLS.format(projectArn))
                    }.bind()
            } else {
                logger.info { "I will try to find the $devicePoolName device pool" }
                devicePools
                    .find { it.name().equals(devicePoolName, true) }
                    .rightIfNotNull {
                        DeviceFarmDevicePoolNotFound(DEVICE_POOL_NOT_FOUND.format(devicePoolName))
                    }.map {
                        logger.info { "I found the device pool $devicePoolName, I will use it" }
                        it
                    }
                    .bind()
            }
        }
    }
}