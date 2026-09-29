$content = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt"

# Remove all `val isReconnecting = ...` lines
$content = $content -replace "(?m)^\s*val isReconnecting = connectionState is TvConnectionState.Reconnecting\r?\n", ""

# Put it correctly after `val haptic = LocalHapticFeedback.current`
$content = $content -replace 'val haptic = LocalHapticFeedback.current', 'val haptic = LocalHapticFeedback.current

    val isReconnecting = connectionState is TvConnectionState.Reconnecting'

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt" -Value $content -Encoding UTF8
