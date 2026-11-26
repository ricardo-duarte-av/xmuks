# xmuks battery drain on the Pixel 8a — investigation notes

Started 2026-11-26. Status: **open**. The code has been read and cleared of the obvious
suspects. The next step is measuring on the device over adb.

## Symptom

| | Pixel 8a | Motorola 60 Pro |
|---|---|---|
| xmuks accounts | 6 | 1 (work, mostly inactive) |
| Notifications (Android's count) | ~330/day | ~30/day |
| xmuks battery since 07:00 | **17%**, 46 min screen, **3h11m background** | ~0% |

The Pixel sat idle most of the day and was at 55% by the evening. The user suspects the
multiple accounts.

## What the code was cleared of (don't re-suspect without device data)

1. **Streams.** Only the account on screen streams `/sse`. `AccountGraph` passes `isActive` as
   `ForegroundConnection`'s `loggedIn` flag (`core/data/.../account/AccountGraph.kt`, the
   `foregroundConnection` lazy block). Switching away drops it to false, so `update()` stops
   the stream with 0 linger. Leaving the app keeps the stream 30 s, then closes it.
   (An earlier claim in that session, that all 6 accounts stream, was **wrong**.)
2. **Pushes don't start accounts.** `XmuksMessagingService` uses `Accounts.signedIn()` →
   `built()`, which doesn't run the `AccountStarter`s.
3. **Push images come from disk.** `MxcCacheKeys` keys every gomuks image by
   `mxc://…#thumbnail=avatar`, the same key the app uses (`MediaUrls.avatar`), and ignores
   `image_auth`. `MediaCacheStrategy` serves the cached copy for 30 days without asking the
   network. Only a never-seen avatar, or a message's own picture, is downloaded.
4. **`get_event` on a push is capped at 3 s** (`RoomNotifier.EVENT_MS`). The cap works:
   `ExecClient.once` uses OkHttp's `await()`, which cancels the call when the coroutine is
   cancelled.
5. **Background work.** `CatchUpWorker` runs every 6 h: it renews every account's push
   registration, then catches up the shown account for up to 90 s, with at most 2 retries.
   That's ≤ ~18 min/day at worst.
6. **Services.** The only one that starts itself is `CallService` (a foreground service,
   only during a call). FCM, the watch link (`WatchLinkService`) and the Contacts sync adapter
   run only when the system calls them. No alarms, no wake locks outside calls, no polling.

Rough budget from the code: 330 pushes × ~1–4 s ≈ 5–20 min/day, plus the worker. That's far
from 3h11m. **Something not visible from the code is keeping the process in a background
state.** The device has to tell us what.

## Device findings (2026-11-26 evening, captures in `battery/`, not committed)

Since 07:08 (12h23m on battery): xmuks 242 mAh estimated, 3rd on the phone; 127 of it is
screen. Rest: CPU 47 (fg 25, bg 13.6, cached 8), mobile radio 47.5 (21 while cached, i.e.
pushes), GPU 9, wake locks 3.4.

- **"Background" = Top Sleeping.** 5h01m of the time is xmuks left on top with the screen
  locked (≈ 4h52m in 30+ spans up to 26 min). The user locks the phone with xmuks open.
- **Live test (19:35, xmuks opened, then locked, sampled 8 min):** the stream closes as designed.
  After lock: ~12 s CPU in worker threads and ~0.5 MB received during the 30 s linger, then
  **flat**: no RenderThread, no main thread, no bytes. Each push while locked cost ~0.4–1.4 s CPU.
  Top Sleeping by itself is **cleared**.
- **While the stream is open, the worker threads are busy:** ~5 s CPU per 20 s on screen
  (render ~1.9 s, main ~1.5 s), and ~14 KB/s received. Process total 43 min CPU in ~24 h:
  RenderThread 10.7 min, main 8 min, **~18 min in threads that have exited** (Dispatchers /
  OkHttp), i.e. stream ingest. Every unlock reconnects and every lock lingers 30 s, so many
  short visits add up.
- **Pushes:** 1151 `XmuksMessagingService` starts; wake locks ~5 min total. Not the main cost,
  but at ~1 s CPU each they're ~15–20 min CPU/day.
- **CatchUpWorker:** 2 runs, 2 s. Cleared.
- **Unexplained: 1.85 GB Wi-Fi received** (next app: 73 MB). ~960 MB of it in the 16:00–18:00
  netstats bucket, state FOREGROUND. Ask the user what they did then; if nothing media-heavy,
  this is a download loop and the top lead. User says they didn't use xmuks then. History:
  xmuks on top 15:34–16:11 (screen mostly off, short ~6 s wake-ups), then on top with the
  screen on 16:11–16:22 with video decoder activity (system-wide, not attributed per app). Open.
- **Android's "3h11m background" is the Top Sleeping time**, not work. The whole `bg` bucket
  (5h09m) is ~31 mAh (≈0.8% of the 3896 mAh batterystats capacity), cached (pushes) ~34 mAh.
- **Ranking:** total drain 2516 mAh since 07:08. xmuks 242 is #2 after Android system (279);
  next is Play services at 84. Settings' "20%" is xmuks's share of the app-attributed drain, so
  in battery terms ≈ 6 points: 127 mAh screen, 47.5 mobile radio (21 of it while cached = pushes
  on mobile), 47 CPU, 9 GPU.
- Settings data usage: 536 MB **mobile** since 2026-10-03, consistent with netstats. The 1.85 GB
  is Wi-Fi; check it in Settings under non-carrier (Wi-Fi) data usage.

**Pushes: 1151 received vs ~330 notifications.** ~70% of pushes show nothing. Every push is now
logged (tag `XmuksPush`, and appended to `Android/data/pt.aguiarvieira.xmuks/files/push-log.txt`,
restarted past 4 MB): account, messages, how many were actually shown, rings, dismiss, invites,
per-message flags (`c` call, `m` mention, `s` sound, `i` image, `-` plain), rooms with counts,
payload size and time held. After a day:

```
adb pull /sdcard/Android/data/pt.aguiarvieira.xmuks/files/push-log.txt battery/
```

Group by account and by kind (shown=0 with messages, dismiss-only, …): that's plan B's input.

Next: (a) find the 960 MB (what was on screen 15:34–16:11; media cache size); (b) measure the
stream's ingest cost per frame/byte while open (`StreamStatsTracker`?), especially the burst
right after reconnect.

## Decisions the user made

- **Never skip `get_event` on a push.** The push payload can't tell the event's full extent
  (formatting, mentions, media) until it has been fetched. Don't propose that again.
- If the push volume turns out to be the cause, **plan B** is reducing it on the gomuks side
  (which accounts and rooms push, the fork's extra pushes such as reactions to own messages).
- The "stream only the shown account" idea is already how it works (point 1). Nothing to do.

## Next steps (on the device)

### 0. Connect without charging

Charging, then unplugging, can clear the "since charged" stats. Use **wireless debugging**:

1. Put the phone and the PC on the same Wi-Fi.
2. On the phone: Settings → System → Developer options → Wireless debugging → on →
   *Pair device with pairing code*.
3. On the PC:
   ```
   adb pair <ip>:<pairing-port>      # enter the 6-digit code
   adb connect <ip>:<port>           # the port on the main Wireless debugging screen
   adb devices
   ```

### 1. Capture (before anything else changes)

```
mkdir -p battery && cd battery
adb shell dumpsys batterystats --charged pt.aguiarvieira.xmuks > batterystats-xmuks.txt
adb shell dumpsys batterystats --charged > batterystats-all.txt     # for comparison with other apps
adb shell dumpsys activity services pt.aguiarvieira.xmuks > services.txt
adb shell dumpsys activity processes | grep -A20 xmuks > processes.txt
adb shell dumpsys jobscheduler | grep -B2 -A12 xmuks > jobs.txt
adb shell dumpsys alarm | grep -B2 -A6 xmuks > alarms.txt
adb shell dumpsys usagestats | grep -i xmuks | tail -200 > usage.txt
adb shell dumpsys netstats detail | grep -A10 "$(adb shell pm list packages -U pt.aguiarvieira.xmuks | sed 's/.*uid://')" > netstats.txt
```

Optional, the full picture for Battery Historian: `adb bugreport bugreport.zip`.

### 2. What to read in `batterystats-xmuks.txt`

- **Process state times**: `Top`, `Fg Service`, `Foreground`, `Background`, `Cached`. Which
  one holds the ~3h?
  - **Background / Foreground service** → something is running: find it in `services.txt`
    and the jobs.
  - **Mostly Cached** → the time is the system's accounting of FCM wakeups; check pushes next.
- **Wake locks** (`Wake lock …`): names and total time. WorkManager's look like `*job*/…`;
  FCM shows as GCM/FCM wake locks.
- **Jobs / syncs**: how many times the catch-up worker and the Contacts sync ran, and for how long.
- **Wakeup alarms** and **`Mobile radio active`** time; network bytes (mobile vs Wi-Fi).
- **CPU time** (`Proc pt.aguiarvieira.xmuks`): user/system CPU. If it's high, something is
  computing, not just waiting.
- Compare with other messaging apps in `batterystats-all.txt`.

### 3. If it points at pushes

Watch them live: `adb logcat -v time | grep -iE "xmuks|FirebaseMessaging|fcm"` while a
message arrives. Measure how long `onMessageReceived` holds (add a timing log around it if
needed) and count pushes per account. That count is input for plan B on the gomuks side.

### 4. If it points at a service, job or stream

Run `adb shell dumpsys activity services pt.aguiarvieira.xmuks` with the app in the
background for a few minutes. Nothing should be running, and anything that is, is the lead.
To see whether the app keeps using the network meanwhile, take `netstats` twice a few minutes
apart and compare (listing its sockets with `ss` needs root).

## Rules while fixing (project conventions)

- Work on `main`, never create a branch.
- No Gradle on the server: CI builds. Before a push, run `scripts/check.sh` (ktlint + detekt).
- Per-account state goes in `AccountGraph` / `scoped`, never in a `@Singleton`.
