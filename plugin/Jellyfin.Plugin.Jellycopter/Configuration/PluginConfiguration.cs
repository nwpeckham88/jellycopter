using MediaBrowser.Model.Plugins;

namespace Jellyfin.Plugin.Jellycopter.Configuration;

public class PluginConfiguration : BasePluginConfiguration
{
    public bool EnableDeckSync { get; set; } = true;

    public bool EnableAchievementSync { get; set; } = true;
}
