package com.omeniv.bedtimelock

import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ConditionProviderService

class BedtimeZenRuleService : ConditionProviderService() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onConnected() {
        publishState(currentState())
    }

    override fun onRequestConditions(relevance: Int) {
        publishState(currentState())
    }

    override fun onSubscribe(conditionId: Uri?) {
        if (conditionId == CONDITION_ID) publishState(currentState())
    }

    override fun onUnsubscribe(conditionId: Uri?) = Unit

    private fun currentState(): Boolean {
        val store = BedtimeStateStore(this)
        return store.sessionActive && store.doNotDisturbEnabled &&
            store.temporaryUnlockExpiry <= System.currentTimeMillis()
    }

    private fun publishState(active: Boolean) {
        try {
            notifyCondition(condition(active))
        } catch (_: SecurityException) {
            NativeLog.d("bedtime DND condition rejected by system")
        }
    }

    companion object {
        private val CONDITION_ID = Uri.parse("condition://com.omeniv.bedtimelock/bedtime")
        @Volatile private var instance: BedtimeZenRuleService? = null

        fun publishState(active: Boolean) {
            instance?.publishState(active)
        }

        private fun condition(active: Boolean) = Condition(
            CONDITION_ID,
            if (active) "Bedtime Lock active" else "Bedtime Lock inactive",
            if (active) Condition.STATE_TRUE else Condition.STATE_FALSE,
        )
    }
}
