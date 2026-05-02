package com.localskills.app.engine.rules

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.localskills.app.data.db.entity.RuleEntity
import com.localskills.app.notifications.RuleNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RuleNotifierTest {

    private fun rule(id: String): RuleEntity = RuleEntity(
        id = id,
        skillId = "skill",
        resultId = null,
        expression = "true",
        action = "notify",
        title = "t",
        body = "b",
        scheduleHint = null,
        enabled = true,
        lastFiredAt = null,
        createdAt = Instant.parse("2026-04-29T00:00:00Z"),
    )

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class CountingPoster : RuleNotifier.Poster {
        var posts: Int = 0
        override fun post(rule: RuleEntity) { posts++ }
    }

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.parse("2026-04-29T09:30:00Z"), ZoneId.of("Asia/Kolkata"))

    @Test
    fun `dedupes within same day for same key`() = runTest {
        val ds = FakeDataStore()
        val poster = CountingPoster()
        val notifier = RuleNotifier(ds, poster, fixedClock())
        val r = rule("r1")

        assertTrue(notifier.dispatch(r))
        assertFalse(notifier.dispatch(r)) // same key today → suppressed
        assertEquals(1, poster.posts)
    }

    @Test
    fun `respects per-rule per-day rate limit`() = runTest {
        val ds = FakeDataStore()
        val poster = CountingPoster()
        val notifier = RuleNotifier(ds, poster, fixedClock())
        val r = rule("r2")

        // Distinct fire keys to bypass dedupe but still hit rate limit.
        assertTrue(notifier.dispatch(r, fireKey = "k1"))
        assertTrue(notifier.dispatch(r, fireKey = "k2"))
        assertTrue(notifier.dispatch(r, fireKey = "k3"))
        assertFalse(notifier.dispatch(r, fireKey = "k4")) // 4th → suppressed
        assertEquals(RuleNotifier.MAX_PER_DAY, poster.posts)
    }

    @Test
    fun `different rules have independent budgets`() = runTest {
        val ds = FakeDataStore()
        val poster = CountingPoster()
        val notifier = RuleNotifier(ds, poster, fixedClock())

        repeat(RuleNotifier.MAX_PER_DAY) { i ->
            assertTrue(notifier.dispatch(rule("a"), fireKey = "k$i"))
        }
        // Another rule still has full budget on the same day.
        assertTrue(notifier.dispatch(rule("b"), fireKey = "k0"))
    }
}
