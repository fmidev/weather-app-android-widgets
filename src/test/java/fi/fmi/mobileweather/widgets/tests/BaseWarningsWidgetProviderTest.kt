package fi.fmi.mobileweather.widgets

import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.Duration
import fi.fmi.mobileweather.widgets.model.Warning
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BaseWarningsWidgetProviderTest {
    private val provider = TestProvider()
    private lateinit var originalTimeZone: TimeZone

    @Before fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        // The cutoff must use Helsinki time regardless of the device's time zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test fun keepsWarningsThatStartedYesterdayAndAreStillActive() {
        setNow("2026-09-08T09:00:00.000Z")

        assertTrue(isValid("2026-09-07T09:00:00.000Z", "2026-09-09T09:00:00.000Z"))
    }

    @Test fun excludesWarningsThatEndedEarlierTodayOrEndExactlyNow() {
        setNow("2026-09-08T09:00:00.000Z")

        assertFalse(isValid("2026-09-08T06:00:00.000Z", "2026-09-08T08:59:59.999Z"))
        assertFalse(isValid("2026-09-08T06:00:00.000Z", "2026-09-08T09:00:00.000Z"))
        assertTrue(isValid("2026-09-08T06:00:00.000Z", "2026-09-08T09:00:00.001Z"))
    }

    @Test fun keepsWarningsStartingNowOrLaterTodayButNotTomorrow() {
        setNow("2026-09-08T09:00:00.000Z")

        assertTrue(isValid("2026-09-08T09:00:00.000Z", "2026-09-09T06:00:00.000Z"))
        assertTrue(isValid("2026-09-08T20:59:59.999Z", "2026-09-09T06:00:00.000Z"))
        assertFalse(isValid("2026-09-08T21:00:00.000Z", "2026-09-09T06:00:00.000Z"))
        assertFalse(isValid("2026-09-09T03:00:00.000Z", "2026-09-09T06:00:00.000Z"))
    }

    @Test fun usesTheHelsinkiCalendarDayWhenUtcIsStillYesterday() {
        setNow("2026-09-08T21:30:00.000Z")

        assertTrue(isValid("2026-09-09T20:59:59.999Z", "2026-09-10T06:00:00.000Z"))
        assertFalse(isValid("2026-09-09T21:00:00.000Z", "2026-09-10T06:00:00.000Z"))
    }

    @Test fun usesLocalMidnightOnTheShortDayWhenDaylightSavingTimeStarts() {
        setNow("2026-03-28T23:00:00.000Z")

        assertTrue(isValid("2026-03-29T20:59:59.999Z", "2026-03-30T06:00:00.000Z"))
        assertFalse(isValid("2026-03-29T21:00:00.000Z", "2026-03-30T06:00:00.000Z"))
    }

    @Test fun usesLocalMidnightOnTheLongDayWhenDaylightSavingTimeEnds() {
        setNow("2026-10-24T22:00:00.000Z")

        assertTrue(isValid("2026-10-25T21:59:59.999Z", "2026-10-26T06:00:00.000Z"))
        assertFalse(isValid("2026-10-25T22:00:00.000Z", "2026-10-26T06:00:00.000Z"))
    }

    @Test fun handlesTheYearChangingAtLocalMidnight() {
        setNow("2026-12-31T10:00:00.000Z")

        assertTrue(isValid("2026-12-31T21:59:59.999Z", "2027-01-01T06:00:00.000Z"))
        assertFalse(isValid("2026-12-31T22:00:00.000Z", "2027-01-01T06:00:00.000Z"))
    }

    @Test fun excludesWarningsWithMissingOrUnparseableTimes() {
        setNow("2026-09-08T09:00:00.000Z")

        assertFalse(provider.isValidDate(Warning()))
        assertFalse(isValid(null, "2026-09-09T06:00:00.000Z"))
        assertFalse(isValid("2026-09-08T06:00:00.000Z", null))
        assertFalse(isValid("invalid", "2026-09-09T06:00:00.000Z"))
        assertFalse(isValid("2026-09-08T06:00:00.000Z", "invalid"))
    }

    private fun setNow(time: String) {
        provider.now = Instant.parse(time).toEpochMilli()
    }

    private fun isValid(start: String?, end: String?): Boolean =
        provider.isValidDate(Warning(duration = Duration(start, end)))

    private class TestProvider : BaseWarningsWidgetProvider() {
        var now: Long = 0
        override fun getWidgetType() = WidgetType.WARNINGS
        override fun getLayoutResourceId() = R.layout.small_warnings_widget_layout
        public override fun isValidDate(w: Warning) = super.isValidDate(w, now)
    }
}
