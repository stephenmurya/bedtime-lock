package com.omeniv.bedtimelock

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayVisibilityPolicyTest {
    @Test
    fun repeatedVisibleEnforcementIsNoOp() {
        assertEquals(
            OverlayVisibilityPolicy.EnsureAction.NO_OP,
            OverlayVisibilityPolicy.ensureAction(isAttached = true, isVisible = true),
        )
    }

    @Test
    fun detachedOverlayIsAddedOnce() {
        assertEquals(
            OverlayVisibilityPolicy.EnsureAction.ADD,
            OverlayVisibilityPolicy.ensureAction(isAttached = false, isVisible = false),
        )
    }

    @Test
    fun attachedHiddenOverlayIsShownWithoutReadding() {
        assertEquals(
            OverlayVisibilityPolicy.EnsureAction.SHOW,
            OverlayVisibilityPolicy.ensureAction(isAttached = true, isVisible = false),
        )
    }
}
