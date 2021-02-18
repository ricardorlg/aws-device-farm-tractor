package io.github.ricardorlg.devicefarm.tractor.model

import software.amazon.awssdk.services.devicefarm.model.UploadType

sealed class DeviceFarmTractorError(override val message: String, override val cause: Throwable) :
    Throwable(message, cause)

data class ErrorFetchingProjects(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorCreatingProject(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorFetchingDevicePools(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class NoRegisteredDevicePoolsError(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class DevicePoolNotFoundError(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class ErrorCreatingUpload(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorUploadingArtifact(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorFetchingUpload(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class UploadFailureError(override val message: String) :
    DeviceFarmTractorError(message, IllegalStateException(message))

data class DeviceFarmTractorErrorIllegalArgumentException(override val message: String) :
    DeviceFarmTractorError(message, IllegalArgumentException(message))

data class DeviceFarmTractorGeneralError(override val cause: Throwable) :
    DeviceFarmTractorError(cause.message.orEmpty(), cause)

data class ErrorSchedulingRun(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorFetchingAWSRun(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorFetchingArtifacts(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorDownloadingArtifact(override val cause: Throwable) :
    DeviceFarmTractorError(cause.message.orEmpty(), cause)

data class ErrorDeletingUpload(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

data class ErrorListingAssociatedJobs(override val message: String, override val cause: Throwable) :
    DeviceFarmTractorError(message, cause)

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