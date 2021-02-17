package com.ricardorlg.devicefarm.tractor.utils

import com.ricardorlg.devicefarm.tractor.model.*
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods.uploadType
import com.ricardorlg.devicefarm.tractor.utils.HelperMethods.validateFileExtensionByType
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import software.amazon.awssdk.services.devicefarm.model.UploadType

class HelperMethodsTest : StringSpec({

    "When Android upload type is used, file must end with .apk" {
        //GIVEN
        val file = tempfile("testFile", ".apk")

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.ANDROID_APP)

        //THEN
        response shouldBeRight file
    }
    "When Android upload type is used, and file does not have .apk extension an error is returned" {
        //GIVEN
        val file = tempfile("testFile")
        val expectedErrorMessage =
            "the file ${file.nameWithoutExtension} is not a valid Android app. It should has .apk extension"

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.ANDROID_APP)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension>()
            it shouldHaveMessage expectedErrorMessage
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }
    "When IOS upload type is used, file must end with .ipa" {
        //GIVEN
        val file = tempfile("testFile", ".ipa")

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.IOS_APP)

        //THEN
        response shouldBeRight file
    }
    "When IOS upload type is used, and file does not have .ipa extension an error is returned" {
        //GIVEN
        val file = tempfile("testFile")
        val expectedErrorMessage =
            "the file ${file.nameWithoutExtension} is not a valid IOS app. It should has .ipa extension"

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.IOS_APP)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension>()
            it shouldHaveMessage expectedErrorMessage
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }
    "When Appium Node Test Spec upload type is used, file must end with .yml" {
        //GIVEN
        val file = tempfile("testFile", ".yml")

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.APPIUM_NODE_TEST_SPEC)

        //THEN
        response shouldBeRight file
    }
    "When Appium Node Test Spec upload type is used, and file does not have .yml extension an error is returned" {
        //GIVEN
        val file = tempfile("testFile")
        val expectedErrorMessage =
            "the file ${file.nameWithoutExtension} is not a valid test spec file. It should has .yml extension"

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.APPIUM_NODE_TEST_SPEC)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension>()
            it shouldHaveMessage expectedErrorMessage
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }
    "When Upload type is not Android,IOS or Appium, the file must have .zip extension" {
        //GIVEN
        val file = tempfile("testFile", ".zip")

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.WEB_APP)

        //THEN
        response shouldBeRight file
    }
    "When Upload type is not Android,IOS or Appium, and file does not have .zip extension an error is returned" {
        //GIVEN
        val file = tempfile("testFile")
        val expectedErrorMessage =
            "the file ${file.nameWithoutExtension} is not a valid general file. It should has .zip extension"

        //WHEN
        val response = file.validateFileExtensionByType(UploadType.WEB_APP)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension>()
            it shouldHaveMessage expectedErrorMessage
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }
    "When unknown Upload type is used, an error should be returned"{
        //WHEN
        val response = tempfile().validateFileExtensionByType(UploadType.UNKNOWN_TO_SDK_VERSION)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmIllegalArtifactExtension.UnsupportedException>()
            it.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }
    "When loading a file from a given path, it should return it as a right" {
        //GIVEN
        val expectedFile = tempfile("testFile")

        //WHEN
        val response = HelperMethods.loadFileFromPath(expectedFile.path)

        //THEN
        response shouldBeRight expectedFile
    }
    "When loading a file from a given path, if the files doesn't exists an error should be returned as a Left" {
        //WHEN
        val path = "path/to/nonexisting/file"
        val response = HelperMethods.loadFileFromPath(path)

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorGeneralError>()
            it shouldHaveMessage "The file at $path does not exists"
            it.cause.shouldBeInstanceOf<java.lang.IllegalArgumentException>()
        }
    }
    "When no path is provided an Illegal Argument error should be returned as a left " {
        //WHEN
        val response = HelperMethods.loadFileFromPath("   ")

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
        }
    }

    "When no path is provided getting the upload type of a path, an Illegal Argument error should be returned as a left"{
        //WHEN
        val response = "   ".uploadType()

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage MANDATORY_PATH_PARAMETER
        }
    }

    "When getting the upload type of an unsupported file, an Illegal Argument error should be returned as a left"{
        //GIVEN
        val app = tempfile("testApp", ".zip")

        //WHEN
        val response = app.path.uploadType()

        //THEN
        response shouldBeLeft {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage UNSUPPORTED_APP_FILE_EXTENSION.format(app.extension)
        }
    }

    "When getting the upload type of a valid file, it should return it as a right"{
        checkAll(
            Exhaustive.collection(
                listOf(
                    ".apk" to UploadType.ANDROID_APP,
                    ".ipa" to UploadType.IOS_APP
                )
            )
        ) { (extension, expectedUploadType) ->
            //GIVEN
            val app = tempfile("testApp", extension)

            //WHEN
            val response = app.path.uploadType()

            //THEN
            response shouldBeRight expectedUploadType
        }
    }
})
