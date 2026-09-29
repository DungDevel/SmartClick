package com.example.smartclick.app.domain.connection

enum class ConnectProgress {
    DISCOVERING,
    CONNECTING_SOCKET,
    WAITING_FOR_USER_ACCEPT_ON_TV,
    SAVING_PAIRING_KEY,
    COMPLETED
}

