using System;
using System.Text.Json.Serialization;

namespace Jellyfin.Plugin.Jellycopter.Models;

public class AchievementSyncDto
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("title")]
    public string Title { get; set; } = string.Empty;

    [JsonPropertyName("unlockedAtUtc")]
    public string? UnlockedAtUtc { get; set; }

    [JsonPropertyName("progress")]
    public float Progress { get; set; }

    [JsonPropertyName("maxProgress")]
    public float MaxProgress { get; set; } = 1.0f;

    [JsonPropertyName("isUnlocked")]
    public bool IsUnlocked { get; set; }
}
