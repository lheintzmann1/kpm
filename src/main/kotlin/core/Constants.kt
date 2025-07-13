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

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

object Constants {
    val KPM_VERSION: String by lazy {
        val props = Properties()
        Constants::class.java.getResourceAsStream("/version.properties").use { stream ->
            props.load(stream)
        }
        props.getProperty("version") ?: "unknown"
    }

    private val osName = System.getProperty("os.name").lowercase()
    val PLATFORM = when {
        "windows" in osName -> Platform.WINDOWS
        listOf("mac", "nix", "sunos", "solaris", "bsd").any { it in osName } -> Platform.UNIX
        else -> Platform.OTHER
    }
    val HOME: Path = Paths.get(System.getProperty("user.home"))
    val KPM_HOME: Path = HOME.resolve(".kpm")
    val KPM_TEMPLATES: Path = KPM_HOME.resolve("templates")
    val KPM_STORE: Path = KPM_HOME.resolve("store")
    val KPM_GCROOTS: Path = KPM_HOME.resolve("gcroots")
    val KPM_PROFILES: Path = KPM_HOME.resolve("profiles")

    const val TEMPLATE_BASE: String = "https://github.com/KotlinPM/template-base"

    const val MANIFEST_SCHEMA: String = "https://raw.githubusercontent.com/KotlinPM/manifest-schema/refs/heads/main/manifest.schema.json"
    const val MANIFEST: String = "kpm.json"
    const val MANIFEST_LOCK: String = "kpm-lock.json"

    const val DL_TIMEOUT_MS = 30_000L
    const val MAX_RETRIES = 3
}

enum class Platform {
    WINDOWS,
    UNIX,
    OTHER
}
