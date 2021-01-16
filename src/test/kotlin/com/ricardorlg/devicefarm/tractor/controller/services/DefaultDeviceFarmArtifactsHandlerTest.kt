package com.ricardorlg.devicefarm.tractor.controller.services

import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmArtifactsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorErrorIllegalArgumentException
import com.ricardorlg.devicefarm.tractor.model.EMPTY_RUN_ARN
import com.ricardorlg.devicefarm.tractor.model.ERROR_FETCHING_ARTIFACTS
import com.ricardorlg.devicefarm.tractor.model.ErrorFetchingArtifacts
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.Artifact
import software.amazon.awssdk.services.devicefarm.model.ArtifactCategory
import software.amazon.awssdk.services.devicefarm.model.ArtifactType
import software.amazon.awssdk.services.devicefarm.model.ListArtifactsRequest

class DefaultDeviceFarmArtifactsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val runArn = "arn:test_run"

    "When fetching the artifacts of a test execution it should return them as a right" {
        //GIVEN
        val expectedArtifacts = (1..10)
            .map {
                Artifact
                    .builder()
                    .arn("arn:artifact:test:$it")
                    .type(ArtifactType.values().random())
                    .name("test_artifact_$it")
                    .build()
            }
        every {
            dfClient.listArtifactsPaginator(any<ListArtifactsRequest>()).artifacts()
        } returns SdkIterable { expectedArtifacts.toMutableList().iterator() }

        //WHEN
        val response = DefaultDeviceFarmArtifactsHandler(dfClient).getArtifacts(runArn)

        //THEN
        response shouldBeRight expectedArtifacts

        verify {
            dfClient.listArtifactsPaginator(ListArtifactsRequest.builder().type(ArtifactCategory.FILE).arn(runArn).build())
        }
        confirmVerified(dfClient)
    }

    "When fetching the artifacts of a test execution any error should be returned as a DeviceFarmTractorError left"{
        //GIVEN
        val expectedError = RuntimeException("test error")
        every { dfClient.listArtifactsPaginator(any<ListArtifactsRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmArtifactsHandler(dfClient).getArtifacts(runArn)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<ErrorFetchingArtifacts>()
            it shouldHaveMessage ERROR_FETCHING_ARTIFACTS.format(runArn)
            it.cause shouldBe expectedError
        }

        verify {
            dfClient.listArtifactsPaginator(ListArtifactsRequest.builder().type(ArtifactCategory.FILE).arn(runArn).build())
        }
        confirmVerified(dfClient)
    }

    "When fetching the artifacts of a test execution if no run arn is provided a DeviceFarmTractorErrorIllegalArgumentException should be returned as a left"{
        //WHEN
        val response = DefaultDeviceFarmArtifactsHandler(dfClient).getArtifacts("")

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_RUN_ARN
        }

        verify {
            dfClient.listArtifactsPaginator(any<ListArtifactsRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }

    afterTest {
        clearMocks(dfClient)
    }

})
