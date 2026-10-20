using System.Text.Json.Serialization;

namespace KxLauncher.Core;

/// <summary>Mojang's version_manifest_v2.json (list of every Minecraft version).</summary>
public sealed class VersionManifest
{
    [JsonPropertyName("latest")]
    public LatestVersions Latest { get; set; } = new();

    [JsonPropertyName("versions")]
    public List<VersionEntry> Versions { get; set; } = new();
}

public sealed class LatestVersions
{
    [JsonPropertyName("release")]
    public string Release { get; set; } = "";

    [JsonPropertyName("snapshot")]
    public string Snapshot { get; set; } = "";
}

public sealed class VersionEntry
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = "";

    /// <summary>"release", "snapshot", "old_beta" or "old_alpha".</summary>
    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("url")]
    public string Url { get; set; } = "";

    [JsonPropertyName("releaseTime")]
    public DateTimeOffset ReleaseTime { get; set; }

    public override string ToString() => Id;
}
