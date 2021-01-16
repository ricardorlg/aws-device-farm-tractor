package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import com.ricardorlg.devicefarm.tractor.model.ErrorDownloadingArtifact
import com.ricardorlg.devicefarm.tractor.stubs.*
import com.ricardorlg.devicefarm.tractor.tempFolder
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.captureStandardOut
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.paths.*
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import software.amazon.awssdk.services.devicefarm.model.Artifact
import software.amazon.awssdk.services.devicefarm.model.ArtifactType
import software.amazon.awssdk.services.devicefarm.model.Job
import software.amazon.awssdk.services.devicefarm.model.Run
import java.nio.file.AccessDeniedException
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.time.milliseconds

class WhenDownloadingCustomerArtifacts : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectsHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val runScheduleHandler = MockedDeviceFarmRunsHandler()
    val jobArn = "arn:test:job"
    val deviceArn = "arn:test:device"
    val deviceName = "nexus 3"

    "It should download the customer artifact of a job execution"{
        //GIVEN
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")

        val executionJob = Job
            .builder()
            .arn(jobArn)
            .name("test_execution")
            .device {
                it.name(deviceName)
                    .arn(deviceArn)
            }
            .build()
        val artifact = Artifact
            .builder()
            .arn("arn:test:artifact")
            .name(customerArtifact.nameWithoutExtension)
            .extension(customerArtifact.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(customerArtifact.toURI().toASCIIString())
            .build()

        val artifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(listOf(artifact)) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder,0.milliseconds)

        //THEN
        response shouldBeRight Unit
        destinyFolder shouldContainFile customerArtifact.name
        destinyFolder shouldContainNFiles 1

    }

    "It should return an ErrorDownloadingArtifact when there is a problem saving the artifact on disk"{
        //GIVEN
        val onlyReadDestinyFolderPermission = PosixFilePermissions.fromString("r--r--r--")
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder =
            tempFolder("testReports", PosixFilePermissions.asFileAttribute(onlyReadDestinyFolderPermission))

        val executionJob = Job
            .builder()
            .arn(jobArn)
            .name("test_execution")
            .device {
                it.name(deviceName)
                    .arn(deviceArn)
            }
            .build()
        val artifact = Artifact
            .builder()
            .arn("arn:test:artifact")
            .name(customerArtifact.nameWithoutExtension)
            .extension(customerArtifact.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(customerArtifact.toURI().toASCIIString())
            .build()

        val artifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(listOf(artifact)) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder,0.milliseconds)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<ErrorDownloadingArtifact>()
            it.cause.shouldBeInstanceOf<AccessDeniedException>()
        }
        destinyFolder.shouldNotExist()
    }

    "It should not fail if there is no customer artifacts to download"{
        //GIVEN
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")

        val executionJob = Job
            .builder()
            .arn(jobArn)
            .name("test_execution")
            .device {
                it.name(deviceName)
                    .arn(deviceArn)
            }
            .build()

        val artifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(emptyList()) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder,0.milliseconds)

        //THEN
        response shouldBeRight Unit
        destinyFolder shouldNotContainFile customerArtifact.name
        destinyFolder shouldContainNFiles 0
    }

    "It should return a DeviceFarmTractorError when fetching artifacts fails"{
        //GIVEN
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val executionJob = Job
            .builder()
            .arn(jobArn)
            .name("test_execution")
            .device {
                it.name(deviceName)
                    .arn(deviceArn)
            }
            .build()

        val artifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.left(expectedError) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder,0.milliseconds)

        //THEN
        response shouldBeLeft expectedError
        destinyFolder shouldNotContainFile customerArtifact.name
        destinyFolder shouldContainNFiles 0
    }

    "It should download all the reports associated to the test Run"{
        //GIVEN
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val jobs = (1..10)
            .map { job ->
                Job
                    .builder()
                    .arn("arn:test:job:$job")
                    .name("test job $job")
                    .device {
                        it.name("Test device $job")
                            .arn("arn:test:device:$job")
                    }.build()
            }
        val artifact = Artifact
            .builder()
            .arn("arn:test:artifact")
            .name(customerArtifact.nameWithoutExtension)
            .extension(customerArtifact.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(customerArtifact.toURI().toASCIIString())
            .build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(listOf(artifact)) }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(jobs) }
        )
        val reportDirectoryPath = Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

        //WHEN
        DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)

        //THEN
        destinyFolder shouldContainFile reportDirectoryPath.toFile().name
        destinyFolder shouldContainNFiles 1
        destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
        destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles jobs.size
        destinyFolder.resolve(reportDirectoryPath).listDirectoryEntries().forAll {
            it.shouldBeADirectory()
            it shouldContainFile customerArtifact.name
            it shouldContainNFiles 1
        }
    }

    "It should log an error message when downloading a report fails"{
        //GIVEN
        val testReportFile = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val job = Job
            .builder()
            .arn("arn:test:job")
            .name("test job")
            .device {
                it.name(deviceName)
                    .arn("arn:test:device")
            }.build()

        val artifact = Artifact
            .builder()
            .arn("arn:test:artifact")
            .name(testReportFile.nameWithoutExtension)
            .extension(testReportFile.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(testReportFile.toURI().toASCIIString())
            .build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(listOf(artifact)) }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(listOf(job)) }
        )
        val reportDirectoryPath = Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

        testReportFile.setReadable(false)

        //WHEN
        val lastOutput = captureStandardOut {
            DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runHandler,
                downloadArtifactsHandler
            ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)
        }.lineSequence()
            .filter(String::isNotBlank)
            .map(String::trim)
            .last()

        //THEN
        destinyFolder shouldContainFile reportDirectoryPath.fileName.toString()
        destinyFolder shouldContainNFiles 1
        destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
        destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles 0
        lastOutput shouldStartWith "There was an error downloading the report of $deviceName test run."
    }

    "It should download all the reports even if any of them fails"{
        //GIVEN
        val expectedReports = (1..10)
            .map {
                tempfile("test_report_${it}_downloadable", ".zip")
            }
        val reportNotReadable = expectedReports.random()
        if (!reportNotReadable.setReadable(false)) fail("An error happens setting up the test")
        val destinyFolder = tempFolder("testReports")
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val jobs = (1..10)
            .map { job ->
                Job
                    .builder()
                    .arn("arn:test:job:$job")
                    .name("test job $job")
                    .device {
                        it.name("Test device $job")
                            .arn("arn:test:device:$job")
                    }.build()
            }
        val artifacts = expectedReports
            .mapIndexed { index, associatedReport ->
                Artifact
                    .builder()
                    .arn("arn:test:artifact:$index")
                    .name(associatedReport.nameWithoutExtension)
                    .extension(associatedReport.extension)
                    .type(ArtifactType.CUSTOMER_ARTIFACT)
                    .url(associatedReport.toURI().toASCIIString())
                    .build()
            }

        val artifactsProvider = artifacts.iterator()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = {
                synchronized(this) {
                    Either.right(listOf(artifactsProvider.next()))
                }
            }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(jobs) }
        )
        val reportDirectoryPath = Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

        //WHEN
        DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)

        //THEN
        destinyFolder shouldContainFile reportDirectoryPath.fileName.toString()
        destinyFolder shouldContainNFiles 1
        destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
        destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles jobs.size - 1
        destinyFolder
            .resolve(reportDirectoryPath)
            .listDirectoryEntries()
            .flatMap {
                it.listDirectoryEntries()
            }
            .map { it.fileName }
            .shouldContainExactlyInAnyOrder(
                expectedReports
                    .filter { it != reportNotReadable }
                    .map { it.toPath().fileName }
            )
    }

    "It should log an error message when creating the test report directory of an specific device fails"{
        //GIVEN
        val destinyFolder = tempFolder("testReports")
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val job = Job
            .builder()
            .arn("arn:test:job")
            .name("test job")
            .device {
                it.name(deviceName)
                    .arn("arn:test:device")
            }.build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { fail("This should never been called") }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(listOf(job)) }
        )
        val reportDirectoryPath =
            destinyFolder.resolve("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")
        reportDirectoryPath.createDirectory()

        //WHEN
        val lastOutput = captureStandardOut {
            DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runHandler,
                downloadArtifactsHandler
            ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)
        }.lineSequence()
            .filter(String::isNotBlank)
            .map(String::trim)
            .last()

        //THEN
        reportDirectoryPath shouldContainNFiles 0
        lastOutput shouldStartWith "There was a problem creating the folder ${reportDirectoryPath.fileName}"

    }

    "It should log an error message when creating the test reports directory fails"{
        //GIVEN
        val destinyFolder = tempFolder("testReports")
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val job = Job
            .builder()
            .arn("arn:test:job")
            .name("test job")
            .device {
                it.name(deviceName)
                    .arn("arn:test:device")
            }.build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { fail("This should never been called") }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(listOf(job)) }
        )
        val testReportsName = "test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}"
        destinyFolder.toFile().setReadOnly()


        //WHEN
        val lastOutput = captureStandardOut {
            DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runHandler,
                downloadArtifactsHandler
            ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)
        }.lineSequence()
            .filter(String::isNotBlank)
            .map(String::trim)
            .last()

        //THEN
        destinyFolder shouldContainNFiles 0
        lastOutput shouldStartWith "There was a problem creating the folder $testReportsName"

    }

    "It should not try to download the reports when an error happens fetching the associated jobs of the test run"{
        //GIVEN
        val destinyFolder = tempFolder("testReports")
        val error = DeviceFarmTractorGeneralError(RuntimeException("test error"))
        val run = Run
            .builder()
            .name("test run")
            .arn("arn:test:run")
            .build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { fail("This should never been called") }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.left(error) }
        )

        //WHEN
        DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllTestReportsOfTestRun(run, destinyFolder,0.milliseconds)


        //THEN
        destinyFolder shouldContainNFiles 0
    }

})