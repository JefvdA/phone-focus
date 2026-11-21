package dev.phonefocus.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.phonefocus.application.FocusRecord
import dev.phonefocus.application.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [FocusSessionRepository] backed by a Preferences DataStore (survives app kill and reboot). */
class DataStoreFocusSessionRepository(
    private val dataStore: DataStore<Preferences>,
) : FocusSessionRepository {
    override val records: Flow<FocusRecord> = dataStore.data.map(FocusRecordCodec::decode)

    override suspend fun update(transform: (FocusRecord) -> FocusRecord): FocusRecord {
        val saved = dataStore.updateData { prefs ->
            val current = FocusRecordCodec.decode(prefs)
            val next = transform(current)
            if (next == current) {
                prefs
            } else {
                prefs.toMutablePreferences().also { FocusRecordCodec.encode(next, it) }
            }
        }
        return FocusRecordCodec.decode(saved)
    }
}
