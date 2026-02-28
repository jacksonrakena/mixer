package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.core.requests.InsertSeedDataRequest
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component

@Component
class BootstrapTaskExecutor(private val jobRequestScheduler: JobRequestScheduler) {
    @PostConstruct
    fun executeBootstrapTasks() {
        jobRequestScheduler.enqueue(InsertSeedDataRequest())
    }
}