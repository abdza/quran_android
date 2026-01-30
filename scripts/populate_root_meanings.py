#!/usr/bin/env python3
"""
Populate root_meanings table with linguistic information about Arabic roots.
"""

import sqlite3
import os

DB_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/assets/word_by_word_en.db"
)

# Root meanings data - format: (root, primary_meaning, extended_meaning, quran_usage, notes)
ROOT_MEANINGS = [
    # ك-ف-ر (k-f-r) - disbelief
    ("ك-ف-ر",
     "to cover, to conceal, to hide",
     "Originally meant to cover something, like a farmer covering seeds with soil. Also means to be ungrateful (covering blessings).",
     "In the Quran, refers to rejecting or concealing the truth of Allah's signs. A kafir is one who 'covers' or denies the evident truth.",
     "In classical Arabic, a farmer was also called 'kafir' because he covers seeds. Night is called 'kafir' because it covers the day."),

    # ر-ح-م (r-h-m) - mercy
    ("ر-ح-م",
     "mercy, compassion, womb",
     "The root connects mercy with the womb (rahim), suggesting that divine mercy is like a mother's love - nurturing, protective, and unconditional.",
     "Allah's names Al-Rahman and Al-Rahim both derive from this root. Rahman indicates all-encompassing mercy, Rahim indicates specific mercy for believers.",
     "The connection between 'womb' and 'mercy' shows how Arabic roots carry deep semantic relationships."),

    # س-م-و (s-m-w) - name/elevation
    ("س-م-و",
     "to be high, to rise, name, sky",
     "Connects the concepts of elevation, naming, and the heavens. A name (ism) elevates and distinguishes something.",
     "Used for sky (sama'), names (ism), and elevation. 'Bismillah' uses this root - beginning with Allah's name that is most elevated.",
     None),

    # ع-ل-م ('a-l-m) - knowledge
    ("ع-ل-م",
     "to know, knowledge, sign, world",
     "Encompasses knowing, teaching, signs/marks, and the world (which is a sign of the Creator).",
     "Al-Alim (The All-Knowing) is one of Allah's names. 'Alameen' (worlds/universe) shares this root - the creation is a sign pointing to knowledge of the Creator.",
     "The word 'ulama' (scholars) and 'alam' (world/flag/sign) all share this root."),

    # ح-م-د (h-m-d) - praise
    ("ح-م-د",
     "to praise, to thank, to commend",
     "Praise that comes from recognizing inherent goodness and beauty, not just receiving benefit.",
     "Al-Hamdulillah (all praise is for Allah) opens Surah Al-Fatihah. Muhammad and Ahmad (names of the Prophet) derive from this root.",
     "Unlike 'shukr' (gratitude for receiving), 'hamd' is praise for inherent qualities."),

    # أ-ل-ه (a-l-h) - deity
    ("أ-ل-ه",
     "deity, god, to worship, to be bewildered",
     "Originally implied being bewildered or seeking refuge, evolving to mean the one worthy of worship.",
     "Allah is the proper name of God, combining 'al' (the) with 'ilah' (deity) - The One True God.",
     "The root suggests that humans naturally turn to a higher power in times of need."),

    # ص-ل-و (s-l-w) - prayer
    ("ص-ل-و",
     "prayer, connection, supplication",
     "Implies a close connection and turning towards. The physical movements of salah reflect this turning and connection.",
     "Salah is not just ritual prayer but a direct connection with Allah. The word also means blessings (as in 'salawat' upon the Prophet).",
     None),

    # ع-ب-د ('a-b-d) - worship/servant
    ("ع-ب-د",
     "to worship, to serve, servant, slave",
     "Complete submission and service. A slave has no will against their master's will.",
     "Ibadah (worship) means complete submission to Allah. 'Abd' (servant) is the highest title - Abdullah means 'servant of Allah'.",
     "True freedom in Islam comes through being a slave only to Allah, not to desires or other creations."),

    # ه-د-ي (h-d-y) - guidance
    ("ه-د-ي",
     "to guide, guidance, gift",
     "Leading someone gently to their destination. Also means a gift (something you guide towards someone).",
     "Al-Hadi is one of Allah's names. 'Ihdina al-sirat al-mustaqim' - Guide us to the straight path.",
     "Hidayah (guidance) is considered the greatest gift from Allah."),

    # ق-ر-ء (q-r-') - recitation
    ("ق-ر-ء",
     "to read, to recite, to gather",
     "Originally meant to gather or collect. Recitation gathers letters into words into meanings.",
     "The Quran literally means 'the recitation' or 'that which is recited'. The first revelation was 'Iqra' - Read/Recite!",
     None),

    # إ-م-ن (a-m-n) - faith/security
    ("أ-م-ن",
     "to be safe, security, faith, trust",
     "Safety, security, and trust are interconnected. Faith (iman) provides inner security.",
     "Iman (faith) provides security for the heart. A mu'min (believer) is one who has inner peace through faith. Amen comes from this root.",
     "The connection between faith and security shows that true peace comes from trust in Allah."),

    # ج-ن-ن (j-n-n) - hidden
    ("ج-ن-ن",
     "to cover, to hide, to be concealed",
     "Anything hidden or concealed. Gardens hide what's inside with their foliage.",
     "Jinn are hidden beings. Jannah (paradise) is a hidden garden. Janin (fetus) is hidden in the womb. Junun (madness) is when reason is hidden.",
     "Many seemingly different words share this root through the concept of being hidden."),

    # ن-و-ر (n-w-r) - light
    ("ن-و-ر",
     "light, illumination, to enlighten",
     "Physical and spiritual light. Knowledge and guidance are described as light.",
     "Allah is described as the Light of the heavens and earth (Ayat al-Nur). The Quran is called a 'light' that guides from darkness.",
     None),

    # ظ-ل-م (z-l-m) - darkness/injustice
    ("ظ-ل-م",
     "darkness, injustice, oppression, to wrong",
     "Darkness and injustice share a root - injustice obscures the light of truth and fairness.",
     "Zulm (oppression/injustice) is putting something in the wrong place. Zulumat (darknesses) is the opposite of nur (light).",
     "Shirk (associating partners with Allah) is called the greatest zulm because it misplaces worship."),

    # ش-ك-ر (sh-k-r) - gratitude
    ("ش-ك-ر",
     "to thank, gratitude, to appreciate",
     "Recognizing and acknowledging blessings received. Implies action in response to blessings.",
     "Ash-Shakur (The Most Appreciative) is one of Allah's names - He appreciates even small good deeds. Shukr is responding to blessings with gratitude.",
     "Different from hamd (praise) - shukr is specifically gratitude for blessings received."),

    # ت-و-ب (t-w-b) - repentance
    ("ت-و-ب",
     "to return, to repent, to turn back",
     "Repentance literally means 'returning' to Allah. Implies a journey away and coming back.",
     "At-Tawwab (The Accepting of Repentance) is one of Allah's names. Tawbah is returning to Allah after straying.",
     "The door of tawbah is always open - one can always return."),

    # ذ-ك-ر (dh-k-r) - remembrance
    ("ذ-ك-ر",
     "to remember, to mention, male",
     "Remembrance, mention, and masculinity share this root. Dhikr keeps something present in the mind.",
     "Dhikr (remembrance of Allah) is one of the most emphasized acts of worship. The Quran itself is called 'Al-Dhikr'.",
     None),

    # ف-ت-ح (f-t-h) - opening
    ("ف-ت-ح",
     "to open, victory, conquest, to begin",
     "Opening can be physical (door) or abstract (victory, beginning). Fatiha opens the Quran.",
     "Al-Fattah (The Opener) is one of Allah's names. Surah Al-Fatiha 'opens' the Quran. Fath also means victory (Surah Al-Fath).",
     None),

    # س-ل-م (s-l-m) - peace/submission
    ("س-ل-م",
     "peace, safety, submission, soundness",
     "Peace comes through submission. Islam means submission (to Allah). Muslim is one who submits. Salam means peace.",
     "Islam, Muslim, and Salam all share this root. True peace (salam) comes through submission (islam) to Allah.",
     "The greeting 'Assalamu Alaikum' means 'peace be upon you' - wishing safety and submission to Allah's will."),

    # ق-ل-ب (q-l-b) - heart/turning
    ("ق-ل-ب",
     "heart, to turn, to flip, to change",
     "The heart is called 'qalb' because it constantly turns and changes states.",
     "The heart (qalb) is the spiritual center that can turn towards or away from Allah. Seeking 'qalb saleem' (sound heart) is the goal.",
     "The Prophet (PBUH) would pray: 'O Turner of hearts, keep my heart firm on Your religion.'"),
]

def populate_root_meanings():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Create table if not exists
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS root_meanings(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            root TEXT NOT NULL UNIQUE,
            primary_meaning TEXT NOT NULL,
            extended_meaning TEXT,
            quran_usage TEXT,
            notes TEXT
        )
    """)

    inserted = 0
    for root, primary, extended, quran_usage, notes in ROOT_MEANINGS:
        try:
            cursor.execute(
                """INSERT OR REPLACE INTO root_meanings
                   (root, primary_meaning, extended_meaning, quran_usage, notes)
                   VALUES (?, ?, ?, ?, ?)""",
                (root, primary, extended, quran_usage, notes)
            )
            inserted += 1
        except Exception as e:
            print(f"Error inserting {root}: {e}")

    conn.commit()
    conn.close()

    print(f"Inserted {inserted} root meanings")

if __name__ == '__main__':
    populate_root_meanings()
