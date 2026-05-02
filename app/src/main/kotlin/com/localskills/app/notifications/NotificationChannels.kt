package com.localskills.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Channel registration helper. Channels are NOT auto-registered at app start —
 * P5 wires this into [com.localskills.app.LocalSkillsApp.onCreate]. The
 * notifier calls [ensureRegistered] defensively before posting.
 */
object NotificationChannels {

    const val RULES_CHANNEL_ID: String = "local_skills_rules"
    const val RULES_CHANNEL_NAME: String = "Skill reminders"
    const val RULES_CHANNEL_DESC: String = "Reminders fired by skill rules (e.g. coupon expiry)."

    @Volatile private var registered: Boolean = false

    fun ensureRegistered(context: Context) {
        if (registered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(RULES_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    RULES_CHANNEL_ID,
                    RULES_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = RULES_CHANNEL_DESC }
                manager.createNotificationChannel(channel)
            }
        }
        registered = true
    }
}
