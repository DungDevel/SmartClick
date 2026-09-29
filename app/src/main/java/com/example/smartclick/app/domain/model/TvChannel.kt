package com.example.smartclick.app.domain.model

enum class ChannelCategory {
    POPULAR,
    RECOMMENDED
}

data class TvChannel(
    val id: String,
    val name: String,
    val label: String,
    val category: ChannelCategory,
    val rating: Int = 0
)
