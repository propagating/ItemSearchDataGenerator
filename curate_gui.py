#!/usr/bin/env python3
"""Desktop (Tkinter) editor for the Item Search combat overlay.

Walks the mobs flagged needsReview, shows each one's OSRS Wiki link, and gives
you a style dropdown plus max-hit / speed / conditional-damage fields per
attack. The "In the plugin" panel renders your input the way the combat card
will show it, so you can see the result before saving. Saving writes
combat-overlay.json and marks the mob reviewed. Re-run the generator afterwards
to bake the values into drops.json:

    ./gradlew run --args="monsters-complete.json"

Run:  python curate_gui.py

Shortcuts: Ctrl+S save · Ctrl+Enter save & next · Ctrl+N add attack · Ctrl+F search
           Ctrl+Shift+F find in the wiki text

Reuses the data helpers from curate_combat.py (same folder).
"""
import copy
import os
import sys
import webbrowser

import tkinter as tk
from tkinter import filedialog, ttk

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curate_combat as core

# offered in the style dropdown; the combobox stays editable for anything unusual
STYLES = ["stab", "slash", "crush", "melee", "ranged", "magic",
          "dragonfire", "metallic dragonfire", "icy breath", "typeless",
          # hybrids: FIRST word is the accuracy roll, SECOND is what you pray against
          "magic ranged", "magic melee", "ranged melee",
          "ranged magic", "melee magic", "melee ranged"]

HYBRID_WORDS = {"melee", "ranged", "magic"}
# wiki spellings that mean the same combat class, so "magical melee" splits like "magic melee"
STYLE_ALIASES = {"magical": "magic", "mage": "magic", "range": "ranged",
                 "stab": "melee", "slash": "melee", "crush": "melee"}
VARIANT_KINDS = {"gear", "prayer", "consumable", "combo"}
# what an attack does besides damage; the wiki only records poison and venom, and only per
# monster, so the rest is curated here per attack
EFFECT_TOKENS = {"poison": "POISON", "venom": "VENOM", "disease": "DISEASE",
                 "drain prayer": "DRAIN_PRAYER", "prayer drain": "DRAIN_PRAYER",
                 "drain stats": "DRAIN_STATS", "stat drain": "DRAIN_STATS"}


def parse_effects(text):
    """'poison, drain prayer' -> ['POISON','DRAIN_PRAYER']; unknown words are an error so a
    typo is caught here rather than silently shipping no warning."""
    out = []
    for part in (text or "").split(","):
        part = part.strip().lower()
        if not part:
            continue
        token = EFFECT_TOKENS.get(part)
        if token is None:
            return None, "Unknown effect '{}' (poison, venom, disease, drain prayer, drain stats)".format(part)
        if token not in out:
            out.append(token)
    return out, None


def format_effects(effects):
    return ", ".join((e or "").lower().replace("_", " ") for e in (effects or []))

# Palette: the OSRS stone-panel interface (warm, brown-shifted dark) with
# RuneLite's own BRAND_ORANGE as the single accent — the plugin this tool feeds
# renders in exactly these colors.
INK = "#1E1B17"        # window background
PANEL = "#262119"      # raised surfaces
PANEL_HI = "#322B21"   # hover
FIELD = "#151310"      # recessed entry interiors
RULE = "#443A2C"       # hairlines and field borders
TEXT = "#E8E0D2"       # parchment
MUTED = "#9A8F7C"      # secondary labels
FAINT = "#6B6353"      # placeholders, hints
GOLD = "#FF981F"       # accent: focus, selection, meter fill, primary action
GOLD_DEEP = "#3A2F1D"  # selection band behind gold text
GREEN = "#5FBF4B"
RED = "#D4553B"

F_DISPLAY = ("Segoe UI Semibold", 15)
F_HEAD = ("Segoe UI Semibold", 9)
F_BODY = ("Segoe UI", 9)
F_SMALL = ("Segoe UI", 8)
F_MONO = ("Consolas", 10)
F_MONO_SM = ("Consolas", 9)
F_MONO_BIG = ("Consolas", 12, "bold")  # the preview's max-hit headline


def parse_variants(text, default_kind="gear"):
    """'8 @ Insulated boots; 12-14 @ Antifire' ->
    (variants_list_or_None_if_empty, error_or_None). Kind comes from the column
    (default_kind); an explicit '(kind)' suffix on an entry overrides it."""
    import re as _re
    text = (text or "").strip()
    if not text:
        return None, None
    out = []
    for part in text.split(";"):
        part = part.strip()
        if not part:
            continue
        m = _re.match(r"^(\d+)(?:-(\d+))?\s*@\s*(.+?)(?:\s*\((\w+)\))?$", part)
        if not m:
            return None, "Bad conditional max hit '{}' (use: N @ Label (kind); ...)".format(part)
        lo = int(m.group(1))
        hi = int(m.group(2)) if m.group(2) else lo
        label = m.group(3).strip()
        kind = (m.group(4) or default_kind).lower()
        if kind not in VARIANT_KINDS:
            return None, "Bad kind '{}' (gear/prayer/consumable/combo)".format(kind)
        out.append({"label": label, "kind": kind.upper(), "maxHit": lo, "maxHitMax": hi})
    return (out or None), None


def fmt_variants(variants, column_kind):
    """Entries for one column: the variants matching that kind (COMBO rides in the
    gear column with an explicit suffix so it round-trips)."""
    if not variants:
        return ""
    parts = []
    for v in variants:
        kind = (v.get("kind") or "GEAR").lower()
        home = "gear" if kind == "combo" else kind
        if home != column_kind:
            continue
        lo, hi = v.get("maxHit", 0), v.get("maxHitMax", 0)
        hit = str(lo) if hi in (0, lo) else "{}-{}".format(lo, hi)
        suffix = " (combo)" if kind == "combo" else ""
        parts.append("{} @ {}{}".format(hit, v.get("label", "?"), suffix))
    return "; ".join(parts)


def id_roles():
    """(canonical ids, {variant id: source name}) from the generated sources.

    The overlay is keyed by npc id, but a monster with several ids becomes one source keyed by
    its canonical id, and applyCombatOverlay only looks that one up. Two very different things
    end up unread as a result, and the queue must not conflate them:

    - a *variant* id, which is a real other version of a live monster. The wiki gives Killerwatt
      an Idle version (470, stab) and an Attacking one (469, crush); curating 469 is real work
      that the generator currently discards rather than pointless work.
    - a *stale* id matching no source at all, left over from the osrsbox-era overlay.
    """
    sources = core.load_sources()
    canonical = {s["id"] for s in sources if s.get("id") is not None}
    variants = {}
    for s in sources:
        for npc_id in s.get("npcIds") or []:
            if npc_id != s.get("id"):
                variants[npc_id] = s.get("name")
    return canonical, variants


def collect_attacks(rows):
    """rows: iterable of (style, max_text, speed_text[, gear_text, prayer_text,
    consumable_text, notes]) -> (attacks_list, error_or_None).

    Blank-style rows are dropped; max/speed accept 'N' or 'low-high' (bad input is an error).
    Row order is preserved: the client sorts attacks into prayer order for display, so the
    order here only breaks ties between attacks prayed against the same way.
    """
    attacks = []
    for row in rows:
        style, mx, sp = row[0], row[1], row[2]
        kind_texts = [("gear", row[3] if len(row) > 3 else ""),
                      ("prayer", row[4] if len(row) > 4 else ""),
                      ("consumable", row[5] if len(row) > 5 else "")]
        style = (style or "").strip().lower()
        if not style:
            continue
        if "," in style:
            # one attack per row: a comma here ships to the client as a single unknown style,
            # and the combat card then claims no prayer protects against it
            return None, ("Put one attack per row — '{}' lists several. "
                          "Use '+ Add attack' for each.".format(style))
        mh = core.parse_range(mx)
        spd = core.parse_range(sp)
        if mh is None or spd is None:
            return None, "Attack '{}' has a bad max hit or speed (use N or low-high).".format(style)
        attack = {"style": style, "maxHit": mh[0], "maxHitMax": mh[1],
                  "speed": spd[0], "speedMax": spd[1]}
        words = [STYLE_ALIASES.get(w, w) for w in style.split()]
        if len(words) == 2 and words[0] in HYBRID_WORDS and words[1] in HYBRID_WORDS and words[0] != words[1]:
            # hybrid: accuracy roll first, prayed-against style second
            attack["style"], attack["prayStyle"] = words[0], words[1]
        variants = []
        for kind, text in kind_texts:
            parsed, verr = parse_variants(text, kind)
            if verr:
                return None, verr
            if parsed:
                variants.extend(parsed)
        if variants:
            attack["variants"] = variants
        notes = (row[6] if len(row) > 6 else "") or ""
        if notes.strip():
            attack["notes"] = notes.strip()
        effects, eerr = parse_effects(row[7] if len(row) > 7 else "")
        if eerr:
            return None, eerr
        if effects:
            attack["effects"] = effects
        attacks.append(attack)
    return attacks, None


def headline_max_hit(attack):
    """The combat card's max-hit headline: the worst case, starred when gear can lower it.

    "50*", not "0 (50)" and not "0/5/35/50" — listing every rung crowds the column as soon as
    there are more than two. Mirrors SourceResultView.maxHitText so this preview keeps telling
    the truth about what the plugin will render; the ladder itself is on hover.
    """
    base = core.fmt_range(attack.get("maxHit", 0), attack.get("maxHitMax", 0))
    variants = attack.get("variants")
    if not variants:
        return base
    worst, text = max(attack.get("maxHit", 0), attack.get("maxHitMax", 0)), base
    for v in variants:
        peak = max(v.get("maxHit", 0), v.get("maxHitMax", 0))
        if peak > worst:                 # a combo can hit above the unprotected number
            worst = peak
            text = core.fmt_range(v.get("maxHit", 0), v.get("maxHitMax", 0))
    return text + "*"


def hover_line(attack):
    """What the client shows on hover, which is where this detail lives now: the conditional
    max hits (over the number) and the curator's notes (over the prayer icon)."""
    parts = []
    variants = attack.get("variants")
    if variants:
        # lowest first, so the lines read in the same order as the slashes on the headline
        bits = []
        for v in sorted(variants, key=lambda v: max(v.get("maxHit", 0), v.get("maxHitMax", 0))):
            bits.append("{} with {}".format(
                core.fmt_range(v.get("maxHit", 0), v.get("maxHitMax", 0)), v.get("label", "?")))
        bits.append("{} unprotected".format(
            core.fmt_range(attack.get("maxHit", 0), attack.get("maxHitMax", 0))))
        parts.append("   ·   ".join(bits))
    for e in attack.get("effects") or []:
        word = (e or "").lower().replace("_", " ")
        parts.append("May drain " + word.replace("drain ", "")
                     if word.startswith("drain") else "May inflict " + word)
    if attack.get("notes"):
        parts.append(attack["notes"])
    return "   ·   ".join(parts)


# how the client orders the attack columns: the prayer book's order, then anything with no
# prayer of its own. Ordering is by what the attack is prayed against, which is what its icon
# shows, so a magic-melee hybrid sits with melee.
PRAYER_ORDER = {"magic": 0, "ranged": 1, "melee": 2}


def prayer_rank(attack):
    token = attack.get("prayStyle") or attack.get("style") or ""
    return PRAYER_ORDER.get(STYLE_ALIASES.get(token, token), 3)


def in_prayer_order(attacks):
    return sorted(attacks, key=prayer_rank)


def style_line(attack):
    """'Magic (pray Ranged)' for hybrids, plain capitalised style otherwise."""
    label = (attack.get("style") or "").capitalize()
    if attack.get("prayStyle"):
        label += " (pray {})".format(attack["prayStyle"].capitalize())
    return label


# ---------- small widget helpers ----------

def flat_button(parent, text, command, kind="secondary", font=F_BODY):
    """Solid gold is reserved for the primary action; 'ghost' is an outlined
    gold-on-ink variant so only one element on screen carries a gold fill.

    Returns the bordering frame, with the clickable label at .label.
    """
    bg, fg, hover, border = {
        "primary": (GOLD, INK, "#FFAE45", GOLD),
        "ghost": (INK, GOLD, PANEL_HI, RULE),
        "secondary": (PANEL_HI, TEXT, RULE, PANEL_HI),
    }[kind]
    outer = tk.Frame(parent, bg=border, padx=1, pady=1)
    label = tk.Label(outer, text=text, font=font, bg=bg, fg=fg,
                     padx=13, pady=6, cursor="hand2")
    label.pack(fill="both", expand=True)
    label.bind("<Enter>", lambda e: label.configure(bg=hover))
    label.bind("<Leave>", lambda e: label.configure(bg=label.rest_bg))
    label.bind("<Button-1>", lambda e: command())
    label.rest_bg = bg
    outer.label = label
    return outer


def field(parent, textvar, width, font=F_MONO, placeholder=None):
    """Recessed entry with a hairline border that lights gold on focus.

    Returns (wrapper, entry, value_fn) — value_fn yields '' while the
    placeholder is showing, so callers never read the hint text as data.
    """
    wrap = tk.Frame(parent, bg=RULE, padx=1, pady=1)
    entry = tk.Entry(wrap, textvariable=textvar, width=width, font=font,
                     bg=FIELD, fg=TEXT, insertbackground=GOLD, relief="flat",
                     highlightthickness=0, bd=0, selectbackground=GOLD_DEEP,
                     selectforeground=GOLD)
    entry.pack(fill="both", expand=True, ipady=3, ipadx=4)

    showing = {"placeholder": False}

    def paint_focus(_evt=None):
        wrap.configure(bg=GOLD)

    def paint_blur(_evt=None):
        wrap.configure(bg=RULE)

    if placeholder is not None:
        def show_hint():
            if not textvar.get():
                showing["placeholder"] = True
                entry.configure(fg=FAINT)
                textvar.set(placeholder)

        def hide_hint():
            if showing["placeholder"]:
                showing["placeholder"] = False
                textvar.set("")
                entry.configure(fg=TEXT)

        entry.bind("<FocusIn>", lambda e: (hide_hint(), paint_focus()))
        entry.bind("<FocusOut>", lambda e: (show_hint(), paint_blur()))
        show_hint()
    else:
        entry.bind("<FocusIn>", paint_focus)
        entry.bind("<FocusOut>", paint_blur)

    # judged by what the box says, not by the flag: the flag only follows focus, and a value put
    # into the field in code never touches focus. Trusting it read a curated entrance back as
    # blank, which on save would have quietly erased it.
    def value():
        text = textvar.get()
        return "" if placeholder is not None and text == placeholder else text

    return wrap, entry, value


class ScrollArea(tk.Frame):
    """Canvas-backed vertical scroller; put content in .body."""

    def __init__(self, parent, **kw):
        tk.Frame.__init__(self, parent, bg=INK, **kw)
        self.canvas = tk.Canvas(self, bg=INK, highlightthickness=0, bd=0)
        self.bar = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview,
                                 style="Curator.Vertical.TScrollbar")
        self.canvas.configure(yscrollcommand=self.bar.set)
        self.canvas.pack(side="left", fill="both", expand=True)
        self.bar.pack(side="right", fill="y")
        self.body = tk.Frame(self.canvas, bg=INK)
        self._win = self.canvas.create_window((0, 0), window=self.body, anchor="nw")
        self.body.bind("<Configure>", self._resized)
        self.canvas.bind("<Configure>", lambda e: (self.canvas.itemconfigure(self._win, width=e.width),
                                                   self._resized()))
        self.canvas.bind("<MouseWheel>", self._wheel)
        self.body.bind("<MouseWheel>", self._wheel)

    def _resized(self, _evt=None):
        box = self.canvas.bbox("all")
        self.canvas.configure(scrollregion=box)
        # a scrollbar for content that fits is just a stray vertical line
        if box and box[3] - box[1] > self.canvas.winfo_height():
            if not self.bar.winfo_ismapped():
                self.bar.pack(side="right", fill="y")
        elif self.bar.winfo_ismapped():
            self.bar.pack_forget()

    def _wheel(self, evt):
        self.canvas.yview_scroll(-1 * (evt.delta // 120), "units")


class CuratorApp:
    def __init__(self, root):
        self.root = root
        root.title("Item Search — Source Curator")
        # as tall as the screen allows: the editor stacks attacks, location, and preview above
        # the save bar, and at 760 the bar was the part that fell off the bottom
        height = min(980, root.winfo_screenheight() - 90)
        root.geometry("1480x{}".format(height))
        root.minsize(1240, 720)
        root.configure(bg=INK)

        self._init_styles()

        self.overlay = core.load_json(core.OVERLAY, {}) or {}
        self.meta = core.build_meta()
        self.canonical, self.variants = id_roles()
        self.sources_by_id = {str(s.get("id")): s for s in core.load_sources()
                              if s.get("id") is not None}
        self.entries = self._merge_sources()
        self.wiki_texts = core.load_wiki_text()
        self.wiki_blocks = {}     # block key -> text index where it starts
        self.wiki_matches = []    # (start, end) of every search hit, in page order
        self.wiki_pos = -1
        self.ids = []            # ids currently shown in the list, in order
        # which column the queue is ordered by, and whether reversed. Pending-first is the
        # default because that is the work; clicking a heading overrides it.
        self.sort_key = "queue"
        self.sort_reverse = False
        self.current_id = None
        self.rows = []
        self.link_url = ""
        self._preview_job = None

        self._build()
        self._refresh_list()
        self._bind_keys()

    def _init_styles(self):
        style = ttk.Style()
        style.theme_use("clam")  # the only stock theme that honours custom colors
        style.configure("Curator.TCombobox", fieldbackground=FIELD, background=PANEL_HI,
                        foreground=TEXT, arrowcolor=MUTED, bordercolor=RULE,
                        lightcolor=RULE, darkcolor=RULE, insertcolor=GOLD,
                        selectbackground=GOLD_DEEP, selectforeground=GOLD, padding=3)
        style.map("Curator.TCombobox", bordercolor=[("focus", GOLD)],
                  lightcolor=[("focus", GOLD)], darkcolor=[("focus", GOLD)],
                  arrowcolor=[("active", GOLD)])
        style.configure("Curator.TCheckbutton", background=INK, foreground=MUTED,
                        focuscolor=INK, indicatorbackground=FIELD, indicatorforeground=INK,
                        bordercolor=RULE, lightcolor=RULE, darkcolor=RULE)
        style.map("Curator.TCheckbutton", background=[("active", INK)],
                  foreground=[("active", TEXT)],
                  indicatorbackground=[("selected", GOLD), ("active", PANEL_HI)],
                  indicatorforeground=[("selected", INK)])
        # drop the stock arrow buttons — they render as light blocks that fight the palette
        style.layout("Curator.Vertical.TScrollbar",
                     [("Vertical.Scrollbar.trough",
                       {"sticky": "ns",
                        "children": [("Vertical.Scrollbar.thumb",
                                      {"expand": "1", "sticky": "nswe"})]})])
        style.configure("Curator.Treeview", background=PANEL, fieldbackground=PANEL,
                        foreground=TEXT, borderwidth=0, rowheight=19, font=F_MONO_SM)
        style.configure("Curator.Treeview.Heading", background=INK, foreground=MUTED,
                        relief="flat", font=F_SMALL, borderwidth=0)
        style.map("Curator.Treeview.Heading", background=[("active", PANEL_HI)],
                  foreground=[("active", GOLD)])
        style.map("Curator.Treeview", background=[("selected", GOLD_DEEP)],
                  foreground=[("selected", GOLD)])
        style.layout("Curator.Treeview", [("Curator.Treeview.treearea", {"sticky": "nswe"})])
        style.configure("Curator.Vertical.TScrollbar", background=RULE, troughcolor=INK,
                        bordercolor=INK, lightcolor=RULE, darkcolor=RULE,
                        relief="flat", width=8)
        style.map("Curator.Vertical.TScrollbar", background=[("active", MUTED)])
        # the combobox dropdown is a classic Listbox, themed via the option database
        self.root.option_add("*TCombobox*Listbox.background", PANEL)
        self.root.option_add("*TCombobox*Listbox.foreground", TEXT)
        self.root.option_add("*TCombobox*Listbox.selectBackground", GOLD_DEEP)
        self.root.option_add("*TCombobox*Listbox.selectForeground", GOLD)
        self.root.option_add("*TCombobox*Listbox.font", F_MONO_SM)

    def _bind_keys(self):
        self.root.bind("<Control-s>", lambda e: self._save())
        self.root.bind("<Control-Return>", lambda e: self._save_next())
        self.root.bind("<Control-n>", lambda e: self._add_row())
        self.root.bind("<Control-f>", lambda e: self.search_entry.focus_set())
        self.root.bind("<Control-F>", lambda e: self.wiki_search_entry.focus_set())

    # ---------- chrome ----------
    @staticmethod
    def _eyebrow(parent, text, bg=INK):
        """A small tracked-out section label — the structural device throughout."""
        return tk.Label(parent, text=text.upper(), font=F_SMALL, bg=bg, fg=FAINT)

    @staticmethod
    def _rule(parent, bg=INK):
        return tk.Frame(parent, bg=RULE, height=1)

    def _build(self):
        self._build_queue()
        tk.Frame(self.root, bg=RULE, width=1).pack(side="left", fill="y")
        # editor and wiki text share the rest, with a sash between them: how much page a
        # curator wants beside the fields depends on the monster
        split = tk.PanedWindow(self.root, orient="horizontal", bg=RULE, sashwidth=3,
                               sashrelief="flat", bd=0, opaqueresize=True)
        split.pack(side="left", fill="both", expand=True)
        editor = tk.Frame(split, bg=INK)
        split.add(editor, minsize=620, stretch="always")
        self._build_editor(editor)
        wiki = tk.Frame(split, bg=INK)
        split.add(wiki, minsize=280, width=400, stretch="never")
        self._build_wiki(wiki)

    # ---------- left: the review queue ----------
    def _build_queue(self):
        left = tk.Frame(self.root, bg=INK, padx=14, pady=14)
        left.pack(side="left", fill="y")
        left.pack_propagate(False)
        left.configure(width=330)

        self._eyebrow(left, "Review queue").pack(anchor="w")

        # segmented meter: how much of the grind is left, at a glance
        self.meter = tk.Canvas(left, height=7, bg=INK, highlightthickness=0, bd=0)
        self.meter.pack(fill="x", pady=(8, 6))
        self.meter.bind("<Configure>", lambda e: self._draw_meter())

        self.count_label = tk.Label(left, text="", font=F_MONO_SM, bg=INK, fg=MUTED)
        self.count_label.pack(anchor="w")

        self.filter_var = tk.StringVar()
        wrap, self.search_entry, _ = field(left, self.filter_var, 1, font=F_BODY,
                                           placeholder="Search by name or id")
        wrap.pack(fill="x", pady=(12, 8))

        self.show_all = tk.BooleanVar(value=False)
        ttk.Checkbutton(left, text="Show reviewed too", variable=self.show_all,
                        style="Curator.TCheckbutton",
                        command=self._refresh_list).pack(anchor="w")
        self.show_imported = tk.BooleanVar(value=False)
        ttk.Checkbutton(left, text="Show cleanly imported too", variable=self.show_imported,
                        style="Curator.TCheckbutton",
                        command=self._refresh_list).pack(anchor="w")
        self.only_placeless = tk.BooleanVar(value=False)
        ttk.Checkbutton(left, text="Only ones with nowhere on the map",
                        variable=self.only_placeless, style="Curator.TCheckbutton",
                        command=self._refresh_list).pack(anchor="w")

        lf = tk.Frame(left, bg=INK)
        lf.pack(fill="both", expand=True, pady=(10, 0))
        # a Treeview rather than a Listbox: the columns need naming, since a bare row of numbers
        # gives no way to tell a combat level from an npc id, and they need to be sortable
        self.listbox = ttk.Treeview(lf, style="Curator.Treeview", show="headings",
                                    selectmode="browse",
                                    columns=("state", "name", "level", "id"))
        for column, title, width, anchor in (
                ("state", "", 22, "center"),
                ("name", "Name", 196, "w"),
                ("level", "Lvl", 38, "e"),
                ("id", "NPC id", 58, "e")):
            self.listbox.heading(column, text=title,
                                 command=lambda c=column: self._sort_by(c))
            self.listbox.column(column, width=width, anchor=anchor, stretch=(column == "name"))
        sb = ttk.Scrollbar(lf, orient="vertical", command=self.listbox.yview,
                           style="Curator.Vertical.TScrollbar")
        self.listbox.configure(yscrollcommand=sb.set)
        self.listbox.pack(side="left", fill="both", expand=True)
        sb.pack(side="left", fill="y")
        self.listbox.bind("<<TreeviewSelect>>", self._on_select)
        # rows are tinted by what the generator will do with them, not by hand per row
        self.listbox.tag_configure("stale", foreground=RED)
        self.listbox.tag_configure("variant", foreground=MUTED)
        self.listbox.tag_configure("reviewed", foreground=FAINT)
        self.listbox.tag_configure("imported", foreground=FAINT)
        # registered last: the search field's placeholder writes on construction
        self.filter_var.trace_add("write", lambda *_: self._refresh_list())

    def _draw_meter(self):
        total = len(self.overlay) or 1
        done = sum(1 for v in self.overlay.values() if not v.get("needsReview"))
        c = self.meter
        c.delete("all")
        w = c.winfo_width()
        if w <= 1:
            return
        segments, gap = 28, 2
        seg_w = (w - (segments - 1) * gap) / float(segments)
        filled = int(round(segments * done / float(total)))
        for i in range(segments):
            x = i * (seg_w + gap)
            c.create_rectangle(x, 0, x + seg_w, 7, width=0,
                               fill=GOLD if i < filled else "#332C22")

    # ---------- right: the record under review ----------
    def _build_editor(self, parent):
        right = tk.Frame(parent, bg=INK, padx=20, pady=14)
        right.pack(side="left", fill="both", expand=True)

        # the save bar claims the bottom edge before anything else is laid out, so however
        # tall the attack list and the cards above it grow, the buttons are never what gets
        # pushed out of the window
        bar = tk.Frame(right, bg=INK)
        bar.pack(side="bottom", fill="x")
        self.status = tk.Label(bar, text="", font=F_BODY, bg=INK, fg=GREEN, anchor="w")
        self.status.pack(side="left", fill="x", expand=True)
        flat_button(bar, "Save & next", self._save_next, kind="primary",
                    font=F_HEAD).pack(side="right")
        flat_button(bar, "Save", self._save).pack(side="right", padx=(0, 8))
        self._rule(right).pack(side="bottom", fill="x", pady=(0, 12))

        head = tk.Frame(right, bg=INK)
        head.pack(fill="x")
        titles = tk.Frame(head, bg=INK)
        titles.pack(side="left", anchor="w")
        self.name_label = tk.Label(titles, text="Select an NPC", font=F_DISPLAY,
                                   bg=INK, fg=TEXT, anchor="w")
        self.name_label.pack(anchor="w")
        self.sub_label = tk.Label(titles, text="Nothing loaded", font=F_MONO_SM,
                                  bg=INK, fg=MUTED, anchor="w")
        self.sub_label.pack(anchor="w", pady=(2, 0))
        self.wiki_button = flat_button(head, "Open wiki page  ↗", self._open_link,
                                       kind="ghost")
        self.wiki_button.pack(side="right", anchor="n")

        self._rule(right).pack(fill="x", pady=(14, 12))

        self._eyebrow(right, "Attacks").pack(anchor="w")
        tk.Label(right, font=F_SMALL, bg=INK, fg=FAINT, justify="left", anchor="w",
                 text="Conditional damage:  8 @ Insulated boots;  12-14 @ Antifire  "
                      "— add (combo) for combinations").pack(anchor="w", pady=(3, 8))

        cols = tk.Frame(right, bg=INK)
        cols.pack(fill="x")
        for text, width in (("Style", 17), ("Max hit", 11), ("Speed", 8)):
            tk.Label(cols, text=text, font=F_HEAD, bg=INK, fg=MUTED,
                     width=width, anchor="w").pack(side="left", padx=(0, 6))

        self.scroller = ScrollArea(right)
        self.scroller.pack(fill="both", expand=True, pady=(4, 0))
        self.attacks_frame = self.scroller.body

        add = tk.Label(right, text="+  Add attack", font=F_BODY, bg=INK, fg=GOLD,
                       cursor="hand2", pady=8)
        add.bind("<Button-1>", lambda e: self._add_row())
        add.bind("<Enter>", lambda e: add.configure(fg="#FFB964"))
        add.bind("<Leave>", lambda e: add.configure(fg=GOLD))
        add.pack(anchor="w")

        self._rule(right).pack(fill="x", pady=(6, 0))
        self._build_location(right)
        self._rule(right).pack(fill="x", pady=(6, 0))
        self._build_preview(right)

    def _build_location(self, parent):
        """Where this monster stands, and where you go in.

        The spawn dump stops at npc id 11151 and the wiki has no map of a good many places, so a
        few hundred monsters can be placed by no automatic means at all. These two fields are the
        way in for those: tiles the map should mark, and the surface tile that leads to them.
        """
        wrap = tk.Frame(parent, bg=INK, pady=12)
        wrap.pack(fill="x")
        self._eyebrow(wrap, "Location").pack(anchor="w")

        self.location_known = tk.Label(wrap, text="", font=F_SMALL, bg=INK, fg=FAINT,
                                       anchor="w", justify="left", wraplength=680)
        self.location_known.pack(anchor="w", pady=(4, 6))

        row = tk.Frame(wrap, bg=INK)
        row.pack(fill="x")
        tk.Label(row, text="Entrance", font=F_BODY, bg=INK, fg=MUTED,
                 width=10, anchor="w").pack(side="left")
        self.entrance_var = tk.StringVar()
        ent_wrap, _, self.entrance_get = field(row, self.entrance_var, 26, font=F_MONO,
                                               placeholder="x, y, plane — the tile you go in by")
        ent_wrap.pack(side="left")

        tiles_row = tk.Frame(wrap, bg=INK)
        tiles_row.pack(fill="x", pady=(8, 0))
        tk.Label(tiles_row, text="Stands at", font=F_BODY, bg=INK, fg=MUTED,
                 width=10, anchor="nw").pack(side="left", anchor="n")
        box = tk.Frame(tiles_row, bg=RULE, padx=1, pady=1)
        box.pack(side="left", fill="x", expand=True)
        self.tiles_text = tk.Text(box, height=4, font=F_MONO_SM, bg=FIELD, fg=TEXT,
                                  insertbackground=GOLD, relief="flat", highlightthickness=0,
                                  bd=0, wrap="none")
        self.tiles_text.pack(fill="both", expand=True, padx=4, pady=3)
        tk.Label(wrap, text="one tile per line: x, y, plane   (plane defaults to 0)",
                 font=F_SMALL, bg=INK, fg=FAINT, anchor="w").pack(anchor="w", padx=(84, 0),
                                                                  pady=(3, 0))

        actions = tk.Frame(wrap, bg=INK)
        actions.pack(fill="x", pady=(8, 0), padx=(84, 0))
        flat_button(actions, "Import mine", self._import_recorded).pack(side="left")
        flat_button(actions, "Import contributed files…",
                    self._import_contributed).pack(side="left", padx=(6, 0))
        self.import_status = tk.Label(actions, text="", font=F_SMALL, bg=INK, fg=FAINT,
                                      anchor="w")
        self.import_status.pack(side="left", padx=(10, 0))

    # ---------- far right: what the wiki says ----------
    # the lead paragraph is a block of its own: for most monsters it is the only prose that says
    # how they fight, since only bosses and slayer monsters get a section for it
    WIKI_BLOCKS = (("infobox", "Infobox", core.flatten_infobox),
                   ("lead", "Overview", core.flatten_markup),
                   ("mechanics", "Mechanics", core.flatten_markup),
                   ("strategy", "Strategy", core.flatten_markup))

    def _build_wiki(self, parent):
        """What a curator used to open a browser tab to read, in one searchable column.

        One Text widget holds all three blocks rather than one per block: a single scroll, a
        single search, and the block labels at the top jump rather than reveal.
        """
        wrap = tk.Frame(parent, bg=INK, padx=16, pady=14)
        wrap.pack(fill="both", expand=True)

        head = tk.Frame(wrap, bg=INK)
        head.pack(fill="x")
        self._eyebrow(head, "From the wiki").pack(side="left")
        self.wiki_hint = tk.Label(head, text="", font=F_SMALL, bg=INK, fg=FAINT, anchor="e")
        self.wiki_hint.pack(side="right")

        srow = tk.Frame(wrap, bg=INK)
        srow.pack(fill="x", pady=(8, 6))
        self.wiki_query = tk.StringVar()
        swrap, self.wiki_search_entry, self.wiki_query_get = field(
            srow, self.wiki_query, 1, font=F_BODY,
            placeholder="Find in wiki text   (Ctrl+Shift+F · Enter for next)")
        swrap.pack(side="left", fill="x", expand=True)
        self.wiki_hits = tk.Label(srow, text="", font=F_MONO_SM, bg=INK, fg=MUTED,
                                  width=9, anchor="e")
        self.wiki_hits.pack(side="left", padx=(8, 0))
        self.wiki_search_entry.bind("<Return>", lambda e: self._wiki_next(1))
        self.wiki_search_entry.bind("<Shift-Return>", lambda e: self._wiki_next(-1))

        jumps = tk.Frame(wrap, bg=INK)
        jumps.pack(fill="x", pady=(0, 6))
        self.wiki_jumps = {}
        for key, label, _ in self.WIKI_BLOCKS:
            jump = tk.Label(jumps, text=label, font=F_SMALL, bg=INK, fg=FAINT,
                            cursor="hand2", padx=6)
            jump.bind("<Button-1>", lambda e, k=key: self._wiki_jump(k))
            jump.pack(side="left")
            self.wiki_jumps[key] = jump

        box = tk.Frame(wrap, bg=RULE, padx=1, pady=1)
        box.pack(fill="both", expand=True)
        inner = tk.Frame(box, bg=PANEL)
        inner.pack(fill="both", expand=True)
        self.wiki_text = tk.Text(inner, font=F_BODY, bg=PANEL, fg=TEXT, wrap="word",
                                 relief="flat", highlightthickness=0, bd=0, padx=10, pady=8,
                                 cursor="arrow", state="disabled",
                                 selectbackground=GOLD_DEEP, selectforeground=GOLD)
        sb = ttk.Scrollbar(inner, orient="vertical", command=self.wiki_text.yview,
                           style="Curator.Vertical.TScrollbar")
        self.wiki_text.configure(yscrollcommand=sb.set)
        self.wiki_text.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")
        self.wiki_text.tag_configure("block", font=F_HEAD, foreground=GOLD,
                                     spacing1=12, spacing3=4)
        self.wiki_text.tag_configure("field", foreground=MUTED)
        self.wiki_text.tag_configure("empty", foreground=FAINT)
        self.wiki_text.tag_configure("hit", background=GOLD_DEEP, foreground=GOLD)
        self.wiki_text.tag_configure("current", background=GOLD, foreground=INK)
        # the search must follow every keystroke, but only once the field exists to read from
        self.wiki_query.trace_add("write", lambda *_: self._wiki_search())

    def _load_wiki(self, idstr):
        _, url, _ = core.describe(self.meta, idstr)
        entry = core.wiki_text_for(self.wiki_texts, url)
        text = self.wiki_text
        text.configure(state="normal")
        text.delete("1.0", "end")
        self.wiki_blocks = {}
        for jump in self.wiki_jumps.values():
            jump.configure(fg=FAINT)
        if not self.wiki_texts:
            self.wiki_hint.configure(text="run the generator with --refresh-wiki to fill this")
            text.insert("end", "No wiki text snapshot yet.\n\nRun\n\n"
                        "    ./gradlew runSources --args=\"--refresh-wiki\"\n\n"
                        "and reopen the curator; the infobox, mechanics, and strategy of every "
                        "monster page will show here.", "empty")
        elif entry is None:
            self.wiki_hint.configure(text="")
            text.insert("end", "The wiki has no monster page for this source, or the snapshot "
                        "predates it.", "empty")
        else:
            self.wiki_hint.configure(text=core.page_title(url) or "")
            for key, label, render in self.WIKI_BLOCKS:
                self.wiki_blocks[key] = text.index("end-1c")
                text.insert("end", label.upper() + "\n", "block")
                body = render(entry.get(key)) if entry.get(key) else ""
                if not body:
                    text.insert("end", "nothing on the page\n", "empty")
                    continue
                self.wiki_jumps[key].configure(fg=GOLD)
                if key == "infobox":
                    for line in body.split("\n"):
                        name, sep, value = line.partition(":")
                        text.insert("end", name + sep, "field")
                        text.insert("end", value + "\n")
                else:
                    text.insert("end", body + "\n")
        text.configure(state="disabled")
        self._wiki_search()

    def _wiki_jump(self, key):
        if key in self.wiki_blocks:
            self.wiki_text.yview(self.wiki_blocks[key])

    def _wiki_search(self):
        text = self.wiki_text
        text.tag_remove("hit", "1.0", "end")
        self.wiki_matches = []
        query = self.wiki_query_get()
        if query:
            idx = "1.0"
            while True:
                idx = text.search(query, idx, stopindex="end", nocase=True)
                if not idx:
                    break
                end = "{}+{}c".format(idx, len(query))
                text.tag_add("hit", idx, end)
                self.wiki_matches.append((idx, end))
                idx = end
        self._wiki_show_hit(0 if self.wiki_matches else -1, query)

    def _wiki_next(self, step):
        if self.wiki_matches:
            self._wiki_show_hit((self.wiki_pos + step) % len(self.wiki_matches),
                                self.wiki_query_get())
        return "break"

    def _wiki_show_hit(self, pos, query):
        text = self.wiki_text
        text.tag_remove("current", "1.0", "end")
        self.wiki_pos = pos
        if pos < 0:
            self.wiki_hits.configure(text="no hits" if query else "")
            return
        start, end = self.wiki_matches[pos]
        text.tag_add("current", start, end)
        text.see(start)
        self.wiki_hits.configure(text="{} of {}".format(pos + 1, len(self.wiki_matches)))

    def _build_preview(self, parent):
        """The signature panel: your input rendered the way the combat card renders it."""
        wrap = tk.Frame(parent, bg=INK, pady=12)
        wrap.pack(fill="x")
        self._eyebrow(wrap, "In the plugin").pack(anchor="w")
        self.preview = tk.Frame(wrap, bg=PANEL, padx=14, pady=10)
        self.preview.pack(fill="x", pady=(6, 0))
        self.preview_empty = tk.Label(self.preview, text="Nothing to preview yet",
                                      font=F_BODY, bg=PANEL, fg=FAINT, anchor="w")
        self.preview_empty.pack(anchor="w")

    def _render_preview(self):
        self._preview_job = None
        for child in self.preview.winfo_children():
            child.destroy()
        if self.current_id is None:
            tk.Label(self.preview, text="Nothing to preview yet", font=F_BODY,
                     bg=PANEL, fg=FAINT, anchor="w").pack(anchor="w")
            return

        attacks, err = collect_attacks(self._gather())
        if err:
            tk.Label(self.preview, text=err, font=F_BODY, bg=PANEL, fg=RED,
                     anchor="w", justify="left", wraplength=680).pack(anchor="w")
            return
        if not attacks:
            tk.Label(self.preview, text="No attacks — this NPC will show \"No attack data\"",
                     font=F_BODY, bg=PANEL, fg=FAINT, anchor="w").pack(anchor="w")
            return

        # the client sorts columns into prayer order regardless of the row order above
        for attack in in_prayer_order(attacks):
            row = tk.Frame(self.preview, bg=PANEL)
            row.pack(fill="x", pady=1)
            tk.Label(row, text=style_line(attack), font=F_BODY, bg=PANEL, fg=TEXT,
                     width=22, anchor="w").pack(side="left")
            tk.Label(row, text="speed " + core.fmt_range(attack.get("speed", 0),
                                                         attack.get("speedMax", 0)),
                     font=F_MONO_SM, bg=PANEL, fg=MUTED, width=10,
                     anchor="w").pack(side="left")
            tk.Label(row, text=headline_max_hit(attack), font=F_MONO_BIG, bg=PANEL,
                     fg=GOLD, anchor="w").pack(side="left")
            hover = hover_line(attack)
            if hover:
                tk.Label(self.preview, text="on hover:  " + hover, font=F_SMALL, bg=PANEL,
                         fg=FAINT, anchor="w", justify="left",
                         wraplength=680).pack(anchor="w", padx=(6, 0))

    def _schedule_preview(self, *_):
        if self._preview_job is not None:
            self.root.after_cancel(self._preview_job)
        self._preview_job = self.root.after(150, self._render_preview)

    def _role(self, idstr):
        """"main" (the generator reads it), "variant" (a real other version whose curation is
        currently discarded), or "stale" (matches no source at all)."""
        if not self.canonical:
            return "main"
        npc_id = int(idstr)
        if npc_id in self.canonical:
            return "main"
        return "variant" if npc_id in self.variants else "stale"

    # ---------- list ----------
    def _adopt_overlay(self):
        """Rebuild the browsable set around the overlay just read back from disk.

        Anything the generator added while this window was open appears in the queue instead of
        being invisible until a restart; stubs the session has not saved keep their imported mark.
        """
        self.entries = self._merge_sources()

    def _merge_sources(self):
        """Every npc that has combat stats, not only the ones the generator flagged.

        The overlay holds what the sweep could not work out, but notes and effects are worth
        adding to monsters it parsed perfectly well too, and there is no way to reach those from
        a queue built out of the overlay alone. So the queue is the overlay plus a stub for every
        generated source carrying that source's parsed attacks. Stubs are marked ``imported`` and
        are *not* written to combat-overlay.json until one is saved — a file of a thousand
        untouched entries would say nothing the generator did not already know, and every one of
        them would then override the sweep forever.
        """
        entries = dict(self.overlay)
        for s in core.load_sources():
            key = str(s.get("id"))
            if s.get("id") is None or key in entries:
                continue
            attacks = (s.get("combat") or {}).get("attacks") or []
            # sources with no attacks are here for their location: a chest or a newly added
            # monster still needs somewhere to be, even with nothing to curate about its combat
            entries[key] = {"attacks": copy.deepcopy(attacks),
                            "needsReview": False, "imported": True}
        return entries

    def _placeless(self, idstr):
        """True when nothing can put this source on the map: no tiles from any source."""
        source = self.sources_by_id.get(idstr)
        if source is None:
            return False
        return not (source.get("spawnPoints") or [])

    def _visible_ids(self):
        needle = self.filter_var.get().strip().lower()
        if needle == "search by name or id":
            needle = ""
        ids = []
        for k, v in self.entries.items():
            if self.only_placeless.get() and not self._placeless(k):
                continue
            if v.get("imported"):
                # cleanly parsed and never touched: browsable, but not part of the queue
                if not self.show_imported.get():
                    continue
            elif not (self.show_all.get() or v.get("needsReview")):
                continue
            if needle:
                name, _, _ = core.describe(self.meta, k)
                if needle not in name.lower() and needle not in k:
                    continue
            ids.append(k)
        # pending first, then by numeric id
        ids.sort(key=self._sort_value, reverse=self.sort_reverse)
        return ids

    def _sort_value(self, k):
        """Ordering for one row under the current sort. Pending-first is the default, so the
        queue opens on the work rather than on whatever happens to have the lowest id."""
        name, _, level = core.describe(self.meta, k)
        if self.sort_key == "name":
            return (name.lower(), int(k))
        if self.sort_key == "level":
            return (level if isinstance(level, int) else -1, name.lower())
        if self.sort_key == "id":
            return (int(k),)
        if self.sort_key == "state":
            return (self._state_rank(k), name.lower())
        return (self._state_rank(k), int(k))

    def _state_rank(self, k):
        """Flagged first, then hand-reviewed, then the ones that just imported cleanly."""
        entry = self.entries[k]
        if entry.get("needsReview"):
            return 0
        return 2 if entry.get("imported") else 1

    def _sort_by(self, column):
        # clicking the same heading again reverses it, the usual table behaviour
        self.sort_reverse = not self.sort_reverse if self.sort_key == column else False
        self.sort_key = column
        selected = self.current_id
        self._refresh_list()
        if selected is not None:
            self._select_in_list(selected)

    def _refresh_list(self):
        self.ids = self._visible_ids()
        self.listbox.delete(*self.listbox.get_children())
        for k in self.ids:
            name, _, level = core.describe(self.meta, k)
            role = self._role(k)
            # the id column is the only thing separating same-name, same-level mobs: two
            # Killerwatts at 55, two Skeletal Wyverns at 140. It is the overlay's key besides.
            rank = self._state_rank(k)
            state = {"variant": "~", "stale": "✗"}.get(role, ("•", "✓", "·")[rank])
            tag = role if role in ("stale", "variant") else ("", "reviewed", "imported")[rank]
            self.listbox.insert("", "end", iid=k, tags=(tag,) if tag else (),
                                values=(state, name, level if level is not None else "—", k))
        pending = sum(1 for v in self.overlay.values() if v.get("needsReview"))
        done = len(self.overlay) - pending
        roles = [self._role(k) for k in self.overlay]
        text = "{} / {} reviewed".format(done, len(self.overlay))
        extra = []
        if roles.count("variant"):
            extra.append("{} variants".format(roles.count("variant")))
        if roles.count("stale"):
            extra.append("{} stale".format(roles.count("stale")))
        imported = sum(1 for v in self.entries.values() if v.get("imported"))
        if imported:
            extra.append("{} imported".format(imported))
        if extra:
            # its own line: beside the count it ran off the edge of the pane
            text += "\n" + ", ".join(extra)
        self.count_label.configure(text=text, justify="left")
        self._draw_meter()

    def _on_select(self, _evt):
        selection = self.listbox.selection()
        if selection:
            self._load(selection[0])

    def _select_in_list(self, idstr):
        if idstr in self.ids:
            self.listbox.selection_set(idstr)
            self.listbox.see(idstr)

    # ---------- editor rows ----------
    def _clear_rows(self):
        for r in self.rows:
            r["frame"].destroy()
        self.rows = []

    def _add_row(self, style="", mh_text="0", sp_text="0",
                 gear_text="", prayer_text="", consumable_text="", notes_text="",
                 effects_text=""):
        """One attack as two tiers: the base facts, then the conditionals beneath
        it — conditionals are exceptions to the max hit, not peers of it."""
        fr = tk.Frame(self.attacks_frame, bg=PANEL, padx=10, pady=8)
        fr.pack(fill="x", pady=(0, 6))

        top = tk.Frame(fr, bg=PANEL)
        top.pack(fill="x")

        style_var = tk.StringVar(value=style)
        max_var = tk.StringVar(value=mh_text)
        speed_var = tk.StringVar(value=sp_text)
        combo = ttk.Combobox(top, values=STYLES, textvariable=style_var, width=15,
                             font=F_BODY, style="Curator.TCombobox")
        combo.pack(side="left", padx=(0, 8))
        max_wrap, _, max_get = field(top, max_var, 9)
        max_wrap.pack(side="left", padx=(0, 8))
        speed_wrap, _, speed_get = field(top, speed_var, 6)
        speed_wrap.pack(side="left")

        row = {"frame": fr, "style": style_var}
        remove = tk.Label(top, text="✕", font=F_BODY, bg=PANEL, fg=FAINT, cursor="hand2",
                          padx=6)
        remove.bind("<Button-1>", lambda e: self._remove_row(row))
        remove.bind("<Enter>", lambda e: remove.configure(fg=RED))
        remove.bind("<Leave>", lambda e: remove.configure(fg=FAINT))
        remove.pack(side="right")

        # reordering: the client sorts into prayer order, so this decides ties between
        # attacks prayed against the same way, and the order the overlay is written in
        for glyph, delta in (("▼", 1), ("▲", -1)):
            mover = tk.Label(top, text=glyph, font=F_SMALL, bg=PANEL, fg=FAINT,
                             cursor="hand2", padx=3)
            mover.bind("<Button-1>", lambda e, d=delta: self._move_row(row, d))
            mover.bind("<Enter>", lambda e, m=mover: m.configure(fg=GOLD))
            mover.bind("<Leave>", lambda e, m=mover: m.configure(fg=FAINT))
            mover.pack(side="right")

        bottom = tk.Frame(fr, bg=PANEL)
        bottom.pack(fill="x", pady=(6, 0))
        tk.Label(bottom, text="↳", font=F_BODY, bg=PANEL, fg=FAINT).pack(side="left",
                                                                         padx=(2, 8))
        getters = {}
        for key, text, hint in (("gear", gear_text, "with gear"),
                                ("prayer", prayer_text, "with prayer"),
                                ("consumable", consumable_text, "with consumable")):
            var = tk.StringVar(value=text)
            wrap, _, getter = field(bottom, var, 21, font=F_MONO_SM, placeholder=hint)
            wrap.pack(side="left", padx=(0, 6))
            getters[key] = getter
            var.trace_add("write", self._schedule_preview)

        notes_row = tk.Frame(fr, bg=PANEL)
        notes_row.pack(fill="x", pady=(6, 0))
        tk.Label(notes_row, text="✎", font=F_BODY, bg=PANEL, fg=FAINT).pack(side="left",
                                                                            padx=(2, 8))
        notes_var = tk.StringVar(value=notes_text)
        notes_wrap, _, notes_get = field(notes_row, notes_var, 60, font=F_MONO_SM,
                                         placeholder="notes — shown on hover in the plugin")
        notes_wrap.pack(side="left", fill="x", expand=True)
        notes_var.trace_add("write", self._schedule_preview)

        effects_var = tk.StringVar(value=effects_text)
        effects_wrap, _, effects_get = field(notes_row, effects_var, 26, font=F_MONO_SM,
                                             placeholder="effects — poison, drain prayer")
        effects_wrap.pack(side="left", padx=(6, 0))
        effects_var.trace_add("write", self._schedule_preview)

        row.update({"max_get": max_get, "speed_get": speed_get, "getters": getters,
                    "notes_get": notes_get, "effects_get": effects_get})
        for var in (style_var, max_var, speed_var):
            var.trace_add("write", self._schedule_preview)
        self.rows.append(row)
        self._schedule_preview()
        return row

    def _remove_row(self, row):
        row["frame"].destroy()
        self.rows.remove(row)
        self._schedule_preview()

    def _move_row(self, row, delta):
        """Moves one attack up or down. Tkinter has no reorder, so the frames are re-packed
        in the new order rather than rebuilt, which keeps every field's current text."""
        i = self.rows.index(row)
        j = i + delta
        if j < 0 or j >= len(self.rows):
            return
        self.rows[i], self.rows[j] = self.rows[j], self.rows[i]
        for r in self.rows:
            r["frame"].pack_forget()
        for r in self.rows:
            r["frame"].pack(fill="x", pady=(0, 6))
        self._schedule_preview()

    def _load(self, idstr):
        self.current_id = idstr
        name, url, level = core.describe(self.meta, idstr)
        self.name_label.configure(text=name)
        role = self._role(idstr)
        note = ""
        if self.entries[idstr].get("imported"):
            note = "  ·  imported cleanly — saving adds an overlay entry that overrides the sweep"
        if role == "variant":
            note = "  ·  another version of {} — the generator keeps only the main one".format(
                self.variants.get(int(idstr)) or "this monster")
        elif role == "stale":
            note = "  ·  matches no source; nothing reads this"
        self.sub_label.configure(
            text="level {}  ·  id {}{}".format(level or "—", idstr, note),
            fg=RED if role == "stale" else (GOLD if role == "variant" else MUTED))
        self.link_url = "" if url.startswith("(") else url
        self.wiki_button.label.configure(
            fg=GOLD if self.link_url else FAINT,
            cursor="hand2" if self.link_url else "arrow",
            text="Open wiki page  ↗" if self.link_url else "No wiki link")
        self.status.configure(text="")
        self._clear_rows()
        for a in self.entries[idstr].get("attacks") or []:
            style = a.get("style", "")
            if a.get("prayStyle"):
                style = style + " " + a["prayStyle"]
            self._add_row(style,
                          core.fmt_range(a.get("maxHit", 0), a.get("maxHitMax", 0)),
                          core.fmt_range(a.get("speed", 0), a.get("speedMax", 0)),
                          fmt_variants(a.get("variants"), "gear"),
                          fmt_variants(a.get("variants"), "prayer"),
                          fmt_variants(a.get("variants"), "consumable"),
                          a.get("notes") or "",
                          format_effects(a.get("effects")))
        if not self.rows:
            self._add_row()
        self._load_location(idstr)
        self._load_wiki(idstr)
        self._render_preview()

    def _load_location(self, idstr):
        """Show what is already known about where this monster is, and any hand-entered override."""
        source = self.sources_by_id.get(idstr) or {}
        spawns = source.get("spawnPoints") or []
        entrance = source.get("entrance")
        place = source.get("locationName")
        known = []
        if spawns:
            known.append("{} tile{} known".format(len(spawns), "" if len(spawns) == 1 else "s"))
        else:
            known.append("no tiles — nothing to mark on the map")
        if entrance:
            known.append("entrance ({}, {}, plane {})".format(
                entrance.get("x"), entrance.get("y"), entrance.get("plane", 0)))
        if place:
            known.append("found in " + place)
        self.location_known.configure(
            text="   ·   ".join(known),
            fg=RED if not spawns else FAINT)

        curated_tiles = core.load_json(core.SPAWNS_OVERLAY, {}) or {}
        curated_entrance = core.load_json(core.ENTRANCE_OVERLAY, {}) or {}
        self.tiles_text.delete("1.0", "end")
        self.tiles_text.insert("1.0", core.fmt_tiles(curated_tiles.get(idstr)))
        own = curated_entrance.get(idstr)
        self.entrance_var.set("{}, {}, {}".format(own.get("x", 0), own.get("y", 0),
                                                  own.get("plane", 0)) if own else "")

    def _open_link(self):
        if self.link_url:
            webbrowser.open(self.link_url)

    def _gather(self):
        return [(r["style"].get(), r["max_get"](), r["speed_get"](),
                 r["getters"]["gear"](), r["getters"]["prayer"](),
                 r["getters"]["consumable"](), r["notes_get"](),
                 r["effects_get"]()) for r in self.rows]

    def _save(self):
        if self.current_id is None:
            return False
        attacks, err = collect_attacks(self._gather())
        if err:
            # inline, next to the fields — a modal here would break the entry rhythm
            self.status.configure(text=err, fg=RED)
            self._render_preview()
            return False
        entry = self.entries[self.current_id]
        entry["attacks"] = attacks
        entry["needsReview"] = False
        # saving is what promotes a browsed-only source into the overlay the generator reads
        entry.pop("imported", None)
        # write through the file rather than over it: a generator run while this window is open
        # adds stubs and seeds effects, and blindly dumping the in-memory dict erases them
        location_error = self._save_location(self.current_id)
        if location_error:
            self.status.configure(text=location_error, fg=RED)
            return False
        if attacks or self.current_id in self.overlay:
            self.overlay = core.save_entry(self.current_id, entry)
        else:
            # nothing to say about its combat: writing an empty attack list here would override
            # the sweep with silence. The location overlays above have already been written.
            self.entries[self.current_id] = entry
        self._adopt_overlay()
        self.status.configure(text="Saved  ·  " + core.fmt_attacks(attacks), fg=GREEN)
        saved_id = self.current_id
        self._refresh_list()
        self._select_in_list(saved_id)
        return True

    def _import_contributed(self):
        """Folds in export files other people sent.

        Several at once, because contributions arrive in batches, and merged rather than swapped
        in: two players standing in different corners of the same dungeon are both right.
        """
        paths = filedialog.askopenfilenames(
            title="Spawn exports to import",
            filetypes=[("Spawn exports", "*.json"), ("All files", "*.*")])
        if not paths:
            return
        monsters, entrances, errors = core.import_spawn_exports(paths)
        summary = "Imported {} monster{} and {} entrance{} from {} file{}".format(
            monsters, "" if monsters == 1 else "s",
            entrances, "" if entrances == 1 else "s",
            len(paths), "" if len(paths) == 1 else "s")
        if errors:
            summary += "   ·   " + "; ".join(errors[:2])
        self.import_status.configure(text=summary, fg=RED if errors else GREEN)
        if self.current_id is not None:
            self._load_location(self.current_id)
        self._refresh_list()

    def _import_recorded(self):
        """Folds in whatever the plugin exported from a play session.

        Recording a monster's tiles in front of it is the easy half; this is the half that gets
        them into the shipped data. Reloads the fields afterwards so the monster on screen shows
        what was just imported rather than what it said a moment ago.
        """
        spawns, entrances, error = core.import_spawn_export()
        if error:
            self.import_status.configure(text=error, fg=RED)
            return
        if not spawns and not entrances:
            self.import_status.configure(text="Nothing recorded to import", fg=FAINT)
            return
        self.import_status.configure(
            text="Imported {} monster{} and {} entrance{}".format(
                spawns, "" if spawns == 1 else "s",
                entrances, "" if entrances == 1 else "s"),
            fg=GREEN)
        if self.current_id is not None:
            self._load_location(self.current_id)
        self._refresh_list()

    def _save_location(self, idstr):
        """Writes the two map overlays. Returns an error message, or None when all is well.

        An emptied field removes the entry rather than storing a blank one, so clearing a mistake
        hands the monster back to the automatic placement instead of pinning it nowhere.
        """
        tiles, error = core.parse_tiles(self.tiles_text.get("1.0", "end"))
        if error:
            return "tiles: " + error
        raw = self.entrance_get().strip()
        entrance = None
        if raw:
            parsed, error = core.parse_tiles(raw)
            if error:
                return "entrance: " + error
            if len(parsed) != 1:
                return "entrance: one tile, as 'x, y, plane'"
            entrance = parsed[0]
        # an entrance is by definition somewhere you can walk to above ground; underground it
        # marks a spot the surface map never draws, which is the very problem it exists to solve
        if entrance is not None and entrance["y"] >= core.UNDERGROUND_Y:
            return ("entrance: ({}, {}) is below ground — the entrance wants the surface tile "
                    "you go in by".format(entrance["x"], entrance["y"]))
        core.save_overlay_entry(core.SPAWNS_OVERLAY, idstr, tiles or None)
        core.save_overlay_entry(core.ENTRANCE_OVERLAY, idstr, entrance)
        return None

    def _save_next(self):
        if not self._save():
            return
        pending = [k for k in self.ids if self.entries[k].get("needsReview")]
        if pending:
            self._load(pending[0])
            self._select_in_list(pending[0])
        else:
            self.name_label.configure(text="Queue clear")
            self.sub_label.configure(text="every overlay entry reviewed")
            self.status.configure(
                text="Re-run the generator to bake the values into drops.json.", fg=GREEN)


def main():
    root = tk.Tk()
    CuratorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
