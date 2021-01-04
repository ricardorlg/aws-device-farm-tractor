package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.rightIfNotNull
import arrow.fx.coroutines.Duration
import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.parMapN
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmUploadArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods.validateFileExtensionByType
import com.ricardorlg.devicefarm.tractor.utils.fold
import software.amazon.awssdk.services.devicefarm.model.*
import arrow.fx.coroutines.repeat as repeatEffectWithPolicy

class DefaultDeviceFarmTractorController(
    deviceFarmTractorLogging: IDeviceFarmTractorLogging,
    deviceFarmProjectsHandler: IDeviceFarmProjectsHandler,
    deviceFarmDevicePoolsHandler: IDeviceFarmDevicePoolsHandler,
    deviceFarmUploadArtifactsHandler: IDeviceFarmUploadArtifactsHandler,
) :
    IDeviceFarmTractorController,
    IDeviceFarmTractorLogging by deviceFarmTractorLogging,
    IDeviceFarmProjectsHandler by deviceFarmProjectsHandler,
    IDeviceFarmDevicePoolsHandler by deviceFarmDevicePoolsHandler,
    IDeviceFarmUploadArtifactsHandler by deviceFarmUploadArtifactsHandler {

    override suspend fun findOrCreateProject(projectName: String): Either<DeviceFarmTractorError, Project> {
        return either {
            logStatus("I will try to find the project $projectName or I will create it if not found")
            val projects = !listProjects()
            val maybeProject = projects.find { it.name().equals(projectName, true) }
            maybeProject.fold(
                ifNone = {
                    logStatus("I didn't find the project $projectName, I will create it")
                    !createProject(projectName)
                },
                ifPresent = {
                    logStatus("I found the project $projectName, I will use it")
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
                logStatus("No device pool name was provided, I will use the first associated device pool of current project")
                devicePools
                    .firstOrNull()
                    .rightIfNotNull {
                        NoRegisteredDevicePoolsError(PROJECT_DOES_NOT_HAVE_DEVICE_POOLS.format(projectArn))
                    }.bind()
            } else {
                logStatus("I will try to find the $devicePoolName device pool")

                devicePools
                    .find { it.name().equals(devicePoolName, true) }
                    .rightIfNotNull {
                        DevicePoolNotFoundError(DEVICE_POOL_NOT_FOUND.format(devicePoolName))
                    }.map {
                        logStatus("I found the device pool $devicePoolName, I will use it")
                        it
                    }
                    .bind()
            }
        }
    }

    override suspend fun uploadArtifactToDeviceFarm(
        projectArn: String,
        artifactPath: String,
        uploadType: UploadType,
        delaySpaceInterval: Duration,
        maximumNumberOfRetries: Int
    ): Either<DeviceFarmTractorError, Upload> {
        return either {

            val fetchAWSUploadSchedulePolicy = Schedule
                .spaced<Either<DeviceFarmTractorError, Upload>>(delaySpaceInterval)
                .whileOutput { retryNumber -> retryNumber < maximumNumberOfRetries }
                .whileInput<Either<DeviceFarmTractorError, Upload>> { currentResult ->
                    currentResult.isLeft()
                            || currentResult.exists { currentUpload -> currentUpload.status() == UploadStatus.INITIALIZED || currentUpload.status() == UploadStatus.PROCESSING }
                }
                .logInput {
                    when (it) {
                        is Either.Left -> {
                            logStatus("AWS Upload is not ready yet")
                        }
                        is Either.Right -> {
                            logStatus("Current status of AWS Upload: ${it.b.status()}")
                        }
                    }
                }
                .zipRight(Schedule.identity())


            val file = HelperMethods
                .loadFileFromPath(artifactPath)
                .flatMap { it.validateFileExtensionByType(uploadType) }
                .bind()

            val initialUpload = !createUpload(
                projectArn = projectArn,
                uploadType = uploadType,
                artifactName = file.name
            )

            !parMapN(
                fa = { !uploadArtifactToS3(file, initialUpload) },
                fb = {
                    repeatEffectWithPolicy(fetchAWSUploadSchedulePolicy) {
                        fetchUpload(initialUpload.arn())
                    }
                }
            ) { _, result ->
                when (result) {
                    is Either.Left -> {
                        result
                    }
                    is Either.Right -> {
                        if (result.b.status() != UploadStatus.SUCCEEDED) {
                            UploadFailureError("The artifact ${file.nameWithoutExtension} AWS status was ${result.b.status()} message = ${result.b.message()}")
                                .left()
                        } else {
                            logStatus("The Artifact ${file.nameWithoutExtension} was uploaded to AWS and is ready to use")
                            result
                        }

                    }
                }
            }
        }
    }

}