using System.Collections.Generic;
using Jellyfin.Plugin.Jellycopter.Models;

namespace Jellyfin.Plugin.Jellycopter.Services;

public interface IShuffleDeckStore
{
    IReadOnlyList<DeckSyncDto> GetDecks(string userId);

    DeckSyncDto? GetDeck(string userId, string deckId);

    DeckSyncDto UpsertDeck(string userId, DeckSyncDto incoming);

    IReadOnlyList<AchievementSyncDto> GetAchievements(string userId);

    void UpsertAchievements(string userId, IEnumerable<AchievementSyncDto> incoming);
}
