#!/usr/bin/env python3
"""Very basic desktop (Tkinter) editor for the drop-search combat overlay.

Lists the mobs flagged needsReview, shows each mob's OSRS Wiki link (click to
open), and gives you a style dropdown plus max-hit / speed text boxes per
attack. Saving writes combat-overlay.json and marks the mob reviewed. Re-run
the generator afterwards to bake the values into drops.json:

    ./gradlew run --args="monsters-complete.json"

Run:  python curate_gui.py

Reuses the data helpers from curate_combat.py (same folder).
"""
import os
import sys
import webbrowser

import tkinter as tk
from tkinter import ttk, messagebox, font as tkfont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curate_combat as core

# offered in the style dropdown; the combobox stays editable for anything unusual
STYLES = ["stab", "slash", "crush", "melee", "ranged", "magic",
          "dragonfire", "metallic dragonfire", "icy breath", "typeless"]


def collect_attacks(rows):
    """rows: iterable of (style, max_text, speed_text) -> (attacks_list, error_or_None).

    Blank-style rows are dropped; max/speed accept 'N' or 'low-high' (bad input is an error).
    """
    attacks = []
    for style, mx, sp in rows:
        style = (style or "").strip().lower()
        if not style:
            continue
        mh = core.parse_range(mx)
        spd = core.parse_range(sp)
        if mh is None or spd is None:
            return None, "Attack '{}' has a bad max hit or speed (use N or low-high).".format(style)
        attacks.append({"style": style, "maxHit": mh[0], "maxHitMax": mh[1],
                        "speed": spd[0], "speedMax": spd[1]})
    return attacks, None


class CuratorApp:
    def __init__(self, root):
        self.root = root
        root.title("Drop Search — Combat Overlay Curator")
        root.geometry("780x540")

        self.overlay = core.load_json(core.OVERLAY, {}) or {}
        self.meta = core.build_meta()
        self.ids = []            # ids currently shown in the listbox, in order
        self.current_id = None
        self.rows = []           # [{frame, style, max, speed}]
        self.link_url = ""

        self._build()
        self._refresh_list()

    # ---------- layout ----------
    def _build(self):
        left = ttk.Frame(self.root, padding=6)
        left.pack(side="left", fill="y")
        ttk.Label(left, text="NPCs", font=("", 10, "bold")).pack(anchor="w")
        self.count_label = ttk.Label(left, text="")
        self.count_label.pack(anchor="w")
        self.show_all = tk.BooleanVar(value=False)
        ttk.Checkbutton(left, text="Show reviewed too", variable=self.show_all,
                        command=self._refresh_list).pack(anchor="w", pady=(2, 4))

        lf = ttk.Frame(left)
        lf.pack(fill="y", expand=True)
        self.listbox = tk.Listbox(lf, width=34, activestyle="none")
        sb = ttk.Scrollbar(lf, orient="vertical", command=self.listbox.yview)
        self.listbox.configure(yscrollcommand=sb.set)
        self.listbox.pack(side="left", fill="y", expand=True)
        sb.pack(side="left", fill="y")
        self.listbox.bind("<<ListboxSelect>>", self._on_select)

        right = ttk.Frame(self.root, padding=10)
        right.pack(side="left", fill="both", expand=True)

        self.name_label = ttk.Label(right, text="Select an NPC on the left", font=("", 13, "bold"))
        self.name_label.pack(anchor="w")
        self.link_label = tk.Label(right, text="", fg="#3a7bd5", cursor="hand2")
        link_font = tkfont.Font(font=self.link_label.cget("font"))
        link_font.configure(underline=True)
        self.link_label.configure(font=link_font)
        self.link_label.pack(anchor="w", pady=(0, 10))
        self.link_label.bind("<Button-1>", lambda e: self._open_link())

        hdr = ttk.Frame(right)
        hdr.pack(fill="x")
        ttk.Label(hdr, text="Style", width=16).grid(row=0, column=0, sticky="w")
        ttk.Label(hdr, text="Max hit (N or lo-hi)", width=16).grid(row=0, column=1, sticky="w")
        ttk.Label(hdr, text="Speed", width=12).grid(row=0, column=2, sticky="w")
        self.attacks_frame = ttk.Frame(right)
        self.attacks_frame.pack(fill="x", pady=(2, 6))

        btns = ttk.Frame(right)
        btns.pack(fill="x", pady=(4, 0))
        ttk.Button(btns, text="+ Add attack", command=lambda: self._add_row()).pack(side="left")
        ttk.Button(btns, text="Save & Next", command=self._save_next).pack(side="right")
        ttk.Button(btns, text="Save", command=self._save).pack(side="right", padx=(0, 6))

        self.status = ttk.Label(right, text="", foreground="#2c9a4a")
        self.status.pack(anchor="w", pady=(8, 0))

    # ---------- list ----------
    def _visible_ids(self):
        ids = [k for k, v in self.overlay.items()
               if self.show_all.get() or v.get("needsReview")]
        # pending first, then by numeric id
        ids.sort(key=lambda k: (not self.overlay[k].get("needsReview"), int(k)))
        return ids

    def _refresh_list(self):
        self.ids = self._visible_ids()
        self.listbox.delete(0, "end")
        for k in self.ids:
            name, _, level = core.describe(self.meta, k)
            prefix = "• " if self.overlay[k].get("needsReview") else "✓ "
            self.listbox.insert("end", "{}{} (lvl {}) [{}]".format(prefix, name, level, k))
        pending = sum(1 for v in self.overlay.values() if v.get("needsReview"))
        self.count_label.configure(text="{} pending / {} total".format(pending, len(self.overlay)))

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

    # ---------- editor ----------
    def _clear_rows(self):
        for r in self.rows:
            r["frame"].destroy()
        self.rows = []

    def _add_row(self, style="", mh_text="0", sp_text="0"):
        fr = ttk.Frame(self.attacks_frame)
        fr.pack(fill="x", pady=1)
        style_var, max_var, speed_var = tk.StringVar(value=style), tk.StringVar(value=mh_text), tk.StringVar(value=sp_text)
        ttk.Combobox(fr, values=STYLES, textvariable=style_var, width=14).grid(row=0, column=0, sticky="w")
        ttk.Entry(fr, textvariable=max_var, width=10).grid(row=0, column=1, sticky="w", padx=4)
        ttk.Entry(fr, textvariable=speed_var, width=8).grid(row=0, column=2, sticky="w", padx=4)
        row = {"frame": fr, "style": style_var, "max": max_var, "speed": speed_var}
        ttk.Button(fr, text="✕", width=3, command=lambda: self._remove_row(row)).grid(row=0, column=3, padx=4)
        self.rows.append(row)

    def _remove_row(self, row):
        row["frame"].destroy()
        self.rows.remove(row)

    def _load(self, idstr):
        self.current_id = idstr
        name, url, level = core.describe(self.meta, idstr)
        self.name_label.configure(text="{} — level {}  (id {})".format(name, level, idstr))
        self.link_url = url
        self.link_label.configure(text=url)
        self.status.configure(text="")
        self._clear_rows()
        for a in self.overlay[idstr].get("attacks") or []:
            self._add_row(a.get("style", ""),
                          core.fmt_range(a.get("maxHit", 0), a.get("maxHitMax", 0)),
                          core.fmt_range(a.get("speed", 0), a.get("speedMax", 0)))
        if not self.rows:
            self._add_row()

    def _open_link(self):
        if self.link_url:
            webbrowser.open(self.link_url)

    def _gather(self):
        return [(r["style"].get(), r["max"].get(), r["speed"].get()) for r in self.rows]

    def _save(self):
        if self.current_id is None:
            return False
        attacks, err = collect_attacks(self._gather())
        if err:
            messagebox.showerror("Invalid input", err)
            return False
        entry = self.overlay[self.current_id]
        entry["attacks"] = attacks
        entry["needsReview"] = False
        core.save(self.overlay)
        self.status.configure(text="Saved: " + core.fmt_attacks(attacks))
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
            self.name_label.configure(text="All reviewed 🎉")
            self.status.configure(text="Re-run the generator to bake the values into drops.json.")


def main():
    root = tk.Tk()
    CuratorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
