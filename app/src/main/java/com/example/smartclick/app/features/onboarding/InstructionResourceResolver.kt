package com.example.smartclick.app.features.onboarding

internal object InstructionResourceResolver {

    fun resolve(
        key: String,
        fallbackResourceId: Int,
        findResourceId: (String) -> Int,
        warn: (String) -> Unit
    ): Int {
        val resourceId = findResourceId(key)
        if (resourceId != 0) return resourceId

        warn("Missing pairing instruction resource: $key")
        return fallbackResourceId
    }
}
