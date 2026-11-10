package com.example.smartclick.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `mute state is stored and retrieved independently per deviceId via MuteStateStore`() = runTest {
        val testFile = temporaryFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )

        val muteStateStore = MuteStateStore(dataStore)

        assertNull(muteStateStore.getMuteState("tv-1"))
        assertNull(muteStateStore.getMuteState("tv-2"))

        muteStateStore.setMuteState("tv-1", 1)
        muteStateStore.setMuteState("tv-2", 0)

        assertEquals(1, muteStateStore.getMuteState("tv-1"))
        assertEquals(0, muteStateStore.getMuteState("tv-2"))

        muteStateStore.setMuteState("tv-1", 0)
        assertEquals(0, muteStateStore.getMuteState("tv-1"))
        assertEquals(0, muteStateStore.getMuteState("tv-2"))
    }
}
