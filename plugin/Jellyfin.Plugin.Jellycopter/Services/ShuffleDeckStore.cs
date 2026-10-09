using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using Jellyfin.Plugin.Jellycopter.Models;

namespace Jellyfin.Plugin.Jellycopter.Services;

public class ShuffleDeckStore : IShuffleDeckStore
{
    private static readonly Lazy<ShuffleDeckStore> _lazyInstance = new(() => new ShuffleDeckStore());
    public static ShuffleDeckStore Instance => _lazyInstance.Value;

    // Key: $"{userId}:{deckId}"
    private readonly ConcurrentDictionary<string, DeckSyncDto> _decks = new();

    // Key: $"{userId}:{achievementId}"
    private readonly ConcurrentDictionary<string, AchievementSyncDto> _achievements = new();

    public IReadOnlyList<DeckSyncDto> GetDecks(string userId)
    {
        var prefix = $"{userId}:";
        return _decks.Where(kvp => kvp.Key.StartsWith(prefix, StringComparison.Ordinal))
                     .Select(kvp => kvp.Value)
                     .ToList();
    }

    public DeckSyncDto? GetDeck(string userId, string deckId)
    {
        var key = $"{userId}:{deckId}";
        return _decks.TryGetValue(key, out var deck) ? deck : null;
    }

    public DeckSyncDto UpsertDeck(string userId, DeckSyncDto incoming)
    {
        var key = $"{userId}:{incoming.DeckId}";

        return _decks.AddOrUpdate(key, incoming, (_, existing) =>
        {
            // 1. Cycle discrepancy: if client is ahead in cycle, client state wins
            if (incoming.CycleNumber > existing.CycleNumber)
            {
                return incoming;
            }

            // If existing is ahead in cycle, existing wins
            if (existing.CycleNumber > incoming.CycleNumber)
            {
                return existing;
            }

            // 2. Same-cycle set-union merge on exhausted items
            var mergedExhausted = existing.ExhaustedItemIds
                .Union(incoming.ExhaustedItemIds)
                .Distinct()
                .ToList();

            // Resume position conflict: preserve active resume position if incoming has no resume data, or use most recent if both present
            var incomingHasResume = !string.IsNullOrEmpty(incoming.LastPlayedItemId) && incoming.LastResumePositionTicks > 0;
            var existingHasResume = !string.IsNullOrEmpty(existing.LastPlayedItemId) && existing.LastResumePositionTicks > 0;

            string? lastPlayed;
            long lastResumeTicks;

            if (incomingHasResume && existingHasResume)
            {
                var useIncoming = incoming.UpdatedAtUtc >= existing.UpdatedAtUtc;
                lastPlayed = useIncoming ? incoming.LastPlayedItemId : existing.LastPlayedItemId;
                lastResumeTicks = useIncoming ? incoming.LastResumePositionTicks : existing.LastResumePositionTicks;
            }
            else if (incomingHasResume)
            {
                lastPlayed = incoming.LastPlayedItemId;
                lastResumeTicks = incoming.LastResumePositionTicks;
            }
            else
            {
                lastPlayed = existing.LastPlayedItemId;
                lastResumeTicks = existing.LastResumePositionTicks;
            }

            var mostRecentUpdated = incoming.UpdatedAtUtc >= existing.UpdatedAtUtc ? incoming.UpdatedAtUtc : existing.UpdatedAtUtc;

            return new DeckSyncDto
            {
                DeckId = incoming.DeckId,
                SeriesId = incoming.SeriesId ?? existing.SeriesId,
                ChannelId = incoming.ChannelId ?? existing.ChannelId,
                CycleNumber = incoming.CycleNumber,
                ExhaustedItemIds = mergedExhausted,
                UnexhaustedPoolIds = incoming.UnexhaustedPoolIds.Count > 0 ? incoming.UnexhaustedPoolIds : existing.UnexhaustedPoolIds,
                LastPlayedItemId = lastPlayed,
                LastResumePositionTicks = lastResumeTicks,
                UpdatedAtUtc = mostRecentUpdated
            };
        });
    }

    public IReadOnlyList<AchievementSyncDto> GetAchievements(string userId)
    {
        var prefix = $"{userId}:";
        return _achievements.Where(kvp => kvp.Key.StartsWith(prefix, StringComparison.Ordinal))
                            .Select(kvp => kvp.Value)
                            .ToList();
    }

    public void UpsertAchievements(string userId, IEnumerable<AchievementSyncDto> incoming)
    {
        foreach (var item in incoming)
        {
            var key = $"{userId}:{item.Id}";
            _achievements.AddOrUpdate(key, item, (_, existing) =>
            {
                // Unlocked status is permanent
                var isUnlocked = existing.IsUnlocked || item.IsUnlocked;

                // Earliest non-null timestamp wins
                string? unlockedAt = null;
                if (!string.IsNullOrEmpty(existing.UnlockedAtUtc) && !string.IsNullOrEmpty(item.UnlockedAtUtc))
                {
                    unlockedAt = string.CompareOrdinal(existing.UnlockedAtUtc, item.UnlockedAtUtc) <= 0
                        ? existing.UnlockedAtUtc
                        : item.UnlockedAtUtc;
                }
                else
                {
                    unlockedAt = existing.UnlockedAtUtc ?? item.UnlockedAtUtc;
                }

                return new AchievementSyncDto
                {
                    Id = item.Id,
                    Title = !string.IsNullOrEmpty(item.Title) ? item.Title : existing.Title,
                    IsUnlocked = isUnlocked,
                    UnlockedAtUtc = unlockedAt,
                    Progress = Math.Max(existing.Progress, item.Progress),
                    MaxProgress = Math.Max(existing.MaxProgress, item.MaxProgress)
                };
            });
        }
    }
}
