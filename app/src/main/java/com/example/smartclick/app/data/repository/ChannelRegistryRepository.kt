package com.example.smartclick.app.data.repository

import com.example.smartclick.app.data.catalog.ChannelCatalog
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import com.example.smartclick.app.domain.repository.IChannelRegistryRepository
import com.example.smartclick.app.domain.usecase.GetInstalledChannelsUseCase
import com.example.smartclick.app.domain.usecase.InstalledChannelsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRegistryRepository @Inject constructor(
    private val catalog: ChannelCatalog,
    private val getInstalledChannels: GetInstalledChannelsUseCase
) : IChannelRegistryRepository {
    override fun getSupportedChannels(): Flow<InstalledChannelsState> = flow {
        emit(InstalledChannelsState.Loading)
        emit(getInstalledChannels())
    }

    override fun getTvChannels(category: ChannelCategory): Flow<List<TvChannel>> = flow {
        val channels = catalog.load().getOrElse { emptyList() }
            .asSequence()
            .filter { it.type == "channel" }
            .mapNotNull { entry ->
                val entryCategory = if (entry.category.equals("recommended", ignoreCase = true)) {
                    ChannelCategory.RECOMMENDED
                } else {
                    ChannelCategory.POPULAR
                }
                if (entryCategory != category) return@mapNotNull null
                TvChannel(
                    id = entry.id,
                    name = entry.name,
                    label = entry.protocolIds["channel"] ?: entry.name.take(3).uppercase(),
                    category = entryCategory
                )
            }
            .toList()
        emit(channels)
    }
}
