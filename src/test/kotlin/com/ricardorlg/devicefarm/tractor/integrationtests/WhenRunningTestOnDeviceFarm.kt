package com.ricardorlg.devicefarm.tractor.integrationtests

import com.ricardorlg.devicefarm.tractor.DeviceFarmTractor
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec

class WhenRunningTestOnDeviceFarm : StringSpec({

    "!A new project should be created if it doesn't exists or the found one should be returned"{
        DeviceFarmTractor()
            .findOrCreateProject("test Project")
            .shouldBeRight()
    }

})
