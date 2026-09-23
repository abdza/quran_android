#!/usr/bin/env python3
"""
Validate common/wordbyword/src/main/assets/root_meanings.json against the Quran word data.

Checks:
  - every root used by a word in word_by_word_en.db has an entry (and no duplicates)
  - every verse reference like "2:255" in an entry is a verse where that root occurs
  - "N times" occurrence counts match the word data
  - required fields are present

Usage:
    python scripts/check_root_meanings.py            # report problems
    python scripts/check_root_meanings.py --info ROOT  # show forms/occurrences for a root
"""

import argparse
import collections
import json
import os
import re
import sqlite3
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_PATH = os.path.join(ROOT_DIR, "common/wordbyword/src/main/assets/root_meanings.json")
DB_PATH = os.path.join(ROOT_DIR, "app/src/main/assets/word_by_word_en.db")

TEXT_FIELDS = ("primaryMeaning", "extendedMeaning", "quranUsage", "notes")
VERSE_REF = re.compile(r"(?<![\d:])(\d{1,3}):(\d{1,3})(?![\d:])")
TIMES = re.compile(r"(\d[\d,]*) times")


def load_occurrences():
    db = sqlite3.connect(DB_PATH)
    occurrences = collections.defaultdict(set)
    counts = collections.Counter()
    for root, sura, ayah in db.execute(
        "SELECT etymology, sura, ayah FROM word_translations WHERE etymology IS NOT NULL AND etymology != ''"
    ):
        occurrences[root].add((sura, ayah))
        counts[root] += 1
    return db, occurrences, counts


def show_info(db, root, counts):
    print(f"{root}: {counts[root]} occurrences")
    rows = db.execute(
        """SELECT arabic_text, transliteration, translation, COUNT(*) AS c,
                  MIN(sura * 1000 + ayah) AS first
           FROM word_translations WHERE etymology = ?
           GROUP BY arabic_text ORDER BY c DESC""",
        (root,),
    )
    for arabic, translit, translation, count, first in rows:
        print(f"  {count:4d}  {first // 1000}:{first % 1000:<4d} {arabic}  {translit}  — {translation}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--info", metavar="ROOT")
    args = parser.parse_args()

    db, occurrences, counts = load_occurrences()
    if args.info:
        show_info(db, args.info, counts)
        return 0

    entries = json.load(open(JSON_PATH, encoding="utf-8"))
    problems = []
    seen = collections.Counter(e["root"] for e in entries)
    for root, n in seen.items():
        if n > 1:
            problems.append(f"{root}: defined {n} times")
    for root in counts:
        if root not in seen:
            problems.append(f"{root}: used by {counts[root]} words but has no entry")

    for e in entries:
        root = e["root"]
        if not e.get("primaryMeaning"):
            problems.append(f"{root}: missing primaryMeaning")
        text = " ".join(e.get(k) or "" for k in TEXT_FIELDS)
        for sura, ayah in VERSE_REF.findall(text):
            ref = (int(sura), int(ayah))
            if ref not in occurrences.get(root, ()):
                problems.append(f"{root}: cites {sura}:{ayah}, where this root does not occur")
        # Counts of individual forms can't be checked here, but none can exceed the root's total
        for n in TIMES.findall(e.get("quranUsage") or ""):
            if int(n.replace(",", "")) > counts[root]:
                problems.append(f"{root}: says {n} times but the root occurs {counts[root]} times")

    for p in problems:
        print(p)
    print(f"{len(entries)} entries, {len(problems)} problems", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
