package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import kotlinx.coroutines.flow.Flow

interface IChannelRegistryRepository {
    fun getSupportedChannels(): Flow<List<AppLaunchConfig>>
    fun getTvChannels(category: ChannelCategory = ChannelCategory.POPULAR): Flow<List<TvChannel>>
}
