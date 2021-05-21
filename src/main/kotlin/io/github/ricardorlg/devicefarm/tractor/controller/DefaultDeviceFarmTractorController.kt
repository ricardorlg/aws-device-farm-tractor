package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.*
import arrow.core.computations.either
import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.parTraverse
import arrow.fx.coroutines.parZip
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextBorder
import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.*
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.github.ricardorlg.devicefarm.tractor.utils.HelperMethods
import io.github.ricardorlg.devicefarm.tractor.utils.HelperMethods.validateFileExtensionByType
import io.github.ricardorlg.devicefarm.tractor.utils.ensure
import io.github.ricardorlg.devicefarm.tractor.utils.prettyName
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
import kotlin.time.DurationUnit

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
            logMessage("I will try to find the project $projectName or I will create it if not found")
            listProjects()
                .bind()
                .firstOrNone { it.name() == projectName }
                .fold(
                    ifEmpty = {
                        logMessage("I didn't find the project $projectName, I will create it")
                        createProject(projectName).bind()
                    },
                    ifSome = {
                        logMessage("I found the project $projectName, I will use it")
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
            val devicePools = fetchDevicePools(projectArn).bind()
            if (devicePoolName.isBlank()) {
                logMessage("No device pool name was provided, I will use the first associated device pool of current project")
                devicePools
                    .firstOrNull()
                    .rightIfNotNull {
                        NoRegisteredDevicePoolsError(PROJECT_DOES_NOT_HAVE_DEVICE_POOLS.format(projectArn))
                    }.bind()
            } else {
                logMessage("I will try to find the $devicePoolName device pool")
                devicePools
                    .firstOrNull { it.name() == devicePoolName }
                    .rightIfNotNull {
                        DevicePoolNotFoundError(DEVICE_POOL_NOT_FOUND.format(devicePoolName))
                    }.map {
                        logMessage("I found the device pool $devicePoolName, I will use it")
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
            val file = HelperMethods
                .loadFileFromPath(artifactPath)
                .flatMap { it.validateFileExtensionByType(uploadType) }
                .bind()

            logMessage("I will start to upload the artifact ${file.name}")

            val initialUpload = createUpload(
                projectArn = projectArn,
                uploadType = uploadType,
                artifactName = file.name
            ).bind()

            val fetchAWSUploadSchedulePolicy = Schedule
                .spaced<Either<DeviceFarmTractorError, Upload>>(delaySpaceInterval.toDouble(DurationUnit.NANOSECONDS))
                .whileOutput { retryNumber -> retryNumber < maximumNumberOfRetries }
                .whileInput<Either<DeviceFarmTractorError, Upload>> { currentResult ->
                    when (currentResult) {
                        is Either.Left -> {
                            currentResult.value.cause !is DeviceFarmException
                        }
                        is Either.Right -> {
                            currentResult.value.status() == UploadStatus.INITIALIZED || currentResult.value.status() == UploadStatus.PROCESSING
                        }
                    }
                }
                .logInput {
                    when (it) {
                        is Either.Left -> {
                            logMessage("${file.nameWithoutExtension} upload is not ready yet")
                        }
                        is Either.Right -> {
                            logMessage("Current status of ${file.nameWithoutExtension} AWS upload: ${it.value.status()}")
                        }
                    }
                }
                .zipRight(Schedule.identity())

            parZip(
                ctx = Dispatchers.IO,
                fa = { uploadArtifactToS3(file, initialUpload).bind() },
                fb = {
                    fetchAWSUploadSchedulePolicy.repeat {
                        fetchUpload(initialUpload.arn())
                    }
                }) { _, b ->
                b.ensure(
                    error = { upload -> UploadFailureError("The artifact ${file.name} upload AWS status was ${upload.status()}, message = ${upload.message().ifEmpty { "There was no error message, please check if file is valid (yml)" }}") },
                    predicate = { upload -> upload.status() == UploadStatus.SUCCEEDED },
                    predicateAction = { logMessage("The Artifact ${file.name} was uploaded to AWS and is ready to use") }
                )
            }.bind()
        }
    }

    override suspend fun deleteUploads(vararg uploads: Upload) {
        uploads.asList().parTraverse { upload ->
            deleteUpload(upload.arn())
                .map {
                    logMessage(DELETE_UPLOAD_MESSAGE.format(upload.name(), upload.arn()))
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
            //TODO: Use duration when arrow kotlin 1.5 is fully supported
            val policy = Schedule
                .spaced<Run>(delaySpaceInterval.toDouble(DurationUnit.NANOSECONDS))
                .whileInput<Run> { run -> run.status() != ExecutionStatus.COMPLETED }
                .logInput { logMessage(" Current Run status = ${it.status()}") }
                .zipRight(Schedule.identity())

            logMessage("I will schedule the run $runName and wait until it finishes")

            val initialRun = scheduleRun(
                appArn = appArn,
                runConfiguration = runConfiguration,
                devicePoolArn = devicePoolArn,
                executionConfiguration = executionConfiguration,
                runName = runName,
                projectArn = projectArn,
                testConfiguration = testConfiguration
            ).bind()
            policy.repeat {
                fetchRun(initialRun.arn()).bind()
            }.also { logMessage("Test execution just finished with result = ${it.result()}") }
        }
    }

    override suspend fun downloadAllEvidencesOfTestRun(run: Run, destinyDirectory: Path, delayForDownload: Duration) {
        getAssociatedJobs(run)
            .map { associatedJobs ->
                createDirectory(
                    destinyDirectory
                        .resolve("test_reports_${run.name().lowercase().replace("\\s".toRegex(), "_")}")
                ).map { reportsPath ->
                    associatedJobs
                        .parTraverse(Dispatchers.IO) { job ->
                            createDirectory(
                                reportsPath.resolve(
                                    job.device()
                                        .name()
                                        .lowercase()
                                        .replace("\\s".toRegex(), "_")
                                )
                            ).map { path ->
                                logMessage(
                                    "I will download the artifacts associated to the device ${
                                        job.device().name()
                                    }"
                                )
                                //TODO Remove this when the artifact status is more clear
                                delay(delayForDownload)
                                getArtifacts(job.arn())
                                    .map { artifacts ->
                                        parZip({
                                            downloadAWSDeviceFarmArtifacts(
                                                artifacts = artifacts,
                                                deviceName = job.device().name().orEmpty(),
                                                path = path,
                                                artifactType = ArtifactType.CUSTOMER_ARTIFACT
                                            )
                                        }, {
                                            downloadAWSDeviceFarmArtifacts(
                                                artifacts = artifacts,
                                                deviceName = job.device().name().orEmpty(),
                                                path = path,
                                                artifactType = ArtifactType.VIDEO
                                            )
                                        }, { _, _ -> })
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
            logMessage("I will start to download the ${artifactType.prettyName()} of $deviceName test run")
            artifacts
                .firstOrNone { it.type() == artifactType }
                .fold(
                    ifEmpty = {
                        logMessage(
                            JOB_DOES_NOT_HAVE_ARTIFACT_OF_TYPE.format(
                                artifactType.name,
                                deviceName
                            )
                        ).right()
                    },
                    ifSome = { artifact ->
                        val destinyPath = path.resolve("${artifact.name()}.${artifact.extension()}")
                        downloadAndSave(destinyPath, artifact, deviceName)
                    }
                ).mapLeft {
                    ErrorDownloadingArtifact(it)
                }
        }
    }

    private fun downloadAndSave(
        destinyPath: Path,
        artifact: Artifact,
        deviceName: String
    ): Either<Throwable, Unit> {
        return Either.catch {
            URL(artifact.url())
                .openStream().use {
                    Files.write(destinyPath, it.readBytes())
                }
        }.fold(
            ifRight = {
                logMessage(
                    "I've finished to download the ${
                        artifact.type().prettyName()
                    } of $deviceName in $destinyPath"
                )
                Unit.right()
            },
            ifLeft = { failure ->
                logMessage(
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
            val associatedJobs = getAssociatedJobs(run).bind()
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

    private fun createDirectory(path: Path): Either<Throwable, Path> {
        return Either.catch { path.createDirectory() }.mapLeft {
            logMessage(ERROR_CREATING_DIRECTORY.format(path.fileName.toString(), it.message.orEmpty()))
            it
        }
    }
}