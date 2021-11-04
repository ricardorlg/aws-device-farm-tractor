package io.github.ricardorlg.devicefarm.tractor.controller.services

import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmProjectsHandler
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.CreateProjectRequest
import software.amazon.awssdk.services.devicefarm.model.CreateProjectResponse
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmProjectsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val projectArn = "arn:aws:device_farm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val projectName = "test project"

    "When fetching projects from AWS, it should return the project as a right" {
        //GIVEN
        val expectedProjects = (1..10).map {
            Project
                .builder()
                .arn("arn:aws:device_farm:us-west-2:project$it")
                .name("test_project_$it")
                .build()
        }

        every { dfClient.listProjectsPaginator().projects() } returns SdkIterable {
            expectedProjects.toMutableList().listIterator()
        }

        //WHEN
        val response = DefaultDeviceFarmProjectsHandler(dfClient).listProjects()

        //THEN
        response.shouldBeRight().shouldBe(expectedProjects)

        verify {
            dfClient.listProjectsPaginator().projects()
        }
        confirmVerified(dfClient)
    }
    "When fetching projects from AWS, any error should be returned as a left" {
        //GIVEN
        val expectedError = RuntimeException("Test error")

        every { dfClient.listProjectsPaginator() } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmProjectsHandler(dfClient).listProjects()

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorFetchingProjects>()
            it shouldHaveMessage ERROR_MESSAGE_FETCHING_AWS_PROJECTS
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.listProjectsPaginator()
        }
        confirmVerified(dfClient)
    }
    "When creating a project in AWS device farm, it should return the created project as a right" {
        //GIVEN
        val expectedProject = Project
            .builder()
            .arn(projectArn)
            .name(projectName)
            .build()

        every {
            dfClient.createProject(any<CreateProjectRequest>())
        } returns CreateProjectResponse.builder().project(expectedProject).build()

        //WHEN
        val response = DefaultDeviceFarmProjectsHandler(dfClient).createProject(projectName)

        //THEN
        response.shouldBeRight() shouldBe expectedProject
        verify {
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
        }
        confirmVerified(dfClient)
    }
    "When creating a project in AWS device farm, any error should be returned as a left" {
        //GIVEN
        val expectedError = RuntimeException("Test error")

        every { dfClient.createProject(any<CreateProjectRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmProjectsHandler(dfClient).createProject(projectName)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorCreatingProject>()
            it shouldHaveMessage "$ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT $projectName"
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
        }
        confirmVerified(dfClient)
    }
    "When creating a project in AWS device farm, if project name is empty an error should be returned as a left"{
        //WHEN
        val response = DefaultDeviceFarmProjectsHandler(dfClient).createProject("   ")

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_PROJECT_NAME
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
        verify {
            dfClient.createProject(any<CreateProjectRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }


    afterTest {
        clearMocks(dfClient)
    }
})
