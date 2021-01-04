package com.ricardorlg.devicefarm.tractor.model

const val ERROR_MESSAGE_FETCHING_AWS_PROJECTS = "There was an error fetching projects from AWS"
const val ERROR_PREFIX_MESSAGE_CREATING_NEW_PROJECT = "There was an error in AWS creating the project"
const val ERROR_MESSAGE_FETCHING_DEVICE_POOLS = "There was an error fetching device pools from AWS"
const val EMPTY_PROJECT_ARN = "The project ARN must not be empty"
const val EMPTY_PROJECT_NAME = "The project name must not be empty"
const val AWS_UPLOAD_CONTENT_TYPE = "application/octet-stream"
const val ERROR_CREATING_AWS_UPLOAD = "There was an error creating an upload for project %s"
const val ERROR_UPLOADING_ARTIFACT_TO_S3 = "There was an error uploading the file %s to S3"
const val ERROR_FETCHING_UPLOAD_FROM_AWS = "There was an error fetching the upload %s from AWS"
const val ARTIFACT_UPLOAD_TO_S3_NOT_SUCCESSFULLY =
    "The upload of the the file %s to s3, wan not successfully, status code was %s"
const val EMPTY_UPLOAD_ARN = "The upload ARN must not be empty"
const val EMPTY_FILENAME = "The artifact name must not be empty"
const val PROJECT_DOES_NOT_HAVE_DEVICE_POOLS = "The project with arn %s doesn't have associated device pools"
const val DEVICE_POOL_NOT_FOUND = "The device pool %s was not found in current project"

