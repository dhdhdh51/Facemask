package com.jawlinefit.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore("user_preferences")

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val language: String = "en",
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val hapticsEnabled: Boolean = true,
    val allergens: Set<String> = emptySet()
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val language = stringPreferencesKey("language")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val allergens = stringPreferencesKey("allergens")
    }

    val settings: Flow<UserSettings> = context.userDataStore.data.map { values ->
        UserSettings(
            onboardingComplete = values[Keys.onboarding] ?: false,
            language = values[Keys.language] ?: "en",
            remindersEnabled = values[Keys.reminders] ?: false,
            reminderHour = (values[Keys.reminderHour] ?: 19).coerceIn(0, 23),
            reminderMinute = (values[Keys.reminderMinute] ?: 0).coerceIn(0, 59),
            hapticsEnabled = values[Keys.haptics] ?: true,
            allergens = values[Keys.allergens]?.split('|')?.filter(String::isNotBlank)?.toSet() ?: emptySet()
        )
    }

    suspend fun completeOnboarding(language: String, age: Int) {
        require(age >= 13)
        context.userDataStore.edit {
            it[Keys.onboarding] = true
            it[Keys.language] = language
        }
    }
    suspend fun setLanguage(value: String) = context.userDataStore.edit { it[Keys.language] = value }
    suspend fun setReminders(enabled: Boolean, hour: Int, minute: Int) = context.userDataStore.edit {
        it[Keys.reminders] = enabled
        it[Keys.reminderHour] = hour.coerceIn(0, 23)
        it[Keys.reminderMinute] = minute.coerceIn(0, 59)
    }
    suspend fun setHaptics(value: Boolean) = context.userDataStore.edit { it[Keys.haptics] = value }
    suspend fun setAllergens(value: Set<String>) = context.userDataStore.edit { it[Keys.allergens] = value.sorted().joinToString("|") }
    suspend fun clear() = context.userDataStore.edit { it.clear() }
}
