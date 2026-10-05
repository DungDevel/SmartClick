package com.example.smartclick.app.domain.model

data class UserAccount(
    val id: String,
    val name: String?,
    val plan: SubscriptionPlan = SubscriptionPlan.FREE
)
