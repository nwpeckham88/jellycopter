using System;
using System.Collections.Generic;
using System.Net.Mime;
using Jellyfin.Plugin.Jellycopter.Models;
using Jellyfin.Plugin.Jellycopter.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.Jellycopter.Api;

[ApiController]
[Route("Jellycopter")]
[Produces(MediaTypeNames.Application.Json)]
public class DecksController : ControllerBase
{
    private readonly ILogger<DecksController> _logger;
    private readonly IShuffleDeckStore _store;

    public DecksController(ILogger<DecksController> logger, IShuffleDeckStore? store = null)
    {
        _logger = logger;
        _store = store ?? ShuffleDeckStore.Instance;
    }

    /// <summary>
    /// Probe endpoint to verify plugin availability and protocol compatibility.
    /// </summary>
    [HttpGet("Ping")]
    [ProducesResponseType(typeof(PingResponseDto), StatusCodes.Status200OK)]
    public ActionResult<PingResponseDto> Ping()
    {
        _logger.LogInformation("Jellycopter Ping probe received");
        return Ok(new PingResponseDto());
    }

    /// <summary>
    /// Retrieve synced decks for the specified or current user.
    /// </summary>
    [HttpGet("Decks")]
    [ProducesResponseType(typeof(IReadOnlyList<DeckSyncDto>), StatusCodes.Status200OK)]
    public ActionResult<IReadOnlyList<DeckSyncDto>> GetDecks([FromQuery] string? userId)
    {
        var targetUser = !string.IsNullOrWhiteSpace(userId) ? userId : "default";
        var decks = _store.GetDecks(targetUser);
        return Ok(decks);
    }

    /// <summary>
    /// Upsert client deck progress using set-union cycle merging.
    /// </summary>
    [HttpPost("Decks")]
    [ProducesResponseType(typeof(DeckSyncDto), StatusCodes.Status200OK)]
    public ActionResult<DeckSyncDto> UpsertDeck([FromBody] DeckSyncDto deck, [FromQuery] string? userId)
    {
        if (deck == null || string.IsNullOrWhiteSpace(deck.DeckId))
        {
            return BadRequest(new { error = "Invalid deck payload. DeckId is required." });
        }

        var targetUser = !string.IsNullOrWhiteSpace(userId) ? userId : "default";
        var merged = _store.UpsertDeck(targetUser, deck);
        _logger.LogInformation(
            "Deck progress upserted for user {UserId}, deck {DeckId}, cycle {CycleNumber}, exhausted count {Count}",
            targetUser,
            deck.DeckId,
            deck.CycleNumber,
            merged.ExhaustedItemIds.Count
        );
        return Ok(merged);
    }
}
