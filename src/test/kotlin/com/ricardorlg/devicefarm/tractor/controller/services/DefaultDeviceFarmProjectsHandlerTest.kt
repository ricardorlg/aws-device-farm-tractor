package com.ricardorlg.devicefarm.tractor.controller.services

import com.ricardorlg.devicefarm.tractor.model.ERROR_MESSAGE_FETCHING_AWS_PROJECTS
import com.ricardorlg.devicefarm.tractor.model.ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT
import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmProjectsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmListingProjectsError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmProjectCreationError
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.CreateProjectRequest
import software.amazon.awssdk.services.devicefarm.model.CreateProjectResponse
import software.amazon.awssdk.services.devicefarm.model.Project

class DefaultDeviceFarmProjectsHandlerTest : DescribeSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val projectName = "test project"

    describe("When fetching projects from AWS") {
        describe("It should return the project as a right") {
            //GIVEN
            val expectedProjects = (1..10).map {
                Project
                    .builder()
                    .arn("arn:aws:devicefarm:us-west-2:project$it")
                    .name("test_project_$it")
                    .build()
            }

            every { dfClient.listProjectsPaginator().projects() } returns SdkIterable {
                expectedProjects.toMutableList().listIterator()
            }

            //WHEN
            val response = DefaultDeviceFarmProjectsHandler(dfClient).listProjects()

            //THEN
            response shouldBeRight expectedProjects

            verify {
                dfClient.listProjectsPaginator().projects()
            }
            confirmVerified(dfClient)
        }

        describe("Any error should be returned as a left") {
            //GIVEN
            val expectedError = RuntimeException("Test error")

            every { dfClient.listProjectsPaginator() } throws expectedError

            //WHEN
            val response = DefaultDeviceFarmProjectsHandler(dfClient).listProjects()

            //THEN
            response shouldBeLeft {
                it.shouldBeInstanceOf<DeviceFarmListingProjectsError>()
                it shouldHaveMessage ERROR_MESSAGE_FETCHING_AWS_PROJECTS
                it.cause shouldBe expectedError
            }
            verify {
                dfClient.listProjectsPaginator()
            }
            confirmVerified(dfClient)
        }
    }
    describe("When creating a project in AWS device farm") {
        describe("It should return the created project as a right") {
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
            response shouldBeRight expectedProject
            verify {
                dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
            }
            confirmVerified(dfClient)
        }

        describe("Any error should be returned as a left") {
            //GIVEN
            val expectedError = RuntimeException("Test error")

            every { dfClient.createProject(any<CreateProjectRequest>()) } throws expectedError

            //WHEN
            val response = DefaultDeviceFarmProjectsHandler(dfClient).createProject(projectName)

            //THEN
            response shouldBeLeft {
                it.shouldBeInstanceOf<DeviceFarmProjectCreationError>()
                it shouldHaveMessage "$ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT $projectName"
                it.cause shouldBe expectedError
            }
            verify {
                dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
            }
            confirmVerified(dfClient)
        }
    }

    afterTest {
        clearMocks(dfClient)
    }
})
