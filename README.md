<H1 align="center">Kx Launcher</H1>

<a href="./README_RU.md">Readme на русском</a>

<img src="./app_kx/src/main/assets/kx.png" align="left" width="150" height="150" alt="Kx Launcher logo">

[![Discord](https://img.shields.io/discord/1365346109131722753.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/VHdwQFsaGX)

* Kx Launcher is a launcher, based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher), that allows you to play Minecraft: Java Edition on your Android device!

* It can run almost every version of Minecraft, allowing you to use .jar only installers to install modloaders such as [Forge](https://files.minecraftforge.net/) and [Fabric](http://fabricmc.net/) and mods like [OptiFine](https://optifine.net).

## Navigation
- [Introduction](#introduction)
- [Getting Kx Launcher](#getting-kx-launcher)
- [Building](#building) 
- [Current roadmap](#current-roadmap) 
- [License](#license) 
- [Contributing](#contributing) 
- [Credits & Third party components and their licenses](#credits--third-party-components-and-their-licenses-if-available)

## Introduction 
* Kx Launcher is a Minecraft: Java Edition launcher for Android based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
* This launcher can launch almost all available Minecraft versions ranging from rd-132211 to 26.x snapshots (including Combat Test versions). 
* Modding via Forge and Fabric are also supported. 

## Getting Kx Launcher

You can get Kx Launcher via two methods:

1. You can get the prebuilt app from the releases section.

2. You can get early builds from GitHub Actions.

3. You can [build](#building) from source.
## Building   
* Build the launcher (it will automatically download all required components)
```
./gradlew :app_kx:assembleDebug
```
If you are building on Windows:
* Replace `./gradlew` with `.\gradlew.bat`
* Make sure `mojoexec`, `sdl`, `glfw` are symlinked into `app_kx/src/main/jni/`

## Current roadmap
- [x] Instance system in favor of profiles
- [x] Out-of-the box 1.21.5 support
- [x] mrpack/CurseForge zip import
- [x] LTW: enable compute shader/image extensions
- [ ] LTW: resolve issues with Create
- [ ] LTW: switch to a color-renderable format for framebuffers
- [ ] Modpack/mod management tool
- [ ] MMC-compatible instance import
- [ ] Vintage Story support
- [ ] Implement common native library standard

## Known Issues
- Some physical mice may have very slow mouse speed
- On Holy GL4ES, large texture atlases may be distorted (resulting in stretched/blocky textures in modpacks)
- Probably more, that's why we have a bug tracker ;) 

## License
- Kx Launcher is licensed under GNU LGPLv3.

## Contributing
Contributions are welcome! We welcome any type of contribution, not only code. For example, you can help the wiki shape up. You can help the [translation](https://crowdin.com/project/pojavlauncher) too!


Any code change to this repository should be submitted as a pull request. The description should explain what the code does and give steps to execute it.

## Third party components, licenses and sources (when applicable)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GNU LGPLv3 License](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).<br>
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).<br>
- GLFW: zlib license
- SDL: zlib license
- LWJGL2-GLFW: 3-Clause BSD license
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- mojoexec: MIT License
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [pro-grade](https://github.com/pro-grade/pro-grade) (Java sandboxing security manager): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
- [bhook](https://github.com/bytedance/bhook) (Used for exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
- [Authlib-Injector](https://github.com/yushijinhun/authlib-injector) (Used for authorisation via ely.by): [AGPL-3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE).
- [alsoft](https://github.com/kcat/openal-soft/) (Audio output library): [GNU LIBRARY GENERAL PUBLIC LICENSE](https://github.com/kcat/openal-soft/blob/master/COPYING) and [modified PFFFT](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft).
- [oboe](https://github.com/google/oboe): [Apache License 2.0](https://github.com/google/oboe/blob/main/LICENSE).
- Thanks to [Mineskin](https://mineskin.eu/) for providing Minecraft avatars.
