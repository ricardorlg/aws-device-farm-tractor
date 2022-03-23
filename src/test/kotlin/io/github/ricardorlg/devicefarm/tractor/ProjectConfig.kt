package io.github.ricardorlg.devicefarm.tractor

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.htmlreporter.HtmlReporter

@Suppress("UNUSED")
object ProjectConfig : AbstractProjectConfig() {
    override val parallelism = 1
    override fun extensions(): List<Extension> = listOf(HtmlReporter())
}
