package kpm.models

data class MavenCoordinate(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    companion object {
        fun fromString(coordinate: String): MavenCoordinate? {
            val parts = coordinate.split(":")
            return if (parts.size == 3) {
                MavenCoordinate(parts[0], parts[1], parts[2])
            } else null
        }
    }

    override fun toString(): String = "$groupId:$artifactId:$version"

    fun toPath(): String = "${groupId.replace('.', '/')}/$artifactId/$version"

    fun toFileName(): String = "$artifactId-$version.jar"
}