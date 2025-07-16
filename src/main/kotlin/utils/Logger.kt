package kpm.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kpm.core.Constants as Consts

object Logger {
    init {
        System.setProperty("org.slf4j.simpleLogger.logFile", "${Consts.KPM_HOME}/kpm.log")
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss")
    }

    private val logger = KotlinLogging.logger {}

    fun info(message: String) {
        logger.info { message }
        println(TextUtils.formatMessage("INFO", message, TextUtils.Colors.BLUE))
    }

    fun success(message: String) {
        logger.info { message }
        println(TextUtils.formatMessage("SUCCESS", message, TextUtils.Colors.BRIGHT_GREEN))
    }

    fun warning(message: String) {
        logger.warn { message }
        println(TextUtils.formatMessage("WARN", message, TextUtils.Colors.BRIGHT_YELLOW))
    }

    fun error(message: String, throwable: Throwable? = null) {
        logger.error(throwable) { message }
        val errorMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        println(TextUtils.formatMessage("ERROR", errorMessage, TextUtils.Colors.BRIGHT_RED))
    }

    fun debug(message: String) {
        logger.debug { message }
        if (isDebugEnabled()) {
            println(TextUtils.formatMessage("DEBUG", message, TextUtils.Colors.PURPLE))
        }
    }

    fun trace(message: String) {
        logger.trace { message }
        if (isTraceEnabled()) {
            println(TextUtils.formatMessage("TRACE", message, TextUtils.Colors.CYAN))
        }
    }

    fun fatal(message: String, throwable: Throwable? = null) {
        logger.error(throwable) { message }
        val fatalMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        println(
            TextUtils.formatMessage(
                "FATAL",
                fatalMessage,
                "${TextUtils.Colors.BOLD}${TextUtils.Colors.BRIGHT_RED}"
            )
        )
    }

    // Utility methods
    fun isDebugEnabled(): Boolean = logger.isDebugEnabled()
    fun isTraceEnabled(): Boolean = logger.isTraceEnabled()
    fun isInfoEnabled(): Boolean = logger.isInfoEnabled()

    // Convenience methods for structured logging
    fun logWithContext(level: LogLevel, message: String, context: Map<String, Any>? = null, throwable: Throwable? = null) {
        val contextStr = context?.let { ctx ->
            " [${ctx.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
        } ?: ""

        val fullMessage = "$message$contextStr"

        when (level) {
            LogLevel.TRACE -> trace(fullMessage)
            LogLevel.DEBUG -> debug(fullMessage)
            LogLevel.INFO -> info(fullMessage)
            LogLevel.SUCCESS -> success(fullMessage)
            LogLevel.WARN -> warning(fullMessage)
            LogLevel.ERROR -> error(fullMessage, throwable)
            LogLevel.FATAL -> fatal(fullMessage, throwable)
        }
    }

    enum class LogLevel {
        TRACE, DEBUG, INFO, SUCCESS, WARN, ERROR, FATAL
    }
}