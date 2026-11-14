# xmuks

A native Android client for [gomuks](https://github.com/gomuks/gomuks), the always-on Matrix client.

gomuks does the Matrix work (sync, end-to-end encryption, push) on a server you run; xmuks is a fast,
Material 3 Expressive front end for it on your phone. It opens instantly from its own cache, keeps up live
while you're looking, and gets notifications through gomuks' push when you're not.

<p align="center">
  <img src="docs/screenshots/chats-main-a.webp" width="24%" alt="Room list" />
  <img src="docs/screenshots/timeline.webp" width="24%" alt="A room with a reply, a reaction, a poll and a thread" />
  <img src="docs/screenshots/widget-page.webp" width="24%" alt="A widget open in a room" />
  <img src="docs/screenshots/call-spotlight.webp" width="24%" alt="A call" />
</p>

## Features

**Accounts**
- Several gomuks accounts side by side: switch from your avatar, each with its own notifications
- Long-press an account to log it out or put it on the home screen

**Rooms**
- Chats, DMs and spaces (with subspaces), unread counts and mention badges, search
- Hold and drag spaces to reorder them; add and remove a space's rooms in place, and see the rooms
  of a space you haven't joined
- Bridged rooms show their network's logo (WhatsApp, Telegram, Instagram…), with delivery ticks for your messages
- Long-press a room: favourite, low priority, mute, mark read, add a home-screen shortcut
- The room directory, past notifications and mentions, and message search across rooms (gomuks' own
  index, encrypted rooms included, or the server's)

**Timeline**
- Formatted messages (markdown, code, quotes, lists, spoilers, mentions, custom emoji), link previews
- Images, GIFs, videos and voice messages that play in place, stickers, locations, files, galleries
- Replies, threads, reactions, edits with their history, deletions, polls, pinned messages
- Read receipts that move from message to message, typing, jump to the first unread
- Per-message profiles (MSC4144) and room-specific names and avatars
- Room widgets: open a room's widget pages right under its header

**Writing**
- Markdown, replies and threads, edits, `@` and `#` completion, gomuks and bot `/commands`
- Photos, videos and files (several at once, or as one gallery), voice messages, locations, polls
- Emoji and sticker packs in a panel where the keyboard was, with a default skin tone
- A durable outbox: messages survive the app being killed and are never sent twice
- Share to xmuks from any app, including Android's Direct Share

**Calls**
- Voice and video calls with Element Call (MatrixRTC over LiveKit), natively: a lobby, grid and
  spotlight layouts, raised hands and reactions, and the call kept going while you use the app

**Rooms and people**
- Room info: name, topic, avatar, who can join, history visibility, encryption, members by power level,
  power levels, moderation, the room's media as a gallery, and its raw state
- Profiles with room-specific and global names, mutual rooms, starting a DM, ignoring

**Notifications**
- One conversation per room, with the room's and senders' avatars, pictures, and inline reply and mark as read
- Android Auto: read aloud and reply from the car; a Wear OS companion for your watch
- Per-room notification settings and gomuks' notification rules, shared with gomuks

**Everything else**
- gomuks' preferences (per account, device, room, and room on this device), shared with gomuks web
- Rounded cards on a tinted ground, or flat bars edge to edge; rooms coloured from their avatars
- Dynamic colour, light and dark themes, predictive back, shared-element transitions, a baseline profile

## Screenshots

### Rooms and spaces

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/chats-main-a.webp" alt="Your rooms, as rounded cards" /> | <img src="docs/screenshots/chats-flat.webp" alt="…or flat, edge to edge" /> | <img src="docs/screenshots/settings-cards.webp" alt="Appearance settings" /> |
| Your rooms, as rounded cards | …or flat, edge to edge | Appearance settings |
| <img src="docs/screenshots/spaces-grid.webp" alt="Spaces" /> | <img src="docs/screenshots/spaces-drag.webp" alt="Hold and drag to reorder" /> | <img src="docs/screenshots/space-homelab.webp" alt="Inside a space" /> |
| Spaces | Hold and drag to reorder | Inside a space |
| <img src="docs/screenshots/testspace.webp" alt="A space's rooms" /> | <img src="docs/screenshots/space-editor.webp" alt="Editing a space's rooms" /> | <img src="docs/screenshots/space-editor-change.webp" alt="Changes waiting to be saved" /> |
| A space's rooms | Editing a space's rooms | Changes waiting to be saved |
| <img src="docs/screenshots/directory.webp" alt="The room directory" /> | <img src="docs/screenshots/mentions.webp" alt="Mentions and notifications" /> | <img src="docs/screenshots/search.webp" alt="Search" /> |
| The room directory | Mentions and notifications | Search |

### Accounts

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/drawer-full.webp" alt="Several accounts" /> | <img src="docs/screenshots/login.webp" alt="Adding an account" /> | <img src="docs/screenshots/own-profile.webp" alt="Your profile" /> |
| Several accounts | Adding an account | Your profile |
| <img src="docs/screenshots/own-profile2.webp" alt="Per-message profiles and settings" /> |  |  |
| Per-message profiles and settings |  |  |

### Messages

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/timeline.webp" alt="Replies, reactions, polls, threads" /> | <img src="docs/screenshots/t6-testes.webp" alt="The same room, flat" /> | <img src="docs/screenshots/timeline2.webp" alt="Thread summaries" /> |
| Replies, reactions, polls, threads | The same room, flat | Thread summaries |
| <img src="docs/screenshots/room-archivist.webp" alt="Colours from the room's avatar" /> | <img src="docs/screenshots/thread-view.webp" alt="A thread" /> | <img src="docs/screenshots/message-menu-own.webp" alt="Long-press your message" /> |
| Colours from the room's avatar | A thread | Long-press your message |
| <img src="docs/screenshots/message-menu.webp" alt="…or someone else's" /> | <img src="docs/screenshots/pinned-timeline.webp" alt="Pinned messages bar" /> | <img src="docs/screenshots/pinned-list.webp" alt="Pinned messages" /> |
| …or someone else's | Pinned messages bar | Pinned messages |

### Writing

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/emoji-panel.webp" alt="Emoji where the keyboard was" /> | <img src="docs/screenshots/attach-sheet.webp" alt="Attach anything" /> | <img src="docs/screenshots/poll-composer.webp" alt="Writing a poll" /> |
| Emoji where the keyboard was | Attach anything | Writing a poll |

### Widgets

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/room-menu.webp" alt="The room menu" /> | <img src="docs/screenshots/widgets-list.webp" alt="A room's widgets" /> | <img src="docs/screenshots/widget-page.webp" alt="A widget, under the room's header" /> |
| The room menu | A room's widgets | A widget, under the room's header |

### Calls

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/jelly-room.webp" alt="A call in progress" /> | <img src="docs/screenshots/call-lobby.webp" alt="The lobby" /> | <img src="docs/screenshots/call-in.webp" alt="In the call" /> |
| A call in progress | The lobby | In the call |
| <img src="docs/screenshots/call-spotlight.webp" alt="Spotlight" /> | <img src="docs/screenshots/call-minimised.webp" alt="Back in the room, still in the call" /> |  |
| Spotlight | Back in the room, still in the call |  |

### Rooms and people

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/room-info.webp" alt="Room info" /> | <img src="docs/screenshots/room-info2.webp" alt="More room info" /> | <img src="docs/screenshots/members.webp" alt="Members by power level" /> |
| Room info | More room info | Members by power level |
| <img src="docs/screenshots/member-actions.webp" alt="Moderating a member" /> | <img src="docs/screenshots/power-levels.webp" alt="Power levels" /> | <img src="docs/screenshots/user-info.webp" alt="Someone's profile" /> |
| Moderating a member | Power levels | Someone's profile |

### Settings

| | | |
|:-:|:-:|:-:|
| <img src="docs/screenshots/preferences.webp" alt="gomuks' preferences" /> | <img src="docs/screenshots/notif-rules.webp" alt="Notification rules" /> |  |
| gomuks' preferences | Notification rules |  |

## How it talks to gomuks

- `GET /_gomuks/sse` (jsonl + zstd) for live data while the app is open, resuming with `last_server_ts`
- `POST /_gomuks/exec/{command}` for everything outbound, with `txn_id` idempotency
- FCM pushes while it is closed, through xmuks' push gateway (`push.aguiarvieira.pt`), encrypted end to end with a key only the app holds

You need a running gomuks (with its web API reachable over HTTPS) and its username and password. For
notifications, gomuks must allow xmuks' push gateway (gomuks' own, `push.gomuks.app`, can't deliver to
xmuks: it sends for another Firebase project). In gomuks' `config.yaml`:

```yaml
push:
  fcm_gateway_whitelist:
    - push.aguiarvieira.pt
```

## Building

Requires JDK 21 and the Android SDK with `platforms;android-37.1` and `build-tools;37.0.0`.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew testDebugUnitTest           # JVM unit tests (Robolectric, no emulator)
./gradlew verifyRoborazziDebug        # screenshot tests against committed goldens
./gradlew recordRoborazziDebug        # re-record goldens after an intended UI change
./gradlew spotlessApply detekt lintDebug
./gradlew :app:generateReleaseBaselineProfile   # on a connected, logged-in device
```

CI (`.github/workflows/ci.yml`) runs the same checks with `-PwarningsAsErrors=true` on every push. A `vX.Y.Z` tag
matching `versionName` also uploads the AAB to the Play internal track and cuts a GitHub Release.

Release signing reads `keystore.properties` (gitignored) or the `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` environment variables; without them the release build is unsigned.

## Modules

| Module | Purpose |
|---|---|
| `app` | Single activity, navigation, DI entry point, share intents, background catch-up |
| `core:protocol` | gomuks' wire types (sync frames, events, RPC payloads) |
| `core:network` | The SSE stream and `/exec` client, reconnects and resumes |
| `core:database` | The local cache the UI reads (rooms, spaces, previews, members, account data) |
| `core:account` | Accounts: credentials, switching, per-account data |
| `core:data` | Repositories: sync ingestion, timelines, the outbox, media, preferences, search |
| `core:richtext` | gomuks' sanitised HTML to Compose text |
| `core:notify` | Notifications, conversation shortcuts, avatars in the shade |
| `core:push` | FCM, push decryption, notification actions, Android Auto |
| `core:call` | MatrixRTC calls over LiveKit |
| `core:designsystem` | M3 Expressive theme (Google Sans Flex, dynamic colour), shared components |
| `feature:login` | Connecting to a gomuks backend |
| `feature:roomlist` | Chats, DMs, spaces, notifications and search |
| `feature:room` | The timeline, the composer, threads, polls, the media gallery |
| `feature:media` | The full-screen image and video viewer |
| `feature:profile` | Profiles, room info, members, room previews |
| `feature:settings` | gomuks' preferences |
| `feature:share` | Sharing into xmuks from other apps |
| `feature:call` | The call lobby and call screen |
| `wear` | The Wear OS companion (notifications) |
| `baselineprofile` | Generates the app's baseline profile on a device |
| `build-logic` | Convention plugins (`xmuks.android.*`, `xmuks.hilt`, `xmuks.screenshots`) |

## Licences

Google Sans Flex is bundled under the SIL Open Font License (`core/designsystem/src/main/assets/licenses`).
