package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
            listProjectsImpl = { Either.Right(projects) },
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
        response.shouldBeRight() shouldBe expectedProject

    }

    "It should create the project if it is no found"{
        //GIVEN
        val expectedProject = projects.random()
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.Right(emptyList()) },
            createProjectImpl = { Either.Right(expectedProject) }
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
        response.shouldBeRight() shouldBe expectedProject
    }

    "It should return a DeviceFarmTractorError if something happens fetching device farm projects"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("Test exception"))
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.Left(expectedError) },
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
        response.shouldBeLeft() shouldBe expectedError

    }

    "It should return a DeviceFarmTractorError if something happens creating a new project"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("Test exception"))
        val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler(
            listProjectsImpl = { Either.Right(emptyList()) },
            createProjectImpl = { Either.Left(expectedError) }
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
        response.shouldBeLeft() shouldBe expectedError
    }
})