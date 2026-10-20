# Kx Launcher for Windows

A native Windows (WinUI 3) version of Kx Launcher. It lives next to the Android app and does **not** change it.

## Why this is a separate C# project

The Android app is a Minecraft launcher whose Java code is tied to Android (about 70% of the files import `android.*`),
and its C code (`app_kx/src/main/jni`) exists only to run a JVM inside an Android process (ELF hooks, `dlopen` hooks,
`ANativeWindow`). On Windows none of that is needed: Minecraft runs on a normal `java.exe`. So the Windows app only
needs the launcher logic: version list, downloads, accounts, building the `java` command line.

## Layout

| Project | What it is |
|---|---|
| `KxLauncher.Core` | .NET 8 class library: Mojang version manifest, Java detection. UI-free, add launch logic here. |
| `KxLauncher.App` | WinUI 3 app with a Mica / Acrylic glass window. |

## Build (on Windows)

1. Install **Visual Studio 2022 17.8+** with the workloads **.NET desktop development** and
   **Windows application development** (the "WinUI application development" component).
2. Open `windows/KxLauncher.sln`.
3. Choose the **x64** platform and press **F5**.

Or from a terminal: `dotnet build windows/KxLauncher.sln -p:Platform=x64`

You also need a Java runtime (Java 17 or 21 recommended) installed to play. The app lists any it finds.

## Status

- Done: window with glass backdrop, version list with filters, Java detection.
- Not done yet: the Play button (download version JSON, libraries, assets and natives, then build and run the java command), Microsoft account login, instances, mods.

## Glass look

Windows has no built-in "liquid glass" refraction. The app uses Mica Alt (Windows 11) with a Desktop Acrylic fallback,
plus translucent acrylic cards, which is the closest native equivalent.
