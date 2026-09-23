package org.jellycopter.discovery.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jellycopter.core.model.MediaDiscoverItem

@Serializable
data class DiscoverPageResponse(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<DiscoverItemDto> = emptyList()
)

@Serializable
data class DiscoverItemDto(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double = 0.0,
    val mediaInfo: MediaInfoDto? = null,
    val genreIds: List<Int> = emptyList()
) {
    fun toMediaDiscoverItem(defaultMediaType: String = "movie"): MediaDiscoverItem {
        val resolvedType = mediaType ?: defaultMediaType
        val resolvedTitle = title ?: name ?: "Untitled"
        val resolvedDate = releaseDate ?: firstAirDate
        val resolvedStatus = mediaInfo?.status ?: 1
        val isRequested = (mediaInfo?.requests?.isNotEmpty() == true) || (resolvedStatus in listOf(2, 3))
        return MediaDiscoverItem(
            id = id,
            tmdbId = id,
            mediaType = resolvedType,
            title = resolvedTitle,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = resolvedDate,
            voteAverage = voteAverage,
            status = resolvedStatus,
            isRequested = isRequested
        )
    }
}

@Serializable
data class MediaInfoDto(
    val id: Int? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val status: Int = 1, // 1=UNKNOWN, 2=PENDING, 3=PROCESSING, 4=PARTIALLY_AVAILABLE, 5=AVAILABLE, 6=DELETED
    val requests: List<MediaRequestDto> = emptyList()
)

@Serializable
data class MediaRequestDto(
    val id: Int,
    val status: Int = 1, // 1=PENDING APPROVAL, 2=APPROVED, 3=DECLINED
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val seasons: List<Int>? = null
)

@Serializable
data class TvDetailsDto(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val seasons: List<SeasonDto> = emptyList(),
    val mediaInfo: MediaInfoDto? = null,
    val genres: List<GenreDto> = emptyList()
)

@Serializable
data class MovieDetailsDto(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val mediaInfo: MediaInfoDto? = null,
    val genres: List<GenreDto> = emptyList()
)

@Serializable
data class SeasonDto(
    val id: Int? = null,
    val seasonNumber: Int,
    val name: String? = null,
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null
)

@Serializable
data class GenreDto(
    val id: Int,
    val name: String
)

@Serializable
data class RadarrServerDto(
    val id: Int,
    val name: String,
    val isDefault: Boolean = false,
    val is4k: Boolean = false,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null
)

@Serializable
data class SonarrServerDto(
    val id: Int,
    val name: String,
    val isDefault: Boolean = false,
    val is4k: Boolean = false,
    val activeProfileId: Int? = null,
    val activeProfileName: String? = null,
    val activeDirectory: String? = null,
    val activeAnimeProfileId: Int? = null,
    val activeAnimeDirectory: String? = null
)

@Serializable
data class QualityProfileDto(
    val id: Int,
    val name: String
)

/**
 * Represents seasons parameter which can be serialized as "all" string
 * or as a JSON array of integers [1, 2, 3].
 */
@Serializable(with = SeasonsFieldSerializer::class)
sealed interface SeasonsField {
    data object All : SeasonsField
    data class Custom(val seasons: List<Int>) : SeasonsField
}

object SeasonsFieldSerializer : KSerializer<SeasonsField> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SeasonsField", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SeasonsField) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("SeasonsFieldSerializer requires JsonEncoder")
        when (value) {
            is SeasonsField.All -> jsonEncoder.encodeJsonElement(JsonPrimitive("all"))
            is SeasonsField.Custom -> {
                val array = JsonArray(value.seasons.map { JsonPrimitive(it) })
                jsonEncoder.encodeJsonElement(array)
            }
        }
    }

    override fun deserialize(decoder: Decoder): SeasonsField {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("SeasonsFieldSerializer requires JsonDecoder")
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString && element.content == "all" -> SeasonsField.All
            element is JsonArray -> {
                val ints = element.map { it.jsonPrimitive.int }
                SeasonsField.Custom(ints)
            }
            else -> SeasonsField.All
        }
    }
}

@Serializable
data class JellyseerrRequestPayload(
    val mediaType: String,
    val mediaId: Int,
    val is4k: Boolean = false,
    val seasons: SeasonsField? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null
)

@Serializable
data class JellyseerrErrorResponse(
    val message: String? = null,
    val error: String? = null
)
