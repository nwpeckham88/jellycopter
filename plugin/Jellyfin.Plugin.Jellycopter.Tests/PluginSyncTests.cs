using System;
using System.Collections.Generic;
using System.Linq;
using Jellyfin.Plugin.Jellycopter.Api;
using Jellyfin.Plugin.Jellycopter.Models;
using Jellyfin.Plugin.Jellycopter.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Jellyfin.Plugin.Jellycopter.Tests;

public class PluginSyncTests
{
    [Fact]
    public void Ping_ReturnsSuccessWithVersion()
    {
        var store = new ShuffleDeckStore();
        var controller = new DecksController(NullLogger<DecksController>.Instance, store);

        var result = controller.Ping();
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var pingResponse = Assert.IsType<PingResponseDto>(okResult.Value);

        Assert.Equal("1.0.0", pingResponse.Version);
        Assert.Contains(1, pingResponse.SupportedSyncProtocols);
    }

    [Fact]
    public void UpsertDeck_SameCycle_PerformsSetUnionMerge()
    {
        var store = new ShuffleDeckStore();
        var controller = new DecksController(NullLogger<DecksController>.Instance, store);
        var userId = "user_test_1";

        // Client 1 (e.g. Shield TV in living room) plays episodes 1 and 2
        var deck1 = new DeckSyncDto
        {
            DeckId = "deck_simpsons",
            SeriesId = "series_simpsons",
            CycleNumber = 1,
            ExhaustedItemIds = new List<string> { "ep_1", "ep_2" },
            LastPlayedItemId = "ep_2",
            LastResumePositionTicks = 12000000000L,
            UpdatedAtUtc = DateTime.UtcNow.AddMinutes(-10)
        };
        controller.UpsertDeck(deck1, userId);

        // Client 2 (e.g. Onn 4K in bedroom) plays episodes 2 and 3
        var deck2 = new DeckSyncDto
        {
            DeckId = "deck_simpsons",
            SeriesId = "series_simpsons",
            CycleNumber = 1,
            ExhaustedItemIds = new List<string> { "ep_2", "ep_3" },
            LastPlayedItemId = "ep_3",
            LastResumePositionTicks = 14000000000L,
            UpdatedAtUtc = DateTime.UtcNow
        };
        var res = controller.UpsertDeck(deck2, userId);
        var okResult = Assert.IsType<OkObjectResult>(res.Result);
        var merged = Assert.IsType<DeckSyncDto>(okResult.Value);

        // Union of {ep_1, ep_2} and {ep_2, ep_3} is {ep_1, ep_2, ep_3}
        Assert.Equal(3, merged.ExhaustedItemIds.Count);
        Assert.Contains("ep_1", merged.ExhaustedItemIds);
        Assert.Contains("ep_2", merged.ExhaustedItemIds);
        Assert.Contains("ep_3", merged.ExhaustedItemIds);

        // More recent update supplies last played item
        Assert.Equal("ep_3", merged.LastPlayedItemId);
        Assert.Equal(14000000000L, merged.LastResumePositionTicks);
    }

    [Fact]
    public void UpsertDeck_CycleAdvance_ClientAheadWins()
    {
        var store = new ShuffleDeckStore();
        var controller = new DecksController(NullLogger<DecksController>.Instance, store);
        var userId = "user_test_2";

        // Cycle 1 on server
        var deck1 = new DeckSyncDto
        {
            DeckId = "deck_futurama",
            CycleNumber = 1,
            ExhaustedItemIds = new List<string> { "ep_10", "ep_11" }
        };
        controller.UpsertDeck(deck1, userId);

        // Client completes cycle 1 and enters cycle 2
        var deck2 = new DeckSyncDto
        {
            DeckId = "deck_futurama",
            CycleNumber = 2,
            ExhaustedItemIds = new List<string> { "ep_01" }
        };
        var res = controller.UpsertDeck(deck2, userId);
        var okResult = Assert.IsType<OkObjectResult>(res.Result);
        var merged = Assert.IsType<DeckSyncDto>(okResult.Value);

        Assert.Equal(2, merged.CycleNumber);
        Assert.Single(merged.ExhaustedItemIds);
        Assert.Equal("ep_01", merged.ExhaustedItemIds.First());
    }

    [Fact]
    public void UpsertAchievements_MergesEarliestUnlockAndMaxProgress()
    {
        var store = new ShuffleDeckStore();
        var controller = new AchievementsController(NullLogger<AchievementsController>.Instance, store);
        var userId = "user_test_3";

        var initialList = new List<AchievementSyncDto>
        {
            new()
            {
                Id = "trip_to_the_moon",
                Title = "Trip to the Moon",
                Progress = 20.0f,
                MaxProgress = 72.0f,
                IsUnlocked = false,
                UnlockedAtUtc = null
            }
        };
        controller.UpsertAchievements(initialList, userId);

        var syncList = new List<AchievementSyncDto>
        {
            new()
            {
                Id = "trip_to_the_moon",
                Title = "Trip to the Moon",
                Progress = 72.0f,
                MaxProgress = 72.0f,
                IsUnlocked = true,
                UnlockedAtUtc = "2026-09-16T04:00:00Z"
            }
        };
        controller.UpsertAchievements(syncList, userId);

        var getRes = controller.GetAchievements(userId);
        var okResult = Assert.IsType<OkObjectResult>(getRes.Result);
        var achievements = Assert.IsAssignableFrom<IReadOnlyList<AchievementSyncDto>>(okResult.Value);

        var ach = Assert.Single(achievements);
        Assert.True(ach.IsUnlocked);
        Assert.Equal("2026-09-16T04:00:00Z", ach.UnlockedAtUtc);
        Assert.Equal(72.0f, ach.Progress);
    }
}
