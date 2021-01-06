package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import com.ricardorlg.devicefarm.tractor.model.ErrorDownloadingArtifact
import com.ricardorlg.devicefarm.tractor.stubs.*
import com.ricardorlg.devicefarm.tractor.tempFolder
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldContainNFiles
import io.kotest.matchers.paths.shouldNotContainFile
import io.kotest.matchers.types.shouldBeInstanceOf
import software.amazon.awssdk.services.devicefarm.model.Artifact
import software.amazon.awssdk.services.devicefarm.model.ArtifactType
import software.amazon.awssdk.services.devicefarm.model.Job
import java.nio.file.AccessDeniedException
import java.nio.file.attribute.PosixFilePermissions

class WhenDownloadingCustomerArtifacts : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler()
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
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder)

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
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<ErrorDownloadingArtifact>()
            it.cause.shouldBeInstanceOf<AccessDeniedException>()
        }
        destinyFolder shouldNotContainFile customerArtifact.name
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
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder)

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
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).downloadCustomerArtifacts(executionJob, destinyFolder)

        //THEN
        response shouldBeLeft expectedError
        destinyFolder shouldNotContainFile customerArtifact.name
        destinyFolder shouldContainNFiles 0
    }
})