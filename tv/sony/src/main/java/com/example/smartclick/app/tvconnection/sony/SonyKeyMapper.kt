package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.model.RemoteCommand

object SonyKeyMapper {
    fun mapCommandToIrcc(command: RemoteCommand): String? {
        val code = when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff -> "AAAAAQAAAAEAAAAVAw=="
            RemoteCommand.PowerOn -> "AAAAAQAAAAEAAAAuAw=="
            RemoteCommand.Home -> "AAAAAQAAAAEAAABgAw=="
            RemoteCommand.Back -> "AAAAAgAAAJcAAAAjAw=="
            RemoteCommand.DPadUp -> "AAAAAQAAAAEAAAB0Aw=="
            RemoteCommand.DPadDown -> "AAAAAQAAAAEAAAB1Aw=="
            RemoteCommand.DPadLeft -> "AAAAAQAAAAEAAAA0Aw=="
            RemoteCommand.DPadRight -> "AAAAAQAAAAEAAAAzAw=="
            RemoteCommand.Ok -> "AAAAAQAAAAEAAABlAw=="
            RemoteCommand.VolumeUp -> "AAAAAQAAAAEAAAASAw=="
            RemoteCommand.VolumeDown -> "AAAAAQAAAAEAAAATAw=="
            RemoteCommand.Mute, RemoteCommand.MuteOn, RemoteCommand.MuteOff -> "AAAAAQAAAAEAAAAUAw=="
            RemoteCommand.ChannelUp -> "AAAAAQAAAAEAAAAQAw=="
            RemoteCommand.ChannelDown -> "AAAAAQAAAAEAAAARAw=="
            is RemoteCommand.Numpad -> when (command.digit) {
                0 -> "AAAAAQAAAAEAAAAFAw=="
                1 -> "AAAAAQAAAAEAAAAAAw=="
                2 -> "AAAAAQAAAAEAAAABAw=="
                3 -> "AAAAAQAAAAEAAAACAw=="
                4 -> "AAAAAQAAAAEAAAADAw=="
                5 -> "AAAAAQAAAAEAAAAEAw=="
                6 -> "AAAAAQAAAAEAAAAFAw=="
                7 -> "AAAAAQAAAAEAAAAGAw=="
                8 -> "AAAAAQAAAAEAAAAHAw=="
                9 -> "AAAAAQAAAAEAAAAIAw=="
                else -> null
            }
            else -> null
        }

        if (code == null) return null

        return """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
                        <IRCCCode>$code</IRCCCode>
                    </u:X_SendIRCC>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}
