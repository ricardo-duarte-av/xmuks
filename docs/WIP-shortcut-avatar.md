# WIP: pinned room shortcut stuck on the old avatar

Delete this file once the issue is fixed.

## Symptom

A pinned room shortcut keeps the room's previous avatar. The room avatar changed more than
12 h earlier. The room list shows the new one, and the room's notifications show it too.
Seen after 8ebce93. Room: `!nexy-EHE-ZDufJbaJZwNc4OpwMkjIR3Jcwp90ylObV4`.

## History

- 01506a2: shortcuts follow the room list (`PinnedShortcuts.follow`). The avatar a shortcut
  was made with is stored in the `xmuks.avatar` extra.
- ea734aa: after an icon update, a second update without an icon is sent 1 s later (the
  launcher was thought to load the icon file it had before the new one was written).
- e94ae51 (WIP): adds `ShortcutStamps` (`xmuks.avatar` plus `xmuks.as_of`). The room list
  skips shortcuts made from a newer view than its own. The notifier
  (`ConversationShortcuts`) stamps its shortcut and sends the second update when the avatar
  changed.
  **The user ruled this cause out** (the room list already had the new avatar), so the
  `as_of` check doesn't fix this issue. It's kept because it's harmless.

## What's left (not confirmed)

1. `RoomNotifier` calls `pushDynamicShortcut` on every push. That replaces the whole
   shortcut, icon included, every time. If the ea734aa theory holds, every push can bring
   the launcher problem back. e94ae51 only sends the second update when the avatar
   changed.
2. Android rate-limits `updateShortcuts` in the background (about 10 calls per day; the
   count resets when the app comes to the foreground). As far as we know,
   `pushDynamicShortcut` isn't limited. Pushes arrive in the background, so the notifier's
   second update, and any `follow` update made in the background, may be dropped without
   notice. We ignore `updateShortcuts`' Boolean result everywhere.
3. Not explained yet: with the app in the foreground, `follow` should republish a
   shortcut whose `xmuks.avatar` doesn't match the room list. Either it doesn't (an
   exception swallowed by `runCatching`, the shortcut not matched by ID or account, no
   emission), or it does and the launcher ignores it.

## On the device

- `adb shell dumpsys shortcut`: find the shortcut ID (plain room ID for the first
  account, `account/roomId` otherwise). Check its flags (pinned/dynamic/cached), extras
  (`xmuks.avatar`, `xmuks.as_of`), icon file, last-changed time, and the package's
  rate-limit counters ("Calls left").
- Pull the icon file it points to and look at it. Does the system hold the old or the
  new bitmap? Old: our side. New: the launcher's icon cache.
- Add temporary logging around every `updateShortcuts` and `pushDynamicShortcut` call
  (result and caught exceptions) in `PinnedShortcuts.publish/update` and
  `ConversationShortcuts.publish`.
- Which launcher? Check it with an icon change while the app is in the foreground and
  again in the background.

## Candidate fix (once confirmed)

When the shortcut already has the same avatar and name, the notifier doesn't push it
again: it calls `reportShortcutUsed` instead (not rate-limited), and only pushes when
something changed. Check `updateShortcuts`' result and retry in the foreground when it
was rate-limited.
