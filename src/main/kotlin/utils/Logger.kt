package kpm.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kpm.core.Constants as Consts

object Logger {
    /**
     * Initializes the logger with specific settings.
     * Sets the log file location, default log level, and date/time format for logs.
     */
    init {
        System.setProperty("org.slf4j.simpleLogger.logFile", "${Consts.KPM_HOME}/kpm.log")
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss")
    }

    private val logger = KotlinLogging.logger {}

    /**
     * Logs an informational message.
     * @param message The message to log.
     */
    fun info(message: String) {
        logger.info { message }
        println(TextUtils.formatMessage("INFO", message, TextUtils.Colors.BLUE))
    }

    /**
     * Logs a success message.
     * @param message The message to log.
     */
    fun success(message: String) {
        logger.info { message }
        println(TextUtils.formatMessage("SUCCESS", message, TextUtils.Colors.BRIGHT_GREEN))
    }

    /**
     * Logs a warning message.
     * @param message The message to log.
     */
    fun warning(message: String) {
        logger.warn { message }
        println(TextUtils.formatMessage("WARN", message, TextUtils.Colors.BRIGHT_YELLOW))
    }

    /**
     * Logs an error message.
     * @param message The message to log.
     * @param throwable Optional throwable to log with the message.
     */
    fun error(message: String, throwable: Throwable? = null) {
        logger.error(throwable) { message }
        val errorMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        println(TextUtils.formatMessage("ERROR", errorMessage, TextUtils.Colors.BRIGHT_RED))
    }

    /**
     * Logs a debug message.
     * @param message The message to log.
     */
    fun debug(message: String) {
        logger.debug { message }
        if (isDebugEnabled()) {
            println(TextUtils.formatMessage("DEBUG", message, TextUtils.Colors.PURPLE))
        }
    }

    /**
     * Logs a trace message.
     * @param message The message to log.
     */
    fun trace(message: String) {
        logger.trace { message }
        if (isTraceEnabled()) {
            println(TextUtils.formatMessage("TRACE", message, TextUtils.Colors.CYAN))
        }
    }

    /**
     * Logs a fatal error message and terminates the application.
     * @param message The message to log.
     * @param throwable Optional throwable to log with the message.
     */
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

    /**
     * Checks if debug logging is enabled.
     * @return True if debug logging is enabled, false otherwise.
     */
    fun isDebugEnabled(): Boolean = logger.isDebugEnabled()
    fun isTraceEnabled(): Boolean = logger.isTraceEnabled()
    fun isInfoEnabled(): Boolean = logger.isInfoEnabled()

    /**
     * Logs a message with a specific log level and optional context.
     * @param level The log level (TRACE, DEBUG, INFO, SUCCESS, WARN, ERROR, FATAL).
     * @param message The message to log.
     * @param context Optional context map to include in the log message.
     * @param throwable Optional throwable to log with the message.
     */
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

    /**
     * Enum representing different log levels.
     */
    enum class LogLevel {
        TRACE, DEBUG, INFO, SUCCESS, WARN, ERROR, FATAL
    }
}