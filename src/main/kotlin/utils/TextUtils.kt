package kpm.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

object TextUtils {
    /**
     * ANSI color codes and styles for terminal output.
     */
    object Colors {
        // ANSI color codes
        const val RESET = "\u001B[0m"
        const val BLACK = "\u001B[30m"
        const val RED = "\u001B[31m"
        const val GREEN = "\u001B[32m"
        const val YELLOW = "\u001B[33m"
        const val BLUE = "\u001B[34m"
        const val PURPLE = "\u001B[35m"
        const val CYAN = "\u001B[36m"
        const val WHITE = "\u001B[37m"
        const val BRIGHT_RED = "\u001B[91m"
        const val BRIGHT_GREEN = "\u001B[92m"
        const val BRIGHT_YELLOW = "\u001B[93m"
        const val BRIGHT_BLUE = "\u001B[94m"
        const val BRIGHT_PURPLE = "\u001B[95m"
        const val BRIGHT_CYAN = "\u001B[96m"
        const val BRIGHT_WHITE = "\u001B[97m"

        // Styles
        const val BOLD = "\u001B[1m"
        const val DIM = "\u001B[2m"
        const val UNDERLINE = "\u001B[4m"
    }

    /**
     * Checks if colored output is enabled based on environment variables.
     * - NO_COLOR: If set, disables colored output.
     * - TERM: If set to "dumb", disables colored output.
     */
    private val colorsEnabled: Boolean by lazy {
        System.getenv("NO_COLOR") == null &&
                System.getenv("TERM") != "dumb"
    }

    /**
     * Colorizes the given text with the specified ANSI color code.
     * If colors are disabled, returns the text unchanged.
     *
     * @param text The text to colorize.
     * @param color The ANSI color code to apply.
     * @return The colorized text or the original text if colors are disabled.
     */
    private fun colorize(text: String, color: String): String {
        return if (colorsEnabled) "$color$text${Colors.RESET}" else text
    }

    /**
     * Formats a log message with a timestamp, log level, and color.
     *
     * @param level The log level (e.g., INFO, ERROR).
     * @param message The log message.
     * @param color The ANSI color code for the log level.
     * @return A formatted string suitable for logging.
     */
    fun formatMessage(level: String, message: String, color: String): String {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val coloredLevel = colorize(level.padEnd(7), color)
        val coloredTimestamp = colorize(timestamp, Colors.DIM)
        return "[$coloredTimestamp] $coloredLevel $message"
    }
}