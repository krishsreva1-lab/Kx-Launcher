namespace KxLauncher.Core;

/// <summary>Finds java.exe installations on Windows (Minecraft itself runs on a real JVM there).</summary>
public static class JavaLocator
{
    public static IReadOnlyList<string> FindJavaExecutables()
    {
        var found = new List<string>();
        var exeName = OperatingSystem.IsWindows() ? "java.exe" : "java";

        void TryAdd(string? path)
        {
            if (!string.IsNullOrEmpty(path) && File.Exists(path) &&
                !found.Contains(path, StringComparer.OrdinalIgnoreCase))
                found.Add(path);
        }

        // 1. JAVA_HOME
        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(javaHome))
            TryAdd(Path.Combine(javaHome, "bin", exeName));

        // 2. Common install roots (Temurin, Microsoft, Oracle, Zulu, ...)
        foreach (var root in CommonJavaRoots())
        {
            if (!Directory.Exists(root)) continue;
            foreach (var dir in Directory.EnumerateDirectories(root))
                TryAdd(Path.Combine(dir, "bin", exeName));
        }

        // 3. Anything on PATH
        var pathVar = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (var dir in pathVar.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
            TryAdd(Path.Combine(dir.Trim('"'), exeName));

        return found;
    }

    private static IEnumerable<string> CommonJavaRoots()
    {
        var pf = Environment.GetEnvironmentVariable("ProgramFiles");
        if (string.IsNullOrEmpty(pf)) yield break;
        foreach (var vendor in new[] { "Eclipse Adoptium", "Microsoft", "Java", "Zulu", "BellSoft", "Amazon Corretto" })
            yield return Path.Combine(pf, vendor);
    }
}
