package com.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.*
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmLogging
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmUploadArtifactsHandler
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import software.amazon.awssdk.services.devicefarm.model.DevicePool

class WhenWorkingWithAWSDevicePools : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectHandler = MockedDeviceFarmProjectsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val projectArn = "test_project_arn"
    val devicePools = (1..10)
        .map {
            DevicePool
                .builder()
                .name("test pool $it")
                .build()
        }

    "It should return the first found device pool as default when no name is provided"{
        //GIVEN
        val expectedDevicePool = devicePools.first()
        val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler(
            fetchDevicePoolsImpl = { Either.right(devicePools) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeRight expectedDevicePool
    }

    "It should return the device pool that has the provided name"{
        //GIVEN
        val expectedDevicePool = devicePools.random()
        val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler(
            fetchDevicePoolsImpl = { Either.right(devicePools) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).findOrUseDefaultDevicePool(projectArn, expectedDevicePool.name())

        //THEN
        response shouldBeRight expectedDevicePool
    }

    "It should return a NoRegisteredDevicePoolsError when there is no device pools associated to the project"{
        //GIVEN
        val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler(
            fetchDevicePoolsImpl = { Either.right(emptyList()) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<NoRegisteredDevicePoolsError>()
            it shouldHaveMessage PROJECT_DOES_NOT_HAVE_DEVICE_POOLS.format(projectArn)
        }
    }

    "It should return a DevicePoolNotFoundError when a device pool is not found"{
        //GIVEN
        val devicePoolName = devicePools.first().name()
        val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler(
            fetchDevicePoolsImpl = { Either.right(devicePools.drop(1)) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).findOrUseDefaultDevicePool(projectArn, devicePoolName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DevicePoolNotFoundError>()
            it shouldHaveMessage DEVICE_POOL_NOT_FOUND.format(devicePoolName)
        }
    }

    "It should return a DeviceFarmTractorError if something happens fetching the associated device pools of the project"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))
        val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler(
            fetchDevicePoolsImpl = { Either.left(expectedError) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectHandler,
            devicePoolsHandler,
            uploadArtifactsHandler
        ).findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeLeft expectedError
    }
})