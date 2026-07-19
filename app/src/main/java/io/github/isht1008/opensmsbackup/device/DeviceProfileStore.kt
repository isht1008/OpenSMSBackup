package io.github.isht1008.opensmsbackup.device

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import java.util.UUID

class DeviceProfileStore private constructor(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun getOrCreate(): DeviceProfile {
        readProfile()?.let { return it }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val profile = DeviceProfile(
            deviceId = id,
            manufacturer = manufacturer,
            model = model,
            marketingName = null,
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            primaryPhoneNumber = null,
            secondaryPhoneNumber = null,
            displayName = DeviceDisplayName.initialBaseName(null, manufacturer, model),
            createdAt = now,
            updatedAt = now,
            defaultRegion = CountryRegion.initial()
        )
        dataStore.edit { preferences ->
            if (preferences[DEVICE_ID] == null) write(preferences, profile)
        }
        return requireNotNull(readProfile())
    }

    suspend fun update(
        displayName: String,
        primaryPhoneNumber: String?,
        secondaryPhoneNumber: String?,
        defaultRegion: String? = null
    ): DeviceProfile {
        val current = getOrCreate()
        val updated = current.copy(
            displayName = DeviceDisplayName.sanitizeLabelSegment(displayName)
                .takeIf { it.isNotBlank() }
                ?: current.displayName,
            primaryPhoneNumber = primaryPhoneNumber?.trim()
                ?.takeIf { it.isNotBlank() } ?: current.primaryPhoneNumber,
            secondaryPhoneNumber = secondaryPhoneNumber?.trim()
                ?.takeIf { it.isNotBlank() } ?: current.secondaryPhoneNumber,
            updatedAt = System.currentTimeMillis(),
            defaultRegion = defaultRegion?.let(CountryRegion::validated)
                ?: current.defaultRegion
        )
        dataStore.edit { write(it, updated) }
        return updated
    }

    suspend fun getGmailDeviceLabelId(profileId: String): String? =
        dataStore.data.first()[stringPreferencesKey("gmail_device_label_$profileId")]

    suspend fun setGmailDeviceLabelId(profileId: String, labelId: String) {
        dataStore.edit { it[stringPreferencesKey("gmail_device_label_$profileId")] = labelId }
    }

    private suspend fun readProfile(): DeviceProfile? {
        val p = dataStore.data.first()
        val id = p[DEVICE_ID] ?: return null
        return DeviceProfile(
            id, p[MANUFACTURER].orEmpty(), p[MODEL].orEmpty(), p[MARKETING_NAME],
            p[ANDROID_VERSION].orEmpty(), p[PRIMARY_PHONE], p[SECONDARY_PHONE],
            p[DISPLAY_NAME] ?: "Android Phone", p[CREATED_AT] ?: 0L,
            p[UPDATED_AT] ?: 0L, p[DEFAULT_REGION] ?: CountryRegion.initial()
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, d: DeviceProfile) {
        p[DEVICE_ID] = d.deviceId; p[MANUFACTURER] = d.manufacturer; p[MODEL] = d.model
        d.marketingName?.let { p[MARKETING_NAME] = it }; p[ANDROID_VERSION] = d.androidVersion
        d.primaryPhoneNumber?.let { p[PRIMARY_PHONE] = it }; d.secondaryPhoneNumber?.let { p[SECONDARY_PHONE] = it }
        p[DISPLAY_NAME] = d.displayName; p[CREATED_AT] = d.createdAt; p[UPDATED_AT] = d.updatedAt
        p[DEFAULT_REGION] = d.defaultRegion
    }

    companion object {
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val MANUFACTURER = stringPreferencesKey("manufacturer")
        private val MODEL = stringPreferencesKey("model")
        private val MARKETING_NAME = stringPreferencesKey("marketing_name")
        private val ANDROID_VERSION = stringPreferencesKey("android_version")
        private val PRIMARY_PHONE = stringPreferencesKey("primary_phone")
        private val SECONDARY_PHONE = stringPreferencesKey("secondary_phone")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val CREATED_AT = longPreferencesKey("created_at")
        private val UPDATED_AT = longPreferencesKey("updated_at")
        private val DEFAULT_REGION = stringPreferencesKey("default_region")
        @Volatile private var instance: DeviceProfileStore? = null
        fun create(context: Context): DeviceProfileStore = instance ?: synchronized(this) {
            instance ?: DeviceProfileStore(
                PreferenceDataStoreFactory.create {
                    context.applicationContext.preferencesDataStoreFile("device_profile.preferences_pb")
                }
            ).also { instance = it }
        }
    }
}
