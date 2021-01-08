package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.*
import arrow.core.computations.either
import arrow.fx.coroutines.Duration
import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.parMapN
import arrow.fx.coroutines.parTraverse
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.*
import com.ricardorlg.devicefarm.tractor.model.*
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods.validateFileExtensionByType
import com.ricardorlg.devicefarm.tractor.utils.fold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.devicefarm.model.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import arrow.fx.coroutines.repeat as repeatEffectWithPolicy

class DefaultDeviceFarmTractorController(
    deviceFarmTractorLogging: IDeviceFarmTractorLogging,
    deviceFarmProjectsHandler: IDeviceFarmProjectsHandler,
    deviceFarmDevicePoolsHandler: IDeviceFarmDevicePoolsHandler,
    deviceFarmUploadArtifactsHandler: IDeviceFarmUploadArtifactsHandler,
    deviceFarmRunsHandler: IDeviceFarmRunsHandler,
    deviceFarmArtifactsHandler: IDeviceFarmArtifactsHandler
) :
    IDeviceFarmTractorController,
    IDeviceFarmTractorLogging by deviceFarmTractorLogging,
    IDeviceFarmProjectsHandler by deviceFarmProjectsHandler,
    IDeviceFarmDevicePoolsHandler by deviceFarmDevicePoolsHandler,
    IDeviceFarmUploadArtifactsHandler by deviceFarmUploadArtifactsHandler,
    IDeviceFarmRunsHandler by deviceFarmRunsHandler,
    IDeviceFarmArtifactsHandler by deviceFarmArtifactsHandler {

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

    override suspend fun deleteUploads(vararg uploads: Upload) {
        uploads.asList().parTraverse { upload ->
            deleteUpload(upload.arn())
                .map {
                    logStatus(DELETE_UPLOAD_MESSAGE.format(upload.name(), upload.arn()))
                }
        }
    }

    override suspend fun scheduleRunAndWait(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest,
        delaySpaceInterval: Duration
    ): Either<DeviceFarmTractorError, Run> {
        return either {
            val policy = Schedule
                .spaced<Run>(delaySpaceInterval)
                .whileInput<Run> { run -> run.status() != ExecutionStatus.COMPLETED }
                .logInput { logStatus("Current Run status = ${it.status()}") }
                .zipRight(Schedule.identity())
            logStatus("I will schedule the run $runName and wait until it finishes")
            val initialRun = !scheduleRun(
                appArn = appArn,
                runConfiguration = runConfiguration,
                devicePoolArn = devicePoolArn,
                executionConfiguration = executionConfiguration,
                runName = runName,
                projectArn = projectArn,
                testConfiguration = testConfiguration
            )
            repeatEffectWithPolicy(policy) {
                !fetchRun(initialRun.arn())
            }.also { logStatus("The test execution has just finished - result = ${it.result()}") }
        }
    }

    override suspend fun downloadAllTestReportsOfTestRun(run: Run, destinyDirectory: Path) {
        getAssociatedJobs(run)
            .map { associatedJobs ->
                createDirectory(
                    destinyDirectory
                        .resolve("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")
                ).map { reportsPath ->
                    associatedJobs
                        .parTraverse { job ->
                            createDirectory(
                                reportsPath.resolve(
                                    job.device().name().toLowerCase().replace("\\s".toRegex(), "_")
                                )
                            )
                                .map { path -> downloadCustomerArtifacts(job, path) }
                        }
                }
            }
    }

    override suspend fun downloadCustomerArtifacts(job: Job, path: Path): Either<DeviceFarmTractorError, Unit> {
        return either {
            val artifacts = !getArtifacts(job.arn())
            !artifacts
                .find { it.type() == ArtifactType.CUSTOMER_ARTIFACT }
                .fold(
                    ifNone = {
                        logStatus(JOB_DOES_NOT_HAVE_CUSTOMER_ARTIFACTS.format(job.device().name().orEmpty())).right()
                    },
                    ifPresent = { artifact ->
                        val destinyPath = path.resolve("${artifact.name()}.${artifact.extension()}")
                        downloadAndSave(destinyPath, artifact, job.device().name())
                    }
                ).mapLeft {
                    ErrorDownloadingArtifact(it)
                }
        }
    }

    private suspend fun downloadAndSave(
        destinyPath: Path,
        artifact: Artifact,
        deviceName: String
    ): Either<Throwable, Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                URL(artifact.url())
                    .openStream().use {
                        logStatus("I will start to download the report of $deviceName test run, into: $destinyPath")
                        Files.write(destinyPath, it.readBytes())
                    }
            }.fold(
                onSuccess = {
                    logStatus("I finish to download the report of $deviceName test execution")
                    Unit.right()
                },
                onFailure = { failure ->
                    logStatus("There was an error downloading the report of $deviceName test run. reason: ${failure.message.orEmpty()}")
                    runCatching { destinyPath.parent.deleteIfExists() }
                    failure.left()
                }
            )
        }
    }

    private suspend fun createDirectory(path: Path): Either<Throwable, Path> {
        return Either.catch {
            path.createDirectory()
        }.mapLeft {
            logStatus(ERROR_CREATING_DIRECTORY.format(path.fileName.toString(), it.message.orEmpty()))
            it
        }
    }
}