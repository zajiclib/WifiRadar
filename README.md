# WiFi Radar Analyzer

A desktop application for scanning and visualizing nearby Wi-Fi networks in real time.

## Features

- **Animated radar view** — networks appear as blips positioned by signal strength and BSSID-derived angle, with a sweeping scan line.
- **Network list** — sortable table showing SSID, signal percentage, channel, band (2.4 / 5 GHz), and security type.
- **Channel congestion chart** — bar chart showing how many networks occupy each 2.4 GHz and 5 GHz channel.
- **Signal history** — line chart tracking signal strength over time for up to 5 networks (or just the selected one).
- **Detail card** — click any network row to inspect its BSSID, signal in % and dBm, channel, band, and security.
- **Auto-scan** — rescans every 10 seconds automatically; manual "Scan Now" button available at any time.

## Platform support

The scanner uses native OS commands — no third-party Wi-Fi library required:

| OS      | Command used                                                        |
|---------|---------------------------------------------------------------------|
| Windows | `netsh wlan show networks mode=bssid`                               |
| macOS   | `airport -s`                                                        |
| Linux   | `nmcli -f SSID,BSSID,SIGNAL,CHAN,SECURITY -t dev wifi`              |

## Tech stack

| Layer        | Technology                                       |
|--------------|--------------------------------------------------|
| Language     | Kotlin 2.4                                       |
| UI framework | Compose Multiplatform 1.11 (desktop / JVM target)|
| Build system | Gradle (Kotlin DSL)                              |
| UI toolkit   | Material 3                                       |

## Requirements

- JDK 17+
- Wi-Fi adapter accessible to the OS scanner command

## Building and running

```bash
# Run from source
./gradlew :desktopApp:run

# Build a native installer
./gradlew :desktopApp:packageMsi       # Windows
./gradlew :desktopApp:packageDmg       # macOS
./gradlew :desktopApp:packageDeb       # Linux
```

## Project structure

```
WifiStat/
├── desktopApp/          # JVM entry point and desktop packaging config
└── shared/
    └── src/
        └── commonMain/
            └── kotlin/org/example/project/
                ├── WifiRadar.kt   # All UI components and WifiScanner logic
                ├── App.kt         # Root composable
                └── Platform.kt   # Platform abstractions
```
