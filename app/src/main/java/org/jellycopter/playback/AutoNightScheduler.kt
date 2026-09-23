package org.jellycopter.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Time-based Auto-Night Mode Scheduler.
 *
 * Automatically monitors wall clock time and toggles Night Mode during quiet hours.
 * Accurately handles time ranges that cross midnight (e.g. 22:30 to 07:00).
 */
class AutoNightScheduler(
    initialAutoEnabled: Boolean = true,
    initialStartTime: LocalTime = LocalTime.of(22, 30),
    initialEndTime: LocalTime = LocalTime.of(7, 0)
) {

    var autoEnabled: Boolean = initialAutoEnabled
        set(value) {
            field = value
            evaluateNow()
        }

    var startTime: LocalTime = initialStartTime
        set(value) {
            field = value
            evaluateNow()
        }

    var endTime: LocalTime = initialEndTime
        set(value) {
            field = value
            evaluateNow()
        }

    private val _isNightModeActive = MutableStateFlow(false)
    val isNightModeActive: StateFlow<Boolean> = _isNightModeActive.asStateFlow()

    private var monitorJob: Job? = null

    init {
        evaluateNow()
    }

    /**
     * Evaluates whether Night Mode should be active at the specified [currentTime].
     */
    fun evaluate(currentTime: LocalTime): Boolean {
        if (!autoEnabled) return false
        return isNightTime(currentTime, startTime, endTime)
    }

    /**
     * Evaluates current system time and updates [isNightModeActive].
     */
    fun evaluateNow(currentTime: LocalTime = LocalTime.now()): Boolean {
        val active = evaluate(currentTime)
        _isNightModeActive.value = active
        return active
    }

    /**
     * Starts periodic background monitoring every [intervalMs] (default 60 seconds).
     */
    fun startMonitoring(
        scope: CoroutineScope,
        intervalMs: Long = 60_000L,
        timeProvider: () -> LocalTime = { LocalTime.now() }
    ) {
        stopMonitoring()
        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                evaluateNow(timeProvider())
                delay(intervalMs)
            }
        }
    }

    /**
     * Stops periodic background monitoring.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun setQuietHours(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        startTime = LocalTime.of(startHour, startMinute)
        endTime = LocalTime.of(endHour, endMinute)
    }

    fun setQuietHoursFromString(start: String, end: String, formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")) {
        startTime = LocalTime.parse(start, formatter)
        endTime = LocalTime.parse(end, formatter)
    }

    companion object {
        /**
         * Pure function determining if [now] falls within [start] and [end],
         * correctly handling midnight rollover when start > end.
         */
        fun isNightTime(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
            if (start == end) return false

            return if (start < end) {
                // Same-day interval (e.g. 13:00 to 17:00)
                !now.isBefore(start) && now.isBefore(end)
            } else {
                // Crosses midnight interval (e.g. 22:30 to 07:00)
                !now.isBefore(start) || now.isBefore(end)
            }
        }
    }
}
