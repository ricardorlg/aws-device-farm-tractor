package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.github.ricardorlg.devicefarm.tractor.tempFolder
import io.github.ricardorlg.devicefarm.tractor.utils.prettyName
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.captureStandardOut
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.paths.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
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

class WhenDownloadingAwsDeviceFarmArtifacts : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectsHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val runScheduleHandler = MockedDeviceFarmRunsHandler()
    val commonArtifactsHandler = MockedDeviceFarmArtifactsHandler()
    val deviceName = "nexus 3"
    val artifactType = ArtifactType.CUSTOMER_ARTIFACT
    val downloadableTypes = listOf(ArtifactType.CUSTOMER_ARTIFACT, ArtifactType.VIDEO)

    val validTypes =
        ArtifactType.values().filter { it != ArtifactType.UNKNOWN && it != ArtifactType.UNKNOWN_TO_SDK_VERSION }

    "It should use a pretty name when the artifact type is video or customer artifact"{
        checkAll(Exhaustive.collection(ArtifactType.values().asList())) { type ->
            when (type) {
                ArtifactType.VIDEO -> type.prettyName() shouldBe "Recorded video"
                ArtifactType.CUSTOMER_ARTIFACT -> type.prettyName() shouldBe "Test reports"
                else -> type.prettyName() shouldBe type.name
            }
        }
    }

    "It should return a DeviceFarmTractorErrorIllegalArgumentException if the searched artifact type is not supported"{
        checkAll(Exhaustive.collection(INVALID_ARTIFACT_TYPES)) { type ->
            //GIVEN
            val customerArtifact = tempfile("test_downloadable_${type.name.toLowerCase()}_", ".zip")
            val destinyFolder = tempFolder("testReports")
            val artifact = Artifact
                .builder()
                .arn("arn:test:artifact")
                .name(customerArtifact.nameWithoutExtension)
                .extension(customerArtifact.extension)
                .type(artifactType)
                .url(customerArtifact.toURI().toASCIIString())
                .build()

            //WHEN
            val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                logger,
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runScheduleHandler,
                commonArtifactsHandler
            ).downloadAWSDeviceFarmArtifacts(
                artifacts = listOf(artifact),
                deviceName = deviceName,
                path = destinyFolder,
                artifactType = type
            )

            //THEN
            response shouldBeLeft {
                it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
                it shouldHaveMessage "$type is not supported"
            }
            destinyFolder shouldNotContainFile customerArtifact.name
            destinyFolder shouldContainNFiles 0
        }
    }

    "It should download an AWS Device farm artifact of a job execution depending of its type"{
        checkAll(Exhaustive.collection(validTypes)) { type ->
            //GIVEN
            val customerArtifact = tempfile("test_downloadable_${type.name.toLowerCase()}_", ".zip")
            val destinyFolder = tempFolder("testReports")
            val artifact = Artifact
                .builder()
                .arn("arn:test:artifact")
                .name(customerArtifact.nameWithoutExtension)
                .extension(customerArtifact.extension)
                .type(type)
                .url(customerArtifact.toURI().toASCIIString())
                .build()

            //WHEN
            val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                logger,
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runScheduleHandler,
                commonArtifactsHandler
            ).downloadAWSDeviceFarmArtifacts(
                artifacts = listOf(artifact),
                deviceName = deviceName,
                path = destinyFolder,
                artifactType = type
            )

            //THEN
            response shouldBeRight Unit
            destinyFolder shouldContainFile customerArtifact.name
            destinyFolder shouldContainNFiles 1
        }
    }

    "It should log a message when the searched artifact type is not found in the job artifacts"{
        checkAll(Exhaustive.collection(validTypes)) { type ->
            //GIVEN
            val customerArtifact = tempfile("test_downloadable_${type.name.toLowerCase()}_", ".zip")
            val destinyFolder = tempFolder("testReports")
            val artifact = Artifact
                .builder()
                .arn("arn:test:artifact")
                .name(customerArtifact.nameWithoutExtension)
                .extension(customerArtifact.extension)
                .type(ArtifactType.UNKNOWN)
                .url(customerArtifact.toURI().toASCIIString())
                .build()

            val expectedLoggedMessage = JOB_DOES_NOT_HAVE_ARTIFACT_OF_TYPE.format(
                type.name,
                deviceName
            )

            //WHEN
            val loggedMessage = captureStandardOut {
                io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                    MockedDeviceFarmLogging(true),
                    deviceFarmProjectsHandler,
                    devicePoolsHandler,
                    uploadArtifactsHandler,
                    runScheduleHandler,
                    commonArtifactsHandler
                ).downloadAWSDeviceFarmArtifacts(
                    artifacts = listOf(artifact),
                    deviceName = deviceName,
                    path = destinyFolder,
                    artifactType = type
                ).shouldBeRight(Unit)
            }.lines()
                .filter(String::isNotBlank)
                .map(String::trim)
                .last()

            //THEN
            loggedMessage shouldBe expectedLoggedMessage
            destinyFolder shouldNotContainFile customerArtifact.name
            destinyFolder shouldContainNFiles 0
        }
    }

    "It should return an ErrorDownloadingArtifact when there is a problem saving the artifact on disk"{
        //GIVEN
        val onlyReadDestinyFolderPermission = PosixFilePermissions.fromString("r--r--r--")
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder =
            tempFolder("testReports", PosixFilePermissions.asFileAttribute(onlyReadDestinyFolderPermission))

        val artifact = Artifact
            .builder()
            .arn("arn:test:artifact")
            .name(customerArtifact.nameWithoutExtension)
            .extension(customerArtifact.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(customerArtifact.toURI().toASCIIString())
            .build()

        //WHEN
        val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            commonArtifactsHandler
        ).downloadAWSDeviceFarmArtifacts(
            artifacts = listOf(artifact),
            deviceName = deviceName,
            path = destinyFolder,
            artifactType = artifactType
        )

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<ErrorDownloadingArtifact>()
            it.cause.shouldBeInstanceOf<AccessDeniedException>()
        }
    }

    "It should not fail if there is no artifacts to download"{
        //GIVEN
        val customerArtifact = tempfile("test_downloadable", ".zip")
        val destinyFolder = tempFolder("testReports")

        //WHEN
        val response = io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            commonArtifactsHandler
        ).downloadAWSDeviceFarmArtifacts(
            artifacts = emptyList(),
            deviceName = deviceName,
            path = destinyFolder,
            artifactType = artifactType
        )

        //THEN
        response shouldBeRight Unit
        destinyFolder shouldNotContainFile customerArtifact.name
        destinyFolder shouldContainNFiles 0
    }

    "It should download all the test reports and recorded videos associated to the test Run"{
        //GIVEN
        val customerArtifactFile = tempfile("test_downloadable", ".zip")
        val recordedVideoFile = tempfile("test_video", ".mp4")
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
        val customerArtifact = Artifact
            .builder()
            .arn("arn:test:customer_artifact")
            .name(customerArtifactFile.nameWithoutExtension)
            .extension(customerArtifactFile.extension)
            .type(ArtifactType.CUSTOMER_ARTIFACT)
            .url(customerArtifactFile.toURI().toASCIIString())
            .build()

        val recordedVideoArtifact = Artifact
            .builder()
            .arn("arn:test:video_artifact")
            .name(recordedVideoFile.nameWithoutExtension)
            .extension(recordedVideoFile.extension)
            .type(ArtifactType.VIDEO)
            .url(recordedVideoFile.toURI().toASCIIString())
            .build()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = { Either.right(listOf(customerArtifact, recordedVideoArtifact)) }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(jobs) }
        )
        val reportDirectoryPath = Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

        //WHEN
        io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)

        //THEN
        destinyFolder shouldContainFile reportDirectoryPath.toFile().name
        destinyFolder shouldContainNFiles 1
        destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
        destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles jobs.size
        destinyFolder.resolve(reportDirectoryPath).listDirectoryEntries().forAll {
            it.shouldBeADirectory()
            it shouldContainFile customerArtifactFile.name
            it shouldContainFile recordedVideoFile.name
            it shouldContainNFiles 2
        }
    }

    "It should log an error message when downloading a recorded video or test report fails"{
        checkAll(Exhaustive.collection(downloadableTypes)) { type ->
            //GIVEN
            val testFile = tempfile("test_downloadable_${type.name.toLowerCase()}", ".zip")
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
                .name(testFile.nameWithoutExtension)
                .extension(testFile.extension)
                .type(type)
                .url(testFile.toURI().toASCIIString())
                .build()

            val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
                getArtifactsImpl = { Either.right(listOf(artifact)) }
            )
            val runHandler = MockedDeviceFarmRunsHandler(
                getAssociatedJobsImpl = { Either.right(listOf(job)) }
            )
            val reportDirectoryPath =
                Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

            testFile.setReadable(false)

            //WHEN
            val loggedMessages = captureStandardOut {
                io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                    MockedDeviceFarmLogging(true),
                    deviceFarmProjectsHandler,
                    devicePoolsHandler,
                    uploadArtifactsHandler,
                    runHandler,
                    downloadArtifactsHandler
                ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)
            }.lineSequence()
                .filter(String::isNotBlank)
                .map(String::trim)

            //THEN
            destinyFolder shouldContainNFiles 1
            destinyFolder shouldContainFile reportDirectoryPath.fileName.toString()
            destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
            destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles 0
            withClue("The logged messages should contain an error message related to the error downloading the artifact $type") {
                loggedMessages.any {
                    it.startsWith(
                        "There was an error downloading the ${
                            type.prettyName()
                        } of $deviceName test run."
                    )
                }
            }
        }
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
        io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)

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

    "It should download all the recorded videos even if any of them fails"{
        //GIVEN
        val expectedRecordedVideos = (1..10)
            .map {
                tempfile("recorde_video_${it}_downloadable", ".mp4")
            }
        val recordedVideoNotReadable = expectedRecordedVideos.random()
        if (!recordedVideoNotReadable.setReadable(false)) fail("An error happens setting up the test")
        val destinyFolder = tempFolder("testResults")
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
        val artifacts = expectedRecordedVideos
            .mapIndexed { index, associatedVideo ->
                Artifact
                    .builder()
                    .arn("arn:test:artifact:$index")
                    .name(associatedVideo.nameWithoutExtension)
                    .extension(associatedVideo.extension)
                    .type(ArtifactType.VIDEO)
                    .url(associatedVideo.toURI().toASCIIString())
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
        io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)

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
                expectedRecordedVideos
                    .filter { it != recordedVideoNotReadable }
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
        val reportDirectoryPath = destinyFolder
            .resolve("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")
            .createDirectory()

        //WHEN
        val lastOutput = captureStandardOut {
            io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runHandler,
                downloadArtifactsHandler
            ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)
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
            io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
                MockedDeviceFarmLogging(true),
                deviceFarmProjectsHandler,
                devicePoolsHandler,
                uploadArtifactsHandler,
                runHandler,
                downloadArtifactsHandler
            ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)
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
        io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)


        //THEN
        destinyFolder shouldContainNFiles 0
    }

    "It should download the recorded videos and test reports even if any of them fails"{
        //GIVEN
        val expectedReports = (1..10)
            .map {
                tempfile("test_report_${it}_downloadable", ".zip")
            }
        val expectedRecordedVideos = (1..10)
            .map {
                tempfile("test_video_${it}_downloadable", ".mp4")
            }
        val reportNotReadable = expectedReports.random()
        val videoNotReadable = expectedRecordedVideos.random()

        if (!reportNotReadable.setReadable(false)) fail("An error happens setting up the test")
        if (!videoNotReadable.setReadable(false)) fail("An error happens setting up the test")

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
        val customerArtifacts = expectedReports
            .mapIndexed { index, associatedReport ->
                Artifact
                    .builder()
                    .arn("arn:test:customer_artifact:$index")
                    .name(associatedReport.nameWithoutExtension)
                    .extension(associatedReport.extension)
                    .type(ArtifactType.CUSTOMER_ARTIFACT)
                    .url(associatedReport.toURI().toASCIIString())
                    .build()
            }

        val videoArtifacts = expectedRecordedVideos
            .mapIndexed { index, associatedVideo ->
                Artifact
                    .builder()
                    .arn("arn:test:video_artifact:$index")
                    .name(associatedVideo.nameWithoutExtension)
                    .extension(associatedVideo.extension)
                    .type(ArtifactType.VIDEO)
                    .url(associatedVideo.toURI().toASCIIString())
                    .build()
            }

        val customerArtifactsProvider = customerArtifacts.iterator()
        val videoArtifactsProvider = videoArtifacts.iterator()

        val downloadArtifactsHandler = MockedDeviceFarmArtifactsHandler(
            getArtifactsImpl = {
                synchronized(this) {
                    Either.right(listOf(customerArtifactsProvider.next(), videoArtifactsProvider.next()))
                }
            }
        )
        val runHandler = MockedDeviceFarmRunsHandler(
            getAssociatedJobsImpl = { Either.right(jobs) }
        )
        val reportDirectoryPath = Paths.get("test_reports_${run.name().toLowerCase().replace("\\s".toRegex(), "_")}")

        val expectedFiles = expectedReports
            .filter { it != reportNotReadable }
            .map { it.toPath().fileName } + expectedRecordedVideos
            .filter { it != videoNotReadable }
            .map { it.toPath().fileName }

        val expectedFilesSize =
            if (expectedReports.indexOf(reportNotReadable) == expectedRecordedVideos.indexOf(videoNotReadable)) jobs.size - 1 else jobs.size

        //WHEN
        io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runHandler,
            downloadArtifactsHandler
        ).downloadAllEvidencesOfTestRun(run, destinyFolder, 0.milliseconds)

        //THEN
        destinyFolder shouldContainFile reportDirectoryPath.fileName.toString()
        destinyFolder shouldContainNFiles 1
        destinyFolder.resolve(reportDirectoryPath).shouldBeADirectory()
        destinyFolder.resolve(reportDirectoryPath) shouldContainNFiles expectedFilesSize
        destinyFolder
            .resolve(reportDirectoryPath)
            .listDirectoryEntries()
            .flatMap {
                it.listDirectoryEntries()
            }
            .map { it.fileName }
            .shouldContainExactlyInAnyOrder(expectedFiles)
    }

})