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

    fun calculateStringHash(input: String, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun calculateContentHash(coordinate: String, version: String, content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(coordinate.toByteArray())
        digest.update(version.toByteArray())
        digest.update(content)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}