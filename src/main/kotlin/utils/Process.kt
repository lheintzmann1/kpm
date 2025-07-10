package kpm.utils

fun runProcess(command: Array<String>, errorMessage: String): Boolean {
    return try {
        val process = ProcessBuilder(*command)
            .inheritIO()
            .start()
        process.waitFor()
        if (process.exitValue() != 0) {
            println(errorMessage)
            false
        } else {
            true
        }
    } catch (e: Exception) {
        println("An error occurred while executing the command: ${e.message}")
        false
    }
}