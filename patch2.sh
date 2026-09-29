sed -i '/val isReconnecting = connectionState is TvConnectionState.Reconnecting/d' app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt
sed -i '/val reconnectAttempt = (connectionState as? TvConnectionState.Reconnecting)?.attempt ?: 0/d' app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt

sed -i '/Reconnecting.../a \
        val reconnectAttempt = (connectionState as? TvConnectionState.Reconnecting)?.attempt ?: 0' app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt

sed -i '/modifier = Modifier/i \
        val isReconnecting = connectionState is TvConnectionState.Reconnecting' app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt
