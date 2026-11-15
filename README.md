# Riftbound Table

Personal desktop virtual table for the Riftbound TCG: play hot-seat, or against Claude through your own
Claude Code login. No rules engine: players move the cards and enforce the rules themselves.

Unofficial and not affiliated with Riot Games. Card images and data are used for personal play.

## Install

Download the installer for your system from the latest
[release](https://github.com/mustypit/riftbound_table/releases): `.msi` or `.exe` for Windows,
`.dmg` for macOS (unsigned: right-click → Open the first time), `.deb` for Linux.

## Build it yourself

Needs a JDK 21 (for example Temurin). Then, in the repository folder:

```bash
./gradlew run                 # start the app
./gradlew packageMsi          # Windows installer → build/compose/binaries/main/msi/
./gradlew packageExe          # Windows .exe installer → build/compose/binaries/main/exe/
./gradlew packageDmg          # macOS
./gradlew packageDeb          # Linux
./gradlew test                # tests, with screenshots in build/screenshots/
```

On Windows use `gradlew.bat` instead of `./gradlew`. Installers can only be built for the system
you're on.
