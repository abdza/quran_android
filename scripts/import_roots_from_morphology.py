#!/usr/bin/env python3
"""
Import Arabic roots from quran-morphology data into word_by_word_en.db.

Downloads morphology data from GitHub and extracts roots for each word.
"""

import sqlite3
import urllib.request
import os
import re
from collections import defaultdict

MORPHOLOGY_URL = "https://raw.githubusercontent.com/mustafa0x/quran-morphology/master/quran-morphology.txt"
DB_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/assets/word_by_word_en.db"
)

def download_morphology():
    """Download morphology data from GitHub."""
    print("Downloading morphology data...")
    with urllib.request.urlopen(MORPHOLOGY_URL) as response:
        return response.read().decode('utf-8')

def format_root(root):
    """Format root with dashes between letters (e.g., رحم -> ر-ح-م)."""
    if not root:
        return None
    # Insert dash between each character
    return '-'.join(root)

def parse_morphology(data):
    """
    Parse morphology data and extract root for each word position.
    Returns dict: {(sura, ayah, word_position): root}
    """
    word_roots = {}

    for line in data.strip().split('\n'):
        if not line or line.startswith('#'):
            continue

        parts = line.split('\t')
        if len(parts) < 4:
            continue

        location = parts[0]  # e.g., 1:1:1:1 (sura:ayah:word:segment)
        morphology = parts[3]  # e.g., ROOT:رحم|LEM:رَحْمٰن|...

        # Parse location
        loc_parts = location.split(':')
        if len(loc_parts) < 3:
            continue

        sura = int(loc_parts[0])
        ayah = int(loc_parts[1])
        word_pos = int(loc_parts[2])

        # Extract root from morphology
        root_match = re.search(r'ROOT:([^\|]+)', morphology)
        if root_match:
            root = root_match.group(1)
            key = (sura, ayah, word_pos)

            # Only store if we don't already have a root for this word
            # (first segment with root wins)
            if key not in word_roots:
                word_roots[key] = format_root(root)

    return word_roots

def update_database(word_roots):
    """Update database with etymology (root) data."""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    updated = 0
    not_found = 0

    for (sura, ayah, word_pos), root in word_roots.items():
        cursor.execute(
            """UPDATE word_translations
               SET etymology = ?
               WHERE sura = ? AND ayah = ? AND word_position = ?""",
            (root, sura, ayah, word_pos)
        )
        if cursor.rowcount > 0:
            updated += 1
        else:
            not_found += 1

    conn.commit()
    conn.close()

    return updated, not_found

def get_stats():
    """Get current etymology statistics."""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM word_translations")
    total = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM word_translations WHERE etymology IS NOT NULL AND etymology != ''")
    with_root = cursor.fetchone()[0]

    conn.close()
    return total, with_root

def main():
    print("=== Quran Root Importer ===\n")

    # Get initial stats
    total, with_root = get_stats()
    print(f"Before: {with_root}/{total} words have etymology ({100*with_root/total:.1f}%)\n")

    # Download and parse morphology
    data = download_morphology()
    print(f"Downloaded {len(data)} bytes of morphology data")

    word_roots = parse_morphology(data)
    print(f"Extracted roots for {len(word_roots)} word positions\n")

    # Update database
    print("Updating database...")
    updated, not_found = update_database(word_roots)
    print(f"Updated: {updated} words")
    print(f"Not found in DB: {not_found} positions\n")

    # Get final stats
    total, with_root = get_stats()
    print(f"After: {with_root}/{total} words have etymology ({100*with_root/total:.1f}%)")

if __name__ == '__main__':
    main()
