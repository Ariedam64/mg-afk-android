# MG AFK Android

MG AFK Android is a lightweight mobile app that lets you stay connected to
Magic Garden without launching the game. It keeps a session open to display pet
ability logs, shop inventory, weather and more while minimizing battery usage.
You can also interact directly from the app: buy items from shops, chat with
other players in the room, manage your feeding trough, feed your pets, and
swap or equip pets from your inventory.

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
|-----------|-------------------------------------------------------|
| Dashboard | Connection setup + live status |
| Room | Chat with players in the room |
| Pets | Pet hunger, STR, abilities, feed/swap/equip, ability logs |
| Shops | Buy seeds / tools / eggs / decors (single, bulk, hybrid modes) |
| Garden | Garden plants and eggs overview |
| Storage | Inventory, feeding trough (add/remove items) |
| Alerts | Notification config (shops, weather, pets, feeding trough) |
| Settings | Background & battery, reconnection, purchase mode, developer options |
| Debug | WebSocket logs, service logs, alert testing |

Sections that require an active connection are greyed out when offline.
The Debug section is hidden by default and can be enabled in Settings.

## Multiple accounts

MG AFK supports multiple sessions. Use the tabs bar to add a new account (+)
and switch between sessions. Each tab keeps its own login, room code, and
reconnect settings.

## Background & lock screen

The app runs in the background even when the phone is locked using a
foreground service. A Wi-Fi lock keeps the network active and an optional
CPU wake lock (off, smart, or always) prevents the system from sleeping
during long AFK sessions. The smart mode automatically enables the CPU lock
after the phone has been locked for a configurable delay and releases it on
unlock.

If the WebSocket disconnects, the app retries indefinitely with exponential
backoff and reconnects immediately when the network comes back. An optional
notification can alert you when a session loses connection.

## Pets

The Pets section shows your active pets with hunger bars, STR stats, and
ability badges with dynamic colors from the game data. Tap a pet to feed it
from your produce, swap it with another pet from inventory/hutch, or remove it.
Empty slots can be filled by equipping a pet directly.

Ability logs show a detailed description for each proc (e.g. "Snail found
25552 coins", "Turtle reduced 1 plant growth by 4m 54s") alongside a colored
ability badge matching the game UI.

## Shops

Three purchase modes are available (configurable in Settings):
- **Hybrid** (default): tap to buy x1, long-press to buy all remaining stock.
- **Single**: tap always buys x1.
- **Bulk**: tap buys all remaining stock at once.

## Alerts

MG AFK can notify you about shop restocks, weather changes, low pet hunger,
and feeding trough levels. Configure thresholds for pet hunger and feeding
trough in the Alerts section. Alerts work in the background and when the
phone is locked.

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
