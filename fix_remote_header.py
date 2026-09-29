import sys

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

target = """
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (activeDevice != null) {
                            if (isReconnecting) colors.orange else colors.green
                        } else colors.tx2, CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = activeDevice?.let { if (isReconnecting) "${it.name} · Reconnecting..." else "${it.name} · Connected" }
                    ?: "No device connected",
                color = colors.tx2,
                fontSize = 15.sp
            )
        }
"""

replacement = """
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (connectionState) {
                is TvConnectionState.Connected -> colors.green
                is TvConnectionState.Connecting, is TvConnectionState.Reconnecting -> colors.orange
                is TvConnectionState.Failed -> colors.acc // Red
                else -> colors.tx2
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val statusText = when (connectionState) {
                is TvConnectionState.Connected -> "${activeDevice?.name} · " + stringResource(R.string.connected)
                is TvConnectionState.Connecting, is TvConnectionState.Reconnecting -> "${activeDevice?.name} · " + stringResource(R.string.connecting)
                is TvConnectionState.Failed -> "${activeDevice?.name} · " + stringResource(R.string.failed)
                else -> stringResource(R.string.no_device)
            }
            Text(
                text = statusText,
                color = colors.tx2,
                fontSize = 15.sp
            )
        }
"""

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "w", encoding="utf-8") as f:
    f.write(content.replace(target, replacement))
