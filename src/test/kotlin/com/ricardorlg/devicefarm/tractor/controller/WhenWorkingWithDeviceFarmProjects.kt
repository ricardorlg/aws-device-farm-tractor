package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import com.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import software.amazon.awssdk.services.devicefarm.model.Project

class WhenWorkingWithDeviceFarmProjects : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val runScheduleHandler = MockedDeviceFarmRunsHandler()
    val artifactsHandler = MockedDeviceFarmArtifactsHandler()

    val projects = (1..10).map {
        Project
            .builder()
            .arn("arn:aws:device_farm:us-west-2:project$it")
            .name("test_project_$it")
            .build()
    }

    "It should return a project if its found"{
        //GIVEN
        val expectedProject = projects.random()
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.right(projects) },
            createProjectImpl = { fail("the impossible happens") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).findOrCreateProject(expectedProject.name())

        //THEN
        response shouldBeRight expectedProject

    }

    "It should create the project if it is no found"{
        //GIVEN
        val expectedProject = projects.random()
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.right(emptyList()) },
            createProjectImpl = { Either.right(expectedProject) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).findOrCreateProject(expectedProject.name())

        //THEN
        response shouldBeRight expectedProject
    }

    "It should return a DeviceFarmTractorError if something happens fetching device farm projects"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("Test exception"))
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.left(expectedError) },
            createProjectImpl = { fail("the impossible happens") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).findOrCreateProject("Non Important")

        //THEN
        response shouldBeLeft expectedError

    }

    "It should return a DeviceFarmTractorError if something happens creating a new project"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("Test exception"))
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.right(emptyList()) },
            createProjectImpl = { Either.left(expectedError) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).findOrCreateProject("Non Important")

        //THEN
        response shouldBeLeft expectedError

    }
})