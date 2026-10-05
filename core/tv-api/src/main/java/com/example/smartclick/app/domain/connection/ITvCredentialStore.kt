package com.example.smartclick.app.domain.connection

interface ITvCredentialStore {
    /**
     * Stores credentials for a given device securely.
     * Returns a string reference (alias/id) that can be stored in the local database.
     */
    suspend fun saveCredentials(deviceId: String, credentials: Credentials): String

    /**
     * Retrieves credentials for a given device using the stored reference.
     */
    suspend fun getCredentials(deviceId: String, credentialsRef: String): Credentials?

    /**
     * Removes credentials for a given device.
     */
    suspend fun clearCredentials(deviceId: String, credentialsRef: String)
}
