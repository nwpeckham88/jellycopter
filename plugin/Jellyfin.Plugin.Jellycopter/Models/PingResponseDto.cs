using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace Jellyfin.Plugin.Jellycopter.Models;

public class PingResponseDto
{
    [JsonPropertyName("version")]
    public string Version { get; set; } = "1.0.0";

    [JsonPropertyName("supportedSyncProtocols")]
    public List<int> SupportedSyncProtocols { get; set; } = new() { 1 };

    [JsonPropertyName("serverTimeUtc")]
    public DateTime ServerTimeUtc { get; set; } = DateTime.UtcNow;
}
