using System;
using System.Collections.Generic;
using Jellyfin.Plugin.Jellycopter.Configuration;
using MediaBrowser.Common.Configuration;
using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins;
using MediaBrowser.Model.Serialization;

namespace Jellyfin.Plugin.Jellycopter;

public class Plugin : BasePlugin<PluginConfiguration>, IHasWebPages
{
    public override string Name => "Jellycopter";

    public override Guid Id => Guid.Parse("3b49e6f2-78d1-419b-a452-9c125df1a89c");

    public override string Description => "Companion plugin for Jellycopter client providing smart shuffle deck synchronization and achievement tracking.";

    public static Plugin? Instance { get; private set; }

    public Plugin(IApplicationPaths applicationPaths, IXmlSerializer xmlSerializer)
        : base(applicationPaths, xmlSerializer)
    {
        Instance = this;
    }

    public IEnumerable<PluginPageInfo> GetPages()
    {
        return new[]
        {
            new PluginPageInfo
            {
                Name = "Jellycopter",
                EmbeddedResourcePath = string.Format(System.Globalization.CultureInfo.InvariantCulture, "{0}.Configuration.configPage.html", GetType().Namespace)
            }
        };
    }
}
