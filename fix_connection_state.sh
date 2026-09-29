sed -i 's/val connectionState: StateFlow<TvConnectionState> = activeTvSession.currentController/val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState/g' app/src/main/java/com/example/smartclick/app/features/channels/ChannelsViewModel.kt
sed -i '36,44d' app/src/main/java/com/example/smartclick/app/features/channels/ChannelsViewModel.kt
sed -i 's/val connectionState: StateFlow<TvConnectionState> = activeTvSession.currentController/val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState/g' app/src/main/java/com/example/smartclick/app/features/cast/CastViewModel.kt
sed -i '33,40d' app/src/main/java/com/example/smartclick/app/features/cast/CastViewModel.kt
