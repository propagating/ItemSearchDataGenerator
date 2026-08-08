"""Tests for the curator's overlay persistence.

    python -m unittest test_curate_combat -v
"""
import json
import os
import tempfile
import unittest

import curate_combat as core


class SaveEntryTest(unittest.TestCase):
    def setUp(self):
        self._real = core.OVERLAY
        handle, self.path = tempfile.mkstemp(suffix=".json")
        os.close(handle)
        core.OVERLAY = self.path

    def tearDown(self):
        core.OVERLAY = self._real
        os.remove(self.path)

    def write(self, data):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(data, f)

    def read(self):
        with open(self.path, encoding="utf-8") as f:
            return json.load(f)

    def test_saving_one_entry_leaves_the_others_alone(self):
        self.write({"100": {"attacks": [], "needsReview": True},
                    "200": {"attacks": [], "needsReview": True}})
        core.save_entry("100", {"attacks": [{"style": "crush"}], "needsReview": False})
        on_disk = self.read()
        self.assertEqual(["100", "200"], sorted(on_disk))
        self.assertEqual("crush", on_disk["100"]["attacks"][0]["style"])
        self.assertTrue(on_disk["200"]["needsReview"])

    def test_a_generator_run_during_the_session_is_not_erased(self):
        """The regression this exists for.

        The GUI reads the overlay once when it opens and used to write the whole in-memory dict
        back on every save. A generator run in between adds a stub per combat gap and seeds
        effects onto attacks, and all of it vanished the next time anything was saved — silently,
        because a window left open across a regeneration looks like one that isn't.
        """
        opened_with = {"100": {"attacks": [], "needsReview": True}}
        self.write(opened_with)

        # the generator runs: a new stub, and an effect seeded onto an existing attack
        self.write({"100": {"attacks": [{"style": "crush", "effects": ["POISON"]}],
                            "needsReview": True},
                    "200": {"attacks": [], "needsReview": True}})

        # the session, still holding its stale snapshot, saves an unrelated edit
        merged = core.save_entry("300", {"attacks": [], "needsReview": False})

        on_disk = self.read()
        self.assertIn("200", on_disk, "the generator's stub must survive the save")
        self.assertEqual(["POISON"], on_disk["100"]["attacks"][0]["effects"],
                         "seeded effects must survive the save")
        self.assertIn("300", on_disk, "and the edit itself must land")
        self.assertEqual(on_disk, merged, "the caller adopts what actually went to disk")

    def test_entries_are_written_in_numeric_id_order(self):
        self.write({})
        core.save_entry("20", {"attacks": []})
        core.save_entry("3", {"attacks": []})
        core.save_entry("-5", {"attacks": []})
        with open(self.path, encoding="utf-8") as f:
            text = f.read()
        self.assertLess(text.index('"-5"'), text.index('"3"'))
        self.assertLess(text.index('"3"'), text.index('"20"'))


class LoadSourcesTest(unittest.TestCase):
    """sources.json holds shared drop tables, so its top level is an object, not a list."""

    def test_reads_both_shapes(self):
        real = core.DROPS
        handle, path = tempfile.mkstemp(suffix=".json")
        os.close(handle)
        core.DROPS = path
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"tables": [], "sources": [{"id": 1}]}, f)
            self.assertEqual([{"id": 1}], core.load_sources())

            with open(path, "w", encoding="utf-8") as f:
                json.dump([{"id": 2}], f)
            self.assertEqual([{"id": 2}], core.load_sources())
        finally:
            core.DROPS = real
            os.remove(path)

class TileTest(unittest.TestCase):
    """Tiles are typed by hand, one per line, so the parsing has to be forgiving but honest."""

    def test_reads_tiles_with_and_without_a_plane(self):
        text = "\n".join(["3016, 3514, 1", "2760,3061", "", "  3000 3100 2  "])
        tiles, err = core.parse_tiles(text)
        self.assertIsNone(err)
        self.assertEqual([{"x": 3016, "y": 3514, "plane": 1},
                          {"x": 2760, "y": 3061, "plane": 0},
                          {"x": 3000, "y": 3100, "plane": 2}], tiles)

    def test_a_bad_line_is_named_rather_than_dropped(self):
        tiles, err = core.parse_tiles("3016, 3514\nsomewhere near the bank")
        self.assertIsNone(tiles, "a typo must not silently lose the good lines with it")
        self.assertIn("line 2", err)

        tiles, err = core.parse_tiles("1, 2, 3, 4")
        self.assertIsNone(tiles)
        self.assertIn("line 1", err)

    def test_round_trips(self):
        text = "3016, 3514, 1\n2760, 3061, 0"
        tiles, err = core.parse_tiles(text)
        self.assertIsNone(err)
        self.assertEqual(text, core.fmt_tiles(tiles))

    def test_empty_means_no_tiles(self):
        self.assertEqual(([], None), core.parse_tiles(""))
        self.assertEqual(([], None), core.parse_tiles(None))
        self.assertEqual("", core.fmt_tiles(None))


class OverlayEntryTest(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".json")
        os.close(handle)

    def tearDown(self):
        os.remove(self.path)

    def write(self, data):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(data, f)

    def read(self):
        with open(self.path, encoding="utf-8") as f:
            return json.load(f)

    def test_merges_rather_than_replaces(self):
        self.write({"100": {"x": 1, "y": 2, "plane": 0}})
        core.save_overlay_entry(self.path, "200", {"x": 3, "y": 4, "plane": 1})
        self.assertEqual(["100", "200"], sorted(self.read()))

    def test_none_removes_an_entry(self):
        self.write({"100": {"x": 1, "y": 2, "plane": 0}})
        core.save_overlay_entry(self.path, "100", None)
        self.assertEqual({}, self.read())

    def test_entries_are_ordered_by_numeric_id(self):
        self.write({})
        for key in ("20", "3", "-5"):
            core.save_overlay_entry(self.path, key, {"x": 1, "y": 1, "plane": 0})
        with open(self.path, encoding="utf-8") as f:
            text = f.read()
        self.assertLess(text.index('"-5"'), text.index('"3"'))
        self.assertLess(text.index('"3"'), text.index('"20"'))


if __name__ == "__main__":
    unittest.main()
