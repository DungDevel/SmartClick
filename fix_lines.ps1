$content = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt"

# Use regex to find everything from `Row(` right after Header to `if (activeDevice == null && !isReconnecting) {`
$pattern = '(?s)        Row\(\r?\n            modifier = Modifier\r?\n                \.padding\(horizontal = 16\.dp, vertical = 3\.dp\).*?if \(activeDevice == null && !isReconnecting\) \{'

$replacement = @"
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .offset(y = (-20).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (activeDevice != null) { if (isReconnecting) colors.orange else colors.green } else colors.tx2, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = activeDevice?.let { if (isReconnecting) "`${it.name} · Reconnecting..." else "`${it.name} · Connected" } ?: "No device connected",
                color = colors.tx2,
                fontSize = 15.sp
            )
        }

        val reconnectAttempt = (connectionState as? TvConnectionState.Reconnecting)?.attempt ?: 0
        ReconnectBanner(isVisible = isReconnecting, attempt = reconnectAttempt)

        if (activeDevice == null && !isReconnecting) {
"@

$content = [regex]::Replace($content, $pattern, $replacement)

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt" -Value $content -Encoding UTF8
