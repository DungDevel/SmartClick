package com.example.smartclick.app.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocaleResourcesConsistencyTest {

    @Test
    fun `english resources match the default resource set`() = assertLocale("en")

    @Test
    fun `vietnamese resources match the default resource set`() = assertLocale("vi")

    @Test
    fun `spanish resources match the default resource set`() = assertLocale("es")

    @Test
    fun `brazilian portuguese resources match the default resource set`() = assertLocale("pt-BR")

    @Test
    fun `german resources match the default resource set`() = assertLocale("de")

    private fun assertLocale(languageTag: String) {
        val resRoot = File("src/main/res")
        assertTrue("Expected test working directory to contain src/main/res", resRoot.isDirectory)
        val configuredLocales = configuredLocales(resRoot)
        assertTrue("Locale $languageTag must be listed in locales_config.xml", languageTag in configuredLocales)

        // English is provided by Android's unqualified default `values/` resource directory.
        val qualifier = when (languageTag) {
            "en" -> "values"
            "pt-BR" -> "values-pt-rBR"
            else -> "values-$languageTag"
        }
        val localeDirectory = File(resRoot, qualifier)
        assertTrue("Missing locale resource directory: ${localeDirectory.path}", localeDirectory.isDirectory)

        val base = readResources(File(resRoot, "values"))
        val translated = readResources(localeDirectory)
        val legalKeys = base.keys.filter {
            it.startsWith("legal_privacy_") || it.startsWith("legal_terms_")
        }.toSet()
        assertTrue("Expected legal resource exceptions to be recorded in step 1", legalKeys.isNotEmpty())
        val excluded = base.filterValues { !it.translatable }.keys + legalKeys
        val expectedKeys = base.keys - excluded
        val actualKeys = translated.keys - excluded
        assertEquals("Resource key mismatch for $languageTag", expectedKeys, actualKeys)

        legalKeys.forEach { key ->
            val source = base.getValue(key)
            val localized = translated[key]
            assertTrue("Missing original-language legal resource $key in $languageTag", localized != null)
            assertEquals("Legal text should remain in its source language for $languageTag: $key", source.values, localized!!.values)
            if (languageTag in setOf("es", "pt-BR", "de")) {
                assertEquals("tools:ignore must be local to legal resource $key", "MissingTranslation", localized.toolsIgnore)
            }
        }

        translated.forEach { (key, resource) ->
            resource.values.values.forEach { value ->
                assertTrue("Empty string resource $key in $languageTag", value.isNotBlank())
            }
            if (key !in legalKeys && resource.translatable) {
                assertEquals("Resource type mismatch for $key in $languageTag", base.getValue(key).type, resource.type)
                val sourceResource = base.getValue(key)
                assertEquals(
                    "Placeholder mismatch for $key in $languageTag",
                    resource.values.keys.associateWith { quantity ->
                        placeholderTokens(
                            sourceResource.values[quantity]
                                ?: sourceResource.values["other"]
                                ?: sourceResource.values.values.first()
                        )
                    },
                    resource.values.mapValues { (_, value) -> placeholderTokens(value) }
                )
            }
        }

        val requiredPluralQuantities = when (languageTag) {
            "es", "pt-BR" -> setOf("one", "many", "other")
            "de" -> setOf("one", "other")
            else -> setOf("one", "other")
        }
        base.filterValues { it.type == "plurals" }.forEach { (key, _) ->
            val localized = translated[key]
            assertTrue("Missing plural resource $key in $languageTag", localized != null)
            assertTrue(
                "Plural $key in $languageTag must contain $requiredPluralQuantities, found ${localized!!.values.keys}",
                localized.values.keys.containsAll(requiredPluralQuantities)
            )
        }

        checkXmlEscaping(localeDirectory, languageTag)
    }

    private fun configuredLocales(resRoot: File): Set<String> {
        val xml = parseXml(File(resRoot, "xml/locales_config.xml"))
        val nodes = xml.getElementsByTagName("locale")
        return (0 until nodes.length).map { index ->
            (nodes.item(index) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        }.toSet()
    }

    private data class Resource(
        val type: String,
        val translatable: Boolean,
        val values: Map<String, String>,
        val toolsIgnore: String
    )

    private fun readResources(directory: File): Map<String, Resource> {
        val result = mutableMapOf<String, Resource>()
        directory.listFiles { file -> file.extension == "xml" }!!.forEach { file ->
            val root = parseXml(file).documentElement
            val children = root.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                val type = child.tagName
                if (type != "string" && type != "plurals") continue
                val name = child.getAttribute("name")
                val values = if (type == "plurals") {
                    val items = child.getElementsByTagName("item")
                    (0 until items.length).associate { itemIndex ->
                        val item = items.item(itemIndex) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                } else {
                    mapOf("value" to child.textContent)
                }
                val ignore = child.getAttributeNS("http://schemas.android.com/tools", "ignore")
                result[name] = Resource(
                    type = type,
                    translatable = child.getAttribute("translatable") != "false",
                    values = values,
                    toolsIgnore = ignore
                )
            }
        }
        return result
    }

    private fun placeholderTokens(value: String): List<String> =
        Regex("%(?:[0-9]+\\$)?[sd]").findAll(value).map { it.value }.sorted().toList()

    private fun checkXmlEscaping(directory: File, languageTag: String) {
        val unescapedApostrophe = Regex("(?<!\\\\)'")
        directory.listFiles { file -> file.extension == "xml" }!!.forEach { file ->
            parseXml(file) // An unescaped ampersand makes this XML invalid.
            val source = file.readText()
            Regex("(?s)<(?:string|item)\\b[^>]*>(.*?)</(?:string|item)>")
                .findAll(source)
                .forEach { match ->
                    assertTrue(
                        "Unescaped apostrophe in ${file.path} for $languageTag: ${match.groupValues[1]}",
                        !unescapedApostrophe.containsMatchIn(match.groupValues[1])
                    )
                }
        }
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)
}
