package com.example.aichat.core.util

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class FormattersTest {
    @Test
    fun formatCount_usesLocaleGroupingAndClampsNegativeValues() {
        assertThat(formatCount(1_234_567, Locale.US)).isEqualTo("1,234,567")
        assertThat(formatCount(-4, Locale.US)).isEqualTo("0")
    }

    @Test
    fun formatRelativeTimeAgo_formatsEverySupportedUnit() {
        val now = 2_000_000_000_000L

        assertThat(formatRelativeTimeAgo(now - 12_000L, now)).isEqualTo("12 seconds ago")
        assertThat(formatRelativeTimeAgo(now - 60_000L, now)).isEqualTo("1 minute ago")
        assertThat(formatRelativeTimeAgo(now - 2 * 3_600_000L, now)).isEqualTo("2 hours ago")
        assertThat(formatRelativeTimeAgo(now - 3 * 86_400_000L, now)).isEqualTo("3 days ago")
        assertThat(formatRelativeTimeAgo(now - 2 * 604_800_000L, now)).isEqualTo("2 weeks ago")
        assertThat(formatRelativeTimeAgo(now - 3 * 2_592_000_000L, now)).isEqualTo("3 months ago")
        assertThat(formatRelativeTimeAgo(now - 2 * 31_536_000_000L, now)).isEqualTo("2 years ago")
    }

    @Test
    fun formatRelativeTimeAgo_usesSingularAndClampsFutureTimes() {
        val now = 2_000_000_000_000L

        assertThat(formatRelativeTimeAgo(now - 1_000L, now)).isEqualTo("1 second ago")
        assertThat(formatRelativeTimeAgo(now + 5_000L, now)).isEqualTo("0 seconds ago")
        assertThat(formatRelativeTimeAgo(0L, now)).isEqualTo("—")
    }
}
