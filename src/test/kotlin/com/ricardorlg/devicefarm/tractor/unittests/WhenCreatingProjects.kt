package com.ricardorlg.devicefarm.tractor.unittests


import com.ricardorlg.devicefarm.tractor.DeviceFarmExceptions
import com.ricardorlg.devicefarm.tractor.DeviceFarmTractor
import com.ricardorlg.devicefarm.tractor.ERROR_MESSAGE_FETCHING_USER_PROJECTS
import com.ricardorlg.devicefarm.tractor.ERROR_PREFIX_MESSAGE_CRETING_NEW_PROJECT
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*

class WhenCreatingProjects : StringSpec({

    val projectArn = "arn:aws:devicefarm:us-west-2:377815266411:project:214b4fcb-e29c-43d2-94ea-7aa6e3b79dce"
    val projectName = "test Project"

    "When a project already exists the tractor should return it" {
        //GIVEN
        val expectedProject = Project.builder().arn(projectArn).name(projectName).build()
        val expectedProjectResponse = ListProjectsResponse.builder().projects(expectedProject).build()
        val dfClient = mockk<DeviceFarmClient>()
        every { dfClient.listProjects(any<ListProjectsRequest>()) } returns expectedProjectResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrCreateProject(projectName)

        //THEN
        response shouldBeRight expectedProject

        verify {
            dfClient.listProjects(any<ListProjectsRequest>())
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build()) wasNot Called
        }

        confirmVerified(dfClient)
    }

    "tractor should check in all project pages provided by device farm"{
        //GIVEN
        val expectedProject = Project.builder().arn(projectArn).name(projectName).build()
        val firstListProjectsResponse =
            ListProjectsResponse.builder().projects(emptyList()).nextToken("nextToken").build()
        val secondListProjectsResponse =
            ListProjectsResponse.builder().projects(expectedProject).nextToken(null).build()
        val dfClient = mockk<DeviceFarmClient>()
        every { dfClient.listProjects(any<ListProjectsRequest>()) } returns firstListProjectsResponse andThen secondListProjectsResponse

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrCreateProject(projectName)

        //THEN
        response shouldBeRight expectedProject

        verify {
            dfClient.listProjects(ListProjectsRequest.builder().build())
            dfClient.listProjects(ListProjectsRequest.builder().nextToken("nextToken").build())
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build()) wasNot Called
        }

        confirmVerified(dfClient)
    }

    "When a project is not found then the tractor should create it"{
        //GIVEN
        val expectedProject = Project.builder().arn(projectArn).name(projectName).build()
        val expectedProjectResponse = ListProjectsResponse.builder().projects(emptyList()).build()
        val dfClient = mockk<DeviceFarmClient>()
        every { dfClient.listProjects(any<ListProjectsRequest>()) } returns expectedProjectResponse
        every { dfClient.createProject(any<CreateProjectRequest>()) } returns CreateProjectResponse.builder()
            .project(expectedProject).build()

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrCreateProject(projectName)

        //THEN
        response shouldBeRight expectedProject

        verify {
            dfClient.listProjects(any<ListProjectsRequest>())
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
        }

        confirmVerified(dfClient)
    }

    "When an exception happens fetching projects then it should be caught"{
        //GIVEN
        val expectedException = DeviceFarmException.create("Error", Throwable())
        val dfClient = mockk<DeviceFarmClient>()
        every { dfClient.listProjects(any<ListProjectsRequest>()) } throws expectedException

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrCreateProject(projectName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.ErrorFetchingProjects>()
            it shouldHaveMessage ERROR_MESSAGE_FETCHING_USER_PROJECTS
            it.cause shouldBe expectedException
        }

        verify {
            dfClient.listProjects(any<ListProjectsRequest>())
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build()) wasNot Called
        }

        confirmVerified(dfClient)
    }

    "When an exception happens creating a new project then it should be caught"{
        //GIVEN
        val expectedException = DeviceFarmException.create("Error", Throwable())
        val mockedFetchProjectsResponse = ListProjectsResponse.builder().projects(emptyList()).build()
        val dfClient = mockk<DeviceFarmClient>()
        every { dfClient.listProjects(any<ListProjectsRequest>()) } returns mockedFetchProjectsResponse
        every { dfClient.createProject(any<CreateProjectRequest>()) } throws expectedException

        //WHEN
        val response = DeviceFarmTractor(dfClient).findOrCreateProject(projectName)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmExceptions.ErrorCreatingProject>()
            it shouldHaveMessage ERROR_PREFIX_MESSAGE_CRETING_NEW_PROJECT + projectName
            it.cause shouldBe expectedException
        }

        verify {
            dfClient.listProjects(any<ListProjectsRequest>())
            dfClient.createProject(CreateProjectRequest.builder().name(projectName).build())
        }

        confirmVerified(dfClient)
    }
})
