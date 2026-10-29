package com.example.smartclick.app.features.onboarding

internal object SupportEmailRequest {

    fun dispatch(
        supportEmail: String,
        unavailableMessageResourceId: Int,
        openMailto: (String) -> Unit,
        showUnavailable: (Int) -> Unit
    ) {
        val email = supportEmail.trim()
        if (email.isEmpty()) {
            showUnavailable(unavailableMessageResourceId)
        } else {
            openMailto("mailto:$email")
        }
    }
}
