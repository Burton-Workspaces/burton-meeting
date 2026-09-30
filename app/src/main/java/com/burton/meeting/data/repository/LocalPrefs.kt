package com.burton.meeting.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.burtonStore: DataStore<Preferences> by preferencesDataStore("burton_meeting")

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.burtonStore

    val displayName: Flow<String> = store.data.map { it[DISPLAY_NAME].orEmpty() }

    suspend fun setDisplayName(name: String) {
        store.edit { it[DISPLAY_NAME] = name.trim() }
    }

    private companion object {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
