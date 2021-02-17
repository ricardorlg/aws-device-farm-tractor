package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.*
import arrow.core.computations.either
import arrow.core.extensions.list.foldable.find
import arrow.fx.coroutines.*
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextBorder
import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.*
import com.ricardorlg.devicefarm.tractor.model.*
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods.validateFileExtensionByType
import com.ricardorlg.devicefarm.tractor.utils.prettyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import software.amazon.awssdk.services.devicefarm.model.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import arrow.fx.coroutines.Duration as ArrowDuration
import arrow.fx.coroutines.repeat as repeatEffectWithPolicy

internal class DefaultDeviceFarmTractorController(
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
            listProjects()
                .bind()
                .find { it.name() == projectName }
                .fold(
                    ifEmpty = {
                        logStatus("I didn't find the project $projectName, I will create it")
                        !createProject(projectName)
                    },
                    ifSome = {
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
                    .firstOrNull { it.name() == devicePoolName }
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
        delaySpaceInterval: ArrowDuration,
        maximumNumberOfRetries: Int
    ): Either<DeviceFarmTractorError, Upload> {
        return either {
            val file = HelperMethods
                .loadFileFromPath(artifactPath)
                .flatMap { it.validateFileExtensionByType(uploadType) }
                .bind()

            logStatus("I will start to upload the artifact ${file.name}")

            val initialUpload = !createUpload(
                projectArn = projectArn,
                uploadType = uploadType,
                artifactName = file.name
            )

            val fetchAWSUploadSchedulePolicy = Schedule
                .spaced<Either<DeviceFarmTractorError, Upload>>(delaySpaceInterval)
                .whileOutput { retryNumber -> retryNumber < maximumNumberOfRetries }
                .whileInput<Either<DeviceFarmTractorError, Upload>> { currentResult ->
                    when (currentResult) {
                        is Either.Left -> {
                            currentResult.a.cause !is DeviceFarmException
                        }
                        is Either.Right -> {
                            currentResult.b.status() == UploadStatus.INITIALIZED || currentResult.b.status() == UploadStatus.PROCESSING
                        }
                    }
                }
                .logInput {
                    when (it) {
                        is Either.Left -> {
                            logStatus("${file.nameWithoutExtension} upload is not ready yet")
                        }
                        is Either.Right -> {
                            logStatus("Current status of ${file.nameWithoutExtension} AWS upload: ${it.b.status()}")
                        }
                    }
                }
                .zipRight(Schedule.identity())

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
        delaySpaceInterval: ArrowDuration
    ): Either<DeviceFarmTractorError, Run> {
        return either {
            val policy = Schedule
                .spaced<Run>(delaySpaceInterval)
                .whileInput<Run> { run -> run.status() != ExecutionStatus.COMPLETED }
                .logInput { logStatus(" Current Run status = ${it.status()}") }
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
            }.also { logStatus("Test execution just finished with result = ${it.result()}") }
        }
    }

    override suspend fun downloadAllEvidencesOfTestRun(run: Run, destinyDirectory: Path, delayForDownload: Duration) {
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
                                    job.device()
                                        .name()
                                        .toLowerCase()
                                        .replace("\\s".toRegex(), "_")
                                )
                            ).map { path ->
                                logStatus(
                                    "I will download the artifacts associated to the device ${
                                        job.device().name()
                                    }"
                                )
                                //TODO Remove this when the artifact status is more clear
                                delay(delayForDownload)
                                getArtifacts(job.arn())
                                    .map { artifacts ->
                                        parTupledN(
                                            fa = {
                                                downloadAWSDeviceFarmArtifacts(
                                                    artifacts = artifacts,
                                                    deviceName = job.device().name().orEmpty(),
                                                    path = path,
                                                    artifactType = ArtifactType.CUSTOMER_ARTIFACT
                                                )
                                            },
                                            fb = {
                                                downloadAWSDeviceFarmArtifacts(
                                                    artifacts = artifacts,
                                                    deviceName = job.device().name().orEmpty(),
                                                    path = path,
                                                    artifactType = ArtifactType.VIDEO
                                                )
                                            }
                                        )
                                        runCatching {
                                            if (path.listDirectoryEntries().isEmpty())
                                                path.deleteExisting()
                                        }

                                    }
                            }
                        }
                }
            }
    }

    override suspend fun downloadAWSDeviceFarmArtifacts(
        artifacts: List<Artifact>,
        deviceName: String,
        path: Path,
        artifactType: ArtifactType
    ): Either<DeviceFarmTractorError, Unit> {
        return if (artifactType in INVALID_ARTIFACT_TYPES)
            DeviceFarmTractorErrorIllegalArgumentException("$artifactType is not supported").left()
        else {
            logStatus("I will start to download the ${artifactType.prettyName()} of $deviceName test run")
            artifacts
                .find { it.type() == artifactType }
                .fold(
                    ifSome = { artifact ->
                        val destinyPath = path.resolve("${artifact.name()}.${artifact.extension()}")
                        downloadAndSave(destinyPath, artifact, deviceName)
                    },
                    ifEmpty = {
                        logStatus(
                            JOB_DOES_NOT_HAVE_ARTIFACT_OF_TYPE.format(
                                artifactType.name,
                                deviceName
                            )
                        ).right()
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
        return Either.catch {
            evalOn(Dispatchers.IO) {
                URL(artifact.url())
                    .openStream().use {
                        Files.write(destinyPath, it.readBytes())
                    }
            }
        }.fold(
            ifRight = {
                logStatus(
                    "I've finished to download the ${
                        artifact.type().prettyName()
                    } of $deviceName in $destinyPath"
                )
                Unit.right()
            },
            ifLeft = { failure ->
                logStatus(
                    "There was an error downloading the ${
                        artifact.type().prettyName()
                    } of $deviceName test run. reason: ${failure.message.orEmpty()}"
                )
                failure.left()
            }
        )
    }

    override suspend fun getDeviceResultsTable(run: Run): String {
        val table = either<DeviceFarmTractorError, Table> {
            val associatedJobs = !getAssociatedJobs(run)
            table {
                cellStyle {
                    border = true
                }
                header {
                    row("Device", "Result")
                }
                associatedJobs
                    .forEach { job ->
                        row(job.device().name(), job.result().name)
                    }
            }
        }
        return table.orNull()?.renderText(border = TextBorder.ASCII).orEmpty()
    }

    private suspend fun createDirectory(path: Path): Either<Throwable, Path> {
        return Either.catch {
            evalOn(Dispatchers.IO) {
                path.createDirectory()
            }
        }.mapLeft {
            logStatus(ERROR_CREATING_DIRECTORY.format(path.fileName.toString(), it.message.orEmpty()))
            it

        }
    }
}