package org.jellycopter.telemetry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jellycopter.core.database.AchievementEntity
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.repository.AchievementRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Telemetry and Milestone Evaluation Engine for Jellycopter.
 *
 * Implements the Sacred Playback Rule:
 * Asynchronous evaluation with ZERO mid-playback toasts or audio interruptions.
 * Achievements are unlocked and queued silently during media playback,
 * surfacing exclusively on the PostPlaybackScreen and in the Trophy Room.
 */
class AchievementEngine(
    private val repository: AchievementRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _unlockedAchievements = MutableStateFlow<List<AchievementEntity>>(emptyList())
    val unlockedAchievements: StateFlow<List<AchievementEntity>> = _unlockedAchievements.asStateFlow()

    private val _allAchievements = MutableStateFlow<List<Achievement>>(MilestoneDefinitions.ALL_MILESTONES)
    val allAchievements: StateFlow<List<Achievement>> = _allAchievements.asStateFlow()

    private val _celebrationQueue = MutableStateFlow<List<Achievement>>(emptyList())
    val celebrationQueue: StateFlow<List<Achievement>> = _celebrationQueue.asStateFlow()

    // Session log for rolling window queries
    private val sessionHistory = mutableListOf<PlaybackSessionStats>()
    private var totalWatchTimeMs: Long = 0L
    private var shufflePlaysCount: Int = 0
    private var forceStereoSessionsCount: Int = 0
    private var exhaustedDecksCount: Int = 0

    init {
        scope.launch(ioDispatcher) {
            // Seed default quirky milestones in Room DB
            repository.seedDefaults(MilestoneDefinitions.ALL_MILESTONES)
            refreshAchievementsFromRepo()
        }
    }

    /**
     * Refreshes internal state caches from the repository.
     */
    suspend fun refreshAchievementsFromRepo() {
        val list = mutableListOf<Achievement>()
        for (m in MilestoneDefinitions.ALL_MILESTONES) {
            val entity = repository.getAchievement(m.id)
            if (entity != null) {
                list.add(entity)
                when (m.id) {
                    MilestoneDefinitions.ID_TRIP_TO_THE_MOON -> {
                        val ms = (entity.progress * 3_600_000.0f).toLong()
                        if (ms > totalWatchTimeMs) totalWatchTimeMs = ms
                    }
                    MilestoneDefinitions.ID_SHUFFLE_CENTURION -> {
                        val count = entity.progress.toInt()
                        if (count > shufflePlaysCount) shufflePlaysCount = count
                    }
                    MilestoneDefinitions.ID_STEREO_PURIST -> {
                        val count = entity.progress.toInt()
                        if (count > forceStereoSessionsCount) forceStereoSessionsCount = count
                    }
                    MilestoneDefinitions.ID_DECK_MASTER -> {
                        val count = entity.progress.toInt()
                        if (count > exhaustedDecksCount) exhaustedDecksCount = count
                    }
                }
            } else {
                list.add(m)
            }
        }
        _allAchievements.value = list
        val unlockedEntities = list.filter { it.isUnlocked }.map { toEntity(it) }
        _unlockedAchievements.value = unlockedEntities
    }

    /**
     * Evaluates all quirky milestone criteria upon playback termination.
     * Sacred Playback Rule: Under NO circumstances are toasts or alerts emitted mid-playback.
     * Newly unlocked achievements are queued for the PostPlaybackScreen celebration banner.
     */
    suspend fun onPlaybackSessionEnded(session: PlaybackSessionStats): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()

        // 1. Record session in ledger
        sessionHistory.add(session)
        totalWatchTimeMs += session.playedMs
        if (session.isShuffle && session.completed) {
            shufflePlaysCount++
        }
        if (session.isForceStereo && session.completed) {
            forceStereoSessionsCount++
        }
        if (session.deckExhausted) {
            exhaustedDecksCount++
        }

        // 2. Evaluate each milestone
        // Trip to the Moon: 72 hours total playback (Apollo 11)
        val watchHours = (totalWatchTimeMs / 3_600_000.0f)
        checkAndUpdateMilestone(
            id = MilestoneDefinitions.ID_TRIP_TO_THE_MOON,
            progress = watchHours,
            newlyUnlocked = newlyUnlocked,
            isCumulative = true
        )

        // Binge Voyager: 8 or more episodes completed in any rolling 24-hour window
        val windowStart = session.endedAtUtcMs - (24L * 60L * 60L * 1000L)
        val rollingEpisodes = sessionHistory.count {
            it.completed && it.endedAtUtcMs in windowStart..session.endedAtUtcMs
        }
        checkAndUpdateMilestone(
            id = MilestoneDefinitions.ID_BINGE_VOYAGER,
            progress = rollingEpisodes.toFloat(),
            newlyUnlocked = newlyUnlocked,
            isCumulative = false
        )

        // Shuffle Centurion: 100 shuffle deck episode playthroughs
        checkAndUpdateMilestone(
            id = MilestoneDefinitions.ID_SHUFFLE_CENTURION,
            progress = shufflePlaysCount.toFloat(),
            newlyUnlocked = newlyUnlocked,
            isCumulative = true
        )

        // Night Owl: Playback completed between 03:00 AM and 05:00 AM local time
        if (session.completed && session.localHourOfDay in 3..4) {
            checkAndUpdateMilestone(
                id = MilestoneDefinitions.ID_NIGHT_OWL,
                progress = 1.0f,
                newlyUnlocked = newlyUnlocked,
                isCumulative = true
            )
        }

        // The Compleatist: Fully exhaust an entire TV series deck (100% of cycle)
        if (session.deckExhausted) {
            checkAndUpdateMilestone(
                id = MilestoneDefinitions.ID_THE_COMPLEATIST,
                progress = 1.0f,
                newlyUnlocked = newlyUnlocked,
                isCumulative = true
            )
        }

        // Stereo Purist: 10 playback sessions with Force Stereo enabled
        checkAndUpdateMilestone(
            id = MilestoneDefinitions.ID_STEREO_PURIST,
            progress = forceStereoSessionsCount.toFloat(),
            newlyUnlocked = newlyUnlocked,
            isCumulative = true
        )

        // Speed Demon: Playback completed at > 1.0x speed or zero pauses on completed item
        if (session.completed && (session.playbackSpeed > 1.0f || (session.pauseCount == 0 && session.playedMs > 60_000L))) {
            checkAndUpdateMilestone(
                id = MilestoneDefinitions.ID_SPEED_DEMON,
                progress = 1.0f,
                newlyUnlocked = newlyUnlocked,
                isCumulative = true
            )
        }

        // Deck Master: Maintain 5 active decks or complete 5 full deck cycles
        val deckMasterMetric = maxOf(session.activeDecksCount, exhaustedDecksCount)
        checkAndUpdateMilestone(
            id = MilestoneDefinitions.ID_DECK_MASTER,
            progress = deckMasterMetric.toFloat(),
            newlyUnlocked = newlyUnlocked,
            isCumulative = true
        )

        // 3. Queue newly unlocked for PostPlaybackScreen celebration
        if (newlyUnlocked.isNotEmpty()) {
            _celebrationQueue.value = _celebrationQueue.value + newlyUnlocked
        }

        refreshAchievementsFromRepo()
        return newlyUnlocked
    }

    /**
     * Synchronous bridge for non-coroutine invocation.
     */
    fun onPlaybackSessionEndedSync(session: PlaybackSessionStats): List<Achievement> = runBlocking(ioDispatcher) {
        onPlaybackSessionEnded(session)
    }

    private suspend fun checkAndUpdateMilestone(
        id: String,
        progress: Float,
        newlyUnlocked: MutableList<Achievement>,
        isCumulative: Boolean = true
    ) {
        val existing = repository.getAchievement(id) ?: return
        if (existing.isUnlocked) return

        val newProgress = if (isCumulative) maxOf(existing.progress, progress) else progress
        if (newProgress >= existing.maxProgress) {
            val ts = currentIsoTimestamp()
            val unlocked = repository.unlockAchievement(id, ts)
            if (unlocked) {
                newlyUnlocked.add(
                    existing.copy(
                        isUnlocked = true,
                        unlockedAtUtc = ts,
                        progress = existing.maxProgress
                    )
                )
            }
        } else if (newProgress != existing.progress) {
            repository.updateProgress(id, newProgress)
        }
    }

    /**
     * Retrieves and clears the pending celebrations for the PostPlaybackScreen.
     */
    fun consumeCelebrations(): List<Achievement> {
        val items = _celebrationQueue.value
        _celebrationQueue.value = emptyList()
        return items
    }

    fun clearCelebrations() {
        _celebrationQueue.value = emptyList()
    }

    private fun toEntity(model: Achievement): AchievementEntity {
        return AchievementEntity(
            id = model.id,
            title = model.title,
            description = model.description,
            tier = model.tier.name,
            points = model.points,
            isUnlocked = model.isUnlocked,
            unlockedAtUtc = model.unlockedAtUtc,
            progress = model.progress,
            maxProgress = model.maxProgress,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
