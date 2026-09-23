package org.jellycopter.telemetry

import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AchievementTier

object MilestoneDefinitions {

    const val ID_TRIP_TO_THE_MOON = "trip_to_the_moon"
    const val ID_BINGE_VOYAGER = "binge_voyager"
    const val ID_SHUFFLE_CENTURION = "shuffle_centurion"
    const val ID_NIGHT_OWL = "night_owl"
    const val ID_THE_COMPLEATIST = "the_compleatist"
    const val ID_STEREO_PURIST = "stereo_purist"
    const val ID_SPEED_DEMON = "speed_demon"
    const val ID_DECK_MASTER = "deck_master"

    val ALL_MILESTONES: List<Achievement> = listOf(
        Achievement(
            id = ID_TRIP_TO_THE_MOON,
            title = "Trip to the Moon",
            description = "Accumulate 72 hours of total playback across the app (Apollo 11 flight time to the Moon).",
            tier = AchievementTier.PLATINUM,
            points = 100,
            progress = 0.0f,
            maxProgress = 72.0f
        ),
        Achievement(
            id = ID_BINGE_VOYAGER,
            title = "Binge Voyager",
            description = "Complete 8 or more episodes within any rolling 24-hour window.",
            tier = AchievementTier.GOLD,
            points = 50,
            progress = 0.0f,
            maxProgress = 8.0f
        ),
        Achievement(
            id = ID_SHUFFLE_CENTURION,
            title = "Shuffle Centurion",
            description = "Complete 100 shuffle deck episode playthroughs.",
            tier = AchievementTier.GOLD,
            points = 50,
            progress = 0.0f,
            maxProgress = 100.0f
        ),
        Achievement(
            id = ID_NIGHT_OWL,
            title = "Night Owl",
            description = "Complete playback of an episode or film between 03:00 AM and 05:00 AM local time.",
            tier = AchievementTier.SILVER,
            points = 25,
            progress = 0.0f,
            maxProgress = 1.0f
        ),
        Achievement(
            id = ID_THE_COMPLEATIST,
            title = "The Compleatist",
            description = "Fully exhaust an entire TV series deck (reach 100% of cycle).",
            tier = AchievementTier.GOLD,
            points = 50,
            progress = 0.0f,
            maxProgress = 1.0f
        ),
        Achievement(
            id = ID_STEREO_PURIST,
            title = "Stereo Purist",
            description = "Complete 10 playback sessions with ITU-R BS.775 Force Stereo enabled.",
            tier = AchievementTier.SILVER,
            points = 25,
            progress = 0.0f,
            maxProgress = 10.0f
        ),
        Achievement(
            id = ID_SPEED_DEMON,
            title = "Speed Demon",
            description = "Complete a playback session at accelerated speed (>1.0x) or with zero pauses.",
            tier = AchievementTier.BRONZE,
            points = 15,
            progress = 0.0f,
            maxProgress = 1.0f
        ),
        Achievement(
            id = ID_DECK_MASTER,
            title = "Deck Master",
            description = "Maintain 5 or more active shuffle decks or complete 5 full deck cycles.",
            tier = AchievementTier.GOLD,
            points = 50,
            progress = 0.0f,
            maxProgress = 5.0f
        )
    )
}
