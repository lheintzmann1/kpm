/*
Copyright 2025 Lucas HEINTZMANN

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kpm.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kpm.core.Constants.KPM_HOME

object Logger {
    init {
        System.setProperty("org.slf4j.simpleLogger.logFile", "$KPM_HOME/kpm.log")
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss")
    }

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
