package fi.fmi.mobileweather.widgets.util

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.TaskCompletionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SingleShotLocationProviderTest {
    private lateinit var context: Context
    private lateinit var result: TaskCompletionSource<Location>
    private lateinit var request: CurrentLocationRequest
    private lateinit var token: CancellationToken
    private lateinit var callback: RecordingCallback
    private val mainLooper get() = shadowOf(Looper.getMainLooper())

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        result = TaskCompletionSource()
        callback = RecordingCallback()
    }

    @Test
    fun missingPermissionsFailsWithoutRequestingLocation() {
        var requested = false
        SingleShotLocationProvider.requestSingleUpdate(context, callback) { _, _ ->
            requested = true
            result.task
        }

        assertFalse(requested)
        assertFailureOnce()
    }

    @Test
    fun coarsePermissionAllowsBalancedRequestAndReturnsLocationOnce() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        startRequest()
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, request.priority)
        assertEquals(Granularity.GRANULARITY_PERMISSION_LEVEL, request.granularity)
        assertEquals(60000L, request.maxUpdateAgeMillis)
        assertEquals(15000L, request.durationMillis)

        val location = location()
        result.setResult(location)
        mainLooper.idleFor(Duration.ofSeconds(30))

        assertEquals(listOf(location), callback.locations)
        assertEquals(0, callback.failures)
        assertTrue(token.isCancellationRequested)
    }

    @Test
    fun finePermissionAloneAllowsLocationRequest() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION)
        startRequest()
        val location = location()

        result.setResult(location)
        mainLooper.idle()

        assertEquals(listOf(location), callback.locations)
        assertEquals(0, callback.failures)
    }

    @Test
    fun nullLocationFailsOnce() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        startRequest()

        result.setResult(null)
        mainLooper.idleFor(Duration.ofSeconds(30))

        assertFailureOnce()
        assertTrue(token.isCancellationRequested)
    }

    @Test
    fun failedTaskFailsOnce() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        startRequest()

        result.setException(IllegalStateException("Location services unavailable"))
        mainLooper.idleFor(Duration.ofSeconds(30))

        assertFailureOnce()
        assertTrue(token.isCancellationRequested)
    }

    @Test
    fun cancelledTaskFailsOnce() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        val cancellation = CancellationTokenSource()
        result = TaskCompletionSource(cancellation.token)
        startRequest()

        cancellation.cancel()
        mainLooper.idle()
        assertFailureOnce()
        mainLooper.idleFor(Duration.ofSeconds(30))
        assertFailureOnce()
    }

    @Test
    fun timeoutCancelsRequestAndIgnoresLateLocation() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        startRequest()
        mainLooper.idleFor(Duration.ofSeconds(14))
        assertEquals(0, callback.failures)
        assertFalse(token.isCancellationRequested)

        mainLooper.idleFor(Duration.ofSeconds(1))
        assertFailureOnce()
        assertTrue(token.isCancellationRequested)

        result.setResult(location())
        mainLooper.idle()
        assertFailureOnce()
    }

    @Test
    fun permissionRevokedDuringRequestFailsOnceAndCancelsRequest() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        SingleShotLocationProvider.requestSingleUpdate(context, callback) { _, cancellationToken ->
            token = cancellationToken
            throw SecurityException("Permission revoked")
        }
        mainLooper.idleFor(Duration.ofSeconds(30))

        assertFailureOnce()
        assertTrue(token.isCancellationRequested)
    }

    @Test
    fun concurrentWidgetRequestsCompleteIndependently() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        startRequest()
        val otherCallback = RecordingCallback()
        val otherResult = TaskCompletionSource<Location>()
        lateinit var otherToken: CancellationToken
        SingleShotLocationProvider.requestSingleUpdate(context, otherCallback) { _, cancellationToken ->
            otherToken = cancellationToken
            otherResult.task
        }

        result.setResult(location())
        mainLooper.idle()
        assertFalse(otherToken.isCancellationRequested)
        mainLooper.idleFor(Duration.ofSeconds(15))

        assertEquals(1, callback.locations.size)
        assertEquals(0, callback.failures)
        assertEquals(1, otherCallback.failures)
        assertTrue(otherCallback.locations.isEmpty())
        assertTrue(otherToken.isCancellationRequested)
    }

    @Test fun cancellationStopsLocationRequestAndSuppressesLateResultsAndTimeout() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        val signal = startRequest()

        signal.cancel()
        assertTrue(token.isCancellationRequested)
        result.setResult(location())
        mainLooper.idleFor(Duration.ofSeconds(30))

        assertTrue(callback.locations.isEmpty())
        assertEquals(0, callback.failures)
    }

    private fun startRequest(): android.os.CancellationSignal {
        return SingleShotLocationProvider.requestSingleUpdate(context, callback) { locationRequest, cancellationToken ->
            request = locationRequest
            token = cancellationToken
            result.task
        }
    }

    private fun grant(permission: String) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(permission)
    }

    private fun location() = Location("fused").apply {
        latitude = 60.1699
        longitude = 24.9384
    }

    private fun assertFailureOnce() {
        assertEquals(1, callback.failures)
        assertTrue(callback.locations.isEmpty())
    }

    private class RecordingCallback : SingleShotLocationProvider.LocationCallback {
        val locations = mutableListOf<Location>()
        var failures = 0

        override fun onNewLocationAvailable(location: Location) { locations.add(location) }
        override fun onLocationFailed() { failures++ }
    }
}
