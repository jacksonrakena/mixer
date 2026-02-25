package com.jacksonrakena.mixer.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

/**
 * Populates the SLF4J MDC with a short per-request ID so every log line emitted
 * during a request carries the same correlation token.
 */
class MdcRequestInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        MDC.put("requestId", UUID.randomUUID().toString().take(8))
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        MDC.clear()
    }
}
