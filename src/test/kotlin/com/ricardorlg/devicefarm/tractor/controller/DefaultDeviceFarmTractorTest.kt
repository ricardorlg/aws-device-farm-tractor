package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.right
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import mu.KLogger
import mu.KotlinLogging
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmTractorTest : StringSpec({
    val projectArn = "test_project_arn"
    val deviceFarmProjectsHandler = mockk<IDeviceFarmProjectsHandler>()
    val deviceFarmDevicePoolsHandler = mockk<IDeviceFarmDevicePoolsHandler>()
    val mockLogger = mockk<KLogger>(relaxed = true)
    mockkObject(KotlinLogging)

    beforeTest {
        every { KotlinLogging.logger(any<String>()) } returns mockLogger
    }

    "When finding or creating a Project, it should return the first project with the provided name" {
        //GIVEN
        val projects = (1..10).map {
            Project
                .builder()
                .arn("arn:aws:devicefarm:us-west-2:project$it")
                .name("test_project_$it")
                .build()
        }
        val expectedProject = projects.random()
        coEvery { deviceFarmProjectsHandler.listProjects() } returns projects.right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrCreateProject(expectedProject.name())

        //THEN
        response shouldBeRight expectedProject

        coVerify {
            deviceFarmProjectsHandler.listProjects()
            deviceFarmProjectsHandler.createProject(any()) wasNot called
        }
        confirmVerified(deviceFarmProjectsHandler)

    }
    "When fetching Projects, any error should be returned as a DeviceFarmTractorError left" {
        //GIVEN
        val expectedResponse = mockk<DeviceFarmTractorError>()
        coEvery { deviceFarmProjectsHandler.listProjects() } returns Either.left(expectedResponse)

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrCreateProject("test project name")

        //THEN
        response shouldBeLeft expectedResponse

        coVerify {
            deviceFarmProjectsHandler.listProjects()
            deviceFarmProjectsHandler.createProject(any()) wasNot called
        }
        confirmVerified(deviceFarmProjectsHandler)
    }
    "When finding or creating a Project, it should create a project when no project with given name is found" {
        //GIVEN
        val projects = (1..10).map {
            Project
                .builder()
                .arn("arn:aws:devicefarm:us-west-2:project$it")
                .name("test_project_$it")
                .build()
        }
        val expectedProject = projects.last()
        coEvery { deviceFarmProjectsHandler.listProjects() } returns projects.dropLast(1).right()
        coEvery { deviceFarmProjectsHandler.createProject(any()) } returns expectedProject.right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrCreateProject(expectedProject.name())

        //THEN
        response shouldBeRight expectedProject

        coVerify {
            deviceFarmProjectsHandler.listProjects()
            deviceFarmProjectsHandler.createProject(expectedProject.name())
        }
        confirmVerified(deviceFarmProjectsHandler)
    }
    "When creating a project, any error should be returned as a DeviceFarmTractorError left" {
        //GIVEN
        val expectedResponse = mockk<DeviceFarmTractorError>()
        val projects = (1..10).map {
            Project
                .builder()
                .arn("arn:aws:devicefarm:us-west-2:project$it")
                .name("test_project_$it")
                .build()
        }
        val expectedProject = projects.last()
        coEvery { deviceFarmProjectsHandler.listProjects() } returns projects.dropLast(1).right()
        coEvery { deviceFarmProjectsHandler.createProject(any()) } returns Either.left(expectedResponse)

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrCreateProject(expectedProject.name())

        //THEN
        response shouldBeLeft expectedResponse

        coVerify {
            deviceFarmProjectsHandler.listProjects()
            deviceFarmProjectsHandler.createProject(expectedProject.name())
        }
        confirmVerified(deviceFarmProjectsHandler)
    }
    "When using device pools, it should use a default device pool when no pool name is provided" {
        //GIVEN
        val devicePools = (1..10)
            .map {
                DevicePool
                    .builder()
                    .name("test pool $it")
                    .build()
            }
        val expectedDevicePool = devicePools.first()
        coEvery { deviceFarmDevicePoolsHandler.fetchDevicePools(any()) } returns devicePools.right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeRight expectedDevicePool
        coVerify {
            deviceFarmDevicePoolsHandler.fetchDevicePools(projectArn)
        }
        confirmVerified(deviceFarmDevicePoolsHandler)
    }
    "When using device pools, it should return the device pool with the provided name" {
        //GIVEN
        val devicePools = (1..10)
            .map {
                DevicePool
                    .builder()
                    .name("test pool $it")
                    .build()
            }
        val expectedDevicePool = devicePools.random()
        coEvery { deviceFarmDevicePoolsHandler.fetchDevicePools(any()) } returns devicePools.right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrUseDefaultDevicePool(projectArn, expectedDevicePool.name())

        //THEN
        response shouldBeRight expectedDevicePool
        coVerify {
            deviceFarmDevicePoolsHandler.fetchDevicePools(projectArn)
        }
        confirmVerified(deviceFarmDevicePoolsHandler)
    }
    "When using device pools, it should return DeviceFarmProjectDoesNotHaveDevicePools left when there is no device pools associated to the project" {
        //GIVEN
        coEvery { deviceFarmDevicePoolsHandler.fetchDevicePools(any()) } returns emptyList<DevicePool>().right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmProjectDoesNotHaveDevicePools>()
            it shouldHaveMessage PROJECT_DOES_NOT_HAVE_DEVICE_POOLS.format(projectArn)
        }
        coVerify {
            deviceFarmDevicePoolsHandler.fetchDevicePools(projectArn)
        }
        confirmVerified(deviceFarmDevicePoolsHandler)
    }
    "When using device pools, it should return DeviceFarmDevicePoolNotFound left when there is no device pool with the provided name" {
        //GIVEN
        val devicePools = (1..10)
            .map {
                DevicePool
                    .builder()
                    .name("test pool $it")
                    .build()
            }
        val notExistedDevicePool = devicePools.last()
        coEvery { deviceFarmDevicePoolsHandler.fetchDevicePools(any()) } returns devicePools.dropLast(1).right()

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrUseDefaultDevicePool(projectArn, notExistedDevicePool.name())

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmDevicePoolNotFound>()
            it shouldHaveMessage DEVICE_POOL_NOT_FOUND.format(notExistedDevicePool.name())
        }
        coVerify {
            deviceFarmDevicePoolsHandler.fetchDevicePools(projectArn)
        }
        confirmVerified(deviceFarmDevicePoolsHandler)
    }
    "When using device pools, any error fetching device pools should be returned as a DeviceFarmTractorError left" {
        val expectedResponse = mockk<DeviceFarmTractorError>()
        coEvery { deviceFarmDevicePoolsHandler.fetchDevicePools(any()) } returns Either.left(
            expectedResponse
        )

        //WHEN
        val response = DefaultDeviceFarmTractor(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
            .findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeLeft expectedResponse
        coVerify {
            deviceFarmDevicePoolsHandler.fetchDevicePools(projectArn)
        }
        confirmVerified(deviceFarmDevicePoolsHandler)

    }

    afterTest {
        clearMocks(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
        unmockkObject(KotlinLogging)
    }
})
