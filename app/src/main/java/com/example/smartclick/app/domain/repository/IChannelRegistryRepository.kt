package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import com.example.smartclick.app.domain.usecase.InstalledChannelsState
import kotlinx.coroutines.flow.Flow

interface IChannelRegistryRepository {
    fun getSupportedChannels(): Flow<InstalledChannelsState>
    fun getTvChannels(category: ChannelCategory = ChannelCategory.POPULAR): Flow<List<TvChannel>>
}
