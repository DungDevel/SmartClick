package com.example.smartclick.app.data.fake

import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import com.example.smartclick.app.domain.repository.IChannelRegistryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeChannelRegistryRepository @Inject constructor() : IChannelRegistryRepository {
    override fun getSupportedChannels(): Flow<List<AppLaunchConfig>> {
        return flowOf(
            listOf(
                AppLaunchConfig("1", "YouTube", "com.google.android.youtube.tv"),
                AppLaunchConfig("2", "Netflix", "com.netflix.ninja"),
                AppLaunchConfig("3", "Prime", "com.amazon.amazonvideo.livingroom"),
                AppLaunchConfig("4", "Disney+", "com.disney.disneyplus"),
                AppLaunchConfig("5", "Spotify", "com.spotify.tv.android"),
                AppLaunchConfig("6", "HBO Max", "com.hbo.hbonow")
            )
        )
    }

    override fun getTvChannels(category: ChannelCategory): Flow<List<TvChannel>> {
        val allChannels = listOf(
            TvChannel("c1", "News 24/7", "N24", ChannelCategory.POPULAR, 4),
            TvChannel("c2", "Sports Live", "SL", ChannelCategory.POPULAR, 5),
            TvChannel("c3", "Movie Hub", "MH", ChannelCategory.POPULAR, 4),
            TvChannel("c4", "Kids Zone", "KZ", ChannelCategory.POPULAR, 5),
            TvChannel("c5", "Music TV", "MTV", ChannelCategory.POPULAR, 3),
            TvChannel("c6", "DocuWorld", "DW", ChannelCategory.POPULAR, 4),
            TvChannel("c7", "Comedy Central", "CC", ChannelCategory.RECOMMENDED, 5),
            TvChannel("c8", "Sci-Fi Channel", "SFC", ChannelCategory.RECOMMENDED, 4)
        )
        return flowOf(allChannels.filter { it.category == category })
    }
}
