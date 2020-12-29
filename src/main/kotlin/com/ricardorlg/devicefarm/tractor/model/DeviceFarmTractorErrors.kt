package com.ricardorlg.devicefarm.tractor.model

import software.amazon.awssdk.services.devicefarm.model.UploadType

sealed class DeviceFarmTractorError(override val message: String, override val cause: Throwable) :
    Throwable(message, cause)

data class DeviceFarmListingProjectsError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmProjectCreationError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmListingDevicePoolsError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmProjectDoesNotHaveDevicePools(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class DeviceFarmDevicePoolNotFound(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class DeviceFarmCreateUploadError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmUploadArtifactError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmUploadFailedError(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class DeviceFarmGetUploadError(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class DeviceFarmIllegalArgumentError(override val message: String) :
    DeviceFarmTractorError(message, IllegalArgumentException(message))

sealed class DeviceFarmIllegalArtifactExtension(message: String) :
    DeviceFarmTractorError(message, IllegalArgumentException(message)) {
    data class InvalidAndroidExtension(private val fileName: String) :
        DeviceFarmIllegalArtifactExtension("the file $fileName is not a valid Android app. It should has .apk extension")

    data class InvalidIOSExtension(private val fileName: String) :
        DeviceFarmIllegalArtifactExtension("the file $fileName is not a valid IOS app. It should has .ipa extension")

    data class InvalidSpecExtension(private val fileName: String) :
        DeviceFarmIllegalArtifactExtension("the file $fileName is not a valid test spec file. It should has .yml extension")

    data class InvalidGeneralFileExtension(private val fileName: String) :
        DeviceFarmIllegalArtifactExtension("the file $fileName is not a valid general file. It should has .zip extension")

    data class UnsupportedException(private val uploadType: UploadType) :
        DeviceFarmIllegalArtifactExtension("The upload type $uploadType is not supported")
}