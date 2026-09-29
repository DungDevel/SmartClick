$content = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt"

$content = $content -replace 'val reconnectAttempt = \f?', 'val isReconnecting = connectionState is TvConnectionState.Reconnecting
        val reconnectAttempt = '

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt" -Value $content -Encoding UTF8
