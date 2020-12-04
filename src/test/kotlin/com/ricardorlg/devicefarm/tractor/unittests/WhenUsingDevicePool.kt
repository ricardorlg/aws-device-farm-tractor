package com.ricardorlg.devicefarm.tractor.unittests

import com.ricardorlg.devicefarm.tractor.DeviceFarmExceptions
import com.ricardorlg.devicefarm.tractor.DeviceFarmTractor
import com.ricardorlg.devicefarm.tractor.EMPTY_PROJECT_ARN
import com.ricardorlg.devicefarm.tractor.ERROR_MESSAGE_FETCHING_DEVICE_POOLS
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.DeviceFarmException
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.ListDevicePoolsRequest
import software.amazon.awssdk.services.devicefarm.model.ListDevicePoolsResponse

class WhenUsingDevicePool : StringSpec({
    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val defaultDevicePoolArn =
        "arn:aws:devicefarm:us-west-2:377815266411:devicepool:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val defaultDevicePoolName = "defaultDevicePool"

    "When no project ARN is provided a left should be returned"{
        //GIVEN
        val expectedDevicePool = DevicePool
            .builder()
            .arn(defaultDevicePoolArn)
            .name(defaultDevicePoolName)
            .build()
        val expectedDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(expectedDevicePool)
            .build()
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder()
                    .arn(projectArn)
                    .build()
            )
        } returns expectedDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool("", defaultDevicePoolName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.EmptyProjectArnException>()
            it shouldHaveMessage EMPTY_PROJECT_ARN
        }
        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>()) wasNot called
        }

        confirmVerified(dfClient)
    }

    "When a device pool name is provided, and the devicePool exists then tractor should use it"{
        //GIVEN
        val expectedDevicePool = DevicePool
            .builder()
            .arn(defaultDevicePoolArn)
            .name(defaultDevicePoolName)
            .build()
        val expectedDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(expectedDevicePool)
            .build()
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder().arn(projectArn).build()
            )
        } returns expectedDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(projectArn, defaultDevicePoolName)

        //THEN
        response shouldBeRight expectedDevicePool

        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>())
        }

        confirmVerified(dfClient)
    }

    "Tractor should scan all registered device pools until it finds the expected one"{
        //GIVEN
        val devicePools = (1..10)
            .map {
                DevicePool
                    .builder()
                    .arn("arn:aws:devicefarm:devicepool:$it")
                    .name("devicePool $it")
                    .build()
            }
        val firstDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(devicePools.take(5))
            .nextToken("nextToken")
            .build()
        val lastDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(devicePools.takeLast(5))
            .build()

        val dfClient = mockk<DeviceFarmClient>()

        every {
            dfClient
                .listDevicePools(
                    ListDevicePoolsRequest
                        .builder()
                        .arn(projectArn)
                        .build()
                )
        } returns firstDevicePoolsResponse

        every {
            dfClient
                .listDevicePools(
                    ListDevicePoolsRequest
                        .builder()
                        .arn(projectArn)
                        .nextToken("nextToken")
                        .build()
                )
        } returns lastDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(
            projectArn = projectArn,
            devicePoolName = devicePools.last().name()
        )

        //THEN
        response shouldBeRight devicePools.last()

        verify {
            dfClient.listDevicePools(ListDevicePoolsRequest.builder().arn(projectArn).build())
            dfClient.listDevicePools(ListDevicePoolsRequest.builder().arn(projectArn).nextToken("nextToken").build())
        }

        confirmVerified(dfClient)
    }

    "When no device pool name is provided, tractor will use the first pool found"{
        //GIVEN
        val devicePools = (1..10)
            .map {
                DevicePool
                    .builder()
                    .arn("arn:aws:devicefarm:devicepool:$it")
                    .name("devicePool $it")
                    .build()
            }
        val expectedDevicePool = devicePools[0]
        val expectedDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(devicePools)
            .build()
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder()
                    .arn(projectArn)
                    .build()
            )
        } returns expectedDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(projectArn = projectArn)

        //THEN
        response shouldBeRight expectedDevicePool

        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>())
        }

        confirmVerified(dfClient)
    }

    "When a device pool is not found a left should be returned"{
        //GIVEN
        val notRegisteredDevicePoolName = "Not registered device pool"
        val expectedDevicePool = DevicePool
            .builder()
            .arn(defaultDevicePoolArn)
            .name(defaultDevicePoolName)
            .build()
        val expectedDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(expectedDevicePool)
            .build()
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder().arn(projectArn).build()
            )
        } returns expectedDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(projectArn, notRegisteredDevicePoolName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.DevicePoolNotFoundException>()
            it shouldHaveMessage "El devicePool $notRegisteredDevicePoolName no esta asociado al proyecto $projectArn"
        }

        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>())
        }

        confirmVerified(dfClient)
    }

    "When there is no device pools registered and no devicePoolName is provided a left should be returned"{
        //GIVEN
        val expectedDevicePoolsResponse = ListDevicePoolsResponse
            .builder()
            .devicePools(emptyList<DevicePool>())
            .build()
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder().arn(projectArn).build()
            )
        } returns expectedDevicePoolsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(projectArn)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.ProjectDoesNotHaveDevicePools>()
            it shouldHaveMessage "El proyecto $projectArn no tiene device pools asociados"
        }

        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>())
        }

        confirmVerified(dfClient)
    }

    "If there is an exception fetching de device pools tractor should map it and return a left"{
        //GIVEN
        val expectedException = DeviceFarmException.create("Error", Throwable())
        val dfClient = mockk<DeviceFarmClient>()
        every {
            dfClient.listDevicePools(
                ListDevicePoolsRequest
                    .builder().arn(projectArn).build()
            )
        } throws expectedException

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrUseDefaultDevicePool(projectArn, defaultDevicePoolName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.ErrorFetchingDevicePools>()
            it shouldHaveMessage ERROR_MESSAGE_FETCHING_DEVICE_POOLS
            it.cause shouldBe expectedException
        }

        verify {
            dfClient.listDevicePools(any<ListDevicePoolsRequest>())
        }

        confirmVerified(dfClient)
    }
})