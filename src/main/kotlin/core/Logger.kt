package kpm.core

import io.github.oshai.kotlinlogging.KotlinLogging

object Logger {
    private val logger = KotlinLogging.logger {}

    fun info(message: String) {
        logger.info { message }
        println("ℹ️  $message")
    }

    fun success(message: String) {
        logger.info { message }
        println("✅ $message")
    }

    fun warning(message: String) {
        logger.warn { message }
        println("⚠️  $message")
    }

    fun error(message: String, throwable: Throwable? = null) {
        logger.error(throwable) { message }
        println("❌ $message")
    }

    fun debug(message: String) {
        logger.debug { message }
    }
}
