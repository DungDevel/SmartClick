sed -i 's/val activeDevice: StateFlow<TvDevice?> = connectionState/val activeDevice: StateFlow<TvDevice?> = activeTvSession.currentController/' app/src/main/java/com/example/smartclick/app/features/channels/ChannelsViewModel.kt
sed -i '/\.map { state ->/,/}/c\        .map { it?.device }' app/src/main/java/com/example/smartclick/app/features/channels/ChannelsViewModel.kt
