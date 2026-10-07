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

        return parsePower(normalized, rawText)
            ?: parseMute(normalized, rawText)
            ?: parseVolume(normalized, rawText)
            ?: parseAppLaunch(normalized, rawText)
            ?: parseNavigation(normalized, rawText)
            ?: parseSearch(normalized, rawText)
            ?: VoiceIntent.Unrecognized(rawText)
    }

    private fun parsePower(normalized: String, rawText: String): VoiceIntent? {
        if (normalized.matches(".*(tat tivi|tat tv|turn off|power off).*".toRegex())) {
            return VoiceIntent.TurnOff(rawText)
        }
        if (normalized.matches(".*(mo tivi|mo tv|bat tivi|bat tv|turn on|power on|wake up).*".toRegex())) {
            return VoiceIntent.TurnOn(rawText)
        }
        return null
    }

    private fun parseMute(normalized: String, rawText: String): VoiceIntent? {
        if (normalized.matches(".*(tat tieng|im lang|mute|unmute|mat tieng).*".toRegex())) {
            return VoiceIntent.MuteToggle(rawText)
        }
        return null
    }

    private fun parseVolume(normalized: String, rawText: String): VoiceIntent? {
        val upRegex = ".*(tang am|to len|volume up).*".toRegex()
        val downRegex = ".*(giam am|nho xuong|nho lai|volume down).*".toRegex()

        if (normalized.matches(upRegex)) {
            val steps = extractNumber(normalized) ?: 1
            return VoiceIntent.VolumeUp(rawText, steps)
        }

        if (normalized.matches(downRegex)) {
            val steps = extractNumber(normalized) ?: 1
            return VoiceIntent.VolumeDown(rawText, steps)
        }

        return null
    }

    private fun extractNumber(text: String): Int? {
        val match = "\\d+".toRegex().find(text)
        return match?.value?.toIntOrNull()
    }

    private fun parseAppLaunch(normalized: String, rawText: String): VoiceIntent? {
        if (!normalized.contains("mo") && !normalized.contains("open") && !normalized.contains("launch") && !normalized.contains("bat")) {
            return null // Must have an open keyword
        }

        val app = when {
            normalized.contains("youtube") -> AppSymbol.YOUTUBE
            normalized.contains("netflix") -> AppSymbol.NETFLIX
            normalized.contains("prime") || normalized.contains("amazon prime") -> AppSymbol.PRIME_VIDEO
            normalized.contains("hulu") -> AppSymbol.HULU
            normalized.contains("disney") -> AppSymbol.DISNEY_PLUS
            normalized.contains("spotify") -> AppSymbol.SPOTIFY
            normalized.contains("apple tv") -> AppSymbol.APPLE_TV
            normalized.contains("hbo") -> AppSymbol.HBO_MAX
            normalized.contains("chrome") || normalized.contains("trinh duyet") || normalized.contains("browser") -> AppSymbol.BROWSER
            else -> return null
        }

        return VoiceIntent.OpenApp(app, rawText)
    }

    private fun parseNavigation(normalized: String, rawText: String): VoiceIntent? {
        val cmd = when {
            normalized.matches(".*(len|up).*".toRegex()) -> RemoteCommand.DPadUp
            normalized.matches(".*(xuong|down).*".toRegex()) -> RemoteCommand.DPadDown
            normalized.matches(".*(trai|left).*".toRegex()) -> RemoteCommand.DPadLeft
            normalized.matches(".*(phai|right).*".toRegex()) -> RemoteCommand.DPadRight
            normalized.matches(".*(chon|ok|enter|select).*".toRegex()) -> RemoteCommand.Ok
            normalized.matches(".*(ve|back|quay lai|tro lai).*".toRegex()) -> RemoteCommand.Back
            normalized.matches(".*(trang chu|home).*".toRegex()) -> RemoteCommand.Home
            else -> null
        }

        return if (cmd != null) VoiceIntent.Navigation(cmd, rawText) else null
    }

    private fun parseSearch(normalized: String, rawText: String): VoiceIntent? {
        val searchPrefixes = listOf("tim kiem", "search", "tim")
        for (prefix in searchPrefixes) {
            val index = normalized.indexOf(prefix)
            if (index != -1) {
                // Ignore if it's just the prefix with nothing after it
                val query = normalized.substring(index + prefix.length).trim()
                if (query.isNotEmpty()) {
                    return VoiceIntent.SearchContent(query, rawText)
                }
            }
        }
        return null
    }
}
