#!/usr/bin/env python3
"""Builds feature/room/src/main/resources/emoji.json — the Unicode emoji the picker offers — from
emojibase (https://emojibase.dev, MIT): groups in order, and per emoji its character, group, name
and search terms (shortcodes + tags), plus its skin-tone variants when it has any (a long press picks
one). Components are left out.
Usage: scripts/update-emoji.py"""
import json
import os
import urllib.request

BASE = "https://raw.githubusercontent.com/milesj/emojibase/refs/heads/master/packages/data/en/"


def get(path):
    with urllib.request.urlopen(BASE + path) as r:
        return json.load(r)


compact = get("compact.raw.json")
shortcodes = get("shortcodes/emojibase.raw.json")
groups = sorted(get("messages.raw.json")["groups"], key=lambda g: g["order"])
COMPONENT = next(g["order"] for g in groups if g["key"] == "component")

emoji = []
for e in sorted((e for e in compact if "group" in e and e["group"] != COMPONENT), key=lambda e: e["order"]):
    codes = shortcodes.get(e["hexcode"], [])
    codes = [codes] if isinstance(codes, str) else codes
    terms = list(dict.fromkeys(codes + e.get("tags", [])))
    row = [e["unicode"], e["group"], e["label"], terms]
    if e.get("skins"):
        row.append([s["unicode"] for s in e["skins"]])
    emoji.append(row)

out = {
    "source": "emojibase (MIT) — https://emojibase.dev",
    "groups": [g["message"] for g in groups],
    "emoji": emoji,
}
path = os.path.join(os.path.dirname(__file__), "..", "feature/room/src/main/resources/emoji.json")
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
print(f"{len(emoji)} emoji in {len(groups)} groups → {os.path.normpath(path)}")
