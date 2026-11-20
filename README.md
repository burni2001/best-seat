# Best Seat

A tvOS-inspired home screen launcher for Google TV / Android TV, built for a Philips OLED TV.

## Features

- Home screen with an app grid, folders, a dock and a top shelf showing content previews
- Control center with profiles, picture and sound shortcuts, and Ambilight and Hue controls
- App switcher and an All Apps view
- Screensaver with aerial videos and online photos, plus a hopping clock (OLED-friendly)
- Light and dark mode; layout and settings backup

## Build

There is no Gradle. `build.sh` builds `build/launcher.apk` directly with the Android build tools (API 34, Java 8 target):

```bash
./build.sh
adb install -r --user 0 build/launcher.apk
```

`build.sh` creates `debug.keystore` on first run; it is not part of this repository.

Package: `tv.bernhard.launcher`

Created by Börni (burni2001). Built with Claude Code.
