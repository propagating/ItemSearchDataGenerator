#!/usr/bin/env python3
"""Interactive curator for the drop-search combat overlay.

Walks the mobs flagged ``needsReview`` in ``combat-overlay.json``, shows each
one's OSRS Wiki page link plus its attacks, and lets you set the style, max
hit, and speed of each attack. You can replace a mob's whole attack list in one
line (handy for messy stubs) or edit the existing entries field-by-field. Each
mob is marked reviewed and the file is saved as you go, so you can stop and
resume any time.

After curating, re-run the generator to bake the values into drops.json:

    ./gradlew run --args="monsters-complete.json"

Usage:
    python curate_combat.py           # curate mobs needing review
    python curate_combat.py --list     # just print them + wiki links
    python curate_combat.py --all       # revisit every overlay entry
"""
import json
import os
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
OVERLAY = os.path.join(BASE, "combat-overlay.json")
MONSTERS = os.path.join(BASE, "monsters-complete.json")
DROPS = os.path.abspath(os.path.join(BASE, "..", "..", "JAVA", "ItemSearch",
                        "src", "main", "resources", "com", "itemsearch", "drops.json"))
WIKI = "https://oldschool.runescape.wiki/w/"


def load_json(path, default=None):
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build_meta():
    """id(str) -> (name, wiki_url, combat_level); exact from osrsbox, else drops.json."""
    meta = {}
    monsters = load_json(MONSTERS)
    if monsters:
        for v in monsters.values():
            if v and v.get("id") is not None:
                meta[str(v["id"])] = (v.get("name"), v.get("wiki_url"), v.get("combat_level"))
        return meta
    for n in load_json(DROPS, []) or []:
        name = n.get("name", "")
        meta[str(n["id"])] = (name, WIKI + name.replace(" ", "_"), n.get("combatLevel"))
    return meta


def describe(meta, idstr):
    """-> (name, wiki_url, level). Same-name mobs differ by level, so level disambiguates."""
    name, url, level = meta.get(idstr, (None, None, None))
    if not url and name:
        url = WIKI + name.replace(" ", "_")
    return name or "NPC " + idstr, url or "(no wiki link found)", level


def save(overlay):
    ordered = {k: overlay[k] for k in sorted(overlay, key=lambda x: int(x))}
    with open(OVERLAY, "w", encoding="utf-8") as f:
        json.dump(ordered, f, indent=2)
        f.write("\n")


def ask(prompt):
    """input() that returns None on EOF (piped input exhausted / Ctrl-D)."""
    try:
        return input(prompt)
    except EOFError:
        return None


def fmt_range(low, high):
    """(low, high) -> 'N' or 'low-high' (single when equal / high missing)."""
    low = low or 0
    high = max(low, high or 0)
    return str(high) if low == high or low == 0 else "{}-{}".format(low, high)


def parse_range(text):
    """'N' or 'low-high' -> (low, high); blank -> (0, 0); bad input -> None."""
    text = (text or "").strip()
    if text == "":
        return (0, 0)
    if "-" in text:
        a, b = text.split("-", 1)
        try:
            lo, hi = int(a.strip()), int(b.strip())
        except ValueError:
            return None
        return (min(lo, hi), max(lo, hi))
    try:
        v = int(text)
        return (v, v)
    except ValueError:
        return None


def fmt_attacks(attacks):
    return ", ".join("{}:{}:{}".format(a.get("style"),
                     fmt_range(a.get("maxHit", 0), a.get("maxHitMax", 0)),
                     fmt_range(a.get("speed", 0), a.get("speedMax", 0)))
                     for a in attacks) or "(none)"


def parse_spec(spec):
    """'style:maxrange:speedrange, ...' (ranges as N or low-high) -> attack dicts, or None."""
    out = []
    for token in spec.split(","):
        token = token.strip()
        if not token:
            continue
        parts = [p.strip() for p in token.split(":")]
        style = parts[0].lower()
        if not style:
            return None
        mh = parse_range(parts[1]) if len(parts) > 1 else (0, 0)
        sp = parse_range(parts[2]) if len(parts) > 2 else (0, 0)
        if mh is None or sp is None:
            return None
        out.append({"style": style, "maxHit": mh[0], "maxHitMax": mh[1],
                    "speed": sp[0], "speedMax": sp[1]})
    return out or None


def edit_each(attacks):
    """Field-by-field edit of each attack; max hit/speed accept N or low-high. -> 'quit'/'eof'/None."""
    for a in attacks:
        raw = ask("      style [{}]: ".format(a.get("style", "")))
        if raw is None:
            return "eof"
        raw = raw.strip()
        if raw.lower() == "q":
            return "quit"
        if raw:
            a["style"] = raw.lower()
        for low, high, label in (("maxHit", "maxHitMax", "max hit"), ("speed", "speedMax", "speed")):
            while True:
                raw = ask("      {} [{}]: ".format(label, fmt_range(a.get(low, 0), a.get(high, 0))))
                if raw is None:
                    return "eof"
                raw = raw.strip()
                if raw == "":
                    break
                if raw.lower() == "q":
                    return "quit"
                rng = parse_range(raw)
                if rng is None:
                    print("      enter N or low-high, blank to keep, 'q' to quit")
                    continue
                a[low], a[high] = rng
                break
    return None


def curate_mob(entry):
    """Curate one mob's attacks in place. Returns 'saved', 'skip', 'quit', or 'eof'."""
    attacks = entry.get("attacks") or []
    print("    current: " + fmt_attacks(attacks))
    print('    Type new attacks as "style:max:speed" (comma-separated) to replace them, or:')
    print("      blank = edit the current ones field-by-field    [s] skip    [q] quit")
    raw = ask("    attacks> ")
    if raw is None:
        return "eof"
    raw = raw.strip()
    if raw.lower() == "q":
        return "quit"
    if raw.lower() == "s":
        return "skip"
    if raw == "":
        if not attacks:
            print("    (nothing to edit — type a spec line to add attacks)")
            return "skip"
        result = edit_each(attacks)
        return result if result in ("quit", "eof") else "saved"
    parsed = parse_spec(raw)
    if parsed is None:
        print("    couldn't parse that — expected style:max:speed[, ...]. Skipping.")
        return "skip"
    entry["attacks"] = parsed
    return "saved"


def main():
    # Windows consoles default to cp1252; force UTF-8 so wiki names/output never crash.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass

    args = sys.argv[1:]
    list_only = "--list" in args
    include_all = "--all" in args

    overlay = load_json(OVERLAY, {})
    if overlay is None:
        print("No combat-overlay.json found at " + OVERLAY)
        return
    meta = build_meta()

    todo = sorted((k for k, v in overlay.items() if include_all or v.get("needsReview")),
                  key=lambda x: int(x))
    if not todo:
        print("Nothing to review. (Pass --all to revisit every overlay entry.)")
        return

    if list_only:
        for i, idstr in enumerate(todo, 1):
            name, url, level = describe(meta, idstr)
            print("[{}/{}] {} (level {}, id {})\n    {}\n    {}".format(
                i, len(todo), name, level, idstr, url, fmt_attacks(overlay[idstr].get("attacks") or [])))
        return

    print("{} mob(s) to review. For each, open the wiki link and set style:max:speed.\n".format(len(todo)))

    for i, idstr in enumerate(todo, 1):
        entry = overlay[idstr]
        name, url, level = describe(meta, idstr)
        print("--- [{}/{}] {}  (level {}, id {}) ---".format(i, len(todo), name, level, idstr))
        print("    wiki: " + url)

        result = curate_mob(entry)
        if result in ("quit", "eof"):
            save(overlay)
            print("\nSaved." + ("" if result == "quit" else " (input closed)"))
            return
        if result == "skip":
            print("    skipped\n")
            continue
        entry["needsReview"] = False
        save(overlay)
        print("    saved: " + fmt_attacks(entry["attacks"]) + "\n")

    print("Done. Re-run the generator to apply:")
    print('  ./gradlew run --args="monsters-complete.json"')


if __name__ == "__main__":
    main()
