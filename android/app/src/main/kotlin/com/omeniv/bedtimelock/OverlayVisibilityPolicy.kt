package com.omeniv.bedtimelock

object OverlayVisibilityPolicy {
    enum class EnsureAction { ADD, SHOW, NO_OP }

    fun ensureAction(isAttached: Boolean, isVisible: Boolean): EnsureAction = when {
        isAttached && isVisible -> EnsureAction.NO_OP
        isAttached -> EnsureAction.SHOW
        else -> EnsureAction.ADD
    }
}
