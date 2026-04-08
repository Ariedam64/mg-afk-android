# MG AFK Android

MG AFK Android is a lightweight, read-only mobile app that lets you stay
connected to Magic Garden without launching the game. It keeps a session open
to display pet ability logs, shop inventory, weather and more while minimizing
battery usage. No in-game actions are performed.

## How it works

MG AFK connects to the game's WebSocket endpoint and authenticates using your
Discord account. Incoming data is parsed and displayed across dedicated
sections (dashboard, pets, shops, alerts).

## Login

Tap **Login with Discord** in the Dashboard. A browser window opens on
Discord's OAuth page — log in and the app captures your session token
automatically. The token is stored persistently so you only need to log in
once.

To log out, tap **Logout**. This clears the stored token.

## Navigation

Swipe from the left edge or tap the hamburger menu to open the navigation
drawer. Sections:

| Section | Content |
|-----------|---------------------------------------------|
| Dashboard | Connection setup + live status |
| Pets | Pet hunger bars + ability logs |
| Shops | Current seed / tool / egg / decor inventory |
| Alerts | Notification config (shops, weather, pets) |

Sections that require an active connection are greyed out when offline.

## Multiple accounts

MG AFK supports multiple sessions. Use the tabs bar to add a new account (+)
and switch between sessions. Each tab keeps its own login, room code, and
reconnect settings.

## Background & lock screen

The app runs in the background even when the phone is locked. The WebSocket
connection stays alive so you never miss an update. Alerts and notifications
are delivered regardless of whether the screen is on or the app is in the
foreground.

## Alerts

MG AFK can notify you about shop restocks, weather changes, and low pet
hunger. Toggle individual items in the Alerts section. Alerts work in the
background and when the phone is locked.

## Build

Prerequisites:
- Android Studio (latest stable)
- JDK 17+
- Android SDK 35

Open the project in Android Studio and run on a device/emulator, or build from
the command line:

```bash
./gradlew assembleDebug
```

The debug APK will be in `app/build/outputs/apk/debug/`.

## Credits

- WebSocket message parsing and actions are based on [MG-Websocket-Helper](https://github.com/Ariedam64/MG-Websocket-Helper).
- Sprites and game data are fetched from the unofficial game API: [Magic-Garden-API](https://github.com/Ariedam64/Magic-Garden-API).

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp (WebSocket)
- Kotlinx Serialization
- Coil (sprite loading)
- DataStore (persistent settings)
