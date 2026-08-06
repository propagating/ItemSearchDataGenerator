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

Reuses the data helpers from curate_combat.py (same folder).
"""
import os
import sys
import webbrowser

import tkinter as tk
from tkinter import ttk

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curate_combat as core

# offered in the style dropdown; the combobox stays editable for anything unusual
STYLES = ["stab", "slash", "crush", "melee", "ranged", "magic",
          "dragonfire", "metallic dragonfire", "icy breath", "typeless",
          # hybrids: FIRST word is the accuracy roll, SECOND is what you pray against
          "magic ranged", "magic melee", "ranged melee",
          "ranged magic", "melee magic", "melee ranged"]

HYBRID_WORDS = {"melee", "ranged", "magic"}
VARIANT_KINDS = {"gear", "prayer", "consumable", "combo"}

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


def collect_attacks(rows):
    """rows: iterable of (style, max_text, speed_text[, gear_text, prayer_text,
    consumable_text]) -> (attacks_list, error_or_None).

    Blank-style rows are dropped; max/speed accept 'N' or 'low-high' (bad input is an error).
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
        mh = core.parse_range(mx)
        spd = core.parse_range(sp)
        if mh is None or spd is None:
            return None, "Attack '{}' has a bad max hit or speed (use N or low-high).".format(style)
        attack = {"style": style, "maxHit": mh[0], "maxHitMax": mh[1],
                  "speed": spd[0], "speedMax": spd[1]}
        words = style.split()
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
        attacks.append(attack)
    return attacks, None


def headline_max_hit(attack):
    """The combat card's max-hit headline: best mitigated case first, base in parens."""
    base = core.fmt_range(attack.get("maxHit", 0), attack.get("maxHitMax", 0))
    variants = attack.get("variants")
    if not variants:
        return base
    best = min(variants, key=lambda v: max(v.get("maxHit", 0), v.get("maxHitMax", 0)))
    return "{} ({})".format(core.fmt_range(best.get("maxHit", 0), best.get("maxHitMax", 0)), base)


def legend_line(attack):
    """The combat card's legend row: base unprotected, then mitigations descending."""
    variants = attack.get("variants")
    if not variants:
        return ""
    parts = ["{} unprotected".format(core.fmt_range(attack.get("maxHit", 0), attack.get("maxHitMax", 0)))]
    for v in sorted(variants, key=lambda v: max(v.get("maxHit", 0), v.get("maxHitMax", 0)), reverse=True):
        parts.append("{} with {}".format(
            core.fmt_range(v.get("maxHit", 0), v.get("maxHitMax", 0)), v.get("label", "?")))
    return "   ·   ".join(parts)


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

    return wrap, entry, lambda: "" if showing["placeholder"] else textvar.get()


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
        root.title("Item Search — Combat Curator")
        root.geometry("1080x700")
        root.minsize(960, 620)
        root.configure(bg=INK)

        self._init_styles()

        self.overlay = core.load_json(core.OVERLAY, {}) or {}
        self.meta = core.build_meta()
        self.ids = []            # ids currently shown in the listbox, in order
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
        self._build_editor()

    # ---------- left: the review queue ----------
    def _build_queue(self):
        left = tk.Frame(self.root, bg=INK, padx=14, pady=14)
        left.pack(side="left", fill="y")
        left.pack_propagate(False)
        left.configure(width=252)

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

        lf = tk.Frame(left, bg=INK)
        lf.pack(fill="both", expand=True, pady=(10, 0))
        self.listbox = tk.Listbox(lf, activestyle="none", font=F_MONO_SM, bg=PANEL,
                                  fg=TEXT, selectbackground=GOLD_DEEP, selectforeground=GOLD,
                                  relief="flat", highlightthickness=0, bd=0)
        sb = ttk.Scrollbar(lf, orient="vertical", command=self.listbox.yview,
                           style="Curator.Vertical.TScrollbar")
        self.listbox.configure(yscrollcommand=sb.set)
        self.listbox.pack(side="left", fill="both", expand=True)
        sb.pack(side="left", fill="y")
        self.listbox.bind("<<ListboxSelect>>", self._on_select)
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
    def _build_editor(self):
        right = tk.Frame(self.root, bg=INK, padx=20, pady=14)
        right.pack(side="left", fill="both", expand=True)

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
        self._build_preview(right)
        self._rule(right).pack(fill="x", pady=(0, 12))

        bar = tk.Frame(right, bg=INK)
        bar.pack(fill="x")
        self.status = tk.Label(bar, text="", font=F_BODY, bg=INK, fg=GREEN, anchor="w")
        self.status.pack(side="left", fill="x", expand=True)
        flat_button(bar, "Save & next", self._save_next, kind="primary",
                    font=F_HEAD).pack(side="right")
        flat_button(bar, "Save", self._save).pack(side="right", padx=(0, 8))

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

        for attack in attacks:
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
            legend = legend_line(attack)
            if legend:
                tk.Label(self.preview, text=legend, font=F_SMALL, bg=PANEL, fg=MUTED,
                         anchor="w", justify="left", wraplength=680).pack(anchor="w",
                                                                          padx=(6, 0))

    def _schedule_preview(self, *_):
        if self._preview_job is not None:
            self.root.after_cancel(self._preview_job)
        self._preview_job = self.root.after(150, self._render_preview)

    # ---------- list ----------
    def _visible_ids(self):
        needle = self.filter_var.get().strip().lower()
        if needle == "search by name or id":
            needle = ""
        ids = []
        for k, v in self.overlay.items():
            if not (self.show_all.get() or v.get("needsReview")):
                continue
            if needle:
                name, _, _ = core.describe(self.meta, k)
                if needle not in name.lower() and needle not in k:
                    continue
            ids.append(k)
        # pending first, then by numeric id
        ids.sort(key=lambda k: (not self.overlay[k].get("needsReview"), int(k)))
        return ids

    def _refresh_list(self):
        self.ids = self._visible_ids()
        self.listbox.delete(0, "end")
        for k in self.ids:
            name, _, level = core.describe(self.meta, k)
            mark = "•" if self.overlay[k].get("needsReview") else "✓"
            if len(name) > 21:
                name = name[:20] + "…"
            self.listbox.insert("end", " {} {:<22}{:>4}".format(mark, name, level or "—"))
        for i, k in enumerate(self.ids):
            if not self.overlay[k].get("needsReview"):
                self.listbox.itemconfigure(i, foreground=FAINT)
        pending = sum(1 for v in self.overlay.values() if v.get("needsReview"))
        done = len(self.overlay) - pending
        self.count_label.configure(text="{} / {} reviewed".format(done, len(self.overlay)))
        self._draw_meter()

    def _on_select(self, _evt):
        sel = self.listbox.curselection()
        if sel:
            self._load(self.ids[sel[0]])

    def _select_in_list(self, idstr):
        if idstr in self.ids:
            i = self.ids.index(idstr)
            self.listbox.selection_clear(0, "end")
            self.listbox.selection_set(i)
            self.listbox.see(i)

    # ---------- editor rows ----------
    def _clear_rows(self):
        for r in self.rows:
            r["frame"].destroy()
        self.rows = []

    def _add_row(self, style="", mh_text="0", sp_text="0",
                 gear_text="", prayer_text="", consumable_text=""):
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

        row.update({"max_get": max_get, "speed_get": speed_get, "getters": getters})
        for var in (style_var, max_var, speed_var):
            var.trace_add("write", self._schedule_preview)
        self.rows.append(row)
        self._schedule_preview()
        return row

    def _remove_row(self, row):
        row["frame"].destroy()
        self.rows.remove(row)
        self._schedule_preview()

    def _load(self, idstr):
        self.current_id = idstr
        name, url, level = core.describe(self.meta, idstr)
        self.name_label.configure(text=name)
        self.sub_label.configure(text="level {}  ·  id {}".format(level or "—", idstr))
        self.link_url = "" if url.startswith("(") else url
        self.wiki_button.label.configure(
            fg=GOLD if self.link_url else FAINT,
            cursor="hand2" if self.link_url else "arrow",
            text="Open wiki page  ↗" if self.link_url else "No wiki link")
        self.status.configure(text="")
        self._clear_rows()
        for a in self.overlay[idstr].get("attacks") or []:
            style = a.get("style", "")
            if a.get("prayStyle"):
                style = style + " " + a["prayStyle"]
            self._add_row(style,
                          core.fmt_range(a.get("maxHit", 0), a.get("maxHitMax", 0)),
                          core.fmt_range(a.get("speed", 0), a.get("speedMax", 0)),
                          fmt_variants(a.get("variants"), "gear"),
                          fmt_variants(a.get("variants"), "prayer"),
                          fmt_variants(a.get("variants"), "consumable"))
        if not self.rows:
            self._add_row()
        self._render_preview()

    def _open_link(self):
        if self.link_url:
            webbrowser.open(self.link_url)

    def _gather(self):
        return [(r["style"].get(), r["max_get"](), r["speed_get"](),
                 r["getters"]["gear"](), r["getters"]["prayer"](),
                 r["getters"]["consumable"]()) for r in self.rows]

    def _save(self):
        if self.current_id is None:
            return False
        attacks, err = collect_attacks(self._gather())
        if err:
            # inline, next to the fields — a modal here would break the entry rhythm
            self.status.configure(text=err, fg=RED)
            self._render_preview()
            return False
        entry = self.overlay[self.current_id]
        entry["attacks"] = attacks
        entry["needsReview"] = False
        core.save(self.overlay)
        self.status.configure(text="Saved  ·  " + core.fmt_attacks(attacks), fg=GREEN)
        saved_id = self.current_id
        self._refresh_list()
        self._select_in_list(saved_id)
        return True

    def _save_next(self):
        if not self._save():
            return
        pending = [k for k in self.ids if self.overlay[k].get("needsReview")]
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
