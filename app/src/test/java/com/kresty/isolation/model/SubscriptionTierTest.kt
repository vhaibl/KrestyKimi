package com.kresty.isolation.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTierTest {

    @Test
    fun freeTier_allowsOnlySingleApp() {
        assertTrue(SubscriptionTier.Free.canAddApp(0))
        assertFalse(SubscriptionTier.Free.canAddApp(1))
    }

    @Test
    fun basicTier_allowsThreeApps() {
        assertTrue(SubscriptionTier.Basic.canAddApp(2))
        assertFalse(SubscriptionTier.Basic.canAddApp(3))
    }

    @Test
    fun unlimitedTier_hasNoPracticalLimit() {
        assertTrue(SubscriptionTier.Unlimited.canAddApp(Int.MAX_VALUE - 1))
    }
}
