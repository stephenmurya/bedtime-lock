package com.omeniv.bedtimelock

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.ZenPolicy
import android.service.notification.Condition

object BedtimeDndController {
    private val conditionId = Uri.parse("condition://com.omeniv.bedtimelock/bedtime")

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun apply(context: Context) {
        if (!hasAccess(context)) {
            NativeLog.d("bedtime DND unavailable - policy access missing")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            val store = BedtimeStateStore(context)
            val ruleId = store.dndRuleId
            if (ruleId == null || manager.getAutomaticZenRule(ruleId) == null) {
                val rule = if (Build.VERSION.SDK_INT >= 35) {
                    AutomaticZenRule(
                        "Bedtime Lock",
                        ComponentName(context, BedtimeZenRuleService::class.java),
                        null,
                        conditionId,
                        bedtimePolicy(),
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        true,
                    )
                } else {
                    AutomaticZenRule(
                        "Bedtime Lock",
                        ComponentName(context, BedtimeZenRuleService::class.java),
                        conditionId,
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        true,
                    )
                }
                manager.addAutomaticZenRule(rule)?.also {
                    store.setDndRuleId(it)
                    publishState(manager, it, active = true)
                }
            } else {
                val rule = manager.getAutomaticZenRule(ruleId) ?: return
                rule.isEnabled = true
                rule.interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
                if (Build.VERSION.SDK_INT >= 35) rule.zenPolicy = bedtimePolicy()
                manager.updateAutomaticZenRule(ruleId, rule)
                publishState(manager, ruleId, active = true)
            }
            NativeLog.d("bedtime DND applied")
        } catch (_: SecurityException) {
            NativeLog.d("bedtime DND rejected by system")
        }
    }

    fun disable(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val ruleId = BedtimeStateStore(context).dndRuleId ?: return
        try {
            val rule = manager.getAutomaticZenRule(ruleId) ?: return
            rule.isEnabled = false
            manager.updateAutomaticZenRule(ruleId, rule)
            publishState(manager, ruleId, active = false)
            NativeLog.d("bedtime DND disabled")
        } catch (_: SecurityException) {
            NativeLog.d("bedtime DND disable rejected by system")
        }
    }

    private fun bedtimePolicy(): ZenPolicy = ZenPolicy.Builder()
        .disallowAllSounds()
        .allowAlarms(true)
        .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
        .allowRepeatCallers(true)
        .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
        .allowEvents(false)
        .allowReminders(false)
        .allowMedia(false)
        .allowSystem(false)
        .showFullScreenIntent(false)
        .showInNotificationList(false)
        .showPeeking(false)
        .showStatusBarIcons(false)
        .showBadges(false)
        .showLights(false)
        .showInAmbientDisplay(false)
        .build()

    private fun condition(active: Boolean) = Condition(
        conditionId,
        if (active) "Bedtime Lock active" else "Bedtime Lock inactive",
        if (active) Condition.STATE_TRUE else Condition.STATE_FALSE,
    )

    private fun publishState(manager: NotificationManager, ruleId: String, active: Boolean) {
        if (Build.VERSION.SDK_INT >= 35) {
            manager.setAutomaticZenRuleState(ruleId, condition(active))
        } else {
            BedtimeZenRuleService.publishState(active)
        }
    }
}
