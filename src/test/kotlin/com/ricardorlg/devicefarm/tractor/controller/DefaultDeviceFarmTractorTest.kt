package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import arrow.core.right
import com.ricardorlg.devicefarm.tractor.model.DEVICE_POOL_NOT_FOUND
import com.ricardorlg.devicefarm.tractor.model.PROJECT_DOES_NOT_HAVE_DEVICE_POOLS
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmDevicePoolNotFound
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmProjectDoesNotHaveDevicePools
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import mu.KLogger
import mu.KotlinLogging
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmTractorTest : DescribeSpec({
    val projectArn = "test_project_arn"
    val deviceFarmProjectsHandler = mockk<IDeviceFarmProjectsHandler>()
    val deviceFarmDevicePoolsHandler = mockk<IDeviceFarmDevicePoolsHandler>()
    val mockLogger = mockk<KLogger>(relaxed = true)
    mockkObject(KotlinLogging)

    beforeTest {
        every { KotlinLogging.logger(any<String>()) } returns mockLogger
    }

    describe("When finding or creating a Project") {
        describe("It should return the project when it exists") {
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
        describe("Any error should be returned as a DeviceFarmTractorError left") {
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
        describe("It should create a project when is not found") {
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
        describe("Any error creating the project should be returned as a DeviceFarmTractorError left") {
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
    }

    describe("When using device pools") {
        describe("It should use a default device pools when no pool name is provided") {
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
        describe("It should return the device pool with the provided device pool name") {
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
        describe("It should return DeviceFarmProjectDoesNotHaveDevicePools left when there is no device pools associated to the project") {
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
        describe("It should return DeviceFarmDevicePoolNotFound left when there is no device pool with the provided name") {
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
        describe("Any error fetching device pools should be returned as a DeviceFarmTractorError left") {
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
    }

    afterTest {
        clearMocks(deviceFarmProjectsHandler, deviceFarmDevicePoolsHandler)
        unmockkObject(KotlinLogging)
    }
})
