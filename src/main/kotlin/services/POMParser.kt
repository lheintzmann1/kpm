﻿/*
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

package kpm.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kpm.core.KResult
import kpm.utils.Logger
import kpm.models.MavenCoordinate
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.readText
import org.xml.sax.InputSource

class POMParser {

    /**
     * Represents a parsed POM file with its coordinate, dependencies, parent, and properties.
     *
     * @property coordinate The Maven coordinate of the project.
     * @property dependencies List of Maven coordinates for the project's dependencies.
     * @property parent Optional parent POM coordinate if it exists.
     * @property properties Map of properties defined in the POM file.
     */
    data class ParsedPOM(
        val coordinate: MavenCoordinate,
        val dependencies: List<MavenCoordinate>,
        val parent: MavenCoordinate? = null,
        val properties: Map<String, String> = emptyMap()
    )

    /**
     * Parses a POM file located at the specified path.
     *
     * @param pomPath The path to the POM file.
     * @return A [KResult] containing the parsed POM data or an error message.
     */
    suspend fun parsePOM(pomPath: Path): KResult<ParsedPOM> = withContext(Dispatchers.IO) {
        try {
            val pomContent = pomPath.readText()
            val document = parseXML(pomContent)

            val projectElement = document.documentElement
            if (projectElement.tagName != "project") {
                return@withContext KResult.Error("Invalid POM file: root element is not 'project'")
            }

            // Extract basic project info
            val groupId = getElementText(projectElement, "groupId")
            val artifactId = getElementText(projectElement, "artifactId")
            val version = getElementText(projectElement, "version")

            // Handle parent POM
            val parentElement = getFirstChildElement(projectElement, "parent")
            val parent = if (parentElement != null) {
                val parentGroupId = getElementText(parentElement, "groupId")
                val parentArtifactId = getElementText(parentElement, "artifactId")
                val parentVersion = getElementText(parentElement, "version")

                if (parentGroupId != null && parentArtifactId != null && parentVersion != null) {
                    MavenCoordinate(parentGroupId, parentArtifactId, parentVersion)
                } else null
            } else null

            // Use parent's groupId/version if not specified
            val finalGroupId = groupId ?: parent?.groupId
            val finalVersion = version ?: parent?.version

            if (finalGroupId == null || artifactId == null || finalVersion == null) {
                return@withContext KResult.Error("Missing required POM elements: groupId, artifactId, or version")
            }

            val coordinate = MavenCoordinate(finalGroupId, artifactId, finalVersion)

            // Extract properties
            val properties = extractProperties(projectElement)

            // Extract dependencies
            val dependencies = extractDependencies(projectElement, properties, coordinate)

            val parsedPOM = ParsedPOM(
                coordinate = coordinate,
                dependencies = dependencies,
                parent = parent,
                properties = properties
            )

            Logger.debug("Parsed POM for ${coordinate}: ${dependencies.size} dependencies")
            KResult.Success(parsedPOM)
        } catch (e: Exception) {
            Logger.error("Failed to parse POM file: ${e.message}", e)
            KResult.Error("Failed to parse POM file: ${e.message}", e)
        }
    }

    /**
     * Parses the XML content of a POM file and returns a DOM Document.
     *
     * @param xmlContent The XML content of the POM file.
     * @return A [Document] representing the parsed XML.
     */
    private fun parseXML(xmlContent: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        val builder = factory.newDocumentBuilder()
        val inputSource = InputSource(StringReader(xmlContent))
        return builder.parse(inputSource)
    }

    /**
     * Extracts properties defined in the POM file.
     *
     * @param projectElement The root element of the POM XML.
     * @return A map of property names to their values.
     */
    private fun extractProperties(projectElement: Element): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        val propertiesElement = getFirstChildElement(projectElement, "properties")
        if (propertiesElement != null) {
            val childNodes = propertiesElement.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    properties[element.tagName] = element.textContent.trim()
                }
            }
        }

        return properties
    }

    /**
     * Extracts dependencies from the POM file.
     *
     * @param projectElement The root element of the POM XML.
     * @param properties Map of properties defined in the POM file.
     * @param currentCoordinate The Maven coordinate of the current project.
     * @return A list of Maven coordinates representing the dependencies.
     */
    private fun extractDependencies(
        projectElement: Element,
        properties: Map<String, String>,
        currentCoordinate: MavenCoordinate
    ): List<MavenCoordinate> {
        val dependencies = mutableListOf<MavenCoordinate>()

        val dependenciesElements = projectElement.getElementsByTagName("dependencies")
        for (i in 0 until dependenciesElements.length) {
            val dependenciesElement = dependenciesElements.item(i) as Element
            val parentNode = dependenciesElement.parentNode
            // Ignore <dependencyManagement> for now
            if (parentNode is Element && parentNode.tagName == "project") {
                val dependencyNodes = dependenciesElement.getElementsByTagName("dependency")
                for (j in 0 until dependencyNodes.length) {
                    val dependencyElement = dependencyNodes.item(j) as Element

                    // Skip if scope is test, provided, or system
                    val scope = getElementText(dependencyElement, "scope")
                    if (scope != null && scope.lowercase() in listOf("test", "provided", "system")) {
                        continue
                    }

                    // Skip if optional
                    val optional = getElementText(dependencyElement, "optional")
                    if (optional?.lowercase() == "true") {
                        continue
                    }

                    val groupId = getElementText(dependencyElement, "groupId")
                    val artifactId = getElementText(dependencyElement, "artifactId")
                    val version = getElementText(dependencyElement, "version")

                    if (groupId != null && artifactId != null && version != null) {
                        // Resolve properties in version
                        val resolvedVersion = resolveProperties(version, properties, currentCoordinate)
                        val resolvedGroupId = resolveProperties(groupId, properties, currentCoordinate)

                        val coordinate = MavenCoordinate(resolvedGroupId, artifactId, resolvedVersion)
                        dependencies.add(coordinate)
                    }
                }
            }
        }
        return dependencies
    }

    /**
     * Resolves properties in a given value string.
     *
     * @param value The string containing properties to resolve.
     * @param properties Map of properties defined in the POM file.
     * @param currentCoordinate The Maven coordinate of the current project.
     * @return The resolved string with properties replaced.
     */
    private fun resolveProperties(
        value: String,
        properties: Map<String, String>,
        currentCoordinate: MavenCoordinate
    ): String {
        var resolved = value

        // Replace ${project.version} with current version
        resolved = resolved.replace("\${project.version}", currentCoordinate.version)
        resolved = resolved.replace("\${version}", currentCoordinate.version)

        // Replace ${project.groupId} with current groupId
        resolved = resolved.replace("\${project.groupId}", currentCoordinate.groupId)

        // Replace custom properties
        properties.forEach { (key, propValue) ->
            resolved = resolved.replace("\${$key}", propValue)
        }

        return resolved
    }

    /**
     * Retrieves the first child element with the specified tag name from a parent element.
     *
     * @param parent The parent element to search within.
     * @param tagName The tag name of the child element to find.
     * @return The first child element with the specified tag name, or null if not found.
     */
    private fun getFirstChildElement(parent: Element, tagName: String): Element? {
        val nodeList = parent.getElementsByTagName(tagName)
        return if (nodeList.length > 0) nodeList.item(0) as Element else null
    }

    /**
     * Retrieves the text content of the first child element with the specified tag name.
     *
     * @param parent The parent element to search within.
     * @param tagName The tag name of the child element to find.
     * @return The trimmed text content of the child element, or null if not found or empty.
     */
    private fun getElementText(parent: Element, tagName: String): String? {
        val element = getFirstChildElement(parent, tagName)
        return element?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }
}