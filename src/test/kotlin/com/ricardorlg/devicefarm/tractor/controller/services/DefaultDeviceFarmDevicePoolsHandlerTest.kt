package com.ricardorlg.devicefarm.tractor.controller.services

import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmDevicePoolsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorErrorIllegalArgumentException
import com.ricardorlg.devicefarm.tractor.model.ErrorFetchingDevicePools
import com.ricardorlg.devicefarm.tractor.model.EMPTY_PROJECT_ARN
import com.ricardorlg.devicefarm.tractor.model.ERROR_MESSAGE_FETCHING_DEVICE_POOLS
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.DevicePool
import software.amazon.awssdk.services.devicefarm.model.ListDevicePoolsRequest

class DefaultDeviceFarmDevicePoolsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"

    "When fetching device pools from AWS device farm, it should return the associated device pools of a given project as a right" {
        //GIVEN
        val expectedDevicePools = (1..10).map {
            DevicePool
                .builder()
                .name("test_device_pool_$it")
                .arn("arn:aws:devicefarm:us-west-2:device_pool_$it")
                .build()
        }
        every {
            dfClient.listDevicePoolsPaginator(any<ListDevicePoolsRequest>()).devicePools()
        } returns SdkIterable { expectedDevicePools.toMutableList().listIterator() }

        //WHEN
        val response = DefaultDeviceFarmDevicePoolsHandler(dfClient).fetchDevicePools(projectArn)

        //THEN
        response shouldBeRight expectedDevicePools
        verify {
            dfClient.listDevicePoolsPaginator(ListDevicePoolsRequest.builder().arn(projectArn).build())
        }
        confirmVerified(dfClient)
    }
    "When fetching device pools from AWS device farm, project ARN is mandatory, a left should be returned if it is not provided" {
        //WHEN
        val response = DefaultDeviceFarmDevicePoolsHandler(dfClient).fetchDevicePools("")

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_PROJECT_ARN
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
        verify {
            dfClient.listDevicePoolsPaginator(any<ListDevicePoolsRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }
    "When fetching device pools from AWS device farm, any error should be returned as a left" {
        //GIVEN
        val expectedError = RuntimeException("Test error")
        every {
            dfClient.listDevicePoolsPaginator(any<ListDevicePoolsRequest>())
        } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmDevicePoolsHandler(dfClient).fetchDevicePools(projectArn)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<ErrorFetchingDevicePools>()
            it shouldHaveMessage ERROR_MESSAGE_FETCHING_DEVICE_POOLS
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.listDevicePoolsPaginator(ListDevicePoolsRequest.builder().arn(projectArn).build())
        }
        confirmVerified(dfClient)
    }

    afterTest {
        clearMocks(dfClient)
    }
})
