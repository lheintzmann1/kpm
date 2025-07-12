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

package kpm.utils

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream

object HashUtils {
    fun calculateFileHash(filePath: Path, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)

        filePath.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        // Return only the first 32 characters of the hash
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }
}