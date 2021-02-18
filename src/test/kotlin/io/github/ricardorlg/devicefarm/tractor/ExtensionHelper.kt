package io.github.ricardorlg.devicefarm.tractor

import io.kotest.core.TestConfiguration
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.createTempDirectory

fun TestConfiguration.tempFolder(prefix: String? = null, vararg attributes: FileAttribute<*>): Path {
    val f = createTempDirectory(prefix, *attributes)
    afterSpec {
        f.toFile().deleteRecursively()
    }
    return f
}