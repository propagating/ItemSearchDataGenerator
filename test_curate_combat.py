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


if __name__ == "__main__":
    unittest.main()
