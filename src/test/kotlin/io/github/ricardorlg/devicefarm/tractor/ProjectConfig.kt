package io.github.ricardorlg.devicefarm.tractor

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.allure.AllureTestReporter
import io.kotest.extensions.system.NoSystemErrListener
import io.kotest.extensions.system.NoSystemOutListener

object ProjectConfig : AbstractProjectConfig() {
    override fun listeners() = listOf(AllureTestReporter(), NoSystemOutListener, NoSystemErrListener)
    //override fun listeners() = listOf(AllureTestReporter())
}
