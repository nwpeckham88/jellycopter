using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace Jellyfin.Plugin.Jellycopter.Models;

public class DeckSyncDto
{
    [JsonPropertyName("deckId")]
    public string DeckId { get; set; } = string.Empty;

    [JsonPropertyName("seriesId")]
    public string? SeriesId { get; set; }

    [JsonPropertyName("channelId")]
    public string? ChannelId { get; set; }

    [JsonPropertyName("cycleNumber")]
    public int CycleNumber { get; set; } = 1;

    [JsonPropertyName("exhaustedItemIds")]
    public List<string> ExhaustedItemIds { get; set; } = new();

    [JsonPropertyName("unexhaustedPoolIds")]
    public List<string> UnexhaustedPoolIds { get; set; } = new();

    [JsonPropertyName("lastPlayedItemId")]
    public string? LastPlayedItemId { get; set; }

    [JsonPropertyName("lastResumePositionTicks")]
    public long LastResumePositionTicks { get; set; }

    [JsonPropertyName("updatedAtUtc")]
    public DateTime UpdatedAtUtc { get; set; } = DateTime.UtcNow;
}
