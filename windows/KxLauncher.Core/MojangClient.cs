using System.Net.Http.Json;

namespace KxLauncher.Core;

/// <summary>Talks to Mojang's public launcher-meta endpoints.</summary>
public sealed class MojangClient : IDisposable
{
    public const string VersionManifestUrl =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private readonly HttpClient _http;

    public MojangClient(HttpClient? http = null)
    {
        _http = http ?? new HttpClient();
        if (_http.DefaultRequestHeaders.UserAgent.Count == 0)
            _http.DefaultRequestHeaders.UserAgent.ParseAdd("KxLauncher-Windows/1.0");
    }

    public async Task<VersionManifest> GetVersionManifestAsync(CancellationToken ct = default)
    {
        var manifest = await _http.GetFromJsonAsync<VersionManifest>(VersionManifestUrl, ct);
        return manifest ?? throw new InvalidDataException("Mojang returned an empty version manifest.");
    }

    public void Dispose() => _http.Dispose();
}
