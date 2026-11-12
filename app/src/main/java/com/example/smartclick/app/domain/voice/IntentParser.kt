package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.voice.AppSymbol
import com.example.smartclick.app.domain.model.voice.Confidence
import com.example.smartclick.app.domain.model.voice.VoiceIntent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses normalized text to determine user intent.
 */
@Singleton
class IntentParser @Inject constructor(
    private val textNormalizer: TextNormalizer
) {
    fun parse(rawText: String): VoiceIntent {
        val normalized = textNormalizer.normalize(rawText)
        if (normalized.isBlank()) {
            return VoiceIntent.Unrecognized(rawText)
        }

        if (hasPowerKeyword(normalized) && hasOtherCommandKeyword(normalized)) {
            return VoiceIntent.Unrecognized(rawText)
        }

        return parsePower(normalized, rawText)
            ?: parseMute(normalized, rawText)
            ?: parseVolume(normalized, rawText)
            ?: parseAppLaunch(normalized, rawText)
            ?: parseNavigation(normalized, rawText)
            ?: parseSearch(normalized, rawText)
            ?: VoiceIntent.Unrecognized(rawText)
    }

    private fun String.hasWord(word: String): Boolean {
        val escaped = Regex.escape(word)
        return Regex("\\b$escaped\\b").containsMatchIn(this)
    }

    private fun findWordIndex(text: String, keyword: String): Int {
        val escaped = Regex.escape(keyword)
        val match = Regex("\\b$escaped\\b").find(text)
        return match?.range?.first ?: -1
    }

    private fun isNegatedAround(normalized: String, keyword: String): Boolean {
        val tokens = normalized.split(Regex("\\s+"))
        val keywordTokens = keyword.split(Regex("\\s+"))

        var matchIdx = -1
        for (i in 0..tokens.size - keywordTokens.size) {
            var matches = true
            for (j in keywordTokens.indices) {
                if (tokens[i + j] != keywordTokens[j]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                matchIdx = i
                break
            }
        }
        if (matchIdx == -1) return false

        val negationWords = listOf("khong", "dung", "chua", "dont", "don't", "do not", "never", "not")
        val windowSize = 3
        val startToken = (matchIdx - windowSize).coerceAtLeast(0)
        val endToken = (matchIdx + keywordTokens.size + windowSize).coerceAtMost(tokens.size - 1)

        for (i in startToken..endToken) {
            if (tokens[i] in negationWords) return true
            if (i < tokens.size - 1 && "${tokens[i]} ${tokens[i+1]}" in negationWords) return true
        }
        return false
    }

    private fun hasPowerKeyword(normalized: String): Boolean {
        val allPowerKeywords = listOf("tat tivi", "tat tv", "turn off", "power off", "mo tivi", "mo tv", "bat tivi", "bat tv", "turn on", "power on", "wake up")
        return allPowerKeywords.any { normalized.hasWord(it) }
    }

    private fun hasOtherCommandKeyword(normalized: String): Boolean {
        val openKeywords = listOf("mo", "open", "launch", "bat")
        val hasOpen = openKeywords.any { ok ->
            if (ok == "bat") {
                normalized.hasWord("bat") && !normalized.hasWord("bat tivi") && !normalized.hasWord("bat tv")
            } else if (ok == "mo") {
                normalized.hasWord("mo") && !normalized.hasWord("mo tivi") && !normalized.hasWord("mo tv")
            } else {
                normalized.hasWord(ok)
            }
        }
        val appNames = listOf("youtube", "netflix", "prime", "hulu", "disney", "spotify", "apple tv", "hbo", "chrome")
        val hasApp = appNames.any { normalized.hasWord(it) }
        val hasVolume = listOf("tang am", "giam am", "to len", "nho xuong", "volume up", "volume down").any { normalized.hasWord(it) }
        val hasNav = listOf("len tren", "xuong duoi", "sang trai", "sang phai", "trang chu", "quay lai", "tro lai").any { normalized.hasWord(it) }
        val hasSearch = listOf("tim kiem", "search", "tim").any { normalized.hasWord(it) }

        return (hasOpen && hasApp) || hasVolume || hasNav || hasSearch
    }

    private fun parsePower(normalized: String, rawText: String): VoiceIntent? {
        val offKeywords = listOf("tat tivi", "tat tv", "turn off", "power off")
        for (keyword in offKeywords) {
            val idx = findWordIndex(normalized, keyword)
            if (idx != -1) {
                if (isNegatedAround(normalized, keyword)) {
                    continue
                }
                return VoiceIntent.TurnOff(rawText)
            }
        }

        val onKeywords = listOf("mo tivi", "mo tv", "bat tivi", "bat tv", "turn on", "power on", "wake up")
        for (keyword in onKeywords) {
            val idx = findWordIndex(normalized, keyword)
            if (idx != -1) {
                if (isNegatedAround(normalized, keyword)) {
                    continue
                }
                return VoiceIntent.TurnOn(rawText)
            }
        }
        return null
    }

    private fun parseMute(normalized: String, rawText: String): VoiceIntent? {
        val muteKeywords = listOf("tat tieng", "im lang", "mute", "unmute", "mat tieng")
        for (keyword in muteKeywords) {
            if (normalized.hasWord(keyword)) {
                return VoiceIntent.MuteToggle(rawText)
            }
        }
        return null
    }

    private fun parseVolume(normalized: String, rawText: String): VoiceIntent? {
        val upKeywords = listOf("tang am", "to len", "volume up")
        for (keyword in upKeywords) {
            if (normalized.hasWord(keyword)) {
                val steps = extractNumber(normalized) ?: 1
                return VoiceIntent.VolumeUp(rawText, steps)
            }
        }

        val downKeywords = listOf("giam am", "nho xuong", "nho lai", "volume down")
        for (keyword in downKeywords) {
            if (normalized.hasWord(keyword)) {
                val steps = extractNumber(normalized) ?: 1
                return VoiceIntent.VolumeDown(rawText, steps)
            }
        }

        return null
    }

    private fun extractNumber(text: String): Int? {
        val match = "\\d+".toRegex().find(text)
        return match?.value?.toIntOrNull()
    }

    private fun parseAppLaunch(normalized: String, rawText: String): VoiceIntent? {
        val openKeywords = listOf("mo", "open", "launch", "bat")
        var earliestOpenIdx = Int.MAX_VALUE
        for (keyword in openKeywords) {
            val idx = findWordIndex(normalized, keyword)
            if (idx != -1 && idx < earliestOpenIdx) {
                earliestOpenIdx = idx
            }
        }
        if (earliestOpenIdx == Int.MAX_VALUE) {
            return null // Must have an open keyword
        }

        val appEntries = listOf(
            AppSymbol.YOUTUBE to listOf("youtube"),
            AppSymbol.NETFLIX to listOf("netflix"),
            AppSymbol.PRIME_VIDEO to listOf("amazon prime", "prime"),
            AppSymbol.HULU to listOf("hulu"),
            AppSymbol.DISNEY_PLUS to listOf("disney"),
            AppSymbol.SPOTIFY to listOf("spotify"),
            AppSymbol.APPLE_TV to listOf("apple tv"),
            AppSymbol.HBO_MAX to listOf("hbo"),
            AppSymbol.BROWSER to listOf("chrome", "trinh duyet", "browser")
        )

        var bestApp: AppSymbol? = null
        var minAppIdx = Int.MAX_VALUE

        for ((symbol, aliases) in appEntries) {
            for (alias in aliases) {
                val appIdx = findWordIndex(normalized, alias)
                if (appIdx != -1 && appIdx > earliestOpenIdx) {
                    if (appIdx < minAppIdx) {
                        minAppIdx = appIdx
                        bestApp = symbol
                    } else if (appIdx == minAppIdx && bestApp != symbol) {
                        bestApp = null
                    }
                }
            }
        }

        return if (bestApp != null) VoiceIntent.OpenApp(bestApp, rawText) else null
    }

    private fun parseNavigation(normalized: String, rawText: String): VoiceIntent? {
        val navMappings = listOf(
            listOf("len", "up") to RemoteCommand.DPadUp,
            listOf("xuong", "down")
            to RemoteCommand.DPadDown,
            listOf("trai", "left") to RemoteCommand.DPadLeft,
            listOf("phai", "right") to RemoteCommand.DPadRight,
            listOf("chon", "ok", "enter", "select") to RemoteCommand.Ok,
            listOf("ve", "back", "quay lai", "tro lai") to RemoteCommand.Back,
            listOf("trang chu", "home") to RemoteCommand.Home
        )

        for ((keywords, cmd) in navMappings) {
            for (keyword in keywords) {
                if (normalized.hasWord(keyword)) {
                    return VoiceIntent.Navigation(cmd, rawText)
                }
            }
        }
        return null
    }

    private fun parseSearch(normalized: String, rawText: String): VoiceIntent? {
        val searchPrefixes = listOf("tim kiem", "search", "tim")
        for (prefix in searchPrefixes) {
            val idx = findWordIndex(normalized, prefix)
            if (idx != -1) {
                val query = normalized.substring(idx + prefix.length).trim()
                if (query.isNotEmpty()) {
                    return VoiceIntent.SearchContent(query, rawText)
                }
            }
        }
        return null
    }
}
