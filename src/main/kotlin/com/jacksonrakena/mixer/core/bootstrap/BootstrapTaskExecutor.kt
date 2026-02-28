package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.core.requests.InsertSeedDataRequest
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component

@Component
class BootstrapTaskExecutor(
    private val jobRequestScheduler: JobRequestScheduler,
    private val config: MixerConfiguration,
) {
    @PostConstruct
    fun executeBootstrapTasks() {
        if (config.data.seed.insert) {
            jobRequestScheduler.enqueue(InsertSeedDataRequest())
        }
    }
}