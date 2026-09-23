using System.Collections.Generic;
using System.Net.Mime;
using Jellyfin.Plugin.Jellycopter.Models;
using Jellyfin.Plugin.Jellycopter.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.Jellycopter.Api;

[ApiController]
[Route("Jellycopter/Achievements")]
[Produces(MediaTypeNames.Application.Json)]
public class AchievementsController : ControllerBase
{
    private readonly ILogger<AchievementsController> _logger;
    private readonly IShuffleDeckStore _store;

    public AchievementsController(ILogger<AchievementsController> logger, IShuffleDeckStore? store = null)
    {
        _logger = logger;
        _store = store ?? ShuffleDeckStore.Instance;
    }

    /// <summary>
    /// Retrieve all achievements synced for a user.
    /// </summary>
    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<AchievementSyncDto>), StatusCodes.Status200OK)]
    public ActionResult<IReadOnlyList<AchievementSyncDto>> GetAchievements([FromQuery] string? userId)
    {
        var targetUser = !string.IsNullOrWhiteSpace(userId) ? userId : "default";
        var achievements = _store.GetAchievements(targetUser);
        return Ok(achievements);
    }

    /// <summary>
    /// Synchronize client milestone progress and unlocked status with the server.
    /// </summary>
    [HttpPost]
    [ProducesResponseType(StatusCodes.Status200OK)]
    public ActionResult UpsertAchievements([FromBody] List<AchievementSyncDto>? achievements, [FromQuery] string? userId)
    {
        if (achievements == null)
        {
            return BadRequest(new { error = "Invalid achievements payload." });
        }

        var targetUser = !string.IsNullOrWhiteSpace(userId) ? userId : "default";
        _store.UpsertAchievements(targetUser, achievements);
        _logger.LogInformation("Synced {Count} achievements for user {UserId}", achievements.Count, targetUser);
        return Ok(new { success = true, count = achievements.Count });
    }
}
