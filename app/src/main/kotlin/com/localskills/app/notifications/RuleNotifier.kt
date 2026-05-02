package com.localskills.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localskills.app.data.db.entity.RuleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts notifications for rules that have fired, with two safety nets:
 *
 *  - **Dedupe** by `(ruleId, fireDate)` — the same rule cannot post twice
 *    on the same calendar day for the same idempotency key.
 *  - **Rate limit** per rule per day (default 3) — even with distinct keys,
 *    a noisy rule cannot spam the user.
 *
 * Markers are persisted in DataStore so dedupe survives process death. The
 * actual platform notification call is delegated to a [Poster] so tests can
 * verify dedupe/rate-limit logic without an Android runtime.
 */
@Singleton
class RuleNotifier @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val poster: Poster,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** Strategy for actually pushing the notification onto the system tray. */
    interface Poster {
        fun post(rule: RuleEntity)
    }

    /**
     * @return true when a notification was posted, false when suppressed by
     *         dedupe or rate-limit.
     */
    suspend fun dispatch(rule: RuleEntity, fireKey: String? = null): Boolean {
        val today = LocalDate.now(clock)
        val key = fireKey ?: today.toString()
        val dedupeKey = dedupeKey(rule.id, key)
        val countKey = countKey(rule.id, today)

        val prefs = dataStore.data.first()
        val alreadyFired = prefs[stringPreferencesKey(dedupeKey)] != null
        if (alreadyFired) return false

        val firedToday = prefs[intPreferencesKey(countKey)] ?: 0
        if (firedToday >= MAX_PER_DAY) return false

        poster.post(rule)

        dataStore.edit { editor ->
            editor[stringPreferencesKey(dedupeKey)] = clock.instant().toString()
            editor[intPreferencesKey(countKey)] = firedToday + 1
        }
        return true
    }

    companion object {
        const val MAX_PER_DAY: Int = 3

        fun dedupeKey(ruleId: String, fireKey: String): String = "fired:$ruleId:$fireKey"
        fun countKey(ruleId: String, day: LocalDate): String = "count:$ruleId:$day"

        /** DataStore name for rule fire markers. P5 may relocate this. */
        const val DATASTORE_NAME: String = "rule_notifier"
    }
}

/**
 * Production [RuleNotifier.Poster] that talks to [NotificationManager]. Held
 * as a separate class so the dedupe logic in [RuleNotifier] stays free of
 * Android dependencies for unit testing.
 */
@Singleton
class AndroidNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
) : RuleNotifier.Poster {

    override fun post(rule: RuleEntity) {
        NotificationChannels.ensureRegistered(context)

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val title = rule.title ?: "Skill reminder"
        val body = rule.body ?: "A rule on one of your skills just matched."

        val contentIntent = Intent().apply {
            setClassName(context, "com.localskills.app.ui.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            rule.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.RULES_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(rule.id.hashCode(), notification)
    }
}

/** Top-level extension binds the DataStore singleton to the application context. */
val Context.ruleNotifierDataStore by preferencesDataStore(name = RuleNotifier.DATASTORE_NAME)
