#!/usr/bin/env python3
"""
Script to populate etymology data in the word_by_word_en.db database.

Usage:
    1. From a JSON file with root mappings:
       python populate_etymology.py --from-json roots.json

       JSON format: {"arabic_text": "root", ...}
       Example: {"بِسْمِ": "س-م-و", "ٱللَّهِ": "أ-ل-ه"}

    2. From a CSV file:
       python populate_etymology.py --from-csv roots.csv

       CSV format: arabic_text,etymology

    3. Interactive mode (update one word at a time):
       python populate_etymology.py --interactive

    4. Export current words (for manual editing):
       python populate_etymology.py --export words.csv
"""

import sqlite3
import json
import csv
import argparse
import os

DB_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/assets/word_by_word_en.db"
)

def get_connection():
    return sqlite3.connect(DB_PATH)

def export_words(output_file):
    """Export all unique Arabic words to CSV for manual etymology entry."""
    conn = get_connection()
    cursor = conn.cursor()

    # Get unique words with their translations
    cursor.execute("""
        SELECT DISTINCT arabic_text, translation, transliteration, etymology
        FROM word_translations
        ORDER BY arabic_text
    """)

    rows = cursor.fetchall()
    conn.close()

    with open(output_file, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['arabic_text', 'translation', 'transliteration', 'etymology'])
        writer.writerows(rows)

    print(f"Exported {len(rows)} unique words to {output_file}")

def update_from_json(json_file):
    """Update etymology from JSON file mapping arabic_text -> root."""
    with open(json_file, 'r', encoding='utf-8') as f:
        mappings = json.load(f)

    conn = get_connection()
    cursor = conn.cursor()

    updated = 0
    for arabic_text, etymology in mappings.items():
        cursor.execute(
            "UPDATE word_translations SET etymology = ? WHERE arabic_text = ?",
            (etymology, arabic_text)
        )
        updated += cursor.rowcount

    conn.commit()
    conn.close()
    print(f"Updated {updated} word entries with etymology data")

def update_from_csv(csv_file):
    """Update etymology from CSV file with arabic_text,etymology columns."""
    conn = get_connection()
    cursor = conn.cursor()

    updated = 0
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get('etymology'):
                cursor.execute(
                    "UPDATE word_translations SET etymology = ? WHERE arabic_text = ?",
                    (row['etymology'], row['arabic_text'])
                )
                updated += cursor.rowcount

    conn.commit()
    conn.close()
    print(f"Updated {updated} word entries with etymology data")

def interactive_mode():
    """Interactively update etymology for words without it."""
    conn = get_connection()
    cursor = conn.cursor()

    # Get words without etymology
    cursor.execute("""
        SELECT DISTINCT arabic_text, translation, transliteration
        FROM word_translations
        WHERE etymology IS NULL OR etymology = ''
        ORDER BY id
        LIMIT 100
    """)

    words = cursor.fetchall()

    print(f"Found {len(words)} words without etymology (showing first 100)")
    print("Enter etymology (Arabic root like ر-ح-م) or 'skip' to skip, 'quit' to exit\n")

    for arabic, translation, translit in words:
        print(f"Arabic: {arabic}")
        print(f"Translation: {translation}")
        print(f"Transliteration: {translit}")

        etymology = input("Etymology: ").strip()

        if etymology.lower() == 'quit':
            break
        elif etymology.lower() == 'skip' or not etymology:
            continue
        else:
            cursor.execute(
                "UPDATE word_translations SET etymology = ? WHERE arabic_text = ?",
                (etymology, arabic)
            )
            print(f"Updated!\n")

    conn.commit()
    conn.close()

def show_stats():
    """Show current etymology population statistics."""
    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM word_translations")
    total = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM word_translations WHERE etymology IS NOT NULL AND etymology != ''")
    with_etymology = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(DISTINCT arabic_text) FROM word_translations")
    unique_words = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(DISTINCT arabic_text) FROM word_translations WHERE etymology IS NOT NULL AND etymology != ''")
    unique_with_etymology = cursor.fetchone()[0]

    conn.close()

    print(f"Total word entries: {total}")
    print(f"Unique words: {unique_words}")
    print(f"Entries with etymology: {with_etymology} ({100*with_etymology/total:.1f}%)")
    print(f"Unique words with etymology: {unique_with_etymology} ({100*unique_with_etymology/unique_words:.1f}%)")

def main():
    parser = argparse.ArgumentParser(description='Populate etymology data in word_by_word database')
    parser.add_argument('--from-json', help='Update from JSON file mapping arabic_text -> etymology')
    parser.add_argument('--from-csv', help='Update from CSV file with arabic_text,etymology columns')
    parser.add_argument('--export', help='Export unique words to CSV for manual editing')
    parser.add_argument('--interactive', action='store_true', help='Interactive mode')
    parser.add_argument('--stats', action='store_true', help='Show statistics')

    args = parser.parse_args()

    if args.export:
        export_words(args.export)
    elif args.from_json:
        update_from_json(args.from_json)
    elif args.from_csv:
        update_from_csv(args.from_csv)
    elif args.interactive:
        interactive_mode()
    elif args.stats:
        show_stats()
    else:
        show_stats()
        print("\nRun with --help for usage options")

if __name__ == '__main__':
    main()
