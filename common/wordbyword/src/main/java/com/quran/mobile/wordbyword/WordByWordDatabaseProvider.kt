package com.quran.mobile.wordbyword

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.wordbyword.data.WordByWordDatabase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
class WordByWordDatabaseProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val databaseName = "word_by_word_en.db"
  private val databasePath = File(quranFileManager.databaseDirectory(), databaseName)
  private var cachedDatabase: WordByWordDatabase? = null

  private suspend fun ensureWordByWordDatabase(): Boolean {
    return if (databasePath.exists()) {
      true
    } else {
      withContext(Dispatchers.IO) {
        runCatching {
          quranFileManager.copyFromAssetsRelative(
            databaseName,
            databaseName,
            quranFileManager.databaseDirectory()
          )
        }.isSuccess
      }
    }
  }

  suspend fun provideDatabase(): WordByWordDatabase? {
    val cached = cachedDatabase
    return cached
      ?: if (ensureWordByWordDatabase()) {
        val filePath = databasePath.absolutePath
        // Use a custom callback that doesn't try to create tables since the database
        // is pre-populated and copied from assets
        val driver = AndroidSqliteDriver(
          schema = WordByWordDatabase.Schema,
          context = appContext,
          name = filePath,
          callback = object : AndroidSqliteDriver.Callback(WordByWordDatabase.Schema) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
              // Don't create tables - the database is pre-populated
            }
            override fun onUpgrade(
              db: androidx.sqlite.db.SupportSQLiteDatabase,
              oldVersion: Int,
              newVersion: Int
            ) {
              // No migrations needed for pre-populated database
            }
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
              super.onOpen(db)
              // Ensure root_meanings table exists for older database versions
              ensureRootMeaningsTable(db)
            }
          }
        )
        val database = WordByWordDatabase(driver)
        cachedDatabase = database
        database
      } else {
        null
      }
  }

  fun isDatabaseAvailable(): Boolean = databasePath.exists()

  private fun ensureRootMeaningsTable(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    try {
      // Check if table exists
      val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='root_meanings'")
      val tableExists = cursor.use { it.count > 0 }

      if (!tableExists) {
        // Create the root_meanings table
        db.execSQL("""
          CREATE TABLE root_meanings(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            root TEXT NOT NULL UNIQUE,
            primary_meaning TEXT NOT NULL,
            extended_meaning TEXT,
            quran_usage TEXT,
            notes TEXT
          )
        """.trimIndent())

        // Seed initial data
        seedRootMeanings(db)
      }
    } catch (e: Exception) {
      // Ignore errors - table creation is optional
    }
  }

  private fun seedRootMeanings(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    val rootMeanings = listOf(
      // === CORE THEOLOGICAL ROOTS ===
      RootMeaningData(
        root = "ك-ف-ر",
        primaryMeaning = "to cover, to conceal, to hide",
        extendedMeaning = "Originally meant to cover something, like a farmer covering seeds with soil. Also means to be ungrateful (covering blessings).",
        quranUsage = "In the Quran, refers to rejecting or concealing the truth of Allah's signs. A kafir is one who 'covers' or denies the evident truth.",
        notes = "In classical Arabic, a farmer was also called 'kafir' because he covers seeds. Night is called 'kafir' because it covers the day."
      ),
      RootMeaningData(
        root = "ر-ح-م",
        primaryMeaning = "mercy, compassion, womb",
        extendedMeaning = "The root connects mercy with the womb (rahim), suggesting that divine mercy is like a mother's love.",
        quranUsage = "Allah's names Al-Rahman and Al-Rahim both derive from this root. Rahman indicates all-encompassing mercy, Rahim indicates specific mercy for believers.",
        notes = "The connection between 'womb' and 'mercy' shows how Arabic roots carry deep semantic relationships."
      ),
      RootMeaningData(
        root = "ع-ل-م",
        primaryMeaning = "to know, knowledge, sign, world",
        extendedMeaning = "Encompasses knowing, teaching, signs/marks, and the world (which is a sign of the Creator).",
        quranUsage = "Al-Alim (The All-Knowing) is one of Allah's names. 'Alameen' (worlds/universe) shares this root.",
        notes = "The word 'ulama' (scholars) and 'alam' (world/flag/sign) all share this root."
      ),
      RootMeaningData(
        root = "ح-م-د",
        primaryMeaning = "to praise, to thank, to commend",
        extendedMeaning = "Praise that comes from recognizing inherent goodness and beauty, not just receiving benefit.",
        quranUsage = "Al-Hamdulillah opens Surah Al-Fatihah. Muhammad and Ahmad (names of the Prophet) derive from this root.",
        notes = "Unlike 'shukr' (gratitude for receiving), 'hamd' is praise for inherent qualities."
      ),
      RootMeaningData(
        root = "أ-ل-ه",
        primaryMeaning = "deity, god, to worship, to be bewildered",
        extendedMeaning = "Originally implied being bewildered or seeking refuge, evolving to mean the one worthy of worship.",
        quranUsage = "Allah is the proper name of God, combining 'al' (the) with 'ilah' (deity) - The One True God.",
        notes = "The root suggests that humans naturally turn to a higher power in times of need."
      ),
      RootMeaningData(
        root = "س-ل-م",
        primaryMeaning = "peace, safety, submission, soundness",
        extendedMeaning = "Peace comes through submission. Islam means submission (to Allah). Muslim is one who submits.",
        quranUsage = "Islam, Muslim, and Salam all share this root. True peace (salam) comes through submission (islam) to Allah.",
        notes = "The greeting 'Assalamu Alaikum' means 'peace be upon you'."
      ),
      RootMeaningData(
        root = "ع-ب-د",
        primaryMeaning = "to worship, to serve, servant, slave",
        extendedMeaning = "Complete submission and service. A slave has no will against their master's will.",
        quranUsage = "Ibadah (worship) means complete submission to Allah. 'Abd' (servant) is the highest title - Abdullah means 'servant of Allah'.",
        notes = "True freedom in Islam comes through being a slave only to Allah, not to desires or other creations."
      ),
      RootMeaningData(
        root = "ه-د-ي",
        primaryMeaning = "to guide, guidance, gift",
        extendedMeaning = "Leading someone gently to their destination. Also means a gift (something you guide towards someone).",
        quranUsage = "Al-Hadi is one of Allah's names. 'Ihdina al-sirat al-mustaqim' - Guide us to the straight path.",
        notes = "Hidayah (guidance) is considered the greatest gift from Allah."
      ),
      RootMeaningData(
        root = "ق-ر-ء",
        primaryMeaning = "to read, to recite, to gather",
        extendedMeaning = "Originally meant to gather or collect. Recitation gathers letters into words into meanings.",
        quranUsage = "The Quran literally means 'the recitation'. The first revelation was 'Iqra' - Read/Recite!",
        notes = null
      ),
      RootMeaningData(
        root = "أ-م-ن",
        primaryMeaning = "to be safe, security, faith, trust",
        extendedMeaning = "Safety, security, and trust are interconnected. Faith (iman) provides inner security.",
        quranUsage = "Iman (faith) provides security for the heart. A mu'min (believer) is one who has inner peace through faith. Amen comes from this root.",
        notes = "The connection between faith and security shows that true peace comes from trust in Allah."
      ),
      RootMeaningData(
        root = "ت-و-ب",
        primaryMeaning = "to return, to repent, to turn back",
        extendedMeaning = "Repentance literally means 'returning' to Allah. Implies a journey away and coming back.",
        quranUsage = "At-Tawwab (The Accepting of Repentance) is one of Allah's names. Tawbah is returning to Allah after straying.",
        notes = "The door of tawbah is always open - one can always return."
      ),
      RootMeaningData(
        root = "ق-ل-ب",
        primaryMeaning = "heart, to turn, to flip, to change",
        extendedMeaning = "The heart is called 'qalb' because it constantly turns and changes states.",
        quranUsage = "The heart (qalb) is the spiritual center that can turn towards or away from Allah. Seeking 'qalb saleem' (sound heart) is the goal.",
        notes = "The Prophet would pray: 'O Turner of hearts, keep my heart firm on Your religion.'"
      ),
      RootMeaningData(
        root = "س-م-و",
        primaryMeaning = "to be high, to rise, name, sky",
        extendedMeaning = "Connects the concepts of elevation, naming, and the heavens. A name (ism) elevates and distinguishes.",
        quranUsage = "Used for sky (sama'), names (ism), and elevation. 'Bismillah' uses this root.",
        notes = null
      ),
      RootMeaningData(
        root = "ن-و-ر",
        primaryMeaning = "light, illumination, to enlighten",
        extendedMeaning = "Physical and spiritual light. Knowledge and guidance are described as light.",
        quranUsage = "Allah is described as the Light of the heavens and earth (Ayat al-Nur). The Quran is called a 'light' that guides from darkness.",
        notes = null
      ),
      RootMeaningData(
        root = "ظ-ل-م",
        primaryMeaning = "darkness, injustice, oppression, to wrong",
        extendedMeaning = "Darkness and injustice share a root - injustice obscures the light of truth and fairness.",
        quranUsage = "Zulm (oppression/injustice) is putting something in the wrong place. Zulumat (darknesses) is the opposite of nur (light).",
        notes = "Shirk (associating partners with Allah) is called the greatest zulm because it misplaces worship."
      ),

      // === WORSHIP AND SPIRITUAL PRACTICE ===
      RootMeaningData(
        root = "ص-ل-و",
        primaryMeaning = "prayer, connection, supplication",
        extendedMeaning = "Implies a close connection and turning towards. The physical movements of salah reflect this turning and connection.",
        quranUsage = "Salah is not just ritual prayer but a direct connection with Allah. The word also means blessings (as in 'salawat' upon the Prophet).",
        notes = null
      ),
      RootMeaningData(
        root = "ذ-ك-ر",
        primaryMeaning = "to remember, to mention, male",
        extendedMeaning = "Remembrance, mention, and masculinity share this root. Dhikr keeps something present in the mind.",
        quranUsage = "Dhikr (remembrance of Allah) is one of the most emphasized acts of worship. The Quran itself is called 'Al-Dhikr'.",
        notes = null
      ),
      RootMeaningData(
        root = "ش-ك-ر",
        primaryMeaning = "to thank, gratitude, to appreciate",
        extendedMeaning = "Recognizing and acknowledging blessings received. Implies action in response to blessings.",
        quranUsage = "Ash-Shakur (The Most Appreciative) is one of Allah's names - He appreciates even small good deeds.",
        notes = "Different from hamd (praise) - shukr is specifically gratitude for blessings received."
      ),
      RootMeaningData(
        root = "س-ج-د",
        primaryMeaning = "to prostrate, to bow down",
        extendedMeaning = "The ultimate physical expression of submission. Placing the highest part of the body (forehead) on the ground.",
        quranUsage = "Sajdah is the position closest to Allah. A masjid (mosque) is a place of prostration.",
        notes = "Even the celestial bodies and all creation are described as making sajdah to Allah."
      ),
      RootMeaningData(
        root = "ص-و-م",
        primaryMeaning = "to fast, to abstain, silence",
        extendedMeaning = "Abstaining from food, drink, and desires. Also means maintaining silence or stillness.",
        quranUsage = "Sawm (fasting) in Ramadan is one of the five pillars. Maryam was commanded to observe a 'fast of silence'.",
        notes = null
      ),
      RootMeaningData(
        root = "ز-ك-و",
        primaryMeaning = "to purify, to grow, charity",
        extendedMeaning = "Purification leads to growth. Zakah purifies wealth and allows it to grow spiritually.",
        quranUsage = "Zakah is obligatory charity that purifies wealth. Tazkiyah is purification of the soul.",
        notes = "The connection between purity and growth shows that spiritual cleansing leads to development."
      ),
      RootMeaningData(
        root = "ح-ج-ج",
        primaryMeaning = "pilgrimage, proof, argument",
        extendedMeaning = "To intend, to visit, to make a compelling argument. Hajj is the ultimate journey of intention.",
        quranUsage = "Hajj is the pilgrimage to Makkah. Hujjah means proof or argument.",
        notes = null
      ),
      RootMeaningData(
        root = "د-ع-و",
        primaryMeaning = "to call, to invite, supplication",
        extendedMeaning = "Calling upon someone, whether in prayer (dua) or in invitation (dawah).",
        quranUsage = "Dua is calling upon Allah. Dawah is calling people to Islam. Both are essential acts of worship.",
        notes = "The Prophet said: 'Dua is the essence of worship.'"
      ),

      // === DIVINE ATTRIBUTES ===
      RootMeaningData(
        root = "ر-ب-ب",
        primaryMeaning = "Lord, master, to nurture, to raise",
        extendedMeaning = "Combines lordship with nurturing - Allah is not just master but the one who raises and develops His creation.",
        quranUsage = "Rabb is one of the most frequent names of Allah in the Quran. It implies ownership, nurturing, and sustaining.",
        notes = "A 'rabbi' in Hebrew shares this Semitic root, meaning 'my master/teacher'."
      ),
      RootMeaningData(
        root = "خ-ل-ق",
        primaryMeaning = "to create, creation, character",
        extendedMeaning = "To bring into existence from nothing. Also refers to one's nature or character (khuluq).",
        quranUsage = "Al-Khaliq (The Creator) is one of Allah's names. The Quran emphasizes creation as proof of Allah's existence.",
        notes = "Good character (husn al-khuluq) is highly emphasized - the Prophet was praised for his 'tremendous character'."
      ),
      RootMeaningData(
        root = "ق-د-ر",
        primaryMeaning = "power, to decree, measure, destiny",
        extendedMeaning = "Divine power to decree and measure all things. Qadr is both power and predestination.",
        quranUsage = "Al-Qadir (The All-Powerful) is one of Allah's names. Laylat al-Qadr is the Night of Decree/Power.",
        notes = "Belief in Qadr (divine decree) is one of the six pillars of faith."
      ),
      RootMeaningData(
        root = "س-م-ع",
        primaryMeaning = "to hear, hearing, to listen",
        extendedMeaning = "Not just physical hearing but comprehension and response. Allah hears all prayers.",
        quranUsage = "As-Sami' (The All-Hearing) is one of Allah's names. 'Sami'a Allahu liman hamidah' - Allah hears those who praise Him.",
        notes = null
      ),
      RootMeaningData(
        root = "ب-ص-ر",
        primaryMeaning = "to see, sight, insight",
        extendedMeaning = "Physical and spiritual sight. Baseerah is spiritual insight or discernment.",
        quranUsage = "Al-Basir (The All-Seeing) is one of Allah's names. The Quran calls for people to reflect and gain baseerah.",
        notes = null
      ),
      RootMeaningData(
        root = "ح-ي-ي",
        primaryMeaning = "to live, life, modesty",
        extendedMeaning = "Life in all its forms. Hayaa (modesty/shyness) is connected to being truly alive spiritually.",
        quranUsage = "Al-Hayy (The Ever-Living) is one of Allah's names. Ihya means to give life or revive.",
        notes = "The Prophet said: 'Hayaa (modesty) is a branch of faith.'"
      ),
      RootMeaningData(
        root = "ق-و-م",
        primaryMeaning = "to stand, to rise, to establish",
        extendedMeaning = "Standing upright, establishing something firmly. A qawm is a people who stand together.",
        quranUsage = "Al-Qayyum (The Self-Subsisting) is one of Allah's names. Iqamah is establishing the prayer. Qiyamah is the Day of Standing (Judgment).",
        notes = "Istiqamah (steadfastness) means to stand firm on the straight path."
      ),
      RootMeaningData(
        root = "و-ح-د",
        primaryMeaning = "one, unity, to be alone",
        extendedMeaning = "Absolute oneness and uniqueness. Tawhid is the foundation of Islamic belief.",
        quranUsage = "Al-Wahid (The One) and Al-Ahad (The Unique) are Allah's names. 'Qul huwa Allahu Ahad' - Say: He is Allah, the One.",
        notes = "Tawhid (monotheism) is the most fundamental concept in Islam."
      ),
      RootMeaningData(
        root = "غ-ف-ر",
        primaryMeaning = "to forgive, to cover, helmet",
        extendedMeaning = "Forgiveness in Arabic is connected to covering - Allah covers sins and protects from their consequences.",
        quranUsage = "Al-Ghafur (The Most Forgiving) and Al-Ghaffar (The Ever-Forgiving) are Allah's names. Istighfar is seeking forgiveness.",
        notes = "A helmet (mighfar) shares this root - it covers and protects the head."
      ),
      RootMeaningData(
        root = "ع-ز-ز",
        primaryMeaning = "might, honor, to be dear",
        extendedMeaning = "Strength, honor, and being precious. True honor comes from Allah alone.",
        quranUsage = "Al-Aziz (The Almighty) is one of Allah's most frequently mentioned names. 'Izzah belongs to Allah, His Messenger, and the believers.'",
        notes = null
      ),
      RootMeaningData(
        root = "ح-ك-م",
        primaryMeaning = "wisdom, judgment, to rule",
        extendedMeaning = "Wisdom that leads to sound judgment and governance. Hikmah is knowledge applied correctly.",
        quranUsage = "Al-Hakim (The All-Wise) is one of Allah's names. The Quran is described as 'hakeem' (wise).",
        notes = "A judge (hakim) and a wise person (hakeem) share this root."
      ),

      // === QURAN AND REVELATION ===
      RootMeaningData(
        root = "ن-ز-ل",
        primaryMeaning = "to descend, to reveal, to send down",
        extendedMeaning = "The downward movement of revelation from heaven to earth. Rain also 'descends' (nazala).",
        quranUsage = "The Quran was 'revealed' (nunzila/anzalna) from Allah. Tanzil refers to the gradual revelation of the Quran.",
        notes = null
      ),
      RootMeaningData(
        root = "و-ح-ي",
        primaryMeaning = "revelation, inspiration, to suggest",
        extendedMeaning = "Divine communication, whether through angels, dreams, or direct inspiration.",
        quranUsage = "Wahy is the revelation received by prophets. The Quran is the final wahy to the final Prophet.",
        notes = "Even bees receive a form of wahy (instinct) from Allah."
      ),
      RootMeaningData(
        root = "ك-ت-ب",
        primaryMeaning = "to write, book, to prescribe",
        extendedMeaning = "Writing, recording, and decreeing. What is written is established.",
        quranUsage = "Al-Kitab (The Book) is a name for the Quran. 'Kutiba' means 'it has been prescribed/written'.",
        notes = "The 'People of the Book' (Ahl al-Kitab) refers to Jews and Christians."
      ),
      RootMeaningData(
        root = "آ-ي-ي",
        primaryMeaning = "sign, verse, miracle",
        extendedMeaning = "A sign that points to something greater. Each verse of the Quran is a sign.",
        quranUsage = "Ayah (pl. ayat) refers to both Quranic verses and signs of Allah in creation. Everything in nature is an ayah.",
        notes = "The word emphasizes that the Quran and creation are both full of signs for those who reflect."
      ),
      RootMeaningData(
        root = "ب-ي-ن",
        primaryMeaning = "to clarify, clear, between",
        extendedMeaning = "Making something clear and distinct. Also means 'between' two things.",
        quranUsage = "The Quran is called 'al-Mubeen' (the Clear). Bayan is clear explanation.",
        notes = null
      ),

      // === MORALITY AND CHARACTER ===
      RootMeaningData(
        root = "ص-د-ق",
        primaryMeaning = "truth, sincerity, to be truthful",
        extendedMeaning = "Truth in speech and action. A siddiq is one who is constantly truthful.",
        quranUsage = "As-Sadiq is a name of the Prophet. Sadaqah (charity) is 'true' giving. Abu Bakr was called 'As-Siddiq'.",
        notes = "Sidq (truthfulness) is considered the foundation of all virtues."
      ),
      RootMeaningData(
        root = "ك-ذ-ب",
        primaryMeaning = "to lie, falsehood, to deny",
        extendedMeaning = "Falsehood in speech. Also means to deny or reject the truth.",
        quranUsage = "The Quran repeatedly warns against kadhib (lying) and those who 'yukadhiboon' (deny) the truth.",
        notes = "Lying is described as one of the signs of hypocrisy."
      ),
      RootMeaningData(
        root = "ص-ب-ر",
        primaryMeaning = "patience, perseverance, to endure",
        extendedMeaning = "Steadfast endurance through difficulty. Not passive waiting but active perseverance.",
        quranUsage = "Sabr is mentioned over 100 times. 'Indeed, Allah is with the patient.' The Prophet Ayyub is the exemplar of sabr.",
        notes = "Sabr comes in three forms: patience in obedience, patience from sin, and patience through trials."
      ),
      RootMeaningData(
        root = "ع-د-ل",
        primaryMeaning = "justice, fairness, balance",
        extendedMeaning = "Giving everything its due right. Perfect balance and equity.",
        quranUsage = "Allah commands 'adl (justice). It appears alongside ihsan (excellence) in the famous verse of Surah An-Nahl.",
        notes = "Justice even toward enemies is commanded: 'Let not hatred prevent you from being just.'"
      ),
      RootMeaningData(
        root = "ح-س-ن",
        primaryMeaning = "good, beautiful, excellence",
        extendedMeaning = "Goodness, beauty, and excellence are unified in this root. True beauty is goodness.",
        quranUsage = "Ihsan is worshipping Allah as if you see Him. Al-Husna refers to Allah's beautiful names. Hasanat are good deeds.",
        notes = "The Prophet said: 'Allah is beautiful and loves beauty.'"
      ),
      RootMeaningData(
        root = "ف-س-ق",
        primaryMeaning = "to transgress, corruption, rebellion",
        extendedMeaning = "Leaving the bounds of obedience. A fasiq is one who transgresses Allah's limits.",
        quranUsage = "Fisq is major disobedience to Allah. The Quran contrasts believers with fasiqeen (transgressors).",
        notes = null
      ),
      RootMeaningData(
        root = "ف-ح-ش",
        primaryMeaning = "obscenity, indecency, excess",
        extendedMeaning = "Going to excess in evil, particularly in matters of modesty and decency.",
        quranUsage = "Fahishah (pl. fawahish) refers to major sins, especially sexual immorality. The Quran forbids even approaching fahishah.",
        notes = null
      ),

      // === AFTERLIFE AND JUDGMENT ===
      RootMeaningData(
        root = "ج-ن-ن",
        primaryMeaning = "to cover, to hide, to be concealed",
        extendedMeaning = "Anything hidden or concealed. Gardens hide what's inside with their foliage.",
        quranUsage = "Jinn are hidden beings. Jannah (paradise) is a hidden garden. Janin (fetus) is hidden in the womb. Junun (madness) is when reason is hidden.",
        notes = "Many seemingly different words share this root through the concept of being hidden."
      ),
      RootMeaningData(
        root = "ن-ر-ر",
        primaryMeaning = "fire, heat",
        extendedMeaning = "Fire in its destructive aspect. The opposite of the coolness and shade of paradise.",
        quranUsage = "An-Nar (The Fire) is a name for Hell. Fire is both punishment and purification in Quranic imagery.",
        notes = null
      ),
      RootMeaningData(
        root = "ح-س-ب",
        primaryMeaning = "to reckon, to count, to suffice",
        extendedMeaning = "Counting and accountability. Also means to be sufficient (hasbunAllah - Allah is sufficient for us).",
        quranUsage = "Al-Hasib is one of Allah's names. Yawm al-Hisab is the Day of Reckoning. 'HasbunAllahu wa ni'mal wakil.'",
        notes = null
      ),
      RootMeaningData(
        root = "ج-ز-ي",
        primaryMeaning = "to reward, to recompense",
        extendedMeaning = "Giving what is deserved, whether reward or punishment. Perfect justice.",
        quranUsage = "Jaza' is the recompense for deeds. 'Is the reward (jaza') of good anything but good?'",
        notes = null
      ),
      RootMeaningData(
        root = "ع-ذ-ب",
        primaryMeaning = "to punish, torment, fresh water",
        extendedMeaning = "Punishment and fresh/sweet water share this root. Perhaps the severity of thirst makes sweet water more appreciated.",
        quranUsage = "Adhab is punishment, particularly in the afterlife. 'Adhab aleem' (painful punishment) is a common phrase.",
        notes = "The contrast between punishment (adhab) and sweet water (adhb) is striking."
      ),
      RootMeaningData(
        root = "ش-ه-د",
        primaryMeaning = "to witness, to testify, martyr",
        extendedMeaning = "Being present and bearing witness. A shahid (martyr) is a witness to faith with their life.",
        quranUsage = "The shahadah is the testimony of faith. Allah is called Ash-Shahid (The Witness). Martyrs are witnesses.",
        notes = "The Prophet will be a witness over the witnesses on the Day of Judgment."
      ),
      RootMeaningData(
        root = "ب-ع-ث",
        primaryMeaning = "to resurrect, to send, to raise",
        extendedMeaning = "Raising from the dead, sending messengers. Both involve bringing forth.",
        quranUsage = "Ba'th is resurrection. Prophets are 'sent' (mab'uth). The Day of Resurrection will raise all people.",
        notes = null
      ),

      // === HUMAN NATURE ===
      RootMeaningData(
        root = "ن-ف-س",
        primaryMeaning = "soul, self, breath",
        extendedMeaning = "The self in all its aspects - the commanding self (ammara), the self-reproaching self (lawwama), and the tranquil self (mutma'inna).",
        quranUsage = "Nafs refers to the human soul. The Quran describes different states of the nafs and the goal of its purification.",
        notes = "The struggle (jihad) against one's nafs is called the 'greater jihad'."
      ),
      RootMeaningData(
        root = "ر-و-ح",
        primaryMeaning = "spirit, soul, to rest",
        extendedMeaning = "The divine breath, spiritual dimension of humans. Also connected to rest and mercy.",
        quranUsage = "Ruh is the spirit. Jibreel is called Ruh al-Qudus (Holy Spirit). Rawh means rest/mercy. 'Do not despair of the rawh of Allah.'",
        notes = "The spirit (ruh) is from Allah's command - its nature is known only to Him."
      ),
      RootMeaningData(
        root = "ع-ق-ل",
        primaryMeaning = "intellect, reason, to understand",
        extendedMeaning = "The faculty of reason that distinguishes humans. Also means to tie or restrain (reason restrains base desires).",
        quranUsage = "The Quran repeatedly asks: 'Afala ta'qiloon?' - Will you not use reason? Reason leads to faith.",
        notes = "A camel's hobble ('iqal) shares this root - reason restrains like a hobble."
      ),
      RootMeaningData(
        root = "ج-ه-ل",
        primaryMeaning = "ignorance, to be ignorant",
        extendedMeaning = "Not just lack of knowledge but actively ignoring truth. Jahiliyyah is the 'age of ignorance'.",
        quranUsage = "Jahl is the opposite of 'ilm (knowledge). The pre-Islamic era is called Jahiliyyah.",
        notes = "True ignorance is not lack of information but denial of evident truth."
      ),

      // === FREQUENTLY USED VERBS ===
      RootMeaningData(
        root = "ق-و-ل",
        primaryMeaning = "to say, speech, word",
        extendedMeaning = "Speaking and words. One of the most frequent roots in the Quran.",
        quranUsage = "'Qul' (Say!) is one of the most common commands in the Quran. Qawl is speech or a statement.",
        notes = "The command 'Qul' appears over 300 times - the Quran is meant to be spoken and shared."
      ),
      RootMeaningData(
        root = "ج-ع-ل",
        primaryMeaning = "to make, to place, to appoint",
        extendedMeaning = "Causing something to be in a certain state or position. Creative transformation.",
        quranUsage = "Allah 'ja'ala' (made/appointed) things in their places. Very frequent in describing Allah's creative acts.",
        notes = null
      ),
      RootMeaningData(
        root = "أ-ت-ي",
        primaryMeaning = "to come, to bring",
        extendedMeaning = "Coming or bringing. Can be physical or metaphorical (bringing news, revelation, etc.).",
        quranUsage = "Frequently used for revelation 'coming' and for people 'coming' on the Day of Judgment.",
        notes = null
      ),
      RootMeaningData(
        root = "ك-و-ن",
        primaryMeaning = "to be, existence, universe",
        extendedMeaning = "Being and existence. 'Kun' (Be!) is Allah's creative command.",
        quranUsage = "'Kun fayakun' - Be! And it is. Allah's command brings things into existence instantly.",
        notes = "The universe is called 'al-kawn' - all that has come to be."
      ),
      RootMeaningData(
        root = "ع-م-ل",
        primaryMeaning = "to do, to work, deed",
        extendedMeaning = "Action and deeds. Islam emphasizes that faith must be accompanied by righteous action.",
        quranUsage = "'Alladhina amanu wa 'amilu as-salihat' - Those who believe and do righteous deeds. Faith and action are always paired.",
        notes = "A'mal (deeds) will be weighed on the Day of Judgment."
      ),

      // === BLESSINGS AND PROVISION ===
      RootMeaningData(
        root = "ن-ع-م",
        primaryMeaning = "blessing, favor, comfort",
        extendedMeaning = "Blessings and favors that bring comfort and ease. Everything good is a ni'mah.",
        quranUsage = "'If you count Allah's blessings (ni'am), you cannot enumerate them.' An'am are livestock (blessings from animals).",
        notes = "Na'im is the state of bliss in paradise."
      ),
      RootMeaningData(
        root = "ر-ز-ق",
        primaryMeaning = "provision, sustenance, to provide",
        extendedMeaning = "All forms of provision - food, wealth, knowledge, children, guidance. All rizq comes from Allah.",
        quranUsage = "Ar-Razzaq (The Provider) is one of Allah's names. Rizq is guaranteed but requires seeking (with trust in Allah).",
        notes = "Rizq includes not just material but spiritual sustenance."
      ),
      RootMeaningData(
        root = "ف-ض-ل",
        primaryMeaning = "favor, bounty, excellence, to prefer",
        extendedMeaning = "Extra favor beyond what is deserved. Allah's fadl is His grace and generosity.",
        quranUsage = "Seeking Allah's fadl (bounty) is encouraged. Some prophets were given fadl (preference) over others.",
        notes = null
      ),
      RootMeaningData(
        root = "ب-ر-ك",
        primaryMeaning = "blessing, increase, to kneel",
        extendedMeaning = "Divine blessing that causes growth and increase. A camel kneels (baraka) to receive its load - blessings come through submission.",
        quranUsage = "Barakah is Allah's blessing. 'Tabaarak' (blessed be) glorifies Allah. Mubarak means blessed.",
        notes = "The connection between kneeling and blessing shows that blessings come through humble submission."
      ),

      // === PATHS AND GUIDANCE ===
      RootMeaningData(
        root = "ص-ر-ط",
        primaryMeaning = "path, way, road",
        extendedMeaning = "A clear, straight path. The main road that leads to the destination.",
        quranUsage = "As-Sirat al-Mustaqim (The Straight Path) is asked for in every prayer. It is the path of guidance.",
        notes = "Sirat also refers to the bridge over Hell that all must cross on Judgment Day."
      ),
      RootMeaningData(
        root = "س-ب-ل",
        primaryMeaning = "way, path, means",
        extendedMeaning = "A path or means to reach something. Can be physical road or metaphorical way.",
        quranUsage = "'Fi sabilillah' (in the way of Allah) refers to striving for Allah's cause. Ibn as-sabil is a traveler.",
        notes = null
      ),
      RootMeaningData(
        root = "ض-ل-ل",
        primaryMeaning = "to go astray, misguidance, to err",
        extendedMeaning = "Losing the path, going astray. The opposite of guidance (huda).",
        quranUsage = "Dalal is misguidance. 'Ghayri al-maghdubi alayhim wa la ad-dalleen' - not the path of those who went astray.",
        notes = null
      ),

      // === OPENING AND VICTORY ===
      RootMeaningData(
        root = "ف-ت-ح",
        primaryMeaning = "to open, victory, conquest, to begin",
        extendedMeaning = "Opening can be physical (door) or abstract (victory, beginning). Fatiha opens the Quran.",
        quranUsage = "Al-Fattah (The Opener) is one of Allah's names. Surah Al-Fatiha 'opens' the Quran. Fath also means victory (Surah Al-Fath).",
        notes = null
      ),
      RootMeaningData(
        root = "ن-ص-ر",
        primaryMeaning = "help, victory, support",
        extendedMeaning = "Divine assistance that leads to victory. The Ansar were the 'helpers' of the Prophet.",
        quranUsage = "Nasr is victory from Allah. 'Idha ja'a nasrullahi wal fath' - When Allah's victory comes.",
        notes = null
      ),
      RootMeaningData(
        root = "ف-ل-ح",
        primaryMeaning = "success, prosperity, to cultivate",
        extendedMeaning = "Ultimate success, both in this life and the next. Originally meant tilling the soil for growth.",
        quranUsage = "'Qad aflaha' - Certainly successful are... The call to prayer includes 'Hayya 'ala al-falah' - Come to success!",
        notes = "True success (falah) is defined by the Quran as success in the Hereafter."
      ),
      RootMeaningData(
        root = "خ-س-ر",
        primaryMeaning = "loss, to lose, failure",
        extendedMeaning = "Loss in trade and in the Hereafter. The opposite of profit and success.",
        quranUsage = "'Wal-'Asr, inna al-insana lafi khusr' - By time, mankind is in loss. Surah Al-Asr defines what saves from loss.",
        notes = null
      ),

      // === CREATION AND NATURE ===
      RootMeaningData(
        root = "أ-ر-ض",
        primaryMeaning = "earth, land, ground",
        extendedMeaning = "The earth as dwelling place, land, territory. Often paired with heavens (samawat).",
        quranUsage = "Al-Ard (earth) is mentioned frequently, often with 'samawat' (heavens) to denote all creation.",
        notes = null
      ),
      RootMeaningData(
        root = "م-و-ت",
        primaryMeaning = "death, to die",
        extendedMeaning = "The end of worldly life and transition to the next realm. Every soul shall taste death.",
        quranUsage = "Mawt (death) is certain. 'Every soul shall taste death.' Death is described as a return to Allah.",
        notes = "The Quran uses death as a reminder to motivate righteous action."
      ),
      RootMeaningData(
        root = "ي-و-م",
        primaryMeaning = "day, time period",
        extendedMeaning = "A day or any period of time. Can refer to 24 hours or cosmic eras.",
        quranUsage = "Yawm al-Qiyamah (Day of Resurrection), Yawm ad-Din (Day of Judgment). The 'days' of creation may be eras.",
        notes = "A 'day' with Allah can be 1,000 or 50,000 years by human reckoning."
      ),

      // === WORSHIP CONCEPTS ===
      RootMeaningData(
        root = "ش-ر-ك",
        primaryMeaning = "to associate, partner, to share",
        extendedMeaning = "Partnership in worship - giving Allah's rights to others. The greatest sin.",
        quranUsage = "Shirk is associating partners with Allah - the only unforgivable sin if one dies upon it. A mushrik is a polytheist.",
        notes = "Even minor shirk (riya - showing off in worship) is warned against."
      ),
      RootMeaningData(
        root = "ط-ه-ر",
        primaryMeaning = "to purify, cleanliness, purity",
        extendedMeaning = "Physical and spiritual cleanliness. Outer purity reflects inner purity.",
        quranUsage = "Tahara (purification) is required for prayer. Hearts can be 'pure' (tahir). Mutahharun are the purified.",
        notes = "The Prophet said: 'Cleanliness is half of faith.'"
      ),
      RootMeaningData(
        root = "ق-ن-ت",
        primaryMeaning = "devotion, obedience, standing in prayer",
        extendedMeaning = "Humble devotion and prolonged standing in worship. Qunut is devoted obedience.",
        quranUsage = "Qanit describes one who is devoutly obedient. Qunut prayers are prayers of humble supplication.",
        notes = null
      ),
      RootMeaningData(
        root = "ت-ق-و",
        primaryMeaning = "God-consciousness, piety, to protect oneself",
        extendedMeaning = "Protecting oneself from Allah's displeasure through mindfulness of Him. Awareness that leads to obedience.",
        quranUsage = "Taqwa is one of the most emphasized qualities. 'The best provision is taqwa.' The muttaqeen are the God-conscious.",
        notes = "Often translated as 'fear of Allah' but better understood as protective awareness of Allah."
      ),
      RootMeaningData(
        root = "خ-ش-ع",
        primaryMeaning = "humility, to be humble, submissive",
        extendedMeaning = "Deep humility and submission, especially in worship. The stillness of awe.",
        quranUsage = "Khushu' in prayer is praised. The earth becomes 'khashi'ah' (humble/barren) before rain revives it.",
        notes = "Khushu' is the heart's submission that shows in the body's stillness."
      ),

      // === PROPHETS AND MESSENGERS ===
      RootMeaningData(
        root = "ر-س-ل",
        primaryMeaning = "to send, messenger, message",
        extendedMeaning = "Sending someone with a mission. A rasul carries a message from the sender.",
        quranUsage = "Rasul is a messenger sent by Allah with scripture and law. 'Muhammad is the Messenger (Rasul) of Allah.'",
        notes = "Every rasul is a nabi (prophet), but not every nabi is a rasul."
      ),
      RootMeaningData(
        root = "ن-ب-ء",
        primaryMeaning = "news, prophecy, to inform",
        extendedMeaning = "Important news or tidings. A nabi receives and conveys divine news.",
        quranUsage = "Nabi (prophet) is one who receives news from Allah. An-Naba (The News) is Surah 78 about the Day of Judgment.",
        notes = "The root emphasizes the informative aspect of prophethood."
      ),
      RootMeaningData(
        root = "و-ص-ي",
        primaryMeaning = "to advise, to bequeath, commandment",
        extendedMeaning = "Giving important advice or instructions, especially as a legacy.",
        quranUsage = "Wasiyyah is a bequest or will. Allah 'commands' (yusi) certain things. The prophets gave wasiyyah to their children.",
        notes = "Ibrahim's wasiyyah to his sons was to remain Muslim until death."
      ),

      // === FEAR, HOPE AND EMOTIONS ===
      RootMeaningData(
        root = "خ-و-ف",
        primaryMeaning = "fear, to be afraid",
        extendedMeaning = "Fear of something harmful or dangerous. Can be healthy fear that protects.",
        quranUsage = "Fear of Allah (khawf) is praised when balanced with hope. 'They call upon their Lord in fear and hope.'",
        notes = "Khawf should lead to action, not paralysis."
      ),
      RootMeaningData(
        root = "ر-ج-و",
        primaryMeaning = "hope, to expect, to anticipate",
        extendedMeaning = "Expectation of something good. Hope that motivates action.",
        quranUsage = "Raja' (hope) in Allah's mercy is essential. 'Whoever hopes to meet his Lord, let him do righteous deeds.'",
        notes = "Hope without action is mere wishful thinking (umniyyah)."
      ),
      RootMeaningData(
        root = "ح-ز-ن",
        primaryMeaning = "sadness, grief, to grieve",
        extendedMeaning = "Emotional pain from loss or difficulty. Natural human emotion.",
        quranUsage = "Ya'qub's grief for Yusuf turned his eyes white. 'Do not grieve (la tahzan), indeed Allah is with us.'",
        notes = "The Prophet experienced huzn but was told not to let it overwhelm him."
      ),
      RootMeaningData(
        root = "ف-ر-ح",
        primaryMeaning = "joy, happiness, to rejoice",
        extendedMeaning = "Happiness and rejoicing. Can be praiseworthy or blameworthy depending on its cause.",
        quranUsage = "Rejoicing in Allah's bounty is good. Excessive pride (farah) in worldly things is criticized.",
        notes = "Qarun's arrogant rejoicing led to his destruction."
      ),
      RootMeaningData(
        root = "غ-ض-ب",
        primaryMeaning = "anger, wrath, to be angry",
        extendedMeaning = "Strong displeasure. Divine anger is just, human anger should be controlled.",
        quranUsage = "'Not those who earned Your anger (ghadab)' in Al-Fatihah. Allah's wrath is upon persistent sinners.",
        notes = "The Prophet advised: 'Do not be angry' repeatedly."
      ),
      RootMeaningData(
        root = "ح-ب-ب",
        primaryMeaning = "love, to love, seed",
        extendedMeaning = "Deep affection and attachment. The seed of the heart's attachment.",
        quranUsage = "Hubb (love) of Allah should be supreme. 'Those who believe are stronger in love for Allah.'",
        notes = "Habib means beloved. The Prophet is Habibullah (Beloved of Allah)."
      ),
      RootMeaningData(
        root = "ب-غ-ض",
        primaryMeaning = "hatred, to hate, enmity",
        extendedMeaning = "Strong dislike or aversion. The opposite of love.",
        quranUsage = "Bughd (hatred) for the sake of Allah is part of faith. Hatred of truth leads to misguidance.",
        notes = "Hating what Allah hates is part of complete faith."
      ),

      // === COMMANDS AND ACTIONS ===
      RootMeaningData(
        root = "أ-م-ر",
        primaryMeaning = "to command, affair, matter",
        extendedMeaning = "Giving orders, any affair or matter. Divine command shapes existence.",
        quranUsage = "Amr is Allah's command. 'His command (amr) when He wills something is only to say Be, and it is.'",
        notes = "Ameer (commander) and umoor (affairs) share this root."
      ),
      RootMeaningData(
        root = "ن-ه-ي",
        primaryMeaning = "to forbid, prohibition, intellect",
        extendedMeaning = "Prohibiting something. The intellect (nuha) forbids evil actions.",
        quranUsage = "Allah prohibits (yanha) immorality and wrongdoing. 'Ulul albab' are people of understanding.",
        notes = "The intellect's role is to 'forbid' base desires."
      ),
      RootMeaningData(
        root = "أ-خ-ذ",
        primaryMeaning = "to take, to seize, to hold",
        extendedMeaning = "Taking hold of something physically or metaphorically.",
        quranUsage = "'Take what We have given you with strength.' Allah 'seizes' wrongdoers in punishment.",
        notes = "Akhdhah is Allah's seizing punishment of nations."
      ),
      RootMeaningData(
        root = "ع-ط-و",
        primaryMeaning = "to give, gift, granting",
        extendedMeaning = "Giving generously. Allah is the ultimate giver.",
        quranUsage = "'Your Lord will give you and you will be satisfied.' 'Ata' (gift) from Allah.",
        notes = "Al-Mu'ti (The Giver) is among Allah's names in hadith."
      ),
      RootMeaningData(
        root = "ت-ر-ك",
        primaryMeaning = "to leave, to abandon, inheritance",
        extendedMeaning = "Leaving something behind, whether abandoning or bequeathing.",
        quranUsage = "Tarikah is inheritance left behind. 'Do not leave any of them on earth' (Nuh's prayer).",
        notes = null
      ),
      RootMeaningData(
        root = "ر-ج-ع",
        primaryMeaning = "to return, to go back",
        extendedMeaning = "Returning to an origin or previous state. All return to Allah.",
        quranUsage = "'Indeed to Allah we belong and to Him we return (raji'un).' Raj'ah is the return.",
        notes = "Death is described as returning to Allah, the origin."
      ),

      // === WATER AND NATURE ===
      RootMeaningData(
        root = "م-ط-ر",
        primaryMeaning = "rain, to rain",
        extendedMeaning = "Rain that brings life. Can also refer to harmful rain (punishment).",
        quranUsage = "Matar is rain. 'We sent down rain (matar)' for life. Punishment also 'rained' on sinful nations.",
        notes = "Rain symbolizes both mercy and punishment in the Quran."
      ),
      RootMeaningData(
        root = "م-ء-ء",
        primaryMeaning = "water",
        extendedMeaning = "The essential element of life. Everything living is made from water.",
        quranUsage = "'We made from water every living thing.' Ma' (water) is mentioned as essential for life.",
        notes = "Water is one of Allah's greatest blessings."
      ),
      RootMeaningData(
        root = "ب-ح-ر",
        primaryMeaning = "sea, ocean, vastness",
        extendedMeaning = "Large body of water. Symbolizes vastness and depth.",
        quranUsage = "Bahr (sea) appears in stories of Musa and others. 'If the sea were ink for the words of my Lord...'",
        notes = "Bahrain means 'two seas' - fresh and salt water."
      ),
      RootMeaningData(
        root = "ن-ه-ر",
        primaryMeaning = "river, to flow, daylight",
        extendedMeaning = "Flowing water. Rivers symbolize continuous blessing.",
        quranUsage = "Anhar (rivers) flow beneath the gardens of paradise. Nahar also means daytime.",
        notes = "Paradise is described with rivers of water, milk, honey, and wine."
      ),
      RootMeaningData(
        root = "ش-ج-ر",
        primaryMeaning = "tree, to dispute",
        extendedMeaning = "Trees with intertwining branches. Disputes involve intertwining arguments.",
        quranUsage = "Shajarah is tree - including the forbidden tree. 'Shajara' also means to dispute.",
        notes = "The tree of Zaqqum in Hell contrasts with trees of Paradise."
      ),
      RootMeaningData(
        root = "ث-م-ر",
        primaryMeaning = "fruit, produce, result",
        extendedMeaning = "The product of growth. Can be physical or metaphorical fruits.",
        quranUsage = "Thamar (fruit) of Paradise and worldly gardens. Good deeds also bear fruit.",
        notes = null
      ),

      // === MORE DIVINE ATTRIBUTES ===
      RootMeaningData(
        root = "ك-ب-ر",
        primaryMeaning = "greatness, to be great, pride",
        extendedMeaning = "Magnitude and greatness. Only Allah deserves takabbur (pride in greatness).",
        quranUsage = "Allahu Akbar - Allah is Greater. Al-Mutakabbir (The Supreme) is Allah's name. Kibr (arrogance) in humans is condemned.",
        notes = "Human kibr (arrogance) is the sin of Iblis."
      ),
      RootMeaningData(
        root = "ج-ل-ل",
        primaryMeaning = "majesty, glory, magnificence",
        extendedMeaning = "Overwhelming greatness and majesty that inspires awe.",
        quranUsage = "Dhul-Jalali wal-Ikram - Owner of Majesty and Honor. Jalal is Allah's majestic aspect.",
        notes = "Jalal (majesty) complements Jamal (beauty) in describing Allah."
      ),
      RootMeaningData(
        root = "ل-ط-ف",
        primaryMeaning = "subtlety, kindness, gentle",
        extendedMeaning = "Subtle kindness, gentle care. Perceiving and handling delicate matters.",
        quranUsage = "Al-Latif (The Subtle) is one of Allah's names. Allah is latif (gentle/subtle) with His servants.",
        notes = "Lutf is kindness so subtle it may go unnoticed."
      ),
      RootMeaningData(
        root = "و-د-د",
        primaryMeaning = "love, affection, to love",
        extendedMeaning = "Loving affection and friendship. Desire for closeness.",
        quranUsage = "Al-Wadud (The Loving) is one of Allah's names. 'My Lord is merciful and loving (wadud).'",
        notes = "Mawaddah is the deep affection between spouses."
      ),
      RootMeaningData(
        root = "ح-ل-م",
        primaryMeaning = "forbearance, dream, puberty",
        extendedMeaning = "Self-restraint despite having power. Also dreams and maturity.",
        quranUsage = "Al-Halim (The Forbearing) is Allah's name. Ibrahim was described as halim (forbearing).",
        notes = "Hilm is restraining anger when one has power to act on it."
      ),
      RootMeaningData(
        root = "ص-م-د",
        primaryMeaning = "eternal, self-sufficient, solid",
        extendedMeaning = "The one to whom all turn in need, yet needs nothing from anyone.",
        quranUsage = "As-Samad appears in Surah Al-Ikhlas - Allah is As-Samad, the Eternal Refuge.",
        notes = "Samad implies Allah is sought by all but needs none."
      ),
      RootMeaningData(
        root = "غ-ن-ي",
        primaryMeaning = "rich, self-sufficient, free from need",
        extendedMeaning = "Having no needs, completely independent. True wealth is contentment.",
        quranUsage = "Al-Ghani (The Self-Sufficient) is Allah's name. 'Allah is the Rich, you are the poor.'",
        notes = "Ghina of the heart (contentment) is true richness."
      ),

      // === FAMILY AND RELATIONSHIPS ===
      RootMeaningData(
        root = "ز-و-ج",
        primaryMeaning = "spouse, pair, to marry",
        extendedMeaning = "Pairing and coupling. Everything is created in pairs.",
        quranUsage = "'We created you in pairs.' Zawj is spouse. Paradise promises pure azwaj (spouses).",
        notes = "The concept of pairs extends to all creation."
      ),
      RootMeaningData(
        root = "و-ل-د",
        primaryMeaning = "child, to give birth, offspring",
        extendedMeaning = "Children and the process of generation. Lineage and progeny.",
        quranUsage = "Walad is child. 'Allah has not taken a son (walad).' Awlad are offspring.",
        notes = "Children are described as both blessing and trial."
      ),
      RootMeaningData(
        root = "أ-خ-و",
        primaryMeaning = "brother, brotherhood",
        extendedMeaning = "Brotherhood by blood or faith. Deep bond between people.",
        quranUsage = "Akh is brother. 'The believers are but brothers (ikhwah).' Prophets called their people 'my brothers.'",
        notes = "Islamic brotherhood transcends blood relations."
      ),
      RootMeaningData(
        root = "أ-ب-و",
        primaryMeaning = "father, ancestor",
        extendedMeaning = "Father, forefather, or anyone in paternal role.",
        quranUsage = "Ab is father. Ibrahim is called 'father (ab) of the prophets.' 'The religion of your father Ibrahim.'",
        notes = "Abu in names means 'father of'."
      ),
      RootMeaningData(
        root = "أ-م-م",
        primaryMeaning = "mother, nation, source",
        extendedMeaning = "Mother, nation (that shares a common origin), or source/foundation.",
        quranUsage = "Umm is mother. Ummah is nation/community. Umm al-Kitab is the Mother of the Book (Al-Fatihah or Preserved Tablet).",
        notes = "Makkah is called Umm al-Qura (Mother of Cities)."
      ),
      RootMeaningData(
        root = "أ-ه-ل",
        primaryMeaning = "family, people, worthy",
        extendedMeaning = "Family, inhabitants, or those qualified for something.",
        quranUsage = "Ahl al-Kitab (People of the Book). Ahl al-Bayt (Household of the Prophet). 'Ahl' of a place are its people.",
        notes = "Ahliyyah means qualification or competence."
      ),
      RootMeaningData(
        root = "ق-ر-ب",
        primaryMeaning = "nearness, closeness, sacrifice",
        extendedMeaning = "Being close in distance or relationship. Qurban brings one closer to Allah.",
        quranUsage = "Qurban is sacrifice that brings nearness to Allah. Aqrabun are close relatives. 'Prostrate and draw near (iqtarib).'",
        notes = "The nearest to Allah are the muttaqeen."
      ),

      // === GOOD AND EVIL ===
      RootMeaningData(
        root = "خ-ي-ر",
        primaryMeaning = "good, better, to choose",
        extendedMeaning = "Goodness and what is preferable. Choice implies selecting the better option.",
        quranUsage = "Khayr is good. 'Whatever good you do, Allah knows it.' Allah chooses (yakhtaru) whom He wills.",
        notes = "Ikhtyar (choice) and khayr (good) share this root."
      ),
      RootMeaningData(
        root = "ش-ر-ر",
        primaryMeaning = "evil, harm, wickedness",
        extendedMeaning = "Evil and what causes harm. The opposite of khayr.",
        quranUsage = "'Say: I seek refuge in the Lord of daybreak, from the evil (sharr) of what He created.'",
        notes = "Sharr can be active evil or potential harm."
      ),
      RootMeaningData(
        root = "س-و-ء",
        primaryMeaning = "evil, bad, ugliness",
        extendedMeaning = "What is bad or ugly, whether in action or appearance.",
        quranUsage = "Sayyi'ah is a bad deed. Su' is evil. 'Repel evil (sayyi'ah) with what is better.'",
        notes = "Sayyi'at are bad deeds that will be weighed against hasanat."
      ),
      RootMeaningData(
        root = "ف-س-د",
        primaryMeaning = "corruption, to corrupt, decay",
        extendedMeaning = "Corruption and spoiling. Can be moral, physical, or social corruption.",
        quranUsage = "Fasad (corruption) appears on land and sea because of what people's hands have earned. Mufsideen are corrupters.",
        notes = "Islah (reform) is the opposite of ifsad (corruption)."
      ),
      RootMeaningData(
        root = "ص-ل-ح",
        primaryMeaning = "righteousness, reform, to be good",
        extendedMeaning = "Being good, making peace, reforming what is corrupt.",
        quranUsage = "Salih means righteous. Amal salih is righteous deed. Islah is reconciliation/reform.",
        notes = "The Prophet Salih was named for righteousness."
      ),

      // === BELIEF AND DISBELIEF ===
      RootMeaningData(
        root = "ن-ف-ق",
        primaryMeaning = "to spend, hypocrisy, tunnel",
        extendedMeaning = "Spending (infaq) and hypocrisy (nifaq) share a root - hypocrites have two openings like a tunnel.",
        quranUsage = "Munafiq is hypocrite - one with two faces. Infaq is spending in Allah's cause. An-Nafaqah is expenditure.",
        notes = "A tunnel (nafaq) has two openings, like a hypocrite's two-faced nature."
      ),
      RootMeaningData(
        root = "ر-ي-ب",
        primaryMeaning = "doubt, suspicion, to doubt",
        extendedMeaning = "Doubt that unsettles and disturbs. Suspicion about truth.",
        quranUsage = "'This is the Book in which there is no doubt (rayb).' Rayb is unsettling doubt.",
        notes = "Different from shakk - rayb implies disturbing suspicion."
      ),
      RootMeaningData(
        root = "ي-ق-ن",
        primaryMeaning = "certainty, to be certain",
        extendedMeaning = "Absolute certainty that removes all doubt. The highest level of knowledge.",
        quranUsage = "Yaqeen is certainty. 'Worship your Lord until certainty (yaqeen) comes to you.' Three levels: 'ilm, 'ayn, haqq al-yaqeen.",
        notes = "Yaqeen often refers to death - the certainty that comes to all."
      ),
      RootMeaningData(
        root = "ظ-ن-ن",
        primaryMeaning = "assumption, to think, suspect",
        extendedMeaning = "Assumption or supposition. Can be positive (good opinion) or negative (suspicion).",
        quranUsage = "'Avoid much assumption (zann), indeed some assumption is sin.' Husn al-zann is thinking well of others.",
        notes = "Zann of Allah should always be positive."
      ),

      // === TIME CONCEPTS ===
      RootMeaningData(
        root = "و-ق-ت",
        primaryMeaning = "time, appointed time",
        extendedMeaning = "A specific, appointed time. Everything has its decreed moment.",
        quranUsage = "Waqt is appointed time. Mawaqeet are fixed times (for prayer, Hajj). Miqat is the appointed place/time.",
        notes = "Prayer times are mawaqeet - fixed appointments with Allah."
      ),
      RootMeaningData(
        root = "أ-ب-د",
        primaryMeaning = "eternity, forever",
        extendedMeaning = "Endless time stretching into the future. Permanence without end.",
        quranUsage = "Abadan means forever. 'Dwelling therein forever (abadan)' describes Paradise and Hell.",
        notes = "Abad is future eternity, azal is past eternity."
      ),
      RootMeaningData(
        root = "أ-خ-ر",
        primaryMeaning = "last, other, to delay",
        extendedMeaning = "What comes after, the other, or delaying something.",
        quranUsage = "Al-Akhirah is the Hereafter (the last abode). Ukhra means other. Ta'khir is delay.",
        notes = "The opposite of awwal (first) and dunya (this world)."
      ),
      RootMeaningData(
        root = "أ-و-ل",
        primaryMeaning = "first, beginning, to return",
        extendedMeaning = "What comes first, the beginning, or returning to origin.",
        quranUsage = "Al-Awwal (The First) is Allah's name. Awwalun are the first generations. Ta'wil is interpretation (returning to origin).",
        notes = "Allah is Al-Awwal (no beginning) and Al-Akhir (no end)."
      ),
      RootMeaningData(
        root = "د-ه-ر",
        primaryMeaning = "time, fate, age",
        extendedMeaning = "Long stretches of time, or fate/destiny. The passage of eras.",
        quranUsage = "'Has there come upon man a period (dahr) when he was nothing?' The dahr is time that passes.",
        notes = "The Prophet forbade cursing 'dahr' as Allah controls time."
      ),

      // === BODY AND SENSES ===
      RootMeaningData(
        root = "ي-د-د",
        primaryMeaning = "hand, power, possession",
        extendedMeaning = "Hand as symbol of power, ability, and possession.",
        quranUsage = "Yad (hand) often means power. 'The hand (yad) of Allah is over their hands.' Biyadihi (in His hand) is dominion.",
        notes = "Giving with both hands symbolizes generosity."
      ),
      RootMeaningData(
        root = "ع-ي-ن",
        primaryMeaning = "eye, spring, essence",
        extendedMeaning = "Eye for seeing, spring of water, or the essence/reality of something.",
        quranUsage = "'Ayn is eye. 'Running springs ('uyun)' in Paradise. 'Ain also means the exact thing itself.",
        notes = "The evil eye ('ayn) is real according to hadith."
      ),
      RootMeaningData(
        root = "و-ج-ه",
        primaryMeaning = "face, direction, to turn toward",
        extendedMeaning = "Face as the front and identity. Direction one faces.",
        quranUsage = "Wajh (face) of Allah represents His essence. 'Wherever you turn, there is the Face (wajh) of Allah.'",
        notes = "Tawajjuh is turning one's face/attention toward something."
      ),
      RootMeaningData(
        root = "ل-س-ن",
        primaryMeaning = "tongue, language",
        extendedMeaning = "Tongue as organ of speech and language itself.",
        quranUsage = "Lisan is tongue/language. 'We sent every messenger in the language (lisan) of his people.'",
        notes = "Lisan al-'Arab is the Arabic language."
      ),
      RootMeaningData(
        root = "ص-د-ر",
        primaryMeaning = "chest, breast, to issue from",
        extendedMeaning = "Chest as seat of emotions and secrets. Also means to originate from.",
        quranUsage = "Sadr (chest) contains the heart's secrets. 'Has He not expanded your chest (sadr)?' Sadara means to emanate.",
        notes = "Sharh al-sadr (expansion of chest) means relief and acceptance."
      ),

      // === SPEAKING AND COMMUNICATION ===
      RootMeaningData(
        root = "ك-ل-م",
        primaryMeaning = "to speak, word, to wound",
        extendedMeaning = "Speech and words. Also means to wound (words can wound).",
        quranUsage = "Kalam is speech. Kalimah is word. Allah spoke (kallama) to Musa directly. 'Be (kun)' is a kalimah.",
        notes = "Kalam Allah is Allah's speech - the Quran is His words."
      ),
      RootMeaningData(
        root = "ن-ط-ق",
        primaryMeaning = "to speak, to articulate, logic",
        extendedMeaning = "Clear, articulate speech. The faculty of reason and expression.",
        quranUsage = "Nutq is articulate speech. 'He does not speak (yantiq) from desire.' Mantiq is logic.",
        notes = "Humans are 'hayawan natiq' - speaking/rational animals."
      ),
      RootMeaningData(
        root = "س-ء-ل",
        primaryMeaning = "to ask, question, request",
        extendedMeaning = "Asking questions or making requests. Seeking something from someone.",
        quranUsage = "Su'al is question. 'Ask (sal) the People of the Book.' 'Ask Allah from His bounty.'",
        notes = "Mas'alah is a question or issue to be resolved."
      ),
      RootMeaningData(
        root = "ج-و-ب",
        primaryMeaning = "to answer, response, to pierce",
        extendedMeaning = "Answering a call or question. Response that penetrates.",
        quranUsage = "Jawab is answer. 'Call upon Me, I will answer (astajib) you.' Istijabah is responding.",
        notes = "Dua requires response (ijabah) from Allah."
      ),

      // === MOVEMENT AND DIRECTION ===
      RootMeaningData(
        root = "م-ش-ي",
        primaryMeaning = "to walk, to go",
        extendedMeaning = "Walking and movement by foot. Proceeding through life.",
        quranUsage = "'Walk (imshi) in the earth and eat of His provision.' Mashsha'un are those who walk.",
        notes = "Walking humbly is praised; walking arrogantly condemned."
      ),
      RootMeaningData(
        root = "ر-ك-ب",
        primaryMeaning = "to ride, to mount, knee",
        extendedMeaning = "Riding or mounting. Also the knee (used in mounting).",
        quranUsage = "'You will surely ride (tarkabunna) stage after stage.' Markab is a vessel/vehicle.",
        notes = "Rukba (knee) shares this root."
      ),
      RootMeaningData(
        root = "ط-ي-ر",
        primaryMeaning = "bird, to fly, omen",
        extendedMeaning = "Birds and flying. Also omens (Arabs read omens from bird flight).",
        quranUsage = "Tayr (bird) appears often. Sulaiman spoke to birds. 'Their fate (ta'ir) is with Allah' - their omen/destiny.",
        notes = "Tatayyur (seeking omens) is prohibited in Islam."
      ),
      RootMeaningData(
        root = "ق-ع-د",
        primaryMeaning = "to sit, to stay behind",
        extendedMeaning = "Sitting down, remaining behind, or being inactive.",
        quranUsage = "Qa'ada means to sit/stay. Qa'idun are those who stay behind from jihad. Qa'idah is foundation (what things sit on).",
        notes = "Maq'ad is a seat or sitting place."
      ),

      // === SOCIAL AND POLITICAL ===
      RootMeaningData(
        root = "م-ل-ك",
        primaryMeaning = "king, to own, kingdom",
        extendedMeaning = "Ownership, sovereignty, and kingship. Ultimate ownership belongs to Allah.",
        quranUsage = "Al-Malik (The King) is Allah's name. Mulk is kingdom/dominion. Malak (angel) may share this root.",
        notes = "Surah Al-Mulk discusses Allah's dominion over creation."
      ),
      RootMeaningData(
        root = "ح-ر-ر",
        primaryMeaning = "freedom, free, heat",
        extendedMeaning = "Being free (not enslaved), heat, or writing. A hurr is a free person.",
        quranUsage = "Tahrir raqabah is freeing a slave. Harr is heat. Huruf are letters (freed/released sounds).",
        notes = "Freedom from slavery is highly encouraged in Islam."
      ),
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "to gather, collect, Friday",
        extendedMeaning = "Bringing together, collecting. Unity and congregation.",
        quranUsage = "Jama'ah is congregation. Jumu'ah (Friday) is the day of gathering. Yawm al-Jam' is Day of Gathering.",
        notes = "Jami' (mosque) is where people gather."
      ),
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "to separate, group, criterion",
        extendedMeaning = "Separating and distinguishing. A firqah is a separated group/sect.",
        quranUsage = "Al-Furqan (The Criterion) distinguishes truth from falsehood. Tafarruq (division) in religion is condemned.",
        notes = "The Quran is called Al-Furqan as it separates truth from falsehood."
      ),
      RootMeaningData(
        root = "ق-س-م",
        primaryMeaning = "to divide, oath, portion",
        extendedMeaning = "Dividing into portions. An oath divides truth from falsehood.",
        quranUsage = "Qasam is oath. Allah takes oaths (uqsimu) by His creation. Qismah is portion/lot in life.",
        notes = "The Quran contains many divine oaths."
      ),

      // === KNOWLEDGE AND TEACHING ===
      RootMeaningData(
        root = "ف-ق-ه",
        primaryMeaning = "understanding, jurisprudence",
        extendedMeaning = "Deep understanding of religion and law. Comprehension beyond surface knowledge.",
        quranUsage = "'That they may obtain understanding (yatafaqqahu) in religion.' Fiqh is Islamic jurisprudence.",
        notes = "A faqih is a scholar of Islamic law."
      ),
      RootMeaningData(
        root = "ف-ك-ر",
        primaryMeaning = "to think, reflection, thought",
        extendedMeaning = "Deep thinking and reflection. Mental processing of ideas.",
        quranUsage = "Tafakkur (reflection) is commanded repeatedly. 'Will they not reflect (yatafakkarun)?'",
        notes = "Thinking about Allah's creation leads to faith."
      ),
      RootMeaningData(
        root = "د-ر-س",
        primaryMeaning = "to study, to learn, lesson",
        extendedMeaning = "Studying and learning through repetition. Also means to wear away/efface.",
        quranUsage = "'By what you have been teaching (tadrusuna) of the Book.' Dars is lesson.",
        notes = "Madrasah (school) comes from this root."
      ),
      RootMeaningData(
        root = "ع-ر-ف",
        primaryMeaning = "to know, recognize, custom",
        extendedMeaning = "Knowledge through recognition and familiarity. Ma'ruf is what is recognized as good.",
        quranUsage = "Ma'ruf is recognized good. 'Commanding ma'ruf and forbidding munkar.' Arafah is the plain of recognition.",
        notes = "'Urf (custom) that doesn't contradict Shariah is considered."
      ),

      // === WEALTH AND TRADE ===
      RootMeaningData(
        root = "م-و-ل",
        primaryMeaning = "wealth, property, money",
        extendedMeaning = "Wealth and possessions. What one owns and relies upon.",
        quranUsage = "Mal (wealth) is mentioned as both blessing and test. 'Your wealth and children are a trial.'",
        notes = "Wealth should not distract from remembrance of Allah."
      ),
      RootMeaningData(
        root = "ب-ي-ع",
        primaryMeaning = "to sell, trade, allegiance",
        extendedMeaning = "Buying and selling. Bay'ah is also pledge of allegiance (a transaction of loyalty).",
        quranUsage = "Bay' is trade. 'Allah has permitted trade (bay') and forbidden usury.' Bay'ah is pledge to a leader.",
        notes = "Honest trade is highly praised."
      ),
      RootMeaningData(
        root = "ر-ب-و",
        primaryMeaning = "to grow, increase, usury",
        extendedMeaning = "Growth and increase. Riba (usury) is illegitimate increase.",
        quranUsage = "Riba (usury) is strictly forbidden. 'Allah destroys riba and increases charity.'",
        notes = "Riba literally means 'increase' but refers to prohibited interest."
      ),
      RootMeaningData(
        root = "ك-س-ب",
        primaryMeaning = "to earn, acquire, gain",
        extendedMeaning = "Earning and acquiring through effort. Applies to wealth and deeds.",
        quranUsage = "'Every soul earns (taksib) for itself.' Kasb is earning. Iktisab is acquisition.",
        notes = "Both good and bad deeds are 'earned'."
      ),

      // === PERCEPTION AND SENSES ===
      RootMeaningData(
        root = "ن-ظ-ر",
        primaryMeaning = "to look, to see, to wait",
        extendedMeaning = "Looking with attention and contemplation. Also waiting and consideration.",
        quranUsage = "Nazr is looking/contemplating. 'Do they not look (yanzurun) at the camels?' Intizar is waiting.",
        notes = "Different from basara (simple seeing) - nazara implies contemplative looking."
      ),
      RootMeaningData(
        root = "ش-ع-ر",
        primaryMeaning = "to feel, perceive, hair, poetry",
        extendedMeaning = "Perception and feeling. Hair (sha'r) is what one feels. Poetry (shi'r) expresses feelings.",
        quranUsage = "'They perceive (yash'urun) not.' Sha'a'ir are sacred symbols/rites that one feels reverence for.",
        notes = "The connection between hair, feeling, and poetry shows semantic depth."
      ),
      RootMeaningData(
        root = "ل-م-س",
        primaryMeaning = "to touch, to feel",
        extendedMeaning = "Physical touching and contact. Also seeking or attempting.",
        quranUsage = "'Or you have touched (lamastum) women' refers to physical contact. Also used for seeking (the sky).",
        notes = "Lamasa can be a euphemism for intimacy."
      ),
      RootMeaningData(
        root = "ذ-و-ق",
        primaryMeaning = "to taste, to experience",
        extendedMeaning = "Tasting physically or experiencing something. Personal, direct experience.",
        quranUsage = "'Every soul shall taste (dha'iqatu) death.' 'Taste (dhuqu) the punishment.' Direct experience.",
        notes = "Dhawq in Sufism refers to spiritual tasting/experience."
      ),
      RootMeaningData(
        root = "ش-م-م",
        primaryMeaning = "to smell, scent",
        extendedMeaning = "The sense of smell. Also pride/haughtiness (holding nose high).",
        quranUsage = "Shamm is smelling. Paradise has pleasant scents. The arrogant are described with 'raised noses.'",
        notes = null
      ),

      // === CELESTIAL BODIES AND NATURE ===
      RootMeaningData(
        root = "ش-م-س",
        primaryMeaning = "sun",
        extendedMeaning = "The sun as the major celestial light source.",
        quranUsage = "Ash-Shams (The Sun) is Surah 91. The sun is a sign of Allah, prostrates to Him, and will be folded up.",
        notes = "The sun's movement is described as evidence of divine design."
      ),
      RootMeaningData(
        root = "ق-م-ر",
        primaryMeaning = "moon",
        extendedMeaning = "The moon as the night luminary and time marker.",
        quranUsage = "Al-Qamar (The Moon) is Surah 54. The moon split as a miracle. It marks months and seasons.",
        notes = "The Islamic calendar is lunar (qamari)."
      ),
      RootMeaningData(
        root = "ن-ج-م",
        primaryMeaning = "star, plant emerging",
        extendedMeaning = "Stars in the sky and plants emerging from earth share the concept of 'appearing.'",
        quranUsage = "An-Najm (The Star) is Surah 53. Stars guide travelers. 'The star and trees prostrate.'",
        notes = "Najm originally means anything that appears/emerges."
      ),
      RootMeaningData(
        root = "ج-ب-ل",
        primaryMeaning = "mountain, to create/mold",
        extendedMeaning = "Mountains as firm, created structures. Also means natural disposition (jibillah).",
        quranUsage = "Jibal (mountains) are pegs stabilizing the earth. 'He created you with a disposition (jibillah).'",
        notes = "Mountains symbolize firmness and stability."
      ),
      RootMeaningData(
        root = "ح-ج-ر",
        primaryMeaning = "stone, to prevent, forbidden",
        extendedMeaning = "Stone, and the concept of being blocked/forbidden. Hijr is also lap/protection.",
        quranUsage = "Hajar is stone. Hijr can mean forbidden sanctuary. 'Stones (hijarah) prepared for disbelievers.'",
        notes = "Al-Hajar al-Aswad is the Black Stone of the Ka'bah."
      ),
      RootMeaningData(
        root = "ر-ع-د",
        primaryMeaning = "thunder, to tremble",
        extendedMeaning = "Thunder and the trembling it causes. A sign of Allah's power.",
        quranUsage = "Ar-Ra'd (The Thunder) is Surah 13. 'The thunder glorifies His praise.'",
        notes = "Thunder is described as praising Allah."
      ),
      RootMeaningData(
        root = "ب-ر-ق",
        primaryMeaning = "lightning, to shine",
        extendedMeaning = "Lightning flash. Brightness and sudden illumination.",
        quranUsage = "Barq (lightning) almost takes away sight. It is a sign causing both fear and hope (of rain).",
        notes = "Al-Buraq was the Prophet's mount on Isra, named for its lightning speed."
      ),
      RootMeaningData(
        root = "ر-ي-ح",
        primaryMeaning = "wind, spirit, rest",
        extendedMeaning = "Wind as moving air. Connected to ruh (spirit) and rahah (rest/comfort).",
        quranUsage = "Riyah (winds) bring rain and are soldiers of Allah. Can be mercy or punishment.",
        notes = "Pleasant wind brings comfort (istiraha)."
      ),
      RootMeaningData(
        root = "س-ح-ب",
        primaryMeaning = "cloud, to drag",
        extendedMeaning = "Clouds and the action of dragging/pulling.",
        quranUsage = "Sahab (clouds) are driven by wind and carry rain. 'Heavy clouds (sahab)' bring water.",
        notes = "Clouds being 'dragged' across the sky is imagery used."
      ),

      // === TIMES OF DAY ===
      RootMeaningData(
        root = "ص-ب-ح",
        primaryMeaning = "morning, to become",
        extendedMeaning = "Morning time and the state of becoming something.",
        quranUsage = "Subh is morning/dawn. 'By the morning (subh)!' Asbaha means 'became' or 'reached morning.'",
        notes = "Salat al-Subh is the dawn prayer."
      ),
      RootMeaningData(
        root = "م-س-ء",
        primaryMeaning = "evening, to become (evening)",
        extendedMeaning = "Evening time. Masa' is when day transitions to night.",
        quranUsage = "Masa' is evening. 'Glorify Allah in the evening (masa').' Amsa means 'reached evening.'",
        notes = "Evening adhkar are called adhkar al-masa'."
      ),
      RootMeaningData(
        root = "ل-ي-ل",
        primaryMeaning = "night",
        extendedMeaning = "Nighttime, darkness. Time of rest and reflection.",
        quranUsage = "Layl (night) is for rest. 'By the night when it covers.' Laylat al-Qadr is the best night.",
        notes = "Night prayer (qiyam al-layl) is highly valued."
      ),
      RootMeaningData(
        root = "ف-ج-ر",
        primaryMeaning = "dawn, to break forth, wickedness",
        extendedMeaning = "Dawn breaking forth. Also wickedness (breaking moral bounds).",
        quranUsage = "Al-Fajr (The Dawn) is Surah 89. Fajr prayer time. Fujur is wickedness/immorality.",
        notes = "The contrast: dawn is pure, fujur is moral corruption."
      ),
      RootMeaningData(
        root = "ظ-ه-ر",
        primaryMeaning = "noon, back, to appear, to prevail",
        extendedMeaning = "Midday, the back (what appears), becoming manifest, victory.",
        quranUsage = "Zuhr is noon. Zahara means to appear/prevail. 'That He may make it prevail (yuzhirahu) over all religion.'",
        notes = "Zahir is the apparent/outer meaning."
      ),
      RootMeaningData(
        root = "ع-ش-ي",
        primaryMeaning = "evening, supper",
        extendedMeaning = "Late afternoon to evening. Time of the evening meal.",
        quranUsage = "'Isha is evening/night. 'Glorify Him morning and evening ('ashiyya).' Salat al-'Isha.",
        notes = "'Asha' (dinner) is from this root."
      ),

      // === OBEDIENCE AND DISOBEDIENCE ===
      RootMeaningData(
        root = "ط-و-ع",
        primaryMeaning = "to obey, willingly, ability",
        extendedMeaning = "Willing obedience and capability. Ta'ah is obedience from choice.",
        quranUsage = "Taw'an means willingly. 'They said: We come willingly (ta'i'in).' Istita'ah is ability/capacity.",
        notes = "True obedience is willing, not forced."
      ),
      RootMeaningData(
        root = "ع-ص-ي",
        primaryMeaning = "to disobey, rebellion",
        extendedMeaning = "Disobedience and rebellion against authority or command.",
        quranUsage = "Ma'siyah is disobedience. Adam 'disobeyed ('asa) his Lord.' 'Asi is one who disobeys.",
        notes = "Even prophets may slip but repent."
      ),
      RootMeaningData(
        root = "ب-ر-ر",
        primaryMeaning = "righteousness, piety, land",
        extendedMeaning = "Righteousness, dutiful conduct, dry land (as opposed to sea).",
        quranUsage = "Birr is comprehensive righteousness. 'Righteousness (birr) is not turning faces east or west...' Barr is land.",
        notes = "Al-Barr (The Source of Goodness) is one of Allah's names."
      ),
      RootMeaningData(
        root = "إ-ث-م",
        primaryMeaning = "sin, guilt, crime",
        extendedMeaning = "Sin that burdens the soul with guilt.",
        quranUsage = "Ithm is sin. 'Avoid much assumption, indeed some assumption is sin (ithm).' Athim is sinner.",
        notes = "Ithm is general sin, distinct from specific terms like fujur."
      ),
      RootMeaningData(
        root = "ذ-ن-ب",
        primaryMeaning = "sin, fault, tail",
        extendedMeaning = "Sin or fault. Also tail (what follows behind, like consequences).",
        quranUsage = "Dhanb is sin. 'Forgive us our sins (dhunub).' Sins follow a person like a tail.",
        notes = "Istighfar seeks forgiveness for dhunub."
      ),

      // === ENTERING AND EXITING ===
      RootMeaningData(
        root = "د-خ-ل",
        primaryMeaning = "to enter",
        extendedMeaning = "Entering a place, state, or condition.",
        quranUsage = "'Enter (udkhulu) Paradise.' 'Enter (udkhulu) Egypt safely.' Dukhul is entry.",
        notes = "Entry into Paradise is the ultimate goal."
      ),
      RootMeaningData(
        root = "خ-ر-ج",
        primaryMeaning = "to exit, to bring out, tribute",
        extendedMeaning = "Going out, extracting, or tribute/tax paid.",
        quranUsage = "'He brings out (yukhrij) the living from the dead.' Khuruj is exit. Kharaj is land tax.",
        notes = "Allah brings forth life from death and vice versa."
      ),
      RootMeaningData(
        root = "ف-ت-ء",
        primaryMeaning = "to cease, young man",
        extendedMeaning = "To stop doing something. Fata is a young man in his prime.",
        quranUsage = "'You will not cease (tafta'u) remembering Yusuf.' Fityan are young men (companions of the cave).",
        notes = "Futuwwah is chivalry/young manly virtue."
      ),

      // === COVENANTS AND PROMISES ===
      RootMeaningData(
        root = "ع-ه-د",
        primaryMeaning = "covenant, promise, era",
        extendedMeaning = "Binding agreement, promise, or a period of time.",
        quranUsage = "'Ahd is covenant. 'Fulfill My covenant ('ahdi), I will fulfill yours.' 'Ahd Allah is Allah's covenant.",
        notes = "Breaking covenants is severely condemned."
      ),
      RootMeaningData(
        root = "و-ع-د",
        primaryMeaning = "to promise",
        extendedMeaning = "Making a promise about the future. Can be good (wa'd) or threatening (wa'id).",
        quranUsage = "Wa'd is promise. 'Allah's promise (wa'd) is true.' Wa'id is threat/warning. Maw'id is appointed time.",
        notes = "Allah never breaks His promise."
      ),
      RootMeaningData(
        root = "و-ف-ي",
        primaryMeaning = "to fulfill, complete, loyal",
        extendedMeaning = "Fulfilling commitments completely. Wafa' is loyalty.",
        quranUsage = "'Fulfill (awfu) the covenant.' 'Every soul will be paid in full (tuwaffa) what it earned.'",
        notes = "Wafa' (loyalty) is highly praised."
      ),
      RootMeaningData(
        root = "ن-ق-ض",
        primaryMeaning = "to break, violate, undo",
        extendedMeaning = "Breaking something, especially covenants or contracts.",
        quranUsage = "'Those who break (yanqudun) Allah's covenant.' Naqd is breaking/undoing.",
        notes = "Covenant-breakers are condemned repeatedly."
      ),

      // === DESTRUCTION AND SALVATION ===
      RootMeaningData(
        root = "ه-ل-ك",
        primaryMeaning = "to perish, destroy",
        extendedMeaning = "Perishing, destruction, ruin. The fate of the rebellious.",
        quranUsage = "Halak is destruction. 'How many generations We destroyed (ahlakna)!' Halakah is perdition.",
        notes = "Past nations perished due to rejection of messengers."
      ),
      RootMeaningData(
        root = "ن-ج-و",
        primaryMeaning = "to save, rescue, escape",
        extendedMeaning = "Salvation and deliverance from harm or danger.",
        quranUsage = "Najah is salvation. 'We saved (najjayna) him and his family.' Munajjah is one who is saved.",
        notes = "Believers are saved through faith and good deeds."
      ),
      RootMeaningData(
        root = "ع-ص-م",
        primaryMeaning = "to protect, preserve",
        extendedMeaning = "Protection and preservation from harm. 'Ismah is divine protection.",
        quranUsage = "'There is no protector ('asim) today from Allah's command.' I'tasama is to hold fast.",
        notes = "'Ismah of prophets means their protection from major sins."
      ),
      RootMeaningData(
        root = "ح-ف-ظ",
        primaryMeaning = "to preserve, protect, memorize",
        extendedMeaning = "Guarding, preserving, and committing to memory.",
        quranUsage = "Hifz is preservation. 'Indeed We sent down the reminder and We will preserve (hafizun) it.' Hafiz memorizes Quran.",
        notes = "Al-Hafiz (The Preserver) is Allah's name."
      ),
      RootMeaningData(
        root = "و-ق-ي",
        primaryMeaning = "to protect, guard, piety",
        extendedMeaning = "Protection and guarding. Taqwa is self-protection through piety.",
        quranUsage = "Wiqayah is protection. 'Guard (qu) yourselves and families from Fire.' Taqwa protects from Allah's anger.",
        notes = "Taqwa is protective consciousness of Allah."
      ),

      // === STRIVING AND FIGHTING ===
      RootMeaningData(
        root = "ج-ه-د",
        primaryMeaning = "to strive, struggle, effort",
        extendedMeaning = "Exerting utmost effort in any endeavor. Jihad is striving in Allah's cause.",
        quranUsage = "Jihad is striving. 'Strive (jahidu) in Allah's cause.' Mujahadah is spiritual struggle.",
        notes = "Jihad al-nafs (struggle against self) is the greater jihad."
      ),
      RootMeaningData(
        root = "ق-ت-ل",
        primaryMeaning = "to kill, fight, combat",
        extendedMeaning = "Killing, fighting in battle, or combat.",
        quranUsage = "Qital is fighting. 'Fighting (qital) is prescribed for you.' Qatl is killing. Maqtul is one killed.",
        notes = "Killing unjustly is among the greatest sins."
      ),
      RootMeaningData(
        root = "ح-ر-ب",
        primaryMeaning = "war, to fight",
        extendedMeaning = "War and warfare. State of hostility.",
        quranUsage = "Harb is war. 'Those who wage war (yuharibun) against Allah and His Messenger.' Mihrab is prayer niche (place of spiritual battle).",
        notes = "Interest-dealers are described as being at war with Allah."
      ),
      RootMeaningData(
        root = "ص-ل-ح",
        primaryMeaning = "peace, reconciliation, reform",
        extendedMeaning = "Making peace, reconciliation, and reform.",
        quranUsage = "Sulh is peace/reconciliation. 'Reconciliation (sulh) is better.' Islah is reform.",
        notes = "Already added as righteousness - this emphasizes peace aspect."
      ),

      // === SEEKING AND FINDING ===
      RootMeaningData(
        root = "ب-غ-ي",
        primaryMeaning = "to seek, desire, transgress",
        extendedMeaning = "Seeking something, desiring, but also transgression when seeking wrongly.",
        quranUsage = "Baghi can mean seeker or transgressor. 'Seek (ibtaghi) Allah's bounty.' Baghiy is prostitute/transgressor.",
        notes = "The meaning depends on what is being sought."
      ),
      RootMeaningData(
        root = "و-ج-د",
        primaryMeaning = "to find, existence, emotion",
        extendedMeaning = "Finding something, existence, and strong emotion (wajd).",
        quranUsage = "'He found (wajada) you lost and guided you.' Wujud is existence. Wijdan is emotion.",
        notes = "In Sufism, wajd is ecstatic finding of divine presence."
      ),
      RootMeaningData(
        root = "ط-ل-ب",
        primaryMeaning = "to seek, request, demand",
        extendedMeaning = "Actively seeking or demanding something.",
        quranUsage = "Talab is seeking. Talib is seeker/student. 'Seeking (yatlubu) the life of this world.'",
        notes = "Talib al-'ilm is seeker of knowledge."
      ),
      RootMeaningData(
        root = "ض-ي-ع",
        primaryMeaning = "to lose, waste, be lost",
        extendedMeaning = "Losing something or wasting it. Being lost or neglected.",
        quranUsage = "'Allah does not waste (yudi'u) the reward of those who do good.' Diya' is loss/waste.",
        notes = "Good deeds are never lost with Allah."
      ),

      // === FOLLOWING AND LEADING ===
      RootMeaningData(
        root = "ت-ب-ع",
        primaryMeaning = "to follow, succession",
        extendedMeaning = "Following someone or something. Coming after in sequence.",
        quranUsage = "'Follow (ittabi') what is revealed to you.' Tabi' is follower. Atba' are followers.",
        notes = "Following the Prophet is commanded."
      ),
      RootMeaningData(
        root = "ق-د-م",
        primaryMeaning = "to precede, foot, to advance",
        extendedMeaning = "Going before, advancing forward. Qadam is foot (what steps forward).",
        quranUsage = "'What their hands have sent forth (qaddamat).' Qadim means ancient/preceding. Muqaddimah is introduction.",
        notes = "Deeds are 'sent forward' for the Hereafter."
      ),
      RootMeaningData(
        root = "إ-م-م",
        primaryMeaning = "leader, to lead, in front",
        extendedMeaning = "Leadership, being in front, and guiding others.",
        quranUsage = "Imam is leader. 'We made them leaders (a'immah) guiding by Our command.' Amam is in front.",
        notes = "Ibrahim was made an imam for mankind."
      ),
      RootMeaningData(
        root = "خ-ل-ف",
        primaryMeaning = "to succeed, behind, differ",
        extendedMeaning = "Coming after/behind, succeeding someone, or differing.",
        quranUsage = "Khalifah is successor/vicegerent. 'He made you successors (khala'if) on earth.' Ikhtilaf is difference.",
        notes = "Adam was made khalifah on earth."
      ),

      // === EATING AND DRINKING ===
      RootMeaningData(
        root = "أ-ك-ل",
        primaryMeaning = "to eat, food, consume",
        extendedMeaning = "Eating food, consuming. Also devouring (wealth, etc.).",
        quranUsage = "'Eat (kulu) from the good things.' 'Do not devour (ta'kulu) each other's wealth unjustly.'",
        notes = "Halal food is emphasized: 'Eat of what is lawful and good.'"
      ),
      RootMeaningData(
        root = "ش-ر-ب",
        primaryMeaning = "to drink, absorb",
        extendedMeaning = "Drinking liquids. Also absorbing (like earth absorbing water).",
        quranUsage = "'Eat and drink (ishrabu).' 'They were made to drink (ushribu) the calf in their hearts.'",
        notes = "Paradise has rivers to drink from."
      ),
      RootMeaningData(
        root = "ط-ع-م",
        primaryMeaning = "food, to feed, taste",
        extendedMeaning = "Food, feeding others, and the taste of food.",
        quranUsage = "'They feed (yut'imun) the poor, orphan, and captive.' Ta'am is food. It'am is feeding.",
        notes = "Feeding the hungry is highly rewarded."
      ),
      RootMeaningData(
        root = "ج-و-ع",
        primaryMeaning = "hunger, to be hungry",
        extendedMeaning = "The state of hunger and need for food.",
        quranUsage = "'Who fed them against hunger (ju').' 'We will test you with hunger (ju').'",
        notes = "Hunger is both a test and a reminder of blessings."
      ),

      // === CLOTHING AND COVERING ===
      RootMeaningData(
        root = "ل-ب-س",
        primaryMeaning = "to wear, clothing, to confuse",
        extendedMeaning = "Wearing clothes, garments. Also mixing/confusing (covering truth).",
        quranUsage = "Libas is clothing. 'Clothing (libas) of taqwa is best.' 'Do not mix (talbisu) truth with falsehood.'",
        notes = "Spouses are described as 'clothing' for each other."
      ),
      RootMeaningData(
        root = "س-ت-ر",
        primaryMeaning = "to cover, conceal, veil",
        extendedMeaning = "Covering and concealing, especially faults or private matters.",
        quranUsage = "Sitr is covering. Allah is As-Satir/As-Sittir who covers faults. Satr al-'awrah is covering private parts.",
        notes = "Covering others' faults is praised."
      ),
      RootMeaningData(
        root = "ع-ر-ي",
        primaryMeaning = "nakedness, bare, to be naked",
        extendedMeaning = "Being uncovered, naked, or bare.",
        quranUsage = "'That he may expose (yubdi) their nakedness ('awrat).' 'Awrah is what must be covered.",
        notes = "Adam and Hawwa's nakedness was exposed after eating from the tree."
      ),

      // === BUILDING AND CREATION ===
      RootMeaningData(
        root = "ب-ن-ي",
        primaryMeaning = "to build, construct, son",
        extendedMeaning = "Building structures. Ibn (son) is a 'building' of the family.",
        quranUsage = "Bina' is building. 'We built (banayna) above you seven strong.' Ibn is son. Banu Isra'il.",
        notes = "Children 'build' the family's future."
      ),
      RootMeaningData(
        root = "ص-ن-ع",
        primaryMeaning = "to make, craft, manufacture",
        extendedMeaning = "Making things with skill and craftsmanship.",
        quranUsage = "'The making (sun') of Allah who perfected everything.' Sina'ah is craft/industry.",
        notes = "Allah is the best of makers."
      ),
      RootMeaningData(
        root = "ف-ط-ر",
        primaryMeaning = "to create, originate, break fast, natural disposition",
        extendedMeaning = "Original creation, splitting/breaking open, and natural state.",
        quranUsage = "Fatir is Originator (Surah 35). Fitrah is natural disposition. Iftar is breaking fast.",
        notes = "Every child is born upon fitrah (natural inclination to truth)."
      ),
      RootMeaningData(
        root = "ب-د-ع",
        primaryMeaning = "to originate, innovate",
        extendedMeaning = "Creating something unprecedented. Can be praiseworthy or blameworthy.",
        quranUsage = "Badi' is Originator of heavens and earth. Bid'ah in religion is condemned innovation.",
        notes = "Allah creates without precedent; humans should not innovate in religion."
      ),

      // === TRUTH AND FALSEHOOD ===
      RootMeaningData(
        root = "ح-ق-ق",
        primaryMeaning = "truth, right, reality",
        extendedMeaning = "Truth, reality, right, and what is due. The opposite of batil (falsehood).",
        quranUsage = "Al-Haqq (The Truth) is Allah's name. 'Truth (haqq) has come and falsehood perished.' Haqq is also right/due.",
        notes = "The Quran is described as Haqq (truth/reality)."
      ),
      RootMeaningData(
        root = "ب-ط-ل",
        primaryMeaning = "falsehood, void, to nullify",
        extendedMeaning = "Falsehood, vanity, and what is null/void.",
        quranUsage = "Batil is falsehood. 'Do not mix truth with falsehood (batil).' Ibtal is nullification.",
        notes = "Batil is inherently destined to perish."
      ),
      RootMeaningData(
        root = "ز-و-ر",
        primaryMeaning = "falsehood, to visit, to forge",
        extendedMeaning = "False testimony, lying, and also visiting.",
        quranUsage = "'Those who do not witness falsehood (zur).' Shahid zur is false witness. Ziyarah is visit.",
        notes = "False testimony is a major sin."
      ),

      // === PERMISSIBLE AND FORBIDDEN ===
      RootMeaningData(
        root = "ح-ل-ل",
        primaryMeaning = "permissible, to untie, to descend",
        extendedMeaning = "What is lawful/permitted, untying, and descending (settling).",
        quranUsage = "Halal is permissible. 'Allah has made trade halal.' Hill is exiting ihram. Mahall is place.",
        notes = "Halal and haram are fundamental categories in Islamic law."
      ),
      RootMeaningData(
        root = "ح-ر-م",
        primaryMeaning = "forbidden, sacred, to prohibit",
        extendedMeaning = "What is forbidden or sacred (protected from violation).",
        quranUsage = "Haram is forbidden. Al-Masjid al-Haram is the Sacred Mosque. Hurmah is sanctity.",
        notes = "The same root gives 'forbidden' and 'sacred' - both are inviolable."
      ),
      RootMeaningData(
        root = "ط-ي-ب",
        primaryMeaning = "good, pure, pleasant",
        extendedMeaning = "What is good, pure, wholesome, and pleasant.",
        quranUsage = "'Eat from the good things (tayyibat).' Tayyib is pure/good. At-Tayyib is Allah's name.",
        notes = "Allah is Tayyib and accepts only what is tayyib."
      ),
      RootMeaningData(
        root = "خ-ب-ث",
        primaryMeaning = "impure, evil, wicked",
        extendedMeaning = "What is impure, filthy, or morally corrupt.",
        quranUsage = "Khabith is impure/evil. 'The evil (khabith) and good are not equal.' Khaba'ith are impurities.",
        notes = "Khabith is opposite of tayyib."
      ),

      // === SLEEP AND REST ===
      RootMeaningData(
        root = "ن-و-م",
        primaryMeaning = "sleep, to sleep",
        extendedMeaning = "Sleep as rest and temporary death.",
        quranUsage = "Nawm is sleep. 'Neither drowsiness nor sleep (nawm) overtakes Him.' Sleep is called 'minor death.'",
        notes = "Allah neither sleeps nor drowses (Ayat al-Kursi)."
      ),
      RootMeaningData(
        root = "ر-ق-د",
        primaryMeaning = "to sleep, to lie down",
        extendedMeaning = "Sleeping, lying down, or being in a dormant state.",
        quranUsage = "'Who has raised us from our sleeping place (marqad)?' - said on resurrection.",
        notes = "The grave is called marqad (sleeping place)."
      ),
      RootMeaningData(
        root = "ي-ق-ظ",
        primaryMeaning = "to awaken, alert",
        extendedMeaning = "Being awake, alert, and vigilant.",
        quranUsage = "'You would think them awake (ayqaz) while they slept' - People of the Cave.",
        notes = "Spiritual awakening is metaphorically connected."
      ),

      // === SECRETS AND REVELATION ===
      RootMeaningData(
        root = "س-ر-ر",
        primaryMeaning = "secret, joy, bed",
        extendedMeaning = "Secret, innermost feelings, joy, and also bed/throne.",
        quranUsage = "Sirr is secret. 'He knows the secret (sirr) and what is hidden.' Surur is joy. Sarir is bed/throne.",
        notes = "Allah knows all secrets."
      ),
      RootMeaningData(
        root = "ك-ش-ف",
        primaryMeaning = "to reveal, uncover, remove",
        extendedMeaning = "Uncovering, revealing, and removing (hardship).",
        quranUsage = "'None can remove (yakshif) it except Allah.' Kashf is unveiling/revelation.",
        notes = "In Sufism, kashf is spiritual unveiling."
      ),
      RootMeaningData(
        root = "ع-ل-ن",
        primaryMeaning = "to announce, public, open",
        extendedMeaning = "Making something public and open, announcement.",
        quranUsage = "'Whether you reveal (tu'linu) something or conceal it.' 'Alaniyah is openness.",
        notes = "Charity can be given secretly or openly."
      ),
      RootMeaningData(
        root = "خ-ف-ي",
        primaryMeaning = "to hide, concealed, secret",
        extendedMeaning = "Hiding, concealment, and what is secret.",
        quranUsage = "'Nothing is hidden (yakhfa) from Allah.' Khafi is hidden. Ikhfa' is concealment.",
        notes = "The unseen (ghayb) is khafi from creation but known to Allah."
      ),

      // === POWER AND WEAKNESS ===
      RootMeaningData(
        root = "ق-و-ي",
        primaryMeaning = "strength, power, strong",
        extendedMeaning = "Strength, power, and being strong.",
        quranUsage = "Al-Qawi (The Strong) is Allah's name. Quwwah is strength. 'Take what We gave you with strength (quwwah).'",
        notes = "True strength is with Allah."
      ),
      RootMeaningData(
        root = "ض-ع-ف",
        primaryMeaning = "weakness, double, to weaken",
        extendedMeaning = "Weakness, and also doubling/multiplying.",
        quranUsage = "Da'if is weak. 'Man was created weak (da'if).' Du'f is weakness. Mudha'afah is multiplication.",
        notes = "Human weakness calls for reliance on Allah."
      ),
      RootMeaningData(
        root = "ع-ج-ز",
        primaryMeaning = "inability, weakness, old age",
        extendedMeaning = "Being unable, incapacity, and the weakness of old age.",
        quranUsage = "'You cannot escape (mu'jizin) in the earth.' 'Ajuz is old woman. I'jaz is miraculous inimitability.",
        notes = "The Quran's i'jaz (inimitability) proves human inability to match it."
      ),

      // === SATAN AND EVIL BEINGS ===
      RootMeaningData(
        root = "ش-ط-ن",
        primaryMeaning = "satan, to be far, rebellious",
        extendedMeaning = "Satan, being far from truth, and rebellion.",
        quranUsage = "Shaytan is Satan/devil - the rebellious one far from Allah's mercy. Shayatin are devils.",
        notes = "Shaytan can be jinn or human who leads astray."
      ),
      RootMeaningData(
        root = "إ-ب-ل",
        primaryMeaning = "Iblis (Satan's name)",
        extendedMeaning = "The personal name of Satan, possibly meaning 'one who despaired.'",
        quranUsage = "Iblis refused to prostrate to Adam and was expelled. He seeks to mislead humanity.",
        notes = "Iblis was from the jinn, not angels."
      ),
      RootMeaningData(
        root = "و-س-و-س",
        primaryMeaning = "to whisper, whisper evil",
        extendedMeaning = "Whispering evil suggestions. Satan's method of temptation.",
        quranUsage = "'From the evil of the whisperer (waswas).' Waswasah is Satan's whispering.",
        notes = "Seeking refuge from waswasah is in Surah An-Nas."
      ),

      // === HARM AND BENEFIT ===
      RootMeaningData(
        root = "ض-ر-ر",
        primaryMeaning = "harm, damage, adversity",
        extendedMeaning = "Harm, damage, and hardship. The state of being in difficulty.",
        quranUsage = "Darr is harm. 'They cannot harm (yadurru) you at all.' Darar is damage. Idtirar is necessity.",
        notes = "Necessity (darurah) can permit the forbidden."
      ),
      RootMeaningData(
        root = "ن-ف-ع",
        primaryMeaning = "benefit, profit, to be useful",
        extendedMeaning = "Benefit, profit, and usefulness.",
        quranUsage = "Naf' is benefit. 'No soul can benefit (tanfa') another.' Manfa'ah is benefit/utility.",
        notes = "On Judgment Day, nothing benefits except faith and good deeds."
      ),
      RootMeaningData(
        root = "أ-ذ-ي",
        primaryMeaning = "harm, hurt, annoyance",
        extendedMeaning = "Causing harm, hurt, or annoyance to others.",
        quranUsage = "Adha is harm/annoyance. 'Do not harm (tu'dhu) the Prophet.' Idha'ah is causing harm.",
        notes = "Harming believers and the Prophet is severely warned against."
      ),
      RootMeaningData(
        root = "ش-ف-ي",
        primaryMeaning = "to heal, cure",
        extendedMeaning = "Healing, curing disease, and providing remedy.",
        quranUsage = "'We reveal from the Quran what is healing (shifa').' 'When I am ill, He heals (yashfi) me.'",
        notes = "The Quran is described as a healing."
      ),

      // === NUMBERS AND COUNTING ===
      RootMeaningData(
        root = "ع-د-د",
        primaryMeaning = "number, to count, prepare",
        extendedMeaning = "Counting, numbering, and preparing.",
        quranUsage = "'Adad is number. 'A limited number ('adad) of days.' I'dad is preparation.",
        notes = "Only Allah knows the exact count of all things."
      ),
      RootMeaningData(
        root = "ح-ص-ي",
        primaryMeaning = "to count, enumerate, pebbles",
        extendedMeaning = "Counting precisely, calculating. Hasa are pebbles (used for counting).",
        quranUsage = "'He has enumerated (ahsa) everything in number.' Ihsa' is precise counting.",
        notes = "Allah counts all things - nothing escapes His knowledge."
      ),
      RootMeaningData(
        root = "ك-ث-ر",
        primaryMeaning = "many, much, abundance",
        extendedMeaning = "Abundance, multitude, and being numerous.",
        quranUsage = "Kathir is many/much. Al-Kawthar (Abundance) is Surah 108. 'Abundant (kathir) good.'",
        notes = "Al-Kawthar is a river in Paradise."
      ),
      RootMeaningData(
        root = "ق-ل-ل",
        primaryMeaning = "few, little, to decrease",
        extendedMeaning = "Being few, small in number or quantity.",
        quranUsage = "Qalil is few/little. 'Few (qalil) of My servants are grateful.' Qillah is scarcity.",
        notes = "Grateful servants are described as few."
      ),

      // === SHAPES AND FORMS ===
      RootMeaningData(
        root = "ص-و-ر",
        primaryMeaning = "form, image, to shape",
        extendedMeaning = "Form, image, shape, and the act of forming.",
        quranUsage = "Al-Musawwir (The Fashioner) is Allah's name. 'He forms (yusawwiru) you in the wombs.' Surah is chapter (a form).",
        notes = "Allah shapes each person uniquely in the womb."
      ),
      RootMeaningData(
        root = "خ-ل-ق",
        primaryMeaning = "to create, character, smooth",
        extendedMeaning = "Creation, natural disposition, and smooth/even (well-created).",
        quranUsage = "Already added - emphasizing form: 'Created (khalaqa) man in the best form.'",
        notes = "Khuluq is character - how one is 'created' morally."
      ),
      RootMeaningData(
        root = "ش-ك-ل",
        primaryMeaning = "form, shape, manner",
        extendedMeaning = "Shape, form, manner, and similarity.",
        quranUsage = "'Each acts according to his manner (shakilah).' Shakl is form/shape.",
        notes = "Everyone acts according to their nature/disposition."
      ),

      // === TRIALS AND TESTS ===
      RootMeaningData(
        root = "ب-ل-و",
        primaryMeaning = "to test, trial, affliction",
        extendedMeaning = "Testing and trying someone to reveal their true nature.",
        quranUsage = "Bala' is test/trial. 'We will surely test (nablu) you.' Ibtila' is being tested.",
        notes = "Life itself is described as a test (bala')."
      ),
      RootMeaningData(
        root = "ف-ت-ن",
        primaryMeaning = "trial, temptation, civil strife",
        extendedMeaning = "Severe trial, temptation, or discord. Originally meant smelting gold to test purity.",
        quranUsage = "Fitnah is trial/temptation. 'Fitnah is worse than killing.' Maftun is one who is tempted.",
        notes = "Fitnah tests faith like fire tests gold."
      ),
      RootMeaningData(
        root = "م-ح-ن",
        primaryMeaning = "to test, examine, trial",
        extendedMeaning = "Testing and examining thoroughly.",
        quranUsage = "'Allah has tested (imtahana) their hearts for taqwa.' Mihnah is ordeal/trial.",
        notes = "Imtihan is examination/test."
      ),

      // === REMEMBERING AND FORGETTING ===
      RootMeaningData(
        root = "ن-س-ي",
        primaryMeaning = "to forget, neglect",
        extendedMeaning = "Forgetting, neglecting, or abandoning. Human nature is forgetful.",
        quranUsage = "'They forgot (nasu) Allah, so He forgot them.' Nisyan is forgetfulness. Insan may relate (the forgetful being).",
        notes = "Adam forgot his covenant - humans are called insan perhaps due to forgetfulness."
      ),
      RootMeaningData(
        root = "غ-ف-ل",
        primaryMeaning = "heedlessness, negligence",
        extendedMeaning = "Being unaware, heedless, or neglectful of important matters.",
        quranUsage = "Ghaflah is heedlessness. 'Do not be among the heedless (ghafilin).' Ghafil is heedless person.",
        notes = "Heedlessness of the Hereafter is particularly warned against."
      ),

      // === SICKNESS AND HEALTH ===
      RootMeaningData(
        root = "م-ر-ض",
        primaryMeaning = "sickness, disease, doubt",
        extendedMeaning = "Physical illness or spiritual disease (doubt, hypocrisy).",
        quranUsage = "Marad is sickness. 'In their hearts is disease (marad).' Marid is sick person.",
        notes = "Heart disease refers to spiritual ailments like doubt and hypocrisy."
      ),
      RootMeaningData(
        root = "ب-ر-ء",
        primaryMeaning = "to heal, create, innocent",
        extendedMeaning = "Healing, creating from nothing, and being free/innocent.",
        quranUsage = "Al-Bari' (The Creator) is Allah's name. Bur' relates to healing. Bari' is innocent.",
        notes = "Creation and healing both involve bringing to a sound state."
      ),

      // === POVERTY AND WEALTH ===
      RootMeaningData(
        root = "ف-ق-ر",
        primaryMeaning = "poverty, need",
        extendedMeaning = "Being in need, poverty. All creation is faqir (needy) before Allah.",
        quranUsage = "'You are the poor (fuqara') unto Allah.' Faqir is poor/needy person.",
        notes = "Spiritual poverty (recognizing need for Allah) is praised."
      ),
      RootMeaningData(
        root = "م-س-ك-ن",
        primaryMeaning = "poor, needy, dwelling",
        extendedMeaning = "One in need, and also dwelling/residence.",
        quranUsage = "Miskin is poor/needy person. Maskan is dwelling. 'Feed the poor (miskin).'",
        notes = "Feeding the miskin is repeatedly commanded."
      ),

      // === DIMENSIONS AND SPACE ===
      RootMeaningData(
        root = "و-س-ع",
        primaryMeaning = "vastness, capacity, to encompass",
        extendedMeaning = "Being vast, spacious, and having capacity to encompass.",
        quranUsage = "Al-Wasi' (The All-Encompassing) is Allah's name. 'Allah's earth is spacious (wasi'ah).'",
        notes = "Allah's mercy and knowledge are wasi' (vast/encompassing)."
      ),
      RootMeaningData(
        root = "ض-ي-ق",
        primaryMeaning = "narrowness, constriction, distress",
        extendedMeaning = "Being narrow, tight, or in distress.",
        quranUsage = "'Do not be in distress (dayq).' 'The earth became narrow (daqat) for them.'",
        notes = "Spiritual constriction comes from turning away from guidance."
      ),
      RootMeaningData(
        root = "ب-ع-د",
        primaryMeaning = "distance, far, after",
        extendedMeaning = "Being far in distance or time. Also means 'after.'",
        quranUsage = "Ba'id is far/distant. 'From after (ba'd) them.' Bu'd is distance.",
        notes = "Being far from Allah's mercy is the real distance."
      ),
      RootMeaningData(
        root = "ع-ل-و",
        primaryMeaning = "height, elevation, to be high",
        extendedMeaning = "Being high, elevated, or exalted.",
        quranUsage = "Al-'Ali (The Most High) is Allah's name. 'Ala means to be elevated.",
        notes = "Allah is Al-'Ali Al-'Azim - The Most High, The Supreme."
      ),
      RootMeaningData(
        root = "س-ف-ل",
        primaryMeaning = "low, bottom, lowly",
        extendedMeaning = "Being low, at the bottom, or in a lowly state.",
        quranUsage = "'Asfala safilin' - lowest of the low. Sufla is lower.",
        notes = "Humans can descend to the lowest of low through disbelief."
      ),

      // === HEAVY AND LIGHT ===
      RootMeaningData(
        root = "ث-ق-ل",
        primaryMeaning = "heavy, weight, burden",
        extendedMeaning = "Heaviness, weight, and burden. Also valuable (weighty) things.",
        quranUsage = "Thaqil is heavy. 'Heavy (thaqil) word.' Thaqalan are jinn and humans.",
        notes = "The Quran is described as a 'heavy word' - weighty in meaning."
      ),
      RootMeaningData(
        root = "خ-ف-ف",
        primaryMeaning = "light, ease, to lighten",
        extendedMeaning = "Being light in weight, easy, or lessening a burden.",
        quranUsage = "'Allah wishes to lighten (yukhaffif) for you.' Khafif is light/easy.",
        notes = "Islam aims to lighten burdens, not make things difficult."
      ),

      // === BLINDNESS AND DEAFNESS ===
      RootMeaningData(
        root = "ع-م-ي",
        primaryMeaning = "blindness, to be blind",
        extendedMeaning = "Physical or spiritual blindness - inability to see truth.",
        quranUsage = "'Ama is blindness. 'It is not the eyes that are blind but the hearts.'",
        notes = "True blindness is of the heart, not the eyes."
      ),
      RootMeaningData(
        root = "ص-م-م",
        primaryMeaning = "deafness, to be deaf, solid",
        extendedMeaning = "Deafness - inability to hear truth. Also solid/firm.",
        quranUsage = "Samm is deaf. 'Deaf (summ), dumb, blind - they do not return.'",
        notes = "Spiritual deafness means refusing to hear guidance."
      ),
      RootMeaningData(
        root = "ب-ك-م",
        primaryMeaning = "dumbness, mute, unable to speak",
        extendedMeaning = "Being mute or unable to articulate truth.",
        quranUsage = "'Deaf, dumb (bukm), blind' describes those who reject truth.",
        notes = "Spiritual dumbness is inability to speak truth."
      ),

      // === CRYING AND LAUGHING ===
      RootMeaningData(
        root = "ب-ك-ي",
        primaryMeaning = "to cry, weep",
        extendedMeaning = "Crying and weeping from emotion.",
        quranUsage = "'They fall down weeping (bukiyyan).' 'Let them laugh little and weep (yabku) much.'",
        notes = "Crying from Allah's fear or Quran's recitation is praised."
      ),
      RootMeaningData(
        root = "ض-ح-ك",
        primaryMeaning = "to laugh, smile",
        extendedMeaning = "Laughing, smiling, or being amused.",
        quranUsage = "'She laughed (dahikat)' - Sarah at news of Ishaq. 'Let them laugh little.'",
        notes = "Excessive laughing hardens the heart."
      ),

      // === INCREASING AND DECREASING ===
      RootMeaningData(
        root = "ز-ي-د",
        primaryMeaning = "to increase, add, more",
        extendedMeaning = "Increasing, adding to, and growth.",
        quranUsage = "'Increase (zidni) me in knowledge.' Ziyadah is increase.",
        notes = "The Prophet asked for increase in knowledge."
      ),
      RootMeaningData(
        root = "ن-ق-ص",
        primaryMeaning = "to decrease, reduce, lack",
        extendedMeaning = "Decreasing, reduction, and deficiency.",
        quranUsage = "'We reduce (nanqusu) it from its edges.' Naqs is decrease/deficiency.",
        notes = "Life and provisions can be decreased as a test."
      ),

      // === BEGINNING AND ENDING ===
      RootMeaningData(
        root = "ب-د-ء",
        primaryMeaning = "to begin, start, originate",
        extendedMeaning = "Beginning, starting, and originating something.",
        quranUsage = "'He originates (yabda'u) creation then repeats it.' Bad' is beginning.",
        notes = "Allah began creation and will repeat it."
      ),
      RootMeaningData(
        root = "خ-ت-م",
        primaryMeaning = "to seal, end, conclude",
        extendedMeaning = "Sealing, concluding, and finalizing.",
        quranUsage = "'Seal (khatam) of the Prophets.' 'Allah sealed (khatama) their hearts.'",
        notes = "Muhammad is the final Prophet - the seal."
      ),
      RootMeaningData(
        root = "ت-م-م",
        primaryMeaning = "to complete, perfect, finish",
        extendedMeaning = "Completing something fully and perfectly.",
        quranUsage = "'Today I have completed (atmamtu) for you your religion.' Tamam is completion.",
        notes = "Islam was completed during the Prophet's final pilgrimage."
      ),

      // === STRIKING AND ACTIONS ===
      RootMeaningData(
        root = "ض-ر-ب",
        primaryMeaning = "to strike, give example, travel",
        extendedMeaning = "Striking, setting forth (examples), and traveling.",
        quranUsage = "'Allah strikes (yadrib) examples.' Darb is striking.",
        notes = "Quran uses 'striking examples' for parables."
      ),
      RootMeaningData(
        root = "ر-م-ي",
        primaryMeaning = "to throw, cast, accuse",
        extendedMeaning = "Throwing, casting, and accusing (throwing blame).",
        quranUsage = "'You did not throw when you threw, but Allah threw.' Ramy is throwing.",
        notes = "At Badr, the Prophet threw dust but Allah directed it."
      ),
      RootMeaningData(
        root = "د-ف-ع",
        primaryMeaning = "to push, repel, defend",
        extendedMeaning = "Pushing away, repelling harm, and defense.",
        quranUsage = "'Allah repels (yadfa'u) from those who believe.' Daf' is repelling.",
        notes = "Allah defends the believers."
      ),

      // === RUNNING AND MOVEMENT ===
      RootMeaningData(
        root = "ج-ر-ي",
        primaryMeaning = "to flow, run, happen",
        extendedMeaning = "Flowing (water), running, and occurring.",
        quranUsage = "'Rivers flowing (tajri) beneath them.' Jaryan is flow.",
        notes = "Paradise is described with flowing rivers."
      ),
      RootMeaningData(
        root = "س-ع-ي",
        primaryMeaning = "to strive, walk briskly, endeavor",
        extendedMeaning = "Striving, walking with purpose, and effort.",
        quranUsage = "'Man has only what he strives (sa'a) for.' Sa'y between Safa and Marwa.",
        notes = "Sa'y in Hajj commemorates Hajar's search for water."
      ),
      RootMeaningData(
        root = "و-ق-ف",
        primaryMeaning = "to stop, stand, pause",
        extendedMeaning = "Stopping, standing still, and pausing.",
        quranUsage = "'Stop (qif) them, they will be questioned.' Wuquf is standing (at Arafah).",
        notes = "Standing at Arafah is the pillar of Hajj."
      ),
      RootMeaningData(
        root = "غ-ر-ق",
        primaryMeaning = "to drown, sink",
        extendedMeaning = "Drowning, sinking, and being overwhelmed.",
        quranUsage = "'We drowned (aghraqna) those who denied.' Gharaq is drowning.",
        notes = "Drowning was the fate of Firawn and denying nations."
      ),

      // === DECEPTION AND HONESTY ===
      RootMeaningData(
        root = "م-ك-ر",
        primaryMeaning = "to plot, scheme, plan",
        extendedMeaning = "Planning and plotting, can be evil or good.",
        quranUsage = "'They plotted and Allah planned.' Allah is the best of planners.",
        notes = "Allah's 'planning' counters evil plots with justice."
      ),
      RootMeaningData(
        root = "خ-د-ع",
        primaryMeaning = "to deceive, cheat",
        extendedMeaning = "Deception and cheating others.",
        quranUsage = "'They seek to deceive (yukhadi'un) Allah.' Khida' is deception.",
        notes = "Hypocrites think they deceive Allah but deceive only themselves."
      ),
      RootMeaningData(
        root = "ن-ص-ح",
        primaryMeaning = "sincere advice, to counsel",
        extendedMeaning = "Giving sincere, genuine advice for someone's benefit.",
        quranUsage = "'I convey to you my Lord's message and advise (ansah) you.' Nasihah is sincere counsel.",
        notes = "Religion is nasihah (sincere advice) - hadith."
      ),

      // === SATISFACTION AND ANGER ===
      RootMeaningData(
        root = "ر-ض-ي",
        primaryMeaning = "to be pleased, satisfaction",
        extendedMeaning = "Being pleased, satisfied, and content.",
        quranUsage = "'Allah is pleased (radiya) with them and they with Him.' Rida is satisfaction.",
        notes = "Mutual pleasure between Allah and believers is the goal."
      ),
      RootMeaningData(
        root = "س-خ-ط",
        primaryMeaning = "anger, displeasure, wrath",
        extendedMeaning = "Divine anger and displeasure.",
        quranUsage = "'They followed what angered (askhata) Allah.' Sakhat is wrath.",
        notes = "Opposite of rida - Allah's displeasure."
      ),
      RootMeaningData(
        root = "ل-و-م",
        primaryMeaning = "to blame, reproach",
        extendedMeaning = "Blaming and reproaching for faults.",
        quranUsage = "'The self-reproaching (lawwamah) soul.' 'Do not fear the blame of blamers.'",
        notes = "An-Nafs al-Lawwamah is the self-blaming soul - sign of conscience."
      ),

      // === TRUST AND RELIANCE ===
      RootMeaningData(
        root = "و-ك-ل",
        primaryMeaning = "to trust, rely, appoint",
        extendedMeaning = "Trusting, relying upon, and appointing as agent.",
        quranUsage = "'In Allah let the believers put trust (yatawakkal).' Tawakkul is reliance. Wakil is trustee.",
        notes = "Al-Wakil (The Trustee) is Allah's name."
      ),

      // === BODY PARTS ===
      RootMeaningData(
        root = "ر-ء-س",
        primaryMeaning = "head, chief, top",
        extendedMeaning = "Head, the top part, or leader.",
        quranUsage = "Ra's is head. 'Heads (ru'us) bowed in humiliation.' Ra'is is chief.",
        notes = "Riba 'heads' must be returned - principal amount."
      ),
      RootMeaningData(
        root = "ج-ل-د",
        primaryMeaning = "skin, to flog, endure",
        extendedMeaning = "Skin, flogging (striking skin), and patient endurance.",
        quranUsage = "'Their skins (julud) will testify.' Jald is flogging.",
        notes = "On Judgment Day, skins will testify about actions."
      ),
      RootMeaningData(
        root = "ع-ظ-م",
        primaryMeaning = "bone, great, to magnify",
        extendedMeaning = "Bone, greatness, and magnifying.",
        quranUsage = "'Who will give life to bones ('izam)?' 'Azim is great.",
        notes = "Allah is Al-'Azim - The Supreme, The Great."
      ),
      RootMeaningData(
        root = "ل-ح-م",
        primaryMeaning = "flesh, meat, to join",
        extendedMeaning = "Flesh, meat, and joining together.",
        quranUsage = "'We clothed the bones with flesh (lahm).' 'Do you like to eat your brother's flesh?'",
        notes = "Backbiting is compared to eating dead brother's flesh."
      ),
      RootMeaningData(
        root = "د-م-م",
        primaryMeaning = "blood",
        extendedMeaning = "Blood - essential to life, sacred.",
        quranUsage = "'Forbidden to you is blood (dam).'",
        notes = "Flowing blood is forbidden to consume."
      ),

      // === ANIMALS ===
      RootMeaningData(
        root = "د-ب-ب",
        primaryMeaning = "creature, to crawl, move",
        extendedMeaning = "Any creature that moves on earth.",
        quranUsage = "'There is no creature (dabbah) on earth but its provision is from Allah.'",
        notes = "All creatures depend on Allah for sustenance."
      ),
      RootMeaningData(
        root = "ح-و-ت",
        primaryMeaning = "whale, fish",
        extendedMeaning = "Large fish, whale.",
        quranUsage = "Hut is whale. Yunus was swallowed by the whale (hut).",
        notes = "Yunus is called Sahib al-Hut - companion of the whale."
      ),
      RootMeaningData(
        root = "ن-م-ل",
        primaryMeaning = "ant",
        extendedMeaning = "Ant - example of small yet organized creature.",
        quranUsage = "An-Naml (The Ant) is Surah 27. An ant warned others of Sulaiman's army.",
        notes = "Ants show organization and communication."
      ),
      RootMeaningData(
        root = "ن-ح-ل",
        primaryMeaning = "bee",
        extendedMeaning = "Bee - producer of honey.",
        quranUsage = "An-Nahl (The Bee) is Surah 16. 'Your Lord inspired the bee.' Honey is healing.",
        notes = "Bees receive divine inspiration (wahy) for their work."
      ),
      RootMeaningData(
        root = "ع-ن-ك-ب",
        primaryMeaning = "spider",
        extendedMeaning = "Spider - known for fragile web.",
        quranUsage = "Al-'Ankabut (The Spider) is Surah 29. 'The weakest house is the spider's house.'",
        notes = "False supports are compared to spider's fragile web."
      ),
      RootMeaningData(
        root = "ب-ق-ر",
        primaryMeaning = "cow, cattle",
        extendedMeaning = "Cow and cattle.",
        quranUsage = "Al-Baqarah (The Cow) is Surah 2. The cow of Bani Isra'il.",
        notes = "The longest surah is named after the cow."
      ),
      RootMeaningData(
        root = "ك-ل-ب",
        primaryMeaning = "dog",
        extendedMeaning = "Dog - companion animal.",
        quranUsage = "Kalb is dog. 'Their dog stretching his forelegs' - People of the Cave.",
        notes = "The dog of the Cave's companions is mentioned honorably."
      ),

      // === JOURNEYING AND MIGRATION ===
      RootMeaningData(
        root = "س-ف-ر",
        primaryMeaning = "journey, travel, book",
        extendedMeaning = "Traveling, journey, and book/scripture (reveals knowledge).",
        quranUsage = "Safar is journey. Asfar are books/scriptures. Musafir is traveler.",
        notes = "Travel is a form of unveiling (revealing new things)."
      ),
      RootMeaningData(
        root = "ه-ج-ر",
        primaryMeaning = "to emigrate, abandon",
        extendedMeaning = "Migration, abandoning, and leaving behind.",
        quranUsage = "Hijrah is migration for Allah's sake. 'Those who emigrated (hajaru).'",
        notes = "The Hijrah marks the beginning of Islamic calendar."
      ),

      // === DWELLING AND SETTLING ===
      RootMeaningData(
        root = "س-ك-ن",
        primaryMeaning = "to dwell, be still, tranquility",
        extendedMeaning = "Dwelling, residing, and finding peace/tranquility.",
        quranUsage = "Sakan is dwelling/tranquility. 'That you may find tranquility (taskunu) with them.'",
        notes = "Spouses are meant to provide sukun (tranquility)."
      ),
      RootMeaningData(
        root = "ق-ر-ر",
        primaryMeaning = "to settle, cool, confirm",
        extendedMeaning = "Settling in place, cooling, and confirming.",
        quranUsage = "'Settle (qarna) in your homes.' Qurrat 'ayn is coolness of eyes (joy).",
        notes = "Children are described as 'coolness of eyes.'"
      ),

      // === WORSHIP POSITIONS ===
      RootMeaningData(
        root = "ر-ك-ع",
        primaryMeaning = "to bow, kneel",
        extendedMeaning = "Bowing in prayer, the ruku' position.",
        quranUsage = "'Bow (irka'u) with those who bow.' Ruku' is bowing.",
        notes = "Ruku' is a pillar of prayer."
      ),

      // === IMPURITY ===
      RootMeaningData(
        root = "ن-ج-س",
        primaryMeaning = "impurity, filth",
        extendedMeaning = "Ritual and physical impurity.",
        quranUsage = "'The polytheists are impure (najas).' Najasah is impurity.",
        notes = "Spiritual impurity is worse than physical."
      ),
      RootMeaningData(
        root = "ر-ج-س",
        primaryMeaning = "abomination, filth",
        extendedMeaning = "Abomination, filth, and spiritual uncleanliness.",
        quranUsage = "'Avoid the abomination (rijs) of idols.' Intoxicants are called rijs.",
        notes = "Rijs covers physical and spiritual abominations."
      ),

      // === OPENING AND CLOSING ===
      RootMeaningData(
        root = "غ-ل-ق",
        primaryMeaning = "to close, lock, shut",
        extendedMeaning = "Closing, locking, and shutting doors.",
        quranUsage = "'She closed (ghallaqat) the doors.' Mughallaq is locked/closed.",
        notes = "In Yusuf's story, she locked the doors attempting seduction."
      ),

      // === AGING ===
      RootMeaningData(
        root = "ش-ي-خ",
        primaryMeaning = "old man, elder, sheikh",
        extendedMeaning = "Old age, elderly person, and respected elder.",
        quranUsage = "'My husband is old (shaykh)?' - Sarah.",
        notes = "Shaykh also means scholar/teacher - one with wisdom of age."
      ),
      RootMeaningData(
        root = "ص-غ-ر",
        primaryMeaning = "small, young, humble",
        extendedMeaning = "Being small, young, or humble.",
        quranUsage = "'My Lord, have mercy on them as they raised me when small (saghir).'",
        notes = "Showing mercy to children as they showed to us."
      ),

      // === MALE AND FEMALE ===
      RootMeaningData(
        root = "أ-ن-ث",
        primaryMeaning = "female, feminine",
        extendedMeaning = "Female, the feminine gender.",
        quranUsage = "'Male (dhakar) and female (untha) He created them.' Untha is female.",
        notes = "Both genders are honored in creation."
      ),

      // === COMPLAINING ===
      RootMeaningData(
        root = "ش-ك-و",
        primaryMeaning = "to complain, grieve",
        extendedMeaning = "Complaining, expressing grief or hardship.",
        quranUsage = "'I only complain (ashku) of my grief to Allah.' Shakwa is complaint.",
        notes = "Ya'qub complained only to Allah, not to people."
      ),

      // === ABANDONMENT ===
      RootMeaningData(
        root = "خ-ذ-ل",
        primaryMeaning = "to abandon, forsake",
        extendedMeaning = "Abandoning someone, leaving without support.",
        quranUsage = "'If Allah forsakes (yakhdhul) you, who can help you?'",
        notes = "Being abandoned by Allah is the worst fate."
      ),

      // === DESTRUCTION AND SALVATION ===
      RootMeaningData(
        root = "ه-ل-ك",
        primaryMeaning = "to perish, destroy",
        extendedMeaning = "Perishing, destruction, ruin. The fate of the rebellious.",
        quranUsage = "'How many generations We destroyed (ahlakna)!' Halak is destruction.",
        notes = "Past nations perished due to rejection of messengers."
      ),
      RootMeaningData(
        root = "ن-ج-و",
        primaryMeaning = "to save, rescue, escape",
        extendedMeaning = "Salvation and deliverance from harm or danger.",
        quranUsage = "'We saved (najjayna) him and his family.' Najah is salvation.",
        notes = "Believers are saved through faith and good deeds."
      ),
      RootMeaningData(
        root = "ع-ص-م",
        primaryMeaning = "to protect, preserve",
        extendedMeaning = "Protection and preservation from harm.",
        quranUsage = "'There is no protector ('asim) today from Allah's command.'",
        notes = "'Ismah of prophets means their protection from major sins."
      ),
      RootMeaningData(
        root = "ح-ف-ظ",
        primaryMeaning = "to preserve, protect, memorize",
        extendedMeaning = "Guarding, preserving, and committing to memory.",
        quranUsage = "'Indeed We sent down the reminder and We will preserve (hafizun) it.'",
        notes = "Al-Hafiz (The Preserver) is Allah's name."
      ),

      // === COVENANTS AND PROMISES ===
      RootMeaningData(
        root = "ع-ه-د",
        primaryMeaning = "covenant, promise, era",
        extendedMeaning = "Binding agreement, promise, or a period of time.",
        quranUsage = "'Fulfill My covenant ('ahdi), I will fulfill yours.'",
        notes = "Breaking covenants is severely condemned."
      ),
      RootMeaningData(
        root = "و-ع-د",
        primaryMeaning = "to promise",
        extendedMeaning = "Making a promise about the future. Can be good (wa'd) or threatening (wa'id).",
        quranUsage = "'Allah's promise (wa'd) is true.' Wa'id is threat/warning.",
        notes = "Allah never breaks His promise."
      ),
      RootMeaningData(
        root = "و-ف-ي",
        primaryMeaning = "to fulfill, complete, loyal",
        extendedMeaning = "Fulfilling commitments completely. Wafa' is loyalty.",
        quranUsage = "'Fulfill (awfu) the covenant.' 'Every soul will be paid in full what it earned.'",
        notes = "Wafa' (loyalty) is highly praised."
      ),

      // === TRUTH AND REALITY ===
      RootMeaningData(
        root = "ح-ق-ق",
        primaryMeaning = "truth, right, reality",
        extendedMeaning = "Truth, reality, right, and what is due. The opposite of batil.",
        quranUsage = "Al-Haqq (The Truth) is Allah's name. 'Truth has come and falsehood perished.'",
        notes = "The Quran is described as Haqq (truth/reality)."
      ),
      RootMeaningData(
        root = "ب-ط-ل",
        primaryMeaning = "falsehood, void, to nullify",
        extendedMeaning = "Falsehood, vanity, and what is null/void.",
        quranUsage = "'Do not mix truth with falsehood (batil).' Batil perishes.",
        notes = "Batil is inherently destined to perish."
      ),

      // === HOLINESS AND GLORY ===
      RootMeaningData(
        root = "ق-د-س",
        primaryMeaning = "holy, sacred, to sanctify",
        extendedMeaning = "Holiness, sacredness, and sanctification.",
        quranUsage = "Al-Quddus (The Holy) is Allah's name. Ruh al-Qudus is Holy Spirit (Jibril).",
        notes = "Quds (Jerusalem) is the Holy city."
      ),
      RootMeaningData(
        root = "م-ج-د",
        primaryMeaning = "glory, honor, nobility",
        extendedMeaning = "Glory, honor, and noble generosity.",
        quranUsage = "Al-Majid (The Glorious) is Allah's name. 'Quran Majid' - Glorious Quran.",
        notes = "Majid implies glorious and generous together."
      ),

      // === EATING AND DRINKING ===
      RootMeaningData(
        root = "أ-ك-ل",
        primaryMeaning = "to eat, food, consume",
        extendedMeaning = "Eating food, consuming. Also devouring (wealth, etc.).",
        quranUsage = "'Eat (kulu) from the good things.' 'Do not devour each other's wealth unjustly.'",
        notes = "Halal food is emphasized: 'Eat of what is lawful and good.'"
      ),
      RootMeaningData(
        root = "ش-ر-ب",
        primaryMeaning = "to drink, absorb",
        extendedMeaning = "Drinking liquids. Also absorbing (like earth absorbing water).",
        quranUsage = "'Eat and drink (ishrabu).' 'They were made to drink the calf in their hearts.'",
        notes = "Paradise has rivers to drink from."
      ),
      RootMeaningData(
        root = "ط-ع-م",
        primaryMeaning = "food, to feed, taste",
        extendedMeaning = "Food, feeding others, and the taste of food.",
        quranUsage = "'They feed (yut'imun) the poor, orphan, and captive.' Ta'am is food.",
        notes = "Feeding the hungry is highly rewarded."
      ),
      RootMeaningData(
        root = "ج-و-ع",
        primaryMeaning = "hunger, to be hungry",
        extendedMeaning = "The state of hunger and need for food.",
        quranUsage = "'Who fed them against hunger (ju').' 'We will test you with hunger.'",
        notes = "Hunger is both a test and a reminder of blessings."
      ),

      // === CLOTHING AND COVERING ===
      RootMeaningData(
        root = "ل-ب-س",
        primaryMeaning = "to wear, clothing, to confuse",
        extendedMeaning = "Wearing clothes, garments. Also mixing/confusing (covering truth).",
        quranUsage = "Libas is clothing. 'Clothing of taqwa is best.' 'Do not mix truth with falsehood.'",
        notes = "Spouses are described as 'clothing' for each other."
      ),
      RootMeaningData(
        root = "س-ت-ر",
        primaryMeaning = "to cover, conceal, veil",
        extendedMeaning = "Covering and concealing, especially faults or private matters.",
        quranUsage = "Sitr is covering. Allah covers faults.",
        notes = "Covering others' faults is praised."
      ),

      // === BUILDING AND CREATION ===
      RootMeaningData(
        root = "ب-ن-ي",
        primaryMeaning = "to build, construct, son",
        extendedMeaning = "Building structures. Ibn (son) is a 'building' of the family.",
        quranUsage = "'We built (banayna) above you seven strong.' Ibn is son. Banu Isra'il.",
        notes = "Children 'build' the family's future."
      ),
      RootMeaningData(
        root = "ص-ن-ع",
        primaryMeaning = "to make, craft, manufacture",
        extendedMeaning = "Making things with skill and craftsmanship.",
        quranUsage = "'The making (sun') of Allah who perfected everything.'",
        notes = "Allah is the best of makers."
      ),
      RootMeaningData(
        root = "ف-ط-ر",
        primaryMeaning = "to create, originate, natural disposition",
        extendedMeaning = "Original creation, splitting/breaking open, and natural state.",
        quranUsage = "Fatir is Originator (Surah 35). Fitrah is natural disposition.",
        notes = "Every child is born upon fitrah (natural inclination to truth)."
      ),

      // === STRIVING AND FIGHTING ===
      RootMeaningData(
        root = "ج-ه-د",
        primaryMeaning = "to strive, struggle, effort",
        extendedMeaning = "Exerting utmost effort in any endeavor.",
        quranUsage = "'Strive (jahidu) in Allah's cause.' Mujahadah is spiritual struggle.",
        notes = "Jihad al-nafs (struggle against self) is the greater jihad."
      ),
      RootMeaningData(
        root = "ق-ت-ل",
        primaryMeaning = "to kill, fight, combat",
        extendedMeaning = "Killing, fighting in battle, or combat.",
        quranUsage = "'Fighting (qital) is prescribed for you.' Qatl is killing.",
        notes = "Killing unjustly is among the greatest sins."
      ),
      RootMeaningData(
        root = "ح-ر-ب",
        primaryMeaning = "war, to fight",
        extendedMeaning = "War and warfare. State of hostility.",
        quranUsage = "'Those who wage war (yuharibun) against Allah and His Messenger.'",
        notes = "Interest-dealers are described as being at war with Allah."
      ),

      // === FOLLOWING AND LEADING ===
      RootMeaningData(
        root = "ت-ب-ع",
        primaryMeaning = "to follow, succession",
        extendedMeaning = "Following someone or something. Coming after in sequence.",
        quranUsage = "'Follow (ittabi') what is revealed to you.' Tabi' is follower.",
        notes = "Following the Prophet is commanded."
      ),
      RootMeaningData(
        root = "ق-د-م",
        primaryMeaning = "to precede, foot, to advance",
        extendedMeaning = "Going before, advancing forward. Qadam is foot.",
        quranUsage = "'What their hands have sent forth (qaddamat).' Muqaddimah is introduction.",
        notes = "Deeds are 'sent forward' for the Hereafter."
      ),
      RootMeaningData(
        root = "خ-ل-ف",
        primaryMeaning = "to succeed, behind, differ",
        extendedMeaning = "Coming after/behind, succeeding someone, or differing.",
        quranUsage = "Khalifah is successor/vicegerent. 'He made you successors (khala'if) on earth.'",
        notes = "Adam was made khalifah on earth."
      ),

      // === EVIL BEINGS ===
      RootMeaningData(
        root = "ش-ط-ن",
        primaryMeaning = "satan, to be far, rebellious",
        extendedMeaning = "Satan, being far from truth, and rebellion.",
        quranUsage = "Shaytan is Satan/devil - the rebellious one far from Allah's mercy.",
        notes = "Shaytan can be jinn or human who leads astray."
      ),
      RootMeaningData(
        root = "و-س-و-س",
        primaryMeaning = "to whisper, whisper evil",
        extendedMeaning = "Whispering evil suggestions. Satan's method of temptation.",
        quranUsage = "'From the evil of the whisperer (waswas).' Waswasah is Satan's whispering.",
        notes = "Seeking refuge from waswasah is in Surah An-Nas."
      ),

      // === HARM AND BENEFIT ===
      RootMeaningData(
        root = "ض-ر-ر",
        primaryMeaning = "harm, damage, adversity",
        extendedMeaning = "Harm, damage, and hardship.",
        quranUsage = "'They cannot harm (yadurru) you at all.' Darar is damage.",
        notes = "Necessity (darurah) can permit the forbidden."
      ),
      RootMeaningData(
        root = "ن-ف-ع",
        primaryMeaning = "benefit, profit, to be useful",
        extendedMeaning = "Benefit, profit, and usefulness.",
        quranUsage = "'No soul can benefit (tanfa') another.' Manfa'ah is benefit.",
        notes = "On Judgment Day, nothing benefits except faith and good deeds."
      ),
      RootMeaningData(
        root = "ش-ف-ي",
        primaryMeaning = "to heal, cure",
        extendedMeaning = "Healing, curing disease, and providing remedy.",
        quranUsage = "'We reveal from the Quran what is healing (shifa').'",
        notes = "The Quran is described as a healing."
      ),

      // === SOCIAL AND POLITICAL ===
      RootMeaningData(
        root = "م-ل-ك",
        primaryMeaning = "king, to own, kingdom",
        extendedMeaning = "Ownership, sovereignty, and kingship.",
        quranUsage = "Al-Malik (The King) is Allah's name. Mulk is kingdom/dominion.",
        notes = "Surah Al-Mulk discusses Allah's dominion over creation."
      ),
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "to gather, collect, Friday",
        extendedMeaning = "Bringing together, collecting. Unity and congregation.",
        quranUsage = "Jama'ah is congregation. Jumu'ah (Friday) is the day of gathering.",
        notes = "Jami' (mosque) is where people gather."
      ),
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "to separate, group, criterion",
        extendedMeaning = "Separating and distinguishing. A firqah is a separated group.",
        quranUsage = "Al-Furqan (The Criterion) distinguishes truth from falsehood.",
        notes = "The Quran is called Al-Furqan as it separates truth from falsehood."
      ),

      // === CERTAINTY AND DOUBT ===
      RootMeaningData(
        root = "ي-ق-ن",
        primaryMeaning = "certainty, to be certain",
        extendedMeaning = "Absolute certainty that removes all doubt.",
        quranUsage = "'Worship your Lord until certainty (yaqeen) comes to you.'",
        notes = "Yaqeen often refers to death - the certainty that comes to all."
      ),
      RootMeaningData(
        root = "ظ-ن-ن",
        primaryMeaning = "assumption, to think, suspect",
        extendedMeaning = "Assumption or supposition. Can be positive or negative.",
        quranUsage = "'Avoid much assumption (zann), indeed some assumption is sin.'",
        notes = "Zann of Allah should always be positive."
      ),

      // === OBEDIENCE AND SIN ===
      RootMeaningData(
        root = "ذ-ن-ب",
        primaryMeaning = "sin, fault, tail",
        extendedMeaning = "Sin or fault. Also tail (what follows behind, like consequences).",
        quranUsage = "'Forgive us our sins (dhunub).' Sins follow a person like a tail.",
        notes = "Istighfar seeks forgiveness for dhunub."
      ),
      RootMeaningData(
        root = "إ-ث-م",
        primaryMeaning = "sin, guilt, crime",
        extendedMeaning = "Sin that burdens the soul with guilt.",
        quranUsage = "'Avoid much assumption, indeed some assumption is sin (ithm).'",
        notes = "Ithm is general sin, distinct from specific terms."
      ),

      // === COLORS ===
      RootMeaningData(
        root = "ب-ي-ض",
        primaryMeaning = "white, bright, egg",
        extendedMeaning = "Whiteness, brightness, and purity. Also egg.",
        quranUsage = "'Faces will be white (tabyaddu)' - believers on Judgment Day. Bayd is egg.",
        notes = "White faces indicate joy and success on Judgment Day."
      ),
      RootMeaningData(
        root = "س-و-د",
        primaryMeaning = "black, dark, master",
        extendedMeaning = "Blackness, darkness. Also master/leader (sayyid).",
        quranUsage = "'Faces will be black (taswaddu)' - disbelievers. Sayyid is master/leader.",
        notes = "Black faces indicate grief and failure on Judgment Day."
      ),
      RootMeaningData(
        root = "خ-ض-ر",
        primaryMeaning = "green, verdant",
        extendedMeaning = "Green color, freshness, and vegetation.",
        quranUsage = "'Wearing green (khudur) garments' - in Paradise. Al-Khidr is the green one.",
        notes = "Green is associated with Paradise and blessing."
      ),
      RootMeaningData(
        root = "ح-م-ر",
        primaryMeaning = "red, donkey",
        extendedMeaning = "Red color. Also donkey (himar).",
        quranUsage = "'Red (humr) and white, varying colors.' Himar is donkey.",
        notes = "Red is mentioned in describing mountains and creation's diversity."
      ),
      RootMeaningData(
        root = "ص-ف-ر",
        primaryMeaning = "yellow, empty",
        extendedMeaning = "Yellow color. Also emptiness.",
        quranUsage = "'A yellow (safra') cow, bright in color.' Sifr is zero/empty.",
        notes = "The cow of Bani Isra'il was bright yellow."
      ),
      RootMeaningData(
        root = "ز-ر-ق",
        primaryMeaning = "blue, blind (with blue eyes)",
        extendedMeaning = "Blue color, particularly pale blue of blind eyes.",
        quranUsage = "'We will gather the criminals that Day blue (zurqa)' - blind/terrified.",
        notes = "Zurq describes the terrified appearance of criminals."
      ),

      // === PLANTS AND AGRICULTURE ===
      RootMeaningData(
        root = "ز-ر-ع",
        primaryMeaning = "to plant, crops, agriculture",
        extendedMeaning = "Planting, cultivation, and crops.",
        quranUsage = "'Is it you who plants (tazra'unahu) it or are We the planters?' Zar' is crops.",
        notes = "Allah is the true grower of all crops."
      ),
      RootMeaningData(
        root = "ح-ب-ب",
        primaryMeaning = "grain, seed, love",
        extendedMeaning = "Grain, seed, and beloved (the seed of affection).",
        quranUsage = "'The splitter of grain (habb) and date-stones.' Habb is seed/grain.",
        notes = "Love (hubb) and grain (habb) share this root."
      ),
      RootMeaningData(
        root = "ن-ب-ت",
        primaryMeaning = "to grow, plant, vegetation",
        extendedMeaning = "Growing, sprouting, and plant life.",
        quranUsage = "'We cause to grow (nunbitu) gardens.' Nabat is plant. 'A good growth (nabatan hasanan).'",
        notes = "Maryam was raised with 'good growth' by Zakariyya."
      ),
      RootMeaningData(
        root = "ن-خ-ل",
        primaryMeaning = "palm tree, date palm",
        extendedMeaning = "Date palm tree - symbol of blessing and provision.",
        quranUsage = "'Shake the trunk of the palm tree (nakhlah)' - to Maryam. Nakhil is palm trees.",
        notes = "Palm trees are frequently mentioned as signs of blessing."
      ),
      RootMeaningData(
        root = "ع-ن-ب",
        primaryMeaning = "grape",
        extendedMeaning = "Grapes and grapevines.",
        quranUsage = "'Gardens of grapes ('inab).' 'From it you take intoxicant and good provision.'",
        notes = "Grapes are mentioned among Paradise fruits."
      ),
      RootMeaningData(
        root = "ز-ي-ت",
        primaryMeaning = "olive, oil",
        extendedMeaning = "Olive and olive oil.",
        quranUsage = "'By the fig and the olive (zaytun).' 'Lit from a blessed olive tree.'",
        notes = "Olive oil is described in the Verse of Light."
      ),
      RootMeaningData(
        root = "ت-ي-ن",
        primaryMeaning = "fig",
        extendedMeaning = "Fig fruit.",
        quranUsage = "'By the fig (tin) and the olive.' At-Tin is Surah 95.",
        notes = "The fig is mentioned alongside the olive in a divine oath."
      ),
      RootMeaningData(
        root = "ر-م-ن",
        primaryMeaning = "pomegranate",
        extendedMeaning = "Pomegranate fruit.",
        quranUsage = "'Therein are fruits, palm trees, and pomegranates (rumman).'",
        notes = "Pomegranates are Paradise fruits."
      ),

      // === TIME AND SEASONS ===
      RootMeaningData(
        root = "س-ن-ن",
        primaryMeaning = "year, way, practice",
        extendedMeaning = "Year, established way, and normative practice (Sunnah).",
        quranUsage = "Sanah is year. Sunnah is established practice. 'The Sunnah of Allah - no change.'",
        notes = "Allah's sunnah (way) never changes."
      ),
      RootMeaningData(
        root = "ع-ص-ر",
        primaryMeaning = "time, era, afternoon, to squeeze",
        extendedMeaning = "Time/era, afternoon time, and pressing/squeezing.",
        quranUsage = "'By time (al-'asr), mankind is in loss.' 'Asr is also afternoon prayer time.",
        notes = "Surah Al-'Asr emphasizes the value of time."
      ),
      RootMeaningData(
        root = "ش-ه-ر",
        primaryMeaning = "month, to make known",
        extendedMeaning = "Month (lunar cycle) and making something famous/known.",
        quranUsage = "'The month (shahr) of Ramadan.' Mashhur is famous/well-known.",
        notes = "Months are determined by moon phases."
      ),
      RootMeaningData(
        root = "ح-و-ل",
        primaryMeaning = "year, around, to change",
        extendedMeaning = "Full year, surroundings, and transformation.",
        quranUsage = "'Two complete years (hawlayn)' for breastfeeding. Hawla means around.",
        notes = "Hawl is a complete year cycle."
      ),

      // === WRITING AND READING ===
      RootMeaningData(
        root = "ق-ل-م",
        primaryMeaning = "pen, to cut/trim",
        extendedMeaning = "Pen (originally a trimmed reed) for writing.",
        quranUsage = "'By the pen (qalam) and what they write.' Al-Qalam is Surah 68.",
        notes = "The pen was among the first things created."
      ),
      RootMeaningData(
        root = "ص-ح-ف",
        primaryMeaning = "pages, scriptures, sheets",
        extendedMeaning = "Written pages and scriptures.",
        quranUsage = "'Scriptures (suhuf) of Ibrahim and Musa.' Sahifah is page/scripture.",
        notes = "Earlier prophets also received written revelation."
      ),
      RootMeaningData(
        root = "س-ط-ر",
        primaryMeaning = "line, to write in lines",
        extendedMeaning = "Lines of writing, to write systematically.",
        quranUsage = "'By the pen and what they write in lines (yasturun).' Asatir are written tales.",
        notes = "Satr is a line of text."
      ),
      RootMeaningData(
        root = "ر-ق-م",
        primaryMeaning = "to write, number, mark",
        extendedMeaning = "Writing, numbering, and marking.",
        quranUsage = "'A written (marqum) register.' Raqm is number/figure.",
        notes = "Kitab marqoom is a numbered/written record."
      ),

      // === EXAMPLES AND SIMILITUDE ===
      RootMeaningData(
        root = "م-ث-ل",
        primaryMeaning = "example, likeness, parable",
        extendedMeaning = "Example, similitude, and parable for instruction.",
        quranUsage = "'Allah strikes examples (amthal) for people.' Mathal is parable/example.",
        notes = "The Quran uses many parables to illustrate truth."
      ),
      RootMeaningData(
        root = "ش-ب-ه",
        primaryMeaning = "similar, resemblance, doubt",
        extendedMeaning = "Similarity, resemblance, and ambiguity.",
        quranUsage = "'It was made to appear (shubiha) to them' - about Isa. Mutashabih is ambiguous.",
        notes = "Some Quran verses are clear (muhkam), others ambiguous (mutashabih)."
      ),

      // === WILL AND DESIRE ===
      RootMeaningData(
        root = "ش-ي-ء",
        primaryMeaning = "thing, to will",
        extendedMeaning = "Thing/object and willing/wanting.",
        quranUsage = "'If Allah wills (sha'a).' Shay' is thing. Mashi'ah is will.",
        notes = "Everything happens by Allah's will (mashi'ah)."
      ),
      RootMeaningData(
        root = "أ-ر-د",
        primaryMeaning = "to want, intend, will",
        extendedMeaning = "Wanting, intending, and willing.",
        quranUsage = "'When He intends (arada) something.' Iradah is will/intention.",
        notes = "Allah's iradah (will) is absolute."
      ),
      RootMeaningData(
        root = "ر-غ-ب",
        primaryMeaning = "to desire, wish, turn away",
        extendedMeaning = "Desiring something (raghiba fi) or turning away from (raghiba 'an).",
        quranUsage = "'To their Lord they turn in desire (yarghabun).' Raghbah is desire.",
        notes = "Can mean desire toward or aversion from, depending on preposition."
      ),

      // === REMAINING AND LEAVING ===
      RootMeaningData(
        root = "ب-ق-ي",
        primaryMeaning = "to remain, lasting, eternal",
        extendedMeaning = "Remaining, lasting, and eternal existence.",
        quranUsage = "'What remains (yabqa) with Allah is better.' Al-Baqi is The Everlasting.",
        notes = "Only Allah is truly everlasting (Al-Baqi)."
      ),
      RootMeaningData(
        root = "ذ-ه-ب",
        primaryMeaning = "to go, gold",
        extendedMeaning = "Going away, departing. Also gold (dhahab).",
        quranUsage = "'He went away (dhahaba) angry.' Dhahab is gold.",
        notes = "Yunus went away angry before being swallowed."
      ),
      RootMeaningData(
        root = "م-ض-ي",
        primaryMeaning = "to pass, proceed, go on",
        extendedMeaning = "Passing by, proceeding forward.",
        quranUsage = "'What has passed (ma mada) is past.' Madi is past.",
        notes = "The past (al-madi) is gone; focus on future deeds."
      ),

      // === HIDDEN AND UNSEEN ===
      RootMeaningData(
        root = "غ-ي-ب",
        primaryMeaning = "unseen, hidden, absent",
        extendedMeaning = "The unseen realm, what is hidden from perception.",
        quranUsage = "'Who believe in the unseen (ghayb).' Ghayb includes future, angels, Paradise, Hell.",
        notes = "Belief in ghayb is fundamental to faith."
      ),
      RootMeaningData(
        root = "ب-ط-ن",
        primaryMeaning = "belly, inner, hidden",
        extendedMeaning = "Belly/stomach, the inner aspect, and what is hidden.",
        quranUsage = "Al-Batin (The Hidden) is Allah's name. Batn is belly. Batin is inner meaning.",
        notes = "Allah is both Zahir (Manifest) and Batin (Hidden)."
      ),

      // === FIRMNESS AND STABILITY ===
      RootMeaningData(
        root = "ث-ب-ت",
        primaryMeaning = "firm, stable, to establish",
        extendedMeaning = "Firmness, stability, and being established.",
        quranUsage = "'Allah keeps firm (yuthabbitu) those who believe.' Thabit is firm/stable.",
        notes = "Allah stabilizes believers with firm words."
      ),
      RootMeaningData(
        root = "ر-س-خ",
        primaryMeaning = "firmly rooted, deeply grounded",
        extendedMeaning = "Being deeply rooted and firmly established in knowledge.",
        quranUsage = "'Those firmly rooted (rasikhun) in knowledge.' Rusukh is being grounded.",
        notes = "Scholars firmly rooted in knowledge understand the Quran properly."
      ),
      RootMeaningData(
        root = "م-ك-ن",
        primaryMeaning = "to establish, enable, place",
        extendedMeaning = "Establishing firmly, enabling, and giving power.",
        quranUsage = "'We established (makkanna) them on earth.' Tamkin is establishment/empowerment.",
        notes = "Allah establishes believers when they establish His religion."
      ),

      // === HOT AND COLD ===
      RootMeaningData(
        root = "ح-ر-ر",
        primaryMeaning = "heat, hot, free",
        extendedMeaning = "Heat, hotness, and freedom (from slavery).",
        quranUsage = "'They said: This is but heat (harr) of travel.' Hurr is free person.",
        notes = "The hypocrites complained of heat when called to battle."
      ),
      RootMeaningData(
        root = "ب-ر-د",
        primaryMeaning = "cold, cool, hail",
        extendedMeaning = "Coldness, cooling, and hail.",
        quranUsage = "'We said: O fire, be cool (bardan) and safe for Ibrahim.' Barad is hail.",
        notes = "Fire became cool for Ibrahim by Allah's command."
      ),
      RootMeaningData(
        root = "ش-ت-و",
        primaryMeaning = "winter, cold season",
        extendedMeaning = "Winter season, the cold time.",
        quranUsage = "'The journey (rihlah) of winter (shita') and summer.' Shita' is winter.",
        notes = "Quraysh's winter journey was to Yemen."
      ),
      RootMeaningData(
        root = "ص-ي-ف",
        primaryMeaning = "summer, hot season",
        extendedMeaning = "Summer season, the hot time.",
        quranUsage = "'The journey of winter and summer (sayf).' Sayf is summer.",
        notes = "Quraysh's summer journey was to Syria."
      ),

      // === EMOTIONS AND STATES ===
      RootMeaningData(
        root = "غ-ي-ر",
        primaryMeaning = "jealousy, other, to change",
        extendedMeaning = "Jealousy (protective), other/different, and change.",
        quranUsage = "Ghayrah is jealousy. Ghayr means other/different. Taghyir is change.",
        notes = "Allah has ghayrah (protective jealousy) for His limits."
      ),
      RootMeaningData(
        root = "ح-س-د",
        primaryMeaning = "envy, to envy",
        extendedMeaning = "Envy - wishing removal of blessings from others.",
        quranUsage = "'From the evil of an envier (hasid) when he envies.' Hasad is envy.",
        notes = "We seek refuge from the evil of enviers."
      ),
      RootMeaningData(
        root = "ك-ر-ه",
        primaryMeaning = "to dislike, hate, aversion",
        extendedMeaning = "Disliking, hating, and having aversion to.",
        quranUsage = "'You may dislike (takrahu) something good for you.' Kurh is aversion.",
        notes = "What we dislike may contain hidden good."
      ),
      RootMeaningData(
        root = "ط-م-ء-ن",
        primaryMeaning = "tranquility, contentment, peace",
        extendedMeaning = "Inner peace, tranquility, and contentment of heart.",
        quranUsage = "'Hearts find rest (tatma'innu) in Allah's remembrance.' Itmi'nan is tranquility.",
        notes = "True peace comes only through remembering Allah."
      ),

      // === BEAUTY AND UGLINESS ===
      RootMeaningData(
        root = "ج-م-ل",
        primaryMeaning = "beauty, camel, to beautify",
        extendedMeaning = "Beauty, camel (noble animal), and beautification.",
        quranUsage = "'Beautiful patience (sabrun jamil).' Jamal is beauty. Jamal is camel.",
        notes = "Jamil describes beautiful patience without complaint."
      ),
      RootMeaningData(
        root = "ز-ي-ن",
        primaryMeaning = "adornment, beauty, to beautify",
        extendedMeaning = "Adornment, decoration, and beautification.",
        quranUsage = "'We adorned (zayyanna) the lower heaven with stars.' Zinah is adornment.",
        notes = "Satan beautifies evil deeds to make them attractive."
      ),
      RootMeaningData(
        root = "ق-ب-ح",
        primaryMeaning = "ugly, bad, repulsive",
        extendedMeaning = "Ugliness, badness, and being repulsive.",
        quranUsage = "'Evil (qubh) is the abode of the arrogant.' Qabih is ugly/bad.",
        notes = "Qubh is the opposite of husn (beauty/goodness)."
      ),

      // === SPEED AND SLOWNESS ===
      RootMeaningData(
        root = "س-ر-ع",
        primaryMeaning = "speed, haste, quick",
        extendedMeaning = "Speed, quickness, and haste.",
        quranUsage = "'Race (sari'u) to forgiveness.' Sur'ah is speed. Musri' is speeding.",
        notes = "We should hasten to good deeds."
      ),
      RootMeaningData(
        root = "ع-ج-ل",
        primaryMeaning = "haste, to hasten, calf",
        extendedMeaning = "Hastiness, hurrying, and calf (young cow).",
        quranUsage = "'Man was created hasty ('ajul).' 'Ijl is calf - the golden calf.",
        notes = "Humans are naturally hasty; patience is commanded."
      ),
      RootMeaningData(
        root = "ب-ط-ء",
        primaryMeaning = "slowness, to be slow",
        extendedMeaning = "Slowness, delay, and being slow.",
        quranUsage = "'Among you is he who is slow (yubatti').' Bat' is slowness.",
        notes = "Some are slow to join in good causes."
      ),

      // === STRAIGHTNESS AND CROOKEDNESS ===
      RootMeaningData(
        root = "ع-و-ج",
        primaryMeaning = "crookedness, deviation",
        extendedMeaning = "Being crooked, deviated, or bent.",
        quranUsage = "'No crookedness ('iwaj) therein' - describing the Quran. A'waj is crooked.",
        notes = "The Quran is perfectly straight with no deviation."
      ),
      RootMeaningData(
        root = "س-و-ي",
        primaryMeaning = "equal, level, to fashion",
        extendedMeaning = "Being equal, level, and properly fashioned.",
        quranUsage = "'Who created and fashioned (sawwa).' Sawa' is equal. Taswiyah is leveling.",
        notes = "Allah fashioned humans in perfect proportion."
      ),

      // === FULLNESS AND EMPTINESS ===
      RootMeaningData(
        root = "م-ل-ء",
        primaryMeaning = "fullness, to fill, assembly",
        extendedMeaning = "Being full, filling, and an assembly of chiefs.",
        quranUsage = "'If you filled (mala'ta) the earth with gold.' Mala' is assembly of chiefs.",
        notes = "Mala' refers to the elite assembly of a community."
      ),
      RootMeaningData(
        root = "ف-ر-غ",
        primaryMeaning = "empty, to be free, to pour",
        extendedMeaning = "Being empty, free (from occupation), and pouring out.",
        quranUsage = "'When you are free (faraghta), strive in worship.' Faragh is emptiness.",
        notes = "Free time should be devoted to worship."
      ),

      // === PURITY AND MIXTURE ===
      RootMeaningData(
        root = "ص-ف-و",
        primaryMeaning = "pure, clear, select",
        extendedMeaning = "Purity, clarity, and being selected/chosen.",
        quranUsage = "'Drink pure (safiya) and pleasant.' Safi is pure. Istifa' is selection.",
        notes = "Allah selects (yastafi) whom He wills for His message."
      ),
      RootMeaningData(
        root = "خ-ل-ط",
        primaryMeaning = "to mix, mingle, confuse",
        extendedMeaning = "Mixing, mingling, and confusion.",
        quranUsage = "'They mixed (khalatu) good deeds with bad.' Khalt is mixing.",
        notes = "Some mix good and bad deeds together."
      ),

      // === HARDNESS AND SOFTNESS ===
      RootMeaningData(
        root = "ق-س-و",
        primaryMeaning = "hardness, cruelty, to harden",
        extendedMeaning = "Hardness of heart, cruelty.",
        quranUsage = "'Their hearts hardened (qasat).' Qaswah is hardness. Qasi is hard-hearted.",
        notes = "Hard hearts do not accept guidance."
      ),
      RootMeaningData(
        root = "ل-ي-ن",
        primaryMeaning = "soft, gentle, flexible",
        extendedMeaning = "Softness, gentleness, and flexibility.",
        quranUsage = "'Speak to him with soft (layyin) speech.' Lin is softness.",
        notes = "Musa was told to speak gently to Firawn."
      ),
      RootMeaningData(
        root = "ر-ق-ق",
        primaryMeaning = "thin, delicate, tender",
        extendedMeaning = "Thinness, delicacy, and tender-heartedness.",
        quranUsage = "'Their skins shiver (taqsha'irru).' Riqq is thinness. Riqqa is tenderness.",
        notes = "Believers' hearts are tender to Allah's words."
      ),

      // === WET AND DRY ===
      RootMeaningData(
        root = "ر-ط-ب",
        primaryMeaning = "fresh, moist, dates",
        extendedMeaning = "Freshness, moisture, and fresh dates.",
        quranUsage = "'Shake the palm, it will drop fresh dates (rutab).' Ratb is fresh/moist.",
        notes = "Fresh dates fell for Maryam during labor."
      ),
      RootMeaningData(
        root = "ي-ب-س",
        primaryMeaning = "dry, withered",
        extendedMeaning = "Dryness, being withered.",
        quranUsage = "'Green or dry (yabis)' - all recorded in a clear book. Yabis is dry.",
        notes = "Nothing escapes Allah's knowledge - fresh or dry."
      ),

      // === CONNECTION AND SEPARATION ===
      RootMeaningData(
        root = "و-ص-ل",
        primaryMeaning = "to connect, join, reach",
        extendedMeaning = "Connecting, joining, and maintaining ties.",
        quranUsage = "'Join (yasilu) what Allah commanded to be joined.' Silah is connection.",
        notes = "Silat al-rahim is maintaining family ties."
      ),
      RootMeaningData(
        root = "ق-ط-ع",
        primaryMeaning = "to cut, sever, disconnect",
        extendedMeaning = "Cutting, severing, and disconnecting.",
        quranUsage = "'They cut (yaqta'un) what Allah commanded to be joined.' Qat' is cutting.",
        notes = "Severing family ties is severely condemned."
      ),
      RootMeaningData(
        root = "ف-ص-ل",
        primaryMeaning = "to separate, distinguish, decide",
        extendedMeaning = "Separating, distinguishing, and making decisive judgment.",
        quranUsage = "'The Day of Decision (fasl).' Tafsil is detailed explanation. Fasl is separation.",
        notes = "Judgment Day separates truth from falsehood decisively."
      ),

      // === WORSHIP CONCEPTS ===
      RootMeaningData(
        root = "ع-ك-ف",
        primaryMeaning = "to devote, retreat, i'tikaf",
        extendedMeaning = "Devotion, spiritual retreat, and staying in mosque.",
        quranUsage = "'While you are in devotion (akifun) in the mosques.' I'tikaf is spiritual retreat.",
        notes = "I'tikaf in mosque, especially in Ramadan's last ten days."
      ),
      RootMeaningData(
        root = "ن-ذ-ر",
        primaryMeaning = "vow, to warn, dedicate",
        extendedMeaning = "Making a vow, warning, and dedicating.",
        quranUsage = "'I have vowed (nadhartu) to the Rahman a fast.' Nadhir is warner. Nadhr is vow.",
        notes = "Prophets are nadhir (warners) to their people."
      ),

      // === PARTS AND WHOLE ===
      RootMeaningData(
        root = "ج-ز-ء",
        primaryMeaning = "part, portion",
        extendedMeaning = "A part or portion of something.",
        quranUsage = "'To each gate is a portion (juz') of them.' Juz' is part/section.",
        notes = "The Quran is divided into 30 ajza' (parts)."
      ),
      RootMeaningData(
        root = "ك-ل-ل",
        primaryMeaning = "all, whole, every",
        extendedMeaning = "Totality, entirety, and everything.",
        quranUsage = "'Every (kull) soul shall taste death.' Kull means all/every.",
        notes = "One of the most frequent words in the Quran."
      ),
      RootMeaningData(
        root = "ب-ع-ض",
        primaryMeaning = "some, part, each other",
        extendedMeaning = "Some of something, a part.",
        quranUsage = "'Some of you (ba'dukum) are enemies of others.' Ba'd means some/part.",
        notes = "Ba'duhum ba'dan - some to others, indicates reciprocity."
      ),

      // === MORE FAMILY ===
      RootMeaningData(
        root = "ب-ن-ت",
        primaryMeaning = "daughter",
        extendedMeaning = "Daughter, female offspring.",
        quranUsage = "'Daughters (banat) of your maternal aunts.' Bint is daughter.",
        notes = "The Prophet had four daughters."
      ),
      RootMeaningData(
        root = "أ-خ-ت",
        primaryMeaning = "sister",
        extendedMeaning = "Sister, female sibling.",
        quranUsage = "'His sister (ukht) said...' - Musa's sister. Ukht is sister.",
        notes = "Musa's sister followed his basket in the river."
      ),
      RootMeaningData(
        root = "ع-م-م",
        primaryMeaning = "uncle (paternal), general",
        extendedMeaning = "Paternal uncle, and general/universal.",
        quranUsage = "'Your fathers, your sons, your brothers, your paternal uncles ('amm).'",
        notes = "Am is paternal uncle; 'amm is also general/common."
      ),
      RootMeaningData(
        root = "خ-و-ل",
        primaryMeaning = "uncle (maternal), to grant",
        extendedMeaning = "Maternal uncle, and granting/bestowing.",
        quranUsage = "'Your maternal uncles (khwal).' Khal is maternal uncle.",
        notes = "Maternal relatives have special status in Islam."
      ),

      // === SEEING AND SHOWING ===
      RootMeaningData(
        root = "ر-ء-ي",
        primaryMeaning = "to see, vision, opinion",
        extendedMeaning = "Seeing, vision, and opinion/view.",
        quranUsage = "'Have you seen (ara'ayta)?' Ra'y is opinion. Ru'yah is vision.",
        notes = "Ru'yah of Allah is a blessing for believers in Paradise."
      ),
      RootMeaningData(
        root = "ش-ه-د",
        primaryMeaning = "to witness, testify, present",
        extendedMeaning = "Witnessing, testifying, and being present.",
        quranUsage = "'Allah witnesses (shahida) that there is no god but He.' Shahid is witness.",
        notes = "Already covered - emphasizing witnessing aspect."
      ),
      RootMeaningData(
        root = "ع-ر-ض",
        primaryMeaning = "to show, present, width",
        extendedMeaning = "Presenting, showing, and width/breadth.",
        quranUsage = "'They will be presented ('uridu) before your Lord.' 'Ard is width. Ma'rad is display.",
        notes = "All will be presented before Allah for judgment."
      ),

      // === NECESSITY AND OBLIGATION ===
      RootMeaningData(
        root = "و-ج-ب",
        primaryMeaning = "necessary, obligatory, to fall",
        extendedMeaning = "Being necessary, obligatory, and falling due.",
        quranUsage = "'When their sides fall (wajabat).' Wajib is obligatory.",
        notes = "Wajib in fiqh means religiously obligatory."
      ),
      RootMeaningData(
        root = "ف-ر-ض",
        primaryMeaning = "obligation, to ordain, portion",
        extendedMeaning = "Religious obligation, ordaining, and fixed portion.",
        quranUsage = "'Allah has ordained (farada) for you.' Faridah is obligation. Fard is duty.",
        notes = "Fard is a religious duty that must be performed."
      ),

      // === ABUNDANCE AND SCARCITY ===
      RootMeaningData(
        root = "و-ف-ر",
        primaryMeaning = "abundance, plentiful",
        extendedMeaning = "Abundance, being plentiful.",
        quranUsage = "'Reward most abundant (awfar).' Wafir is abundant.",
        notes = "Allah's rewards are always abundant."
      ),
      RootMeaningData(
        root = "ش-ح-ح",
        primaryMeaning = "stinginess, greed",
        extendedMeaning = "Stinginess, miserliness, and greed.",
        quranUsage = "'Whoever is protected from the stinginess (shuhh) of his soul.' Shahih is stingy.",
        notes = "Greed of the soul must be overcome."
      ),
      RootMeaningData(
        root = "ب-خ-ل",
        primaryMeaning = "miserliness, to be stingy",
        extendedMeaning = "Being miserly, stinginess.",
        quranUsage = "'Do not be stingy (tabkhal) nor extravagant.' Bukhl is miserliness.",
        notes = "Balance between stinginess and extravagance is commanded."
      ),

      // === MORE BODY PARTS ===
      RootMeaningData(
        root = "أ-ذ-ن",
        primaryMeaning = "ear, permission, to announce",
        extendedMeaning = "Ear (for hearing), permission, and announcing.",
        quranUsage = "'In their ears (adhanihim) is deafness.' Udhun is ear. Idhn is permission. Adhan is call to prayer.",
        notes = "The adhan announces prayer time to the ears of believers."
      ),
      RootMeaningData(
        root = "أ-ن-ف",
        primaryMeaning = "nose, self, pride",
        extendedMeaning = "Nose, oneself, and pride/disdain.",
        quranUsage = "'The nose (anf) for a nose' - in retribution laws. Anifa means to disdain.",
        notes = "Anf also relates to self-respect and pride."
      ),
      RootMeaningData(
        root = "ش-ف-ه",
        primaryMeaning = "lip",
        extendedMeaning = "Lips - organs of speech.",
        quranUsage = "'Two lips (shafatayn)' - among Allah's blessings. Shafah is lip.",
        notes = "Lips are mentioned as blessings enabling speech."
      ),
      RootMeaningData(
        root = "ف-م-م",
        primaryMeaning = "mouth",
        extendedMeaning = "Mouth - organ of eating and speaking.",
        quranUsage = "'They say with their mouths (afwah) what is not in their hearts.' Fam is mouth.",
        notes = "Hypocrites' mouths contradict their hearts."
      ),
      RootMeaningData(
        root = "ج-ب-ه",
        primaryMeaning = "forehead, front",
        extendedMeaning = "Forehead - the noble part placed in prostration.",
        quranUsage = "'Their foreheads (jibah), sides, and backs will be branded.' Jabhah is forehead.",
        notes = "The forehead touches the ground in sujud."
      ),
      RootMeaningData(
        root = "ع-ن-ق",
        primaryMeaning = "neck",
        extendedMeaning = "Neck - connection between head and body.",
        quranUsage = "'Shackles on their necks ('anaq).' 'Unuq is neck.",
        notes = "Freeing a neck (raqabah) means freeing a slave."
      ),
      RootMeaningData(
        root = "ظ-ه-ر",
        primaryMeaning = "back, to appear, noon",
        extendedMeaning = "Back (body part), appearing, and midday.",
        quranUsage = "'They turned their backs (zuhur).' Zahara is to appear. Zuhr is noon.",
        notes = "Already partially covered - adding back meaning."
      ),
      RootMeaningData(
        root = "ر-ج-ل",
        primaryMeaning = "foot, leg, man",
        extendedMeaning = "Foot/leg, and man (one who walks).",
        quranUsage = "'Wipe over your feet (arjul).' Rajul is man. Rijl is foot.",
        notes = "Feet are wiped in wudu according to some readings."
      ),

      // === METALS AND MATERIALS ===
      RootMeaningData(
        root = "ح-د-د",
        primaryMeaning = "iron, limit, sharp",
        extendedMeaning = "Iron, limits/boundaries, and sharpness.",
        quranUsage = "Al-Hadid (Iron) is Surah 57. 'We sent down iron (hadid).' Hudud are Allah's limits.",
        notes = "Iron was sent down as a blessing with great benefits."
      ),
      RootMeaningData(
        root = "ف-ض-ض",
        primaryMeaning = "silver",
        extendedMeaning = "Silver - precious metal.",
        quranUsage = "'Vessels of silver (fiddah)' - in Paradise. Fiddah is silver.",
        notes = "Paradise contains vessels of gold and silver."
      ),
      RootMeaningData(
        root = "ن-ح-س",
        primaryMeaning = "copper, brass, bad luck",
        extendedMeaning = "Copper/brass metal, and ill-fortune.",
        quranUsage = "'Flames of fire and smoke (nuhass).' Nahs is bad luck.",
        notes = "Molten copper will be poured on the guilty."
      ),
      RootMeaningData(
        root = "ت-ر-ب",
        primaryMeaning = "dust, soil, earth",
        extendedMeaning = "Dust, soil - the material of human creation.",
        quranUsage = "'We created you from dust (turab).' Turab is soil/dust.",
        notes = "Humans were created from dust and return to it."
      ),
      RootMeaningData(
        root = "ط-ي-ن",
        primaryMeaning = "clay, mud",
        extendedMeaning = "Clay - dust mixed with water.",
        quranUsage = "'We created man from clay (tin).' Tin is clay.",
        notes = "Adam was fashioned from clay."
      ),
      RootMeaningData(
        root = "ح-م-ء",
        primaryMeaning = "black mud, altered clay",
        extendedMeaning = "Black mud, clay that has been altered.",
        quranUsage = "'From black mud (hama') altered.' Hama' is dark mud.",
        notes = "Describes the stage of human creation."
      ),
      RootMeaningData(
        root = "ص-ل-ص-ل",
        primaryMeaning = "dry clay, ringing",
        extendedMeaning = "Dry clay that makes a ringing sound.",
        quranUsage = "'From clay (salsal) like pottery.' Salsal is dried clay.",
        notes = "Final stage of clay before life was breathed in."
      ),

      // === MORE ANIMALS ===
      RootMeaningData(
        root = "أ-س-د",
        primaryMeaning = "lion",
        extendedMeaning = "Lion - symbol of strength and courage.",
        quranUsage = "'As if they were fleeing from a lion (qaswarah/asad).'",
        notes = "The fleeing from truth is compared to fleeing from a lion."
      ),
      RootMeaningData(
        root = "ذ-ء-ب",
        primaryMeaning = "wolf",
        extendedMeaning = "Wolf - predatory animal.",
        quranUsage = "'I fear the wolf (dhi'b) will eat him' - Ya'qub about Yusuf.",
        notes = "The brothers' false excuse for Yusuf's disappearance."
      ),
      RootMeaningData(
        root = "ف-ر-س",
        primaryMeaning = "horse, Persia",
        extendedMeaning = "Horse, and Persia/Persians.",
        quranUsage = "'Horses (khayl), mules, and donkeys.' Faris is horseman/Persia.",
        notes = "Horses are mentioned for their beauty and use in battle."
      ),
      RootMeaningData(
        root = "خ-ي-ل",
        primaryMeaning = "horse, imagination",
        extendedMeaning = "Horses, and imagination/fancy.",
        quranUsage = "'Horses (khayl) and mules.' Khayal is imagination.",
        notes = "Khayl specifically refers to cavalry horses."
      ),
      RootMeaningData(
        root = "ب-غ-ل",
        primaryMeaning = "mule",
        extendedMeaning = "Mule - hybrid animal.",
        quranUsage = "'Horses, mules (bighal), and donkeys.' Baghl is mule.",
        notes = "Mules are mentioned among riding animals."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "crow, west, strange",
        extendedMeaning = "Crow/raven, the west, and being strange/foreign.",
        quranUsage = "'Allah sent a crow (ghurab)' - to show Qabil burial. Gharb is west. Gharib is stranger.",
        notes = "A crow taught the first burial to Qabil."
      ),
      RootMeaningData(
        root = "ه-د-ه-د",
        primaryMeaning = "hoopoe bird",
        extendedMeaning = "Hoopoe - the bird of Sulaiman.",
        quranUsage = "'He inspected the birds and said: Why do I not see the hoopoe (hudhud)?'",
        notes = "The hoopoe brought news of the Queen of Sheba."
      ),
      RootMeaningData(
        root = "ص-ر-د",
        primaryMeaning = "cold, birds in rows",
        extendedMeaning = "Coldness, and birds arranged in rows.",
        quranUsage = "'Birds in rows (saffat).' Sard relates to cold.",
        notes = "Birds flying in formation glorify Allah."
      ),

      // === DIRECTIONS ===
      RootMeaningData(
        root = "ش-ر-ق",
        primaryMeaning = "east, sunrise, to shine",
        extendedMeaning = "East, sunrise, and shining.",
        quranUsage = "'Lord of the east (mashriq) and west.' Sharq is east. Shurooq is sunrise.",
        notes = "Allah is Lord of all directions."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "west, sunset, strange",
        extendedMeaning = "West, sunset, and being foreign.",
        quranUsage = "'Lord of the east and west (maghrib).' Ghurub is sunset.",
        notes = "Already partially covered - emphasizing direction."
      ),
      RootMeaningData(
        root = "ي-م-ن",
        primaryMeaning = "right, blessing, Yemen",
        extendedMeaning = "Right side, blessing, and Yemen.",
        quranUsage = "'Companions of the right (yamin).' Yamin is right hand. Yumn is blessing.",
        notes = "The right side is associated with blessing and honor."
      ),
      RootMeaningData(
        root = "ش-م-ل",
        primaryMeaning = "left, north, to include",
        extendedMeaning = "Left side, north, and encompassing.",
        quranUsage = "'Companions of the left (shimal).' Shimal is left. Shamil is comprehensive.",
        notes = "The left side is associated with misfortune."
      ),
      RootMeaningData(
        root = "ق-ب-ل",
        primaryMeaning = "before, front, to accept, qiblah",
        extendedMeaning = "Before (in time/place), facing, accepting, and prayer direction.",
        quranUsage = "'Turn your face toward the Sacred Mosque (qiblah).' Qabl is before. Qabul is acceptance.",
        notes = "Qiblah is the direction Muslims face in prayer."
      ),
      RootMeaningData(
        root = "د-ب-ر",
        primaryMeaning = "behind, back, to plan",
        extendedMeaning = "Behind, the back, and planning/managing.",
        quranUsage = "'They turned their backs (adbara).' Tadbir is planning. Dubur is back.",
        notes = "Allah manages (yudabbiru) all affairs."
      ),
      RootMeaningData(
        root = "ف-و-ق",
        primaryMeaning = "above, over, superior",
        extendedMeaning = "Above, over, and being superior.",
        quranUsage = "'Above (fawqa) every knowledgeable one is One more knowing.' Fawq is above.",
        notes = "Allah is above all in every sense."
      ),
      RootMeaningData(
        root = "ت-ح-ت",
        primaryMeaning = "under, below, beneath",
        extendedMeaning = "Under, below, and beneath.",
        quranUsage = "'Rivers flowing beneath (tahta) them.' Taht is under.",
        notes = "Paradise has rivers flowing beneath its gardens."
      ),

      // === ABLUTION AND PURIFICATION ===
      RootMeaningData(
        root = "و-ض-ء",
        primaryMeaning = "ablution, brightness",
        extendedMeaning = "Ritual washing, and brightness/radiance.",
        quranUsage = "'When you rise for prayer, wash (fa'ghsilu) your faces.' Wudu' is ablution.",
        notes = "Wudu brings physical and spiritual brightness."
      ),
      RootMeaningData(
        root = "غ-س-ل",
        primaryMeaning = "to wash, bathe",
        extendedMeaning = "Washing, bathing, and ritual bath.",
        quranUsage = "'Wash (fa'ghsilu) your faces and hands.' Ghusl is ritual bath.",
        notes = "Ghusl is required after major impurity."
      ),
      RootMeaningData(
        root = "م-س-ح",
        primaryMeaning = "to wipe, anoint",
        extendedMeaning = "Wiping, anointing, and the Messiah.",
        quranUsage = "'Wipe (imsahu) over your heads.' Masih is the anointed one (Christ).",
        notes = "Isa is called Al-Masih (the Messiah/Anointed)."
      ),
      RootMeaningData(
        root = "ت-ي-م-م",
        primaryMeaning = "dry ablution, to intend",
        extendedMeaning = "Dry ablution with earth, and intending.",
        quranUsage = "'Then perform tayammum with clean earth.' Tayammum replaces wudu when water unavailable.",
        notes = "Tayammum is a mercy for travelers and the sick."
      ),

      // === CLOTHING AND GARMENTS ===
      RootMeaningData(
        root = "ث-و-ب",
        primaryMeaning = "garment, to return, reward",
        extendedMeaning = "Garment/clothing, returning, and reward.",
        quranUsage = "'Purify your garments (thiyab).' Thawb is garment. Thawab is reward.",
        notes = "Thawab (reward) is what 'returns' to you for good deeds."
      ),
      RootMeaningData(
        root = "ق-م-ص",
        primaryMeaning = "shirt",
        extendedMeaning = "Shirt - upper body garment.",
        quranUsage = "'They brought his shirt (qamis) with false blood.' - Yusuf's shirt.",
        notes = "Yusuf's shirt appears multiple times in his story."
      ),
      RootMeaningData(
        root = "ح-ج-ب",
        primaryMeaning = "veil, barrier, to cover",
        extendedMeaning = "Veil, barrier, and covering/screening.",
        quranUsage = "'Speak to them from behind a veil (hijab).' Hijab is screen/veil.",
        notes = "Hijab provides modesty and protection."
      ),
      RootMeaningData(
        root = "خ-م-ر",
        primaryMeaning = "to cover, wine, veil",
        extendedMeaning = "Covering, wine (covers the mind), and head covering.",
        quranUsage = "'Draw their head-covers (khumur) over their chests.' Khamr is wine.",
        notes = "Khamr covers/intoxicates the mind; khimar covers the head."
      ),
      RootMeaningData(
        root = "ج-ل-ب-ب",
        primaryMeaning = "outer garment, cloak",
        extendedMeaning = "Outer garment, large cloak.",
        quranUsage = "'Draw over themselves their outer garments (jalabib).' Jilbab is cloak.",
        notes = "Women are commanded to wear outer garments for modesty."
      ),

      // === HOUSING AND DWELLING ===
      RootMeaningData(
        root = "ب-ي-ت",
        primaryMeaning = "house, to stay overnight",
        extendedMeaning = "House, staying overnight, and verses (bayts).",
        quranUsage = "'The first House (bayt) established for people.' Al-Bayt is the Ka'bah.",
        notes = "Bayt Allah is the House of Allah - the Ka'bah."
      ),
      RootMeaningData(
        root = "د-و-ر",
        primaryMeaning = "house, to revolve, turn",
        extendedMeaning = "House/abode, revolving, and turning around.",
        quranUsage = "'The Home (dar) of the Hereafter.' Dar is abode. Dawr is turn/cycle.",
        notes = "Dar al-Akhirah is the eternal abode."
      ),
      RootMeaningData(
        root = "غ-ر-ف",
        primaryMeaning = "room, upper chamber",
        extendedMeaning = "Room, upper chamber, and scooping water.",
        quranUsage = "'They will have chambers (ghuraf) built above chambers.' Ghurfah is room.",
        notes = "Paradise has elevated chambers as rewards."
      ),
      RootMeaningData(
        root = "ب-و-ب",
        primaryMeaning = "door, gate, chapter",
        extendedMeaning = "Door, gate, and chapter/section.",
        quranUsage = "'Enter through the gate (bab) prostrating.' Bab is door/gate.",
        notes = "Paradise has eight gates; Hell has seven."
      ),
      RootMeaningData(
        root = "س-ق-ف",
        primaryMeaning = "roof, ceiling",
        extendedMeaning = "Roof, ceiling, and canopy.",
        quranUsage = "'The sky as a protected ceiling (saqf).' Saqf is roof.",
        notes = "The sky is described as a protective ceiling."
      ),

      // === FOOD AND DRINK ===
      RootMeaningData(
        root = "ل-ب-ن",
        primaryMeaning = "milk, brick",
        extendedMeaning = "Milk, and brick (building block).",
        quranUsage = "'Rivers of milk (laban) unchanged in taste.' Laban is milk.",
        notes = "Paradise has rivers of pure, unchanging milk."
      ),
      RootMeaningData(
        root = "ع-س-ل",
        primaryMeaning = "honey",
        extendedMeaning = "Honey - sweet healing substance.",
        quranUsage = "'Rivers of honey ('asal) purified.' 'In it is healing for people.'",
        notes = "Honey is described as having healing properties."
      ),
      RootMeaningData(
        root = "خ-م-ر",
        primaryMeaning = "wine, intoxicant",
        extendedMeaning = "Wine and intoxicants that cover the mind.",
        quranUsage = "'Wine (khamr) and gambling are abomination.' Paradise has wine without intoxication.",
        notes = "Worldly wine is forbidden; Paradise wine causes no harm."
      ),
      RootMeaningData(
        root = "س-ك-ر",
        primaryMeaning = "intoxication, to close",
        extendedMeaning = "Intoxication, and closing/blocking.",
        quranUsage = "'Do not approach prayer while intoxicated (sukara).' Sukr is intoxication.",
        notes = "Intoxication blocks the mind from proper worship."
      ),

      // === TASTE ===
      RootMeaningData(
        root = "ح-ل-و",
        primaryMeaning = "sweet, pleasant",
        extendedMeaning = "Sweetness and pleasantness.",
        quranUsage = "'This is sweet (hulw) and palatable.' Hulw is sweet.",
        notes = "Fresh water is described as sweet."
      ),
      RootMeaningData(
        root = "م-ر-ر",
        primaryMeaning = "bitter, to pass",
        extendedMeaning = "Bitterness, and passing by.",
        quranUsage = "'This is salty and bitter (murr).' Murr is bitter. Marra is to pass.",
        notes = "Salt water is described as bitter."
      ),
      RootMeaningData(
        root = "م-ل-ح",
        primaryMeaning = "salt, salty",
        extendedMeaning = "Salt and saltiness.",
        quranUsage = "'This is salty (milh) and bitter.' Milh is salt.",
        notes = "Two seas - fresh and salty - do not mix."
      ),

      // === SOUND AND VOICE ===
      RootMeaningData(
        root = "ص-و-ت",
        primaryMeaning = "voice, sound",
        extendedMeaning = "Voice, sound, and voting.",
        quranUsage = "'Lower your voice (sawt).' Sawt is voice/sound.",
        notes = "Lowering one's voice shows respect."
      ),
      RootMeaningData(
        root = "ص-ر-خ",
        primaryMeaning = "to scream, cry out",
        extendedMeaning = "Screaming, crying out for help.",
        quranUsage = "'They will cry out (yasrikhun) therein.' Sarkhah is scream.",
        notes = "The damned will cry out in Hell."
      ),
      RootMeaningData(
        root = "ه-م-س",
        primaryMeaning = "whisper, faint sound",
        extendedMeaning = "Whispering, faint/hushed sound.",
        quranUsage = "'You will hear only whispers (hams).' Hams is whisper.",
        notes = "On Judgment Day, voices will be hushed."
      ),
      RootMeaningData(
        root = "ص-م-ت",
        primaryMeaning = "silence, to be silent",
        extendedMeaning = "Silence, being quiet.",
        quranUsage = "Samt is silence. Samit is silent one.",
        notes = "Silence can be wisdom or can be sinful."
      ),

      // === ASCENDING AND DESCENDING ===
      RootMeaningData(
        root = "ص-ع-د",
        primaryMeaning = "to ascend, rise up",
        extendedMeaning = "Ascending, rising, and climbing.",
        quranUsage = "'To Him ascends (yas'adu) the good word.' Su'ud is ascending.",
        notes = "Good words and deeds ascend to Allah."
      ),
      RootMeaningData(
        root = "ه-ب-ط",
        primaryMeaning = "to descend, go down",
        extendedMeaning = "Descending, going down, and falling.",
        quranUsage = "'Descend (ihbitu) from it all.' Hubut is descent.",
        notes = "Adam and Hawwa were told to descend from Paradise."
      ),
      RootMeaningData(
        root = "ط-ل-ع",
        primaryMeaning = "to rise, appear, inform",
        extendedMeaning = "Rising (sun), appearing, and informing.",
        quranUsage = "'When the sun rises (tala'at).' Tulu' is rising. Ittala'a is to learn/discover.",
        notes = "The sun's rising is a daily sign."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "to set, west",
        extendedMeaning = "Setting (sun), west direction.",
        quranUsage = "'Until the sun set (gharabat).' Ghurub is sunset.",
        notes = "Already covered - emphasizing sun setting."
      ),
      RootMeaningData(
        root = "ر-ف-ع",
        primaryMeaning = "to raise, elevate, lift",
        extendedMeaning = "Raising, elevating, and lifting up.",
        quranUsage = "'We raised (rafa'na) him to a high station.' Raf' is raising.",
        notes = "Isa was raised to Allah. The Quran raises ranks."
      ),
      RootMeaningData(
        root = "و-ض-ع",
        primaryMeaning = "to put down, place, lower",
        extendedMeaning = "Placing down, lowering, and giving birth.",
        quranUsage = "'She placed (wada'at) him.' Wad' is placing. Mawdu' is placed/topic.",
        notes = "Can mean giving birth or putting something down."
      ),

      // === NARRATION AND SPEECH ===
      RootMeaningData(
        root = "ق-ص-ص",
        primaryMeaning = "to narrate, story, track",
        extendedMeaning = "Narrating stories, and following tracks.",
        quranUsage = "'We narrate (naqussu) to you the best stories.' Qasas is stories/narration.",
        notes = "The Quran contains the best of stories."
      ),
      RootMeaningData(
        root = "ح-د-ث",
        primaryMeaning = "to speak, new, event",
        extendedMeaning = "Speaking, something new, and events.",
        quranUsage = "'Proclaim (haddith) the favor of your Lord.' Hadith is speech/narration.",
        notes = "Hadith refers to the Prophet's sayings."
      ),
      RootMeaningData(
        root = "خ-ط-ب",
        primaryMeaning = "to address, sermon, matter",
        extendedMeaning = "Addressing, giving sermon, and important matter.",
        quranUsage = "'What is your matter (khatb)?' Khutbah is sermon. Khitab is address.",
        notes = "Khutbah is the Friday sermon."
      ),
      RootMeaningData(
        root = "ب-ش-ر",
        primaryMeaning = "to give good news, human, skin",
        extendedMeaning = "Giving glad tidings, human being, and skin.",
        quranUsage = "'Give good news (bashshir) to the believers.' Bashar is human. Bishara is good news.",
        notes = "Prophets are human (bashar) who bring glad tidings."
      ),
      RootMeaningData(
        root = "ن-ذ-ر",
        primaryMeaning = "to warn, vow",
        extendedMeaning = "Warning, and making a vow.",
        quranUsage = "'A warner (nadhir) to you before severe punishment.' Indhar is warning.",
        notes = "Prophets are warners (mundhirun) to their people."
      ),

      // === LEGAL AND ETHICAL ===
      RootMeaningData(
        root = "ش-ر-ع",
        primaryMeaning = "to legislate, path, law",
        extendedMeaning = "Legislating, ordained path, and Islamic law.",
        quranUsage = "'He has ordained (shara'a) for you the religion.' Shari'ah is divine law.",
        notes = "Shari'ah is the path Allah ordained for humanity."
      ),
      RootMeaningData(
        root = "ح-ك-م",
        primaryMeaning = "to judge, wisdom, rule",
        extendedMeaning = "Judging, wisdom, and ruling.",
        quranUsage = "'Judge (uhkum) between them by what Allah revealed.' Hukm is judgment.",
        notes = "Already covered - emphasizing judgment aspect."
      ),
      RootMeaningData(
        root = "ق-ض-ي",
        primaryMeaning = "to decree, judge, fulfill",
        extendedMeaning = "Decreeing, judging, and fulfilling.",
        quranUsage = "'When He decrees (qada) a matter.' Qada' is decree. Qadi is judge.",
        notes = "Allah's decree cannot be overturned."
      ),
      RootMeaningData(
        root = "ع-ف-ف",
        primaryMeaning = "chastity, to abstain",
        extendedMeaning = "Chastity, modesty, and abstaining from forbidden.",
        quranUsage = "'Let those who cannot marry keep chaste (yasta'fifu).' 'Iffah is chastity.",
        notes = "Chastity is highly praised in Islam."
      ),
      RootMeaningData(
        root = "ح-ي-ء",
        primaryMeaning = "modesty, shyness",
        extendedMeaning = "Modesty, shyness, and bashfulness.",
        quranUsage = "'One of them came walking with shyness (istihya').' Haya' is modesty.",
        notes = "Haya' (modesty) is a branch of faith."
      ),
      RootMeaningData(
        root = "ف-ج-ر",
        primaryMeaning = "dawn, wickedness, to burst",
        extendedMeaning = "Dawn, immorality, and bursting forth.",
        quranUsage = "'Nay! But you love the immediate.' Fujur is wickedness.",
        notes = "Already covered - emphasizing immorality aspect."
      ),

      // === SPREADING AND GATHERING ===
      RootMeaningData(
        root = "ن-ش-ر",
        primaryMeaning = "to spread, publish, resurrect",
        extendedMeaning = "Spreading, publishing, and resurrection.",
        quranUsage = "'He spreads (yanshuru) His mercy.' Nashr is spreading. Nushur is resurrection.",
        notes = "Allah spreads His mercy and will spread the dead in resurrection."
      ),
      RootMeaningData(
        root = "ش-ت-ت",
        primaryMeaning = "to scatter, disperse",
        extendedMeaning = "Scattering, dispersing, and being separated.",
        quranUsage = "'That Day people will proceed in scattered (ashtat) groups.' Shatat is dispersion.",
        notes = "On Judgment Day, people emerge scattered from graves."
      ),
      RootMeaningData(
        root = "ب-ث-ث",
        primaryMeaning = "to scatter, spread, broadcast",
        extendedMeaning = "Scattering, spreading widely.",
        quranUsage = "'He scattered (baththa) therein all kinds of creatures.' Bathth is spreading.",
        notes = "Allah scattered diverse creatures across the earth."
      ),

      // === BINDING AND RELEASING ===
      RootMeaningData(
        root = "ع-ق-د",
        primaryMeaning = "to tie, contract, knot",
        extendedMeaning = "Tying, contracts, and knots.",
        quranUsage = "'Fulfill the contracts ('uqud).' 'Aqd is contract. 'Uqdah is knot.",
        notes = "Contracts must be fulfilled in Islam."
      ),
      RootMeaningData(
        root = "ر-ب-ط",
        primaryMeaning = "to tie, bind, strengthen",
        extendedMeaning = "Tying, binding, and strengthening hearts.",
        quranUsage = "'We strengthened (rabatna) her heart.' Rabt is tying. Ribat is garrison.",
        notes = "Allah ties/strengthens the hearts of believers."
      ),
      RootMeaningData(
        root = "ف-ك-ك",
        primaryMeaning = "to release, free, ransom",
        extendedMeaning = "Releasing, freeing, and ransom.",
        quranUsage = "'The freeing (fakk) of a slave.' Fakk is release.",
        notes = "Freeing slaves is highly rewarded."
      ),
      RootMeaningData(
        root = "ط-ل-ق",
        primaryMeaning = "to release, divorce, set free",
        extendedMeaning = "Releasing, divorce, and setting free.",
        quranUsage = "'Divorce (talaq) is twice.' Talaq is divorce. Itlaq is release.",
        notes = "Divorce has specific rules in Islam."
      ),

      // === MORE WORSHIP ===
      RootMeaningData(
        root = "س-ج-ن",
        primaryMeaning = "prison, to imprison",
        extendedMeaning = "Prison, imprisonment.",
        quranUsage = "'O my two companions of the prison (sijn).' Sijn is prison.",
        notes = "Yusuf was imprisoned unjustly but remained patient."
      ),
      RootMeaningData(
        root = "أ-س-ر",
        primaryMeaning = "to capture, prisoner, family",
        extendedMeaning = "Capturing, prisoners of war, and family ties.",
        quranUsage = "'They feed the captive (asir).' Asir is captive. Usrah is family.",
        notes = "Treating captives well is commanded."
      ),

      // === CREATION STAGES ===
      RootMeaningData(
        root = "ن-ط-ف",
        primaryMeaning = "drop, sperm",
        extendedMeaning = "Drop of fluid, sperm.",
        quranUsage = "'We created man from a drop (nutfah).' Nutfah is sperm drop.",
        notes = "Human creation begins from a tiny drop."
      ),
      RootMeaningData(
        root = "ع-ل-ق",
        primaryMeaning = "clot, to cling, attach",
        extendedMeaning = "Clinging clot, attachment.",
        quranUsage = "'Created man from a clinging clot ('alaq).' 'Alaqah is clot.",
        notes = "The embryo clings to the womb like a leech."
      ),
      RootMeaningData(
        root = "م-ض-غ",
        primaryMeaning = "chewed substance, morsel",
        extendedMeaning = "Chewed lump, morsel of flesh.",
        quranUsage = "'Then from a chewed lump (mudghah).' Mudghah is chewed substance.",
        notes = "Stage of embryo resembling chewed flesh."
      ),
      RootMeaningData(
        root = "ج-ن-ن",
        primaryMeaning = "fetus, to cover, jinn",
        extendedMeaning = "Fetus (hidden), covering, and jinn.",
        quranUsage = "'When you were fetuses (ajinnah) in your mothers' wombs.' Janin is fetus.",
        notes = "Already covered jinn - adding fetus meaning."
      ),

      // === MEASUREMENTS ===
      RootMeaningData(
        root = "ك-ي-ل",
        primaryMeaning = "measure, to measure",
        extendedMeaning = "Measuring by volume.",
        quranUsage = "'Give full measure (kayl).' Kayl is measure. Mikyal is measuring vessel.",
        notes = "Giving full measure in trade is commanded."
      ),
      RootMeaningData(
        root = "و-ز-ن",
        primaryMeaning = "weight, to weigh, balance",
        extendedMeaning = "Weighing, weight, and balance.",
        quranUsage = "'Give full measure and weigh (zinu) with justice.' Mizan is scale/balance.",
        notes = "The scales (mawazin) will weigh deeds on Judgment Day."
      ),
      RootMeaningData(
        root = "ذ-ر-ع",
        primaryMeaning = "arm, cubit, measure",
        extendedMeaning = "Arm/forearm, cubit measurement.",
        quranUsage = "'A chain of seventy cubits (dhira').' Dhira' is cubit/arm.",
        notes = "Cubit was a common unit of measurement."
      ),

      // === LIGHT CONCEPTS ===
      RootMeaningData(
        root = "ض-و-ء",
        primaryMeaning = "light, brightness, illumination",
        extendedMeaning = "Light, brightness, and illumination.",
        quranUsage = "'He made the sun a brightness (diya').' Daw' is light.",
        notes = "The sun gives diya' (bright light), moon gives nur (reflected light)."
      ),
      RootMeaningData(
        root = "ش-ع-ع",
        primaryMeaning = "ray, beam of light",
        extendedMeaning = "Ray of light, beam.",
        quranUsage = "Shu'a' is ray of light. Related to spreading of light.",
        notes = "Light spreads in rays."
      ),
      RootMeaningData(
        root = "ص-ب-ح",
        primaryMeaning = "morning, lamp",
        extendedMeaning = "Morning, and lamp (source of morning-like light).",
        quranUsage = "'The lamp (misbah) in a glass.' Subh is morning. Misbah is lamp.",
        notes = "Already covered morning - adding lamp meaning."
      ),

      // === ADDITIONAL PROPHETIC ROOTS ===
      RootMeaningData(
        root = "ن-ب-ء",
        primaryMeaning = "news, information, prophecy",
        extendedMeaning = "Important news or information, especially from unseen realm. A nabi (prophet) is one who receives and conveys divine news.",
        quranUsage = "'We relate to you the news (naba') of those who came before.' Naba' is significant news. Nabiy is prophet.",
        notes = "Naba' refers to momentous news, unlike ordinary khabar. Prophets convey news from Allah."
      ),
      RootMeaningData(
        root = "ر-س-ل",
        primaryMeaning = "to send, messenger, message",
        extendedMeaning = "Sending forth, especially sending messengers. Implies gentleness in sending, like letting camels loose to graze.",
        quranUsage = "'We sent (arsalna) messengers.' Rasul is messenger. Risalah is message/mission.",
        notes = "A Rasul brings a new scripture; a Nabiy follows existing revelation."
      ),
      RootMeaningData(
        root = "و-ح-ي",
        primaryMeaning = "to inspire, revelation, swift communication",
        extendedMeaning = "Quick, subtle communication. Can be divine revelation or natural instinct given to creatures.",
        quranUsage = "'We revealed (awhayna) to the bee.' Wahy is revelation. Divine wahy is the highest form.",
        notes = "Allah inspires bees, mountains, and prophets - each receiving guidance appropriate to their nature."
      ),
      RootMeaningData(
        root = "ن-ز-ل",
        primaryMeaning = "to descend, send down, revelation",
        extendedMeaning = "Coming down from high to low. Used for rain, revelation, and guests (who 'descend' upon a host).",
        quranUsage = "'We sent down (nazzalna) the Quran.' Tanzil is the sending down of scripture. Munzal is place of descent.",
        notes = "The Quran's tanzil emphasizes its divine origin from above."
      ),

      // === COVENANT AND PROMISE ===
      RootMeaningData(
        root = "ع-ه-د",
        primaryMeaning = "covenant, promise, era, to know",
        extendedMeaning = "A binding agreement, also refers to a time period. To have knowledge or experience of something.",
        quranUsage = "'Fulfill My covenant ('ahdi), I will fulfill yours.' 'Ahd is covenant. Ma'hud is known/promised.",
        notes = "Allah's covenant with humanity includes worship and following guidance."
      ),
      RootMeaningData(
        root = "م-ي-ث",
        primaryMeaning = "firm covenant, solemn pledge",
        extendedMeaning = "A strongly binding covenant, more emphatic than 'ahd.",
        quranUsage = "'We took from them a solemn covenant (mithaq).' Mithaq is solemn covenant.",
        notes = "Used for the primordial covenant when souls testified to Allah's lordship."
      ),
      RootMeaningData(
        root = "و-ع-د",
        primaryMeaning = "to promise, appointment",
        extendedMeaning = "Making a promise, usually for good. The appointed time or place.",
        quranUsage = "'The promise (wa'd) of Allah is true.' Wa'd is promise. Maw'id is appointed time/place.",
        notes = "Allah's promises are always fulfilled. Paradise is the ultimate promise."
      ),
      RootMeaningData(
        root = "و-ف-ي",
        primaryMeaning = "to fulfill, complete, loyal",
        extendedMeaning = "Fulfilling obligations completely. Being faithful and loyal.",
        quranUsage = "'Ibrahim who fulfilled (waffa).' Wafa' is fulfillment/loyalty. Tawaffa is to take in full (death).",
        notes = "Death is described as Allah taking the soul in full (tawaffa)."
      ),

      // === SPIRITUAL STATES ===
      RootMeaningData(
        root = "خ-ش-ع",
        primaryMeaning = "humility, submissiveness, awe",
        extendedMeaning = "Deep humility and lowering oneself, especially in worship. Inner stillness and reverence.",
        quranUsage = "'Those who are humble (khashi'un) in their prayer.' Khushu' is reverent humility.",
        notes = "Khushu' in prayer means the heart is present and humble before Allah."
      ),
      RootMeaningData(
        root = "خ-و-ف",
        primaryMeaning = "fear, to frighten",
        extendedMeaning = "Fear, especially fear of harm or loss.",
        quranUsage = "'They fear (yakhafuna) a Day.' Khawf is fear. Takhwif is to frighten.",
        notes = "Khawf of Allah is praiseworthy - it leads to righteousness."
      ),
      RootMeaningData(
        root = "ر-ج-و",
        primaryMeaning = "hope, to expect, to wish",
        extendedMeaning = "Hope and expectation, especially hoping for good from Allah.",
        quranUsage = "'Whoever hopes (yarju) to meet his Lord.' Raja' is hope. Marjuw is hoped for.",
        notes = "Balance between khawf (fear) and raja' (hope) is the ideal spiritual state."
      ),
      RootMeaningData(
        root = "ط-م-ع",
        primaryMeaning = "greed, to covet, to hope",
        extendedMeaning = "Eager desire, can be positive (hoping for mercy) or negative (greed).",
        quranUsage = "'They hope (yatma'una) for His mercy.' Tama' is eager desire. Matma' is object of desire.",
        notes = "Tama' in Allah's mercy is encouraged; tama' for worldly things leads to trouble."
      ),
      RootMeaningData(
        root = "ي-ء-س",
        primaryMeaning = "despair, to lose hope",
        extendedMeaning = "Complete loss of hope, giving up.",
        quranUsage = "'Do not despair (ta'yasu) of Allah's mercy.' Ya's is despair. Iblas is extreme despair.",
        notes = "Despair of Allah's mercy is itself a sin - His mercy is limitless."
      ),
      RootMeaningData(
        root = "ق-ن-ط",
        primaryMeaning = "to despair utterly",
        extendedMeaning = "Extreme despair and hopelessness.",
        quranUsage = "'Do not be of those who despair (qanitin).' Qunut is utter despair.",
        notes = "Similar to ya's but more intense - complete giving up on Allah's help."
      ),

      // === INTELLECT AND UNDERSTANDING ===
      RootMeaningData(
        root = "ع-ق-ل",
        primaryMeaning = "reason, intellect, to understand",
        extendedMeaning = "The faculty of reason that restrains one from foolishness. Originally meant to hobble a camel (restrain it).",
        quranUsage = "'Do you not reason (ta'qilun)?' 'Aql is intellect. Ma'qul is reasonable.",
        notes = "The Quran repeatedly calls people to use their 'aql to recognize truth."
      ),
      RootMeaningData(
        root = "ف-ق-ه",
        primaryMeaning = "understanding, comprehension, jurisprudence",
        extendedMeaning = "Deep understanding, especially of religion. Fiqh became the term for Islamic jurisprudence.",
        quranUsage = "'That they may understand (yafqahun).' Fiqh is deep understanding. Faqih is jurist.",
        notes = "Fiqh goes beyond mere knowledge ('ilm) to penetrating understanding."
      ),
      RootMeaningData(
        root = "ف-ك-ر",
        primaryMeaning = "thought, reflection, pondering",
        extendedMeaning = "Deep thought and contemplation.",
        quranUsage = "'Those who reflect (yatafakkarun) on the creation.' Fikr is thought. Tafakkur is contemplation.",
        notes = "Tafakkur in Allah's creation strengthens faith."
      ),
      RootMeaningData(
        root = "ت-د-ب-ر",
        primaryMeaning = "to ponder, reflect on consequences",
        extendedMeaning = "Contemplating something by looking at its end (dubur - back/end). Deep reflection.",
        quranUsage = "'Do they not ponder (yatadabbarun) the Quran?' Tadabbur is deep reflection.",
        notes = "Tadabbur of the Quran is commanded - not just recitation but understanding."
      ),
      RootMeaningData(
        root = "ب-ص-ر",
        primaryMeaning = "sight, vision, insight",
        extendedMeaning = "Physical sight and inner insight. Basira is spiritual perception.",
        quranUsage = "'Have they not traveled and had hearts to reason and ears to hear? For it is not the eyes that are blind but the hearts.' Basar is sight. Basira is insight.",
        notes = "Al-Basir (The All-Seeing) is one of Allah's names."
      ),

      // === TRUTH AND FALSEHOOD ===
      RootMeaningData(
        root = "ص-د-ق",
        primaryMeaning = "truth, sincerity, to confirm",
        extendedMeaning = "Speaking truth, being sincere, and confirming/fulfilling. Sidq includes truthfulness in speech, action, and intention.",
        quranUsage = "'This is what the Most Merciful promised, and the messengers told the truth (sadaqa).' Sidq is truthfulness. Siddiq is extremely truthful.",
        notes = "Abu Bakr was called As-Siddiq for his unwavering belief in the Prophet."
      ),
      RootMeaningData(
        root = "ك-ذ-ب",
        primaryMeaning = "to lie, falsehood, denial",
        extendedMeaning = "Lying and denying the truth. Opposite of sidq.",
        quranUsage = "'They denied (kadhdhabu) Our signs.' Kidhb is lying. Mukadhdhibun are deniers.",
        notes = "Lying is among the worst sins in Islam."
      ),
      RootMeaningData(
        root = "ب-ط-ل",
        primaryMeaning = "falsehood, vanity, to nullify",
        extendedMeaning = "That which has no substance or reality. Invalid, void, futile.",
        quranUsage = "'Truth has come and falsehood (batil) has perished.' Batil is falsehood/vanity.",
        notes = "Batil is inherently temporary - truth always prevails."
      ),
      RootMeaningData(
        root = "ز-و-ر",
        primaryMeaning = "falsehood, forgery, to visit",
        extendedMeaning = "Deliberate falsehood and fabrication. Also means visiting (turning towards someone).",
        quranUsage = "'Avoid false testimony (zur).' Zur is false testimony. Ziyara is visit.",
        notes = "False testimony (shahadat al-zur) is a major sin."
      ),

      // === NATURE AND CREATION ===
      RootMeaningData(
        root = "س-م-ء",
        primaryMeaning = "sky, heaven, rain, height",
        extendedMeaning = "The sky, heavens, and anything elevated. Also refers to rain that comes from sky.",
        quranUsage = "'We sent down from the sky (sama') water.' Sama' is sky/heaven. Samawi is heavenly.",
        notes = "Seven heavens (sab' samawat) are mentioned in the Quran."
      ),
      RootMeaningData(
        root = "أ-ر-ض",
        primaryMeaning = "earth, land, ground",
        extendedMeaning = "The earth, land, ground, and territory.",
        quranUsage = "'The earth (ard) will shine with the light of its Lord.' Ard is earth. Ardi is earthly.",
        notes = "The earth is described as spread out, stable, and producing sustenance."
      ),
      RootMeaningData(
        root = "ب-ح-ر",
        primaryMeaning = "sea, vast expanse",
        extendedMeaning = "Sea, ocean, and any vast expanse. One who delves deep is 'bahhar' (expert).",
        quranUsage = "'The two seas (bahrayn) are not alike.' Bahr is sea. Bahri is maritime.",
        notes = "Fresh and salt water seas that don't mix are signs of Allah."
      ),
      RootMeaningData(
        root = "ن-ه-ر",
        primaryMeaning = "river, to flow, daytime",
        extendedMeaning = "River, flowing water. Also related to daytime (nahar).",
        quranUsage = "'Gardens beneath which rivers (anhar) flow.' Nahr is river. Nahar is daytime.",
        notes = "Rivers of Paradise include water, milk, wine, and honey."
      ),
      RootMeaningData(
        root = "ج-ب-ل",
        primaryMeaning = "mountain, to create, nature",
        extendedMeaning = "Mountain, creation, and innate nature. Jibillah is innate disposition.",
        quranUsage = "'He created you in natures (jibillan) different.' Jabal is mountain. Jibal is mountains.",
        notes = "Mountains are described as pegs (awtad) that stabilize the earth."
      ),
      RootMeaningData(
        root = "ش-ج-ر",
        primaryMeaning = "tree, to dispute",
        extendedMeaning = "Trees (with intertwining branches), and disputation (ideas intertwining).",
        quranUsage = "'Have you seen what you sow? Is it you who makes it grow or are We the grower?' Shajar is tree. Shajara is dispute.",
        notes = "The forbidden tree in Paradise and the tree of Zaqqum in Hell."
      ),
      RootMeaningData(
        root = "ز-ر-ع",
        primaryMeaning = "agriculture, crops, to plant",
        extendedMeaning = "Planting, agriculture, and crops.",
        quranUsage = "'Is it you who cause it to grow (tazra'unahu) or are We the grower?' Zar' is crops/planting.",
        notes = "Agriculture is presented as a sign of Allah's creative power."
      ),
      RootMeaningData(
        root = "ث-م-ر",
        primaryMeaning = "fruit, result, outcome",
        extendedMeaning = "Fruit and the result or product of something.",
        quranUsage = "'Eat of His fruit (thamarihi) when it ripens.' Thamar is fruit. Thamara is to bear fruit.",
        notes = "Paradise has fruits of every kind."
      ),

      // === ANIMALS ===
      RootMeaningData(
        root = "د-ب-ب",
        primaryMeaning = "creature, to crawl, move",
        extendedMeaning = "Any creature that moves on earth (from insect to elephant).",
        quranUsage = "'There is no creature (dabbah) on earth but that upon Allah is its provision.' Dabbah is creature.",
        notes = "Allah provides for every creature, however small."
      ),
      RootMeaningData(
        root = "ط-ي-ر",
        primaryMeaning = "bird, to fly, omen",
        extendedMeaning = "Birds, flying, and (in pre-Islamic times) omens from bird flight.",
        quranUsage = "'Do they not see the birds (tayr) controlled in the atmosphere?' Tayr is birds. Tayaran is flying.",
        notes = "Birds praising Allah and obeying Sulayman are mentioned."
      ),
      RootMeaningData(
        root = "ن-ع-م",
        primaryMeaning = "blessing, livestock, soft",
        extendedMeaning = "Blessings, grazing livestock (camels, cattle, sheep), and softness.",
        quranUsage = "'The livestock (an'am) He created for you.' Ni'mah is blessing. An'am is livestock.",
        notes = "Surah Al-An'am discusses livestock as blessings from Allah."
      ),
      RootMeaningData(
        root = "ب-ق-ر",
        primaryMeaning = "cow, cattle, to split open",
        extendedMeaning = "Cattle/cows, and splitting open (cows split the earth plowing).",
        quranUsage = "'A yellow cow (baqarah), bright in color.' Baqar is cattle. Baqarah is cow.",
        notes = "Surah Al-Baqarah is named after the cow in Musa's story."
      ),
      RootMeaningData(
        root = "ج-م-ل",
        primaryMeaning = "camel, beauty, entirety",
        extendedMeaning = "Camel, beauty, and totality. The camel represents patience and endurance.",
        quranUsage = "'Do they not look at the camel (ibil) - how it is created?' Jamal is camel. Jamil is beautiful.",
        notes = "The same root gives both 'camel' (jamal) and 'beautiful' (jamil)."
      ),
      RootMeaningData(
        root = "خ-ن-ز-ر",
        primaryMeaning = "pig, swine",
        extendedMeaning = "Pig or swine.",
        quranUsage = "'Forbidden to you is the flesh of swine (khinzir).' Khinzir is pig.",
        notes = "Pork is forbidden (haram) in Islam."
      ),
      RootMeaningData(
        root = "ك-ل-ب",
        primaryMeaning = "dog",
        extendedMeaning = "Dog, and rabidity/madness.",
        quranUsage = "'Their dog stretched his forelegs at the entrance.' Kalb is dog.",
        notes = "The dog of the People of the Cave (Ashab al-Kahf)."
      ),
      RootMeaningData(
        root = "ن-م-ل",
        primaryMeaning = "ant",
        extendedMeaning = "Ant.",
        quranUsage = "'An ant (namlah) said: O ants, enter your dwellings.' Naml is ants.",
        notes = "Surah An-Naml tells of Sulayman understanding the ant's speech."
      ),
      RootMeaningData(
        root = "ن-ح-ل",
        primaryMeaning = "bee",
        extendedMeaning = "Bee, and gifting.",
        quranUsage = "'Your Lord inspired the bee (nahl).' Nahl is bee.",
        notes = "Surah An-Nahl describes the bee's divine guidance."
      ),
      RootMeaningData(
        root = "ع-ن-ك-ب",
        primaryMeaning = "spider",
        extendedMeaning = "Spider.",
        quranUsage = "'The likeness of those who take protectors other than Allah is that of the spider ('ankabut).' 'Ankabut is spider.",
        notes = "The spider's web represents the weakness of false protectors."
      ),
      RootMeaningData(
        root = "ح-و-ت",
        primaryMeaning = "fish, whale",
        extendedMeaning = "Large fish or whale.",
        quranUsage = "'The companion of the fish (dhul-nun).' Hut is whale/fish.",
        notes = "Yunus was swallowed by a huge fish (hut)."
      ),

      // === FAMILY RELATIONS ===
      RootMeaningData(
        root = "أ-ب-و",
        primaryMeaning = "father, ancestor",
        extendedMeaning = "Father, forefather, and ancestors.",
        quranUsage = "'And (remember) when Ibrahim said to his father (abihi).' Ab is father. Aba' is fathers/ancestors.",
        notes = "Respecting parents, especially fathers, is strongly emphasized."
      ),
      RootMeaningData(
        root = "أ-م-م",
        primaryMeaning = "mother, nation, source",
        extendedMeaning = "Mother, nation/community, and origin/source. The ummah is like a mother nurturing its members.",
        quranUsage = "'His mother (ummuhu) is Jahannam.' Umm is mother. Ummah is nation.",
        notes = "Umm al-Kitab is the Mother of the Book (essence of scripture)."
      ),
      RootMeaningData(
        root = "ب-ن-و",
        primaryMeaning = "son, children, build",
        extendedMeaning = "Son, children, and building. Banu (sons of) indicates tribal descent.",
        quranUsage = "'O Children (bani) of Israel.' Ibn is son. Bina' is building.",
        notes = "Building (bina') and children (ibn) share the root - both are constructions."
      ),
      RootMeaningData(
        root = "ب-ن-ت",
        primaryMeaning = "daughter",
        extendedMeaning = "Daughter.",
        quranUsage = "'These are my daughters (banati).' Bint is daughter. Banat is daughters.",
        notes = "The Quran condemned the pre-Islamic practice of burying daughters alive."
      ),
      RootMeaningData(
        root = "أ-خ-و",
        primaryMeaning = "brother, brotherhood",
        extendedMeaning = "Brother, brotherhood, and companionship.",
        quranUsage = "'The believers are but brothers (ikhwah).' Akh is brother. Ukhuwwah is brotherhood.",
        notes = "Brotherhood in faith transcends blood relations."
      ),
      RootMeaningData(
        root = "أ-خ-ت",
        primaryMeaning = "sister",
        extendedMeaning = "Sister.",
        quranUsage = "'And (remember) when she said to his sister (ukhtihi).' Ukht is sister.",
        notes = "Maryam was addressed as 'sister of Harun' (O sister of Aaron)."
      ),
      RootMeaningData(
        root = "ز-و-ج",
        primaryMeaning = "spouse, pair, kind",
        extendedMeaning = "Spouse (husband or wife), pair, and type/kind. Everything created in pairs.",
        quranUsage = "'We created you in pairs (azwaj).' Zawj is spouse/pair. Zawja is wife.",
        notes = "Creation in pairs is a divine sign - male/female, positive/negative."
      ),
      RootMeaningData(
        root = "ن-ك-ح",
        primaryMeaning = "marriage, to marry",
        extendedMeaning = "Marriage contract and the marital relationship.",
        quranUsage = "'Marry (ankihu) those among you who are single.' Nikah is marriage.",
        notes = "Marriage is described as half of faith."
      ),
      RootMeaningData(
        root = "ي-ت-م",
        primaryMeaning = "orphan, solitary",
        extendedMeaning = "Orphan (one who lost their father), and being alone.",
        quranUsage = "'Did He not find you (O Muhammad) an orphan (yatim) and give refuge?' Yatim is orphan.",
        notes = "Caring for orphans is heavily emphasized in Islam."
      ),
      RootMeaningData(
        root = "أ-ر-م-ل",
        primaryMeaning = "widow",
        extendedMeaning = "Widow, one left alone.",
        quranUsage = "Armalah is widow. Caring for widows is encouraged.",
        notes = "The Prophet specifically encouraged marrying widows."
      ),

      // === BODY PARTS ===
      RootMeaningData(
        root = "ر-ء-س",
        primaryMeaning = "head, chief, capital",
        extendedMeaning = "Head, leader, and the main/principal thing (capital in money).",
        quranUsage = "'If you repent, you may have your principal (ra's).' Ra's is head/principal. Ra'is is chief.",
        notes = "The head represents leadership and primacy."
      ),
      RootMeaningData(
        root = "و-ج-ه",
        primaryMeaning = "face, direction, aspect",
        extendedMeaning = "Face, countenance, direction, and manner/way.",
        quranUsage = "'Wherever you turn, there is the Face (wajh) of Allah.' Wajh is face. Wijhah is direction.",
        notes = "Turning one's face (wajh) to Allah means directing oneself entirely to Him."
      ),
      RootMeaningData(
        root = "ع-ي-ن",
        primaryMeaning = "eye, spring, essence",
        extendedMeaning = "Eye, water spring, and the essence/self of something.",
        quranUsage = "'In it are springs ('uyun).' 'Ayn is eye/spring. 'Ayni is my eye (expression of care).",
        notes = "Eye, spring, and essence share the root - all are sources of life."
      ),
      RootMeaningData(
        root = "أ-ذ-ن",
        primaryMeaning = "ear, permission, announcement",
        extendedMeaning = "Ear, permission (hearing and accepting), and announcement.",
        quranUsage = "'Let those who have ears (adhan) hear.' Udhun is ear. Idhn is permission. Adhan is call to prayer.",
        notes = "The adhan (call to prayer) comes from this root - the announcement to the ears."
      ),
      RootMeaningData(
        root = "ل-س-ن",
        primaryMeaning = "tongue, language",
        extendedMeaning = "Tongue and language.",
        quranUsage = "'We sent no messenger except in the language (lisan) of his people.' Lisan is tongue/language.",
        notes = "The Quran is in clear Arabic tongue (lisan 'arabi mubin)."
      ),
      RootMeaningData(
        root = "ي-د-د",
        primaryMeaning = "hand, power, favor",
        extendedMeaning = "Hand, and by extension power, ability, and favor.",
        quranUsage = "'The Hand (yad) of Allah is over their hands.' Yad is hand. Aydi is hands.",
        notes = "Hand symbolizes power, generosity, and covenant."
      ),
      RootMeaningData(
        root = "ر-ج-ل",
        primaryMeaning = "foot, leg, man",
        extendedMeaning = "Foot/leg, and man (one who walks on legs). Rijal are men.",
        quranUsage = "'Men (rijal) whom neither commerce nor sale distracts.' Rijl is foot. Rajul is man.",
        notes = "The standing (qiyam) in prayer is on one's feet."
      ),
      RootMeaningData(
        root = "ص-د-ر",
        primaryMeaning = "chest, breast, to proceed from",
        extendedMeaning = "Chest/breast (where emotions reside), and issuing forth.",
        quranUsage = "'We expand his breast (sadr) for Islam.' Sadr is chest. Masdar is source/origin.",
        notes = "The chest (sadr) is where spiritual states manifest."
      ),
      RootMeaningData(
        root = "ب-ط-ن",
        primaryMeaning = "belly, interior, hidden",
        extendedMeaning = "Belly/stomach, interior, and the hidden aspect of things.",
        quranUsage = "'The outer (zahir) and the inner (batin).' Batn is belly. Batin is inner/hidden.",
        notes = "Al-Batin (The Hidden) is one of Allah's names - His essence is beyond comprehension."
      ),
      RootMeaningData(
        root = "ظ-ه-ر",
        primaryMeaning = "back, outer, apparent, to appear",
        extendedMeaning = "Back, outer surface, appearing, and becoming manifest.",
        quranUsage = "'That He may make it prevail (yuzhirahu) over all religion.' Zahr is back. Zahir is apparent.",
        notes = "Al-Zahir (The Manifest) pairs with Al-Batin - Allah is both apparent in His signs and hidden in His essence."
      ),

      // === TIME CONCEPTS ===
      RootMeaningData(
        root = "د-ه-ر",
        primaryMeaning = "time, age, eternity",
        extendedMeaning = "A long period of time, fate, eternity.",
        quranUsage = "'Has there not been over man a period of time (dahr)?' Dahr is time/fate.",
        notes = "Pre-Islamic Arabs blamed 'dahr' (time/fate) for misfortunes."
      ),
      RootMeaningData(
        root = "ز-م-ن",
        primaryMeaning = "time, period",
        extendedMeaning = "Time, period, and era.",
        quranUsage = "Zaman is time/period. Azminah is times.",
        notes = "Time (zaman) is a creation of Allah."
      ),
      RootMeaningData(
        root = "ح-ي-ن",
        primaryMeaning = "time, moment, when",
        extendedMeaning = "A specific time or moment.",
        quranUsage = "'Has there come upon man a period of time (hin)?' Hin is a period of time.",
        notes = "Hin implies a specific, appointed moment."
      ),
      RootMeaningData(
        root = "أ-ب-د",
        primaryMeaning = "eternity, forever",
        extendedMeaning = "Eternity, forever, perpetually.",
        quranUsage = "'Abiding therein forever (abadan).' Abad is eternity. Abadi is eternal.",
        notes = "Paradise and Hell are described as eternal (abadan)."
      ),
      RootMeaningData(
        root = "أ-ز-ل",
        primaryMeaning = "eternity past, pre-eternal",
        extendedMeaning = "Eternity without beginning, pre-eternity.",
        quranUsage = "Azal is pre-eternity. Azali is pre-eternal (no beginning).",
        notes = "Allah is Azali (pre-eternal) - He has no beginning."
      ),
      RootMeaningData(
        root = "ق-د-م",
        primaryMeaning = "to precede, ancient, foot",
        extendedMeaning = "Preceding, ancient, and foot (what goes forward first).",
        quranUsage = "'The truth which they had sent before (qaddamat).' Qadim is ancient. Qadam is foot.",
        notes = "Al-Qadim describes Allah's eternal existence without beginning."
      ),
      RootMeaningData(
        root = "أ-خ-ر",
        primaryMeaning = "other, last, to delay",
        extendedMeaning = "Other, another, the last/final, and delaying.",
        quranUsage = "'The Last Day (al-yawm al-akhir).' Akhir is last. Akhar is other. Akhkhara is to delay.",
        notes = "Al-Akhir (The Last) is one of Allah's names - He remains after all else perishes."
      ),

      // === ISLAMIC TERMS ===
      RootMeaningData(
        root = "ف-ت-و",
        primaryMeaning = "youth, religious verdict",
        extendedMeaning = "Youth, and issuing a religious verdict (fatwa).",
        quranUsage = "'They ask you for a verdict (yastaftunaka).' Fata is youth. Fatwa is religious verdict.",
        notes = "The youth of the cave (fityah) were praised for their faith."
      ),
      RootMeaningData(
        root = "ج-ه-د",
        primaryMeaning = "effort, struggle, strive",
        extendedMeaning = "Exerting effort, striving, and struggling. Jihad is striving in Allah's cause.",
        quranUsage = "'Strive (jahidu) in the way of Allah.' Jihad is striving. Mujtahid is one who strives.",
        notes = "The greater jihad is the struggle against one's own ego."
      ),
      RootMeaningData(
        root = "ه-ج-ر",
        primaryMeaning = "emigration, to abandon",
        extendedMeaning = "Emigration, abandoning, and separation.",
        quranUsage = "'Those who emigrated (hajaru) for Allah.' Hijrah is emigration. Hajr is abandonment.",
        notes = "The Hijrah from Makkah to Madinah marks the start of the Islamic calendar."
      ),
      RootMeaningData(
        root = "ص-ح-ب",
        primaryMeaning = "companion, to accompany",
        extendedMeaning = "Companionship and accompanying someone.",
        quranUsage = "'When he said to his companion (sahibihi).' Sahib is companion. Sahaba are the Prophet's companions.",
        notes = "The Sahabah (Companions) are the Prophet's closest followers."
      ),
      RootMeaningData(
        root = "ت-ب-ع",
        primaryMeaning = "to follow, followers",
        extendedMeaning = "Following, succession, and followers.",
        quranUsage = "'Follow (ittabi') what has been revealed to you.' Tabi' is follower. Tabi'un are the followers of Sahabah.",
        notes = "The Tabi'un are the generation after the Sahabah."
      ),
      RootMeaningData(
        root = "س-ن-ن",
        primaryMeaning = "way, practice, law of nature",
        extendedMeaning = "Established way, practice, and natural law. Sunnah is the Prophet's practice.",
        quranUsage = "'The practice (sunnah) of those who were sent before.' Sunnah is established way.",
        notes = "Allah's sunnah (way) in dealing with nations does not change."
      ),
      RootMeaningData(
        root = "ب-د-ع",
        primaryMeaning = "to innovate, originate, create anew",
        extendedMeaning = "Creating something unprecedented, innovation.",
        quranUsage = "'Originator (badi') of the heavens and earth.' Bid'ah is innovation. Ibda' is creation.",
        notes = "Al-Badi' (The Originator) creates without precedent."
      ),

      // === MORAL QUALITIES ===
      RootMeaningData(
        root = "خ-ل-ق",
        primaryMeaning = "creation, character, morals",
        extendedMeaning = "Creating, and moral character (which is the 'creation' of one's soul).",
        quranUsage = "'And indeed, you are of great moral character (khuluq).' Khalq is creation. Khuluq is character.",
        notes = "The Prophet was praised for his excellent character (khuluq 'azim)."
      ),
      RootMeaningData(
        root = "أ-د-ب",
        primaryMeaning = "manners, literature, discipline",
        extendedMeaning = "Good manners, refined behavior, literature, and discipline.",
        quranUsage = "Adab is manners/literature. Ta'dib is disciplining/refining.",
        notes = "The Prophet said: 'My Lord disciplined me (addabani) and perfected my discipline.'"
      ),
      RootMeaningData(
        root = "ح-ل-م",
        primaryMeaning = "forbearance, gentleness, dream",
        extendedMeaning = "Forbearance, clemency, puberty/maturity, and dreams.",
        quranUsage = "'When they reach maturity (hulm).' Hilm is forbearance. Hulm is dream.",
        notes = "Al-Halim (The Forbearing) is one of Allah's names."
      ),
      RootMeaningData(
        root = "س-خ-و",
        primaryMeaning = "generosity, to be generous",
        extendedMeaning = "Generosity and open-handedness.",
        quranUsage = "Sakha' is generosity. Sakhi is generous.",
        notes = "The Prophet was described as more generous than the blowing wind."
      ),
      RootMeaningData(
        root = "ب-خ-ل",
        primaryMeaning = "miserliness, stinginess",
        extendedMeaning = "Miserliness and withholding.",
        quranUsage = "'Those who are miserly (yabkhalun) and enjoin miserliness.' Bukhl is miserliness.",
        notes = "Miserliness is condemned; generosity is praised."
      ),
      RootMeaningData(
        root = "ك-ب-ر",
        primaryMeaning = "greatness, to grow, arrogance",
        extendedMeaning = "Being great/large, growing up, and arrogance (seeing oneself as great).",
        quranUsage = "'Allahu Akbar' - Allah is Greater. Kibr is arrogance. Kabir is great.",
        notes = "Greatness belongs to Allah; human arrogance (kibr) is condemned."
      ),
      RootMeaningData(
        root = "ت-و-ض-ع",
        primaryMeaning = "humility, to be humble",
        extendedMeaning = "Lowering oneself, humility.",
        quranUsage = "'Lower (ikhfid) your wing to the believers.' Tawadu' is humility.",
        notes = "Humility before Allah and creation is praiseworthy."
      ),
      RootMeaningData(
        root = "غ-ر-ر",
        primaryMeaning = "deception, delusion",
        extendedMeaning = "Deception, delusion, and being fooled by appearances.",
        quranUsage = "'Let not the worldly life delude (yaghurrannaka) you.' Ghurur is delusion.",
        notes = "The world (dunya) is described as deceptive enjoyment."
      ),

      // === PRAYER AND WORSHIP ===
      RootMeaningData(
        root = "ر-ك-ع",
        primaryMeaning = "bowing, to bow",
        extendedMeaning = "Bowing, especially in prayer. Also humbling oneself.",
        quranUsage = "'Bow (irka'u) and prostrate.' Ruku' is bowing. Raki' is one who bows.",
        notes = "Ruku' in prayer symbolizes humility before Allah."
      ),
      RootMeaningData(
        root = "ق-و-م",
        primaryMeaning = "standing, to stand, establish, people",
        extendedMeaning = "Standing, rising, establishing, and a people/nation.",
        quranUsage = "'Establish (aqim) the prayer.' Qiyam is standing. Qawm is people. Qiyamah is resurrection.",
        notes = "Standing (qiyam) in prayer and on Judgment Day (Yawm al-Qiyamah)."
      ),
      RootMeaningData(
        root = "ت-ل-و",
        primaryMeaning = "recitation, to follow, to recite",
        extendedMeaning = "Following (something after another), and reciting (words following each other).",
        quranUsage = "'When the Quran is recited (tutla).' Tilawah is recitation.",
        notes = "Tilawah implies reciting with proper following of rules (tajweed)."
      ),
      RootMeaningData(
        root = "س-ب-ح",
        primaryMeaning = "to glorify, swim",
        extendedMeaning = "Glorifying (Allah), and swimming/floating.",
        quranUsage = "'Glorify (sabbih) the name of your Lord.' Tasbih is glorification. Subhan is glory be.",
        notes = "Everything in the heavens and earth glorifies Allah."
      ),
      RootMeaningData(
        root = "ق-د-س",
        primaryMeaning = "holiness, sacred, to purify",
        extendedMeaning = "Holiness, sacredness, and spiritual purification.",
        quranUsage = "'The Holy Spirit (Ruh al-Qudus).' Quds is holiness. Muqaddas is sacred.",
        notes = "Al-Quddus (The Holy) is one of Allah's names."
      ),
      RootMeaningData(
        root = "ح-ج-ج",
        primaryMeaning = "pilgrimage, argument, proof",
        extendedMeaning = "Pilgrimage (journeying to sacred place), and presenting an argument/proof.",
        quranUsage = "'Pilgrimage (hajj) to the House is a duty.' Hajj is pilgrimage. Hujjah is proof.",
        notes = "Hajj is one of the five pillars of Islam."
      ),
      RootMeaningData(
        root = "ع-م-ر",
        primaryMeaning = "life, to visit, to populate",
        extendedMeaning = "Life span, visiting (Umrah), and populating/building up.",
        quranUsage = "'Complete the Hajj and Umrah for Allah.' 'Umr is life. 'Umrah is lesser pilgrimage.",
        notes = "Umrah is the lesser pilgrimage that can be performed anytime."
      ),

      // === ESCHATOLOGY ===
      RootMeaningData(
        root = "ب-ع-ث",
        primaryMeaning = "to resurrect, send, raise",
        extendedMeaning = "Raising from death, sending (messengers), and awakening.",
        quranUsage = "'The Day they will be resurrected (yub'athun).' Ba'th is resurrection. Mab'uth is sent.",
        notes = "Ba'th refers to the bodily resurrection on Judgment Day."
      ),
      RootMeaningData(
        root = "ح-ش-ر",
        primaryMeaning = "gathering, to assemble",
        extendedMeaning = "Gathering together, especially the gathering on Judgment Day.",
        quranUsage = "'The Day of Gathering (hashr).' Hashr is gathering. Mahshar is place of gathering.",
        notes = "All creatures will be gathered (hashr) for judgment."
      ),
      RootMeaningData(
        root = "ح-س-ب",
        primaryMeaning = "reckoning, account, to calculate",
        extendedMeaning = "Counting, calculating, reckoning, and considering sufficient.",
        quranUsage = "'Swift is He in reckoning (hisab).' Hisab is account/reckoning. Hasib is accountant.",
        notes = "Everyone will be held to account for their deeds."
      ),
      RootMeaningData(
        root = "ص-ر-ط",
        primaryMeaning = "path, way, road",
        extendedMeaning = "A clear, straight path.",
        quranUsage = "'Guide us to the straight path (sirat al-mustaqim).' Sirat is path.",
        notes = "The Sirat is also the bridge over Hell that all must cross."
      ),
      RootMeaningData(
        root = "ج-ز-ء",
        primaryMeaning = "recompense, part, reward",
        extendedMeaning = "Recompense (reward or punishment), and a part/portion.",
        quranUsage = "'Is the recompense (jaza') of good except good?' Jaza' is recompense. Juz' is part.",
        notes = "Allah's recompense is perfectly just."
      ),
      RootMeaningData(
        root = "ث-و-ب",
        primaryMeaning = "reward, garment, to return",
        extendedMeaning = "Reward, garment/clothing, and returning.",
        quranUsage = "'A good reward (thawab) from Allah.' Thawab is reward. Thawb is garment.",
        notes = "Good deeds bring thawab (reward) in the Hereafter."
      ),
      RootMeaningData(
        root = "ع-ذ-ب",
        primaryMeaning = "punishment, torment, fresh water",
        extendedMeaning = "Punishment, torment, and (paradoxically) fresh/sweet water.",
        quranUsage = "'A painful punishment ('adhab alim).' 'Adhab is punishment. 'Adhb is fresh water.",
        notes = "The same root gives both 'punishment' and 'sweet water' - linguistic richness."
      ),
      RootMeaningData(
        root = "ن-ع-م",
        primaryMeaning = "blessing, bounty, comfort",
        extendedMeaning = "Blessing, favor, comfort, and livestock (which are blessings).",
        quranUsage = "'Which of the favors (ni'am) of your Lord will you deny?' Na'im is bliss. Ni'mah is blessing.",
        notes = "An-Na'im is the bliss of Paradise."
      ),

      // === FOOD AND DRINK ===
      RootMeaningData(
        root = "ط-ع-م",
        primaryMeaning = "food, taste, to feed",
        extendedMeaning = "Food, taste, and feeding others.",
        quranUsage = "'They give food (ta'am) despite loving it.' Ta'am is food. It'am is feeding.",
        notes = "Feeding the hungry is highly rewarded."
      ),
      RootMeaningData(
        root = "ش-ر-ب",
        primaryMeaning = "drink, to drink",
        extendedMeaning = "Drinking and beverages.",
        quranUsage = "'Eat and drink (ishrabu).' Shurb is drinking. Sharab is drink.",
        notes = "Paradise has rivers of various drinks."
      ),
      RootMeaningData(
        root = "أ-ك-ل",
        primaryMeaning = "eating, food, to consume",
        extendedMeaning = "Eating and consuming. Includes consuming wealth (metaphorically).",
        quranUsage = "'Do not consume (ta'kulu) one another's wealth unjustly.' Akl is eating.",
        notes = "Consuming others' wealth wrongly is like eating forbidden food."
      ),
      RootMeaningData(
        root = "ذ-ب-ح",
        primaryMeaning = "slaughter, sacrifice",
        extendedMeaning = "Ritual slaughter and sacrifice.",
        quranUsage = "'So pray to your Lord and sacrifice (wanhar).' Dhabh is slaughter. Dhabiha is sacrifice.",
        notes = "Proper slaughter (dhabh) makes meat halal."
      ),
      RootMeaningData(
        root = "ح-ل-ل",
        primaryMeaning = "lawful, to untie, to permit",
        extendedMeaning = "Permissible, lawful, and untying/loosening.",
        quranUsage = "'Made lawful (uhilla) for you are good foods.' Halal is lawful. Hill is untying.",
        notes = "Halal encompasses all that is permissible in Islam."
      ),
      RootMeaningData(
        root = "ح-ر-م",
        primaryMeaning = "forbidden, sacred, to prohibit",
        extendedMeaning = "Forbidden, sacred/inviolable, and prohibition.",
        quranUsage = "'Forbidden (hurrimat) to you are dead animals.' Haram is forbidden. Harim is sanctuary.",
        notes = "The same root gives 'forbidden' and 'sacred' - both are set apart."
      ),

      // === ADDITIONAL IMPORTANT ROOTS ===
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "to separate, distinguish, difference",
        extendedMeaning = "Separating, distinguishing truth from falsehood, and difference.",
        quranUsage = "'The Criterion (al-Furqan) between truth and falsehood.' Farq is difference. Furqan is criterion.",
        notes = "The Quran is Al-Furqan - it distinguishes truth from falsehood."
      ),
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "to gather, collect, assemble",
        extendedMeaning = "Gathering, collecting, uniting, and Friday (day of gathering).",
        quranUsage = "'The Day of Assembly (jumu'ah).' Jam' is gathering. Jum'ah is Friday. Jami' is comprehensive.",
        notes = "Jumu'ah prayer gathers the community weekly."
      ),
      RootMeaningData(
        root = "و-ح-د",
        primaryMeaning = "one, alone, to unify",
        extendedMeaning = "Oneness, being alone/unique, and unification.",
        quranUsage = "'Your God is One God (ilahun wahid).' Wahid is one. Tawhid is monotheism.",
        notes = "Tawhid (monotheism) is the foundation of Islam."
      ),
      RootMeaningData(
        root = "ش-ر-ك",
        primaryMeaning = "to associate, partner, share",
        extendedMeaning = "Partnership, association, and associating partners with Allah.",
        quranUsage = "'Do not associate (tushrik) anything with Him.' Shirk is polytheism. Sharik is partner.",
        notes = "Shirk (associating partners with Allah) is the only unforgivable sin if one dies upon it."
      ),
      RootMeaningData(
        root = "ن-ف-س",
        primaryMeaning = "soul, self, breath",
        extendedMeaning = "Soul, self, breath, and the essence of a person.",
        quranUsage = "'Every soul (nafs) will taste death.' Nafs is soul/self. Tanaffus is breathing.",
        notes = "The nafs has stages: commanding to evil (ammara), self-reproaching (lawwama), and at peace (mutma'inna)."
      ),
      RootMeaningData(
        root = "ر-و-ح",
        primaryMeaning = "spirit, soul, wind, rest",
        extendedMeaning = "Spirit, soul (higher aspect), wind/breeze, and rest/comfort.",
        quranUsage = "'He breathed into him of His spirit (ruh).' Ruh is spirit. Rawh is rest/mercy.",
        notes = "Ruh al-Qudus (Holy Spirit) is Jibril. The ruh is breathed into the fetus at 120 days."
      ),
      RootMeaningData(
        root = "ق-ص-د",
        primaryMeaning = "intention, to intend, moderate",
        extendedMeaning = "Intention, purpose, and moderation in path.",
        quranUsage = "'Upon Allah is the moderate way (qasd al-sabil).' Qasd is intention. Maqsud is intended.",
        notes = "Actions are judged by intentions (niyyat)."
      ),
      RootMeaningData(
        root = "ن-و-ي",
        primaryMeaning = "intention, to intend",
        extendedMeaning = "Intention and purpose behind actions.",
        quranUsage = "Niyyah is intention. The Prophet said: 'Actions are by intentions.'",
        notes = "Every act of worship requires proper intention (niyyah)."
      ),
      RootMeaningData(
        root = "إ-خ-ل-ص",
        primaryMeaning = "sincerity, purity, to purify",
        extendedMeaning = "Sincerity, purifying intention for Allah alone.",
        quranUsage = "'Sincere (mukhlisin) to Him in religion.' Ikhlas is sincerity. Mukhlis is sincere one.",
        notes = "Surah Al-Ikhlas embodies pure monotheism."
      ),
      RootMeaningData(
        root = "ن-ص-ح",
        primaryMeaning = "sincere advice, to advise",
        extendedMeaning = "Sincere counsel and well-wishing for others.",
        quranUsage = "'I am a sincere adviser (nasih) to you.' Nasihah is sincere advice. Nasih is adviser.",
        notes = "The religion is sincere advice (al-din al-nasihah)."
      ),
      RootMeaningData(
        root = "أ-م-ر",
        primaryMeaning = "command, matter, affair",
        extendedMeaning = "Commanding, an affair/matter, and authority.",
        quranUsage = "'To Allah belongs the command (amr).' Amr is command. Amir is commander.",
        notes = "Amr bil-ma'ruf is commanding the good."
      ),
      RootMeaningData(
        root = "ن-ه-ي",
        primaryMeaning = "to forbid, prohibit, end",
        extendedMeaning = "Forbidding, prohibiting, and reaching an end.",
        quranUsage = "'Forbid (yanha) from wrongdoing.' Nahy is prohibition. Muntaha is ultimate end.",
        notes = "Nahy 'an al-munkar is forbidding the wrong."
      ),
      RootMeaningData(
        root = "ح-ق-ق",
        primaryMeaning = "truth, right, reality",
        extendedMeaning = "Truth, right (legal and moral), reality, and what is due.",
        quranUsage = "'The truth (haqq) has come and falsehood has departed.' Haqq is truth/right. Haqiqa is reality.",
        notes = "Al-Haqq (The Truth) is one of Allah's names."
      ),
      RootMeaningData(
        root = "ع-د-ل",
        primaryMeaning = "justice, equity, to be fair",
        extendedMeaning = "Justice, fairness, equity, and balance.",
        quranUsage = "'Be just ('adilu); that is nearer to righteousness.' 'Adl is justice. 'Adil is just.",
        notes = "Allah commands justice and excellence (ihsan)."
      ),
      RootMeaningData(
        root = "ظ-ل-م",
        primaryMeaning = "oppression, injustice, darkness",
        extendedMeaning = "Wrongdoing, oppression, injustice, and darkness (absence of light of justice).",
        quranUsage = "'Do not wrong (tazlimun) one another.' Zulm is oppression. Zalim is oppressor. Zulmat is darkness.",
        notes = "Zulm includes wronging oneself, others, and (worst) wronging Allah through shirk."
      ),
      RootMeaningData(
        root = "ف-س-د",
        primaryMeaning = "corruption, to spoil",
        extendedMeaning = "Corruption, spoiling, and causing mischief.",
        quranUsage = "'Do not cause corruption (tufsidu) in the earth.' Fasad is corruption. Mufsid is corruptor.",
        notes = "Corruption in the land is severely condemned."
      ),
      RootMeaningData(
        root = "ص-ل-ح",
        primaryMeaning = "righteousness, reform, peace",
        extendedMeaning = "Righteousness, reform, reconciliation, and goodness.",
        quranUsage = "'Those who believe and do righteous deeds (salihat).' Salih is righteous. Islah is reform.",
        notes = "Righteousness (salah) is the opposite of corruption (fasad)."
      ),

      // === COLORS ===
      RootMeaningData(
        root = "ب-ي-ض",
        primaryMeaning = "white, egg, to whiten",
        extendedMeaning = "Whiteness, eggs (white things), and becoming white/bright.",
        quranUsage = "'His hand emerged white (bayda').' Abyad is white. Bayd is eggs.",
        notes = "Musa's hand turning white was a sign. White symbolizes purity."
      ),
      RootMeaningData(
        root = "س-و-د",
        primaryMeaning = "black, to blacken, master",
        extendedMeaning = "Blackness, becoming black, and being a master/chief (sayyid).",
        quranUsage = "'On the Day when faces will be white and faces will be black (taswaddu).' Aswad is black. Sayyid is master.",
        notes = "Sayyid (master/chief) shares this root - perhaps from authority's gravity."
      ),
      RootMeaningData(
        root = "ح-م-ر",
        primaryMeaning = "red, donkey",
        extendedMeaning = "Redness and donkeys (often reddish-brown).",
        quranUsage = "'Mountains of various colors - white, red (humr), and black.' Ahmar is red. Himar is donkey.",
        notes = "Red is mentioned in describing mountains and other natural phenomena."
      ),
      RootMeaningData(
        root = "خ-ض-ر",
        primaryMeaning = "green, verdant",
        extendedMeaning = "Green color, verdure, and freshness.",
        quranUsage = "'Reclining on green (khudur) cushions.' Akhdar is green. Khadir is verdant.",
        notes = "Green is associated with Paradise and life. Al-Khidr's name means 'the green one.'"
      ),
      RootMeaningData(
        root = "ص-ف-ر",
        primaryMeaning = "yellow, to whistle",
        extendedMeaning = "Yellow color, and whistling/empty sound.",
        quranUsage = "'A yellow (safra') cow, bright in color.' Asfar is yellow. Safir is whistling.",
        notes = "The cow in Surah Al-Baqarah was specifically bright yellow."
      ),
      RootMeaningData(
        root = "ز-ر-ق",
        primaryMeaning = "blue, to be blue-eyed",
        extendedMeaning = "Blue color, especially blue eyes.",
        quranUsage = "'We will gather the criminals that Day blue-eyed (zurqan).' Azraq is blue.",
        notes = "Blue-eyed on Judgment Day indicates terror and thirst."
      ),
      RootMeaningData(
        root = "ل-و-ن",
        primaryMeaning = "color, kind, type",
        extendedMeaning = "Color and variety/type.",
        quranUsage = "'Of various colors (alwan).' Lawn is color. Mulawwan is colored/varied.",
        notes = "Diversity of colors in creation is a sign of Allah."
      ),

      // === NUMBERS ===
      RootMeaningData(
        root = "و-ح-د",
        primaryMeaning = "one, unique, alone",
        extendedMeaning = "Oneness, uniqueness, and being alone.",
        quranUsage = "'Your God is One (wahid) God.' Wahid is one. Ahad is uniquely one.",
        notes = "Ahad emphasizes absolute uniqueness; Wahid emphasizes being single."
      ),
      RootMeaningData(
        root = "ث-ن-ي",
        primaryMeaning = "two, to double, fold",
        extendedMeaning = "Two, doubling, folding, and repetition.",
        quranUsage = "'Seven of the oft-repeated (mathani).' Ithnan is two. Mathani is repeated.",
        notes = "Al-Fatiha is called 'Sab' al-Mathani' (Seven Oft-Repeated)."
      ),
      RootMeaningData(
        root = "ث-ل-ث",
        primaryMeaning = "three, third",
        extendedMeaning = "Three and one-third.",
        quranUsage = "'Do not say three (thalatha).' Thalatha is three. Thuluth is one-third.",
        notes = "The Quran refutes the concept of trinity."
      ),
      RootMeaningData(
        root = "ر-ب-ع",
        primaryMeaning = "four, spring, square",
        extendedMeaning = "Four, springtime, and a square/dwelling place.",
        quranUsage = "'Four (arba'ah) months.' Arba'ah is four. Rabi' is spring.",
        notes = "Four sacred months, four witnesses for certain cases."
      ),
      RootMeaningData(
        root = "خ-م-س",
        primaryMeaning = "five, fifth",
        extendedMeaning = "Five and one-fifth.",
        quranUsage = "'Five (khamsah) of them.' Khamsah is five. Khamis is Thursday (fifth day).",
        notes = "Five daily prayers, five pillars of Islam."
      ),
      RootMeaningData(
        root = "س-ت-ت",
        primaryMeaning = "six",
        extendedMeaning = "Six.",
        quranUsage = "'Created the heavens and earth in six (sittah) days.' Sittah is six.",
        notes = "Creation in six days demonstrates Allah's power and wisdom."
      ),
      RootMeaningData(
        root = "س-ب-ع",
        primaryMeaning = "seven, to satisfy",
        extendedMeaning = "Seven, and being satisfied/full.",
        quranUsage = "'Seven (sab') heavens.' Sab'ah is seven. Sab' also means wild beast.",
        notes = "Seven heavens, seven earths, seven circuits of tawaf."
      ),
      RootMeaningData(
        root = "ث-م-ن",
        primaryMeaning = "eight, price, value",
        extendedMeaning = "Eight and price/value.",
        quranUsage = "'Eight (thamaniyah) pairs.' Thamaniyah is eight. Thaman is price.",
        notes = "Eight angels carry the Throne on Judgment Day."
      ),
      RootMeaningData(
        root = "ت-س-ع",
        primaryMeaning = "nine",
        extendedMeaning = "Nine.",
        quranUsage = "'Nine (tis') clear signs.' Tis'ah is nine.",
        notes = "Nine signs were given to Musa."
      ),
      RootMeaningData(
        root = "ع-ش-ر",
        primaryMeaning = "ten, to associate",
        extendedMeaning = "Ten, and social interaction/companionship.",
        quranUsage = "'Ten ('ashr) complete nights.' 'Ashrah is ten. 'Ushrah is companionship.",
        notes = "The first ten days of Dhul Hijjah are blessed."
      ),
      RootMeaningData(
        root = "م-ء-ة",
        primaryMeaning = "hundred",
        extendedMeaning = "One hundred.",
        quranUsage = "'A hundred (mi'ah) lashes.' Mi'ah is hundred.",
        notes = "Used in various legal punishments and stories."
      ),
      RootMeaningData(
        root = "أ-ل-ف",
        primaryMeaning = "thousand, to be familiar",
        extendedMeaning = "Thousand, and familiarity/friendship.",
        quranUsage = "'A thousand (alf) years.' Alf is thousand. Ulfah is familiarity.",
        notes = "Laylat al-Qadr is better than a thousand months."
      ),

      // === DIRECTIONS ===
      RootMeaningData(
        root = "ش-ر-ق",
        primaryMeaning = "east, sunrise, to rise",
        extendedMeaning = "East direction, sunrise, and rising.",
        quranUsage = "'Lord of the East (mashriq) and West.' Sharq is east. Shurooq is sunrise.",
        notes = "The sun rises from the east - a daily sign."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "west, sunset, strange",
        extendedMeaning = "West direction, sunset, and being strange/foreign.",
        quranUsage = "'Lord of the East and West (maghrib).' Gharb is west. Gharib is stranger.",
        notes = "Islam began as something strange and will return to being strange."
      ),
      RootMeaningData(
        root = "ش-م-ل",
        primaryMeaning = "north, left, to include",
        extendedMeaning = "North, left side, and encompassing/including.",
        quranUsage = "'Those of the left (shimal).' Shimal is left/north. Shamil is comprehensive.",
        notes = "Companions of the left are those destined for punishment."
      ),
      RootMeaningData(
        root = "ي-م-ن",
        primaryMeaning = "right, south, blessing",
        extendedMeaning = "Right side, south (Yemen), blessing, and oath.",
        quranUsage = "'Those of the right (yamin).' Yamin is right. Yumn is blessing. Yemen is south.",
        notes = "Companions of the right are blessed. Oaths are sworn by the right hand."
      ),
      RootMeaningData(
        root = "ف-و-ق",
        primaryMeaning = "above, over, superior",
        extendedMeaning = "Being above, superiority.",
        quranUsage = "'Above (fawqa) them.' Fawq is above. Fawqi is upper.",
        notes = "Allah is above His creation in status, not location."
      ),
      RootMeaningData(
        root = "ت-ح-ت",
        primaryMeaning = "below, under, beneath",
        extendedMeaning = "Being below or underneath.",
        quranUsage = "'Gardens beneath (tahta) which rivers flow.' Taht is below.",
        notes = "Rivers flow beneath the gardens of Paradise."
      ),
      RootMeaningData(
        root = "أ-م-م",
        primaryMeaning = "front, before, mother, nation",
        extendedMeaning = "In front, mother, nation, and leader (imam).",
        quranUsage = "'Before (amama) them.' Amam is in front. Imam is leader.",
        notes = "An imam leads from the front in prayer and guidance."
      ),
      RootMeaningData(
        root = "خ-ل-ف",
        primaryMeaning = "behind, after, successor",
        extendedMeaning = "Behind, after, succeeding, and being different.",
        quranUsage = "'Behind (khalfa) them.' Khalaf is successor. Khalifah is vicegerent.",
        notes = "Humans are khalifah (successors/vicegerents) on earth."
      ),
      RootMeaningData(
        root = "ق-ب-ل",
        primaryMeaning = "before, facing, to accept",
        extendedMeaning = "Before in time, facing toward, and accepting.",
        quranUsage = "'Before (qabla) this.' Qabl is before. Qiblah is direction faced.",
        notes = "The Qiblah is the direction Muslims face in prayer."
      ),
      RootMeaningData(
        root = "ب-ع-د",
        primaryMeaning = "after, far, distance",
        extendedMeaning = "After in time, far in distance.",
        quranUsage = "'After (ba'da) that.' Ba'd is after. Bu'd is distance. Ba'id is far.",
        notes = "Used for both temporal and spatial distance."
      ),

      // === WEATHER AND NATURAL PHENOMENA ===
      RootMeaningData(
        root = "م-ط-ر",
        primaryMeaning = "rain",
        extendedMeaning = "Rain, especially heavy or harmful rain.",
        quranUsage = "'We rained (amtarna) upon them stones.' Matar is rain.",
        notes = "Matar often refers to destructive rain, while ghayth is beneficial rain."
      ),
      RootMeaningData(
        root = "غ-ي-ث",
        primaryMeaning = "rain, relief, help",
        extendedMeaning = "Beneficial rain that brings relief.",
        quranUsage = "'He sends down the rain (ghayth).' Ghayth is saving rain.",
        notes = "Ghayth implies rain that relieves drought - a mercy."
      ),
      RootMeaningData(
        root = "ر-ع-د",
        primaryMeaning = "thunder",
        extendedMeaning = "Thunder and trembling.",
        quranUsage = "'The thunder (ra'd) glorifies His praise.' Ra'd is thunder.",
        notes = "Surah Ar-Ra'd - thunder glorifies Allah."
      ),
      RootMeaningData(
        root = "ب-ر-ق",
        primaryMeaning = "lightning, to shine",
        extendedMeaning = "Lightning and shining brightly.",
        quranUsage = "'The lightning (barq) almost takes away their sight.' Barq is lightning.",
        notes = "Lightning is a sign of Allah's power."
      ),
      RootMeaningData(
        root = "س-ح-ب",
        primaryMeaning = "cloud, to drag",
        extendedMeaning = "Clouds and dragging/pulling.",
        quranUsage = "'He drives the clouds (sahab).' Sahab is clouds.",
        notes = "Allah drives the clouds to revive dead lands."
      ),
      RootMeaningData(
        root = "ر-ي-ح",
        primaryMeaning = "wind, spirit, rest",
        extendedMeaning = "Wind, scent/spirit, and rest/relief.",
        quranUsage = "'We sent the winds (riyah) as good tidings.' Rih is wind. Rawh is rest.",
        notes = "Winds carry rain clouds - a mercy from Allah."
      ),
      RootMeaningData(
        root = "ع-ص-ف",
        primaryMeaning = "violent wind, chaff",
        extendedMeaning = "Violent wind and empty husks/chaff.",
        quranUsage = "'Like eaten chaff ('asf).' 'Asif is violent wind. 'Asf is chaff.",
        notes = "Violent winds were sent as punishment to past nations."
      ),
      RootMeaningData(
        root = "ص-ر-ص-ر",
        primaryMeaning = "cold violent wind",
        extendedMeaning = "Extremely cold, howling wind.",
        quranUsage = "'A screaming wind (sarsar).' Sarsar is freezing violent wind.",
        notes = "The 'Ad were destroyed by sarsar wind."
      ),
      RootMeaningData(
        root = "ث-ل-ج",
        primaryMeaning = "snow, ice",
        extendedMeaning = "Snow and ice.",
        quranUsage = "Thalj is snow/ice. Used in purification prayers.",
        notes = "The Prophet prayed to be purified with water, snow, and ice."
      ),
      RootMeaningData(
        root = "ب-ر-د",
        primaryMeaning = "cold, hail, coolness",
        extendedMeaning = "Cold temperature, hail, and coolness.",
        quranUsage = "'Mountains of hail (barad).' Bard is cold. Barad is hail.",
        notes = "Hail is stored in the heavens and sent down."
      ),
      RootMeaningData(
        root = "ح-ر-ر",
        primaryMeaning = "heat, free, silk",
        extendedMeaning = "Heat, freedom, and silk (fine/smooth).",
        quranUsage = "'The heat (harr) of Hell.' Harr is heat. Hurr is free. Harir is silk.",
        notes = "The righteous will wear silk (harir) in Paradise."
      ),
      RootMeaningData(
        root = "ز-ل-ز-ل",
        primaryMeaning = "earthquake, to shake",
        extendedMeaning = "Earthquake, violent shaking.",
        quranUsage = "'When the earth is shaken (zulzilat).' Zalzalah is earthquake.",
        notes = "Surah Az-Zalzalah describes the final earthquake."
      ),

      // === CLOTHING AND ADORNMENT ===
      RootMeaningData(
        root = "ل-ب-س",
        primaryMeaning = "clothing, to wear, confusion",
        extendedMeaning = "Wearing clothes, and mixing/confusing.",
        quranUsage = "'Clothing (libas) of righteousness is best.' Libas is clothing. Labs is confusion.",
        notes = "Righteousness is the best clothing for the soul."
      ),
      RootMeaningData(
        root = "ث-و-ب",
        primaryMeaning = "garment, to return, reward",
        extendedMeaning = "Garment, returning, and reward.",
        quranUsage = "'Purify your garments (thiyab).' Thawb is garment. Thawab is reward.",
        notes = "Physical and spiritual purity are connected."
      ),
      RootMeaningData(
        root = "ح-ل-ي",
        primaryMeaning = "ornament, jewelry",
        extendedMeaning = "Ornaments, jewelry, and adornment.",
        quranUsage = "'Bracelets of gold (huliy).' Hilyah is ornament.",
        notes = "Paradise dwellers will be adorned with gold and pearls."
      ),
      RootMeaningData(
        root = "ز-ي-ن",
        primaryMeaning = "adornment, beauty, to beautify",
        extendedMeaning = "Adornment, beautification, and decoration.",
        quranUsage = "'We adorned (zayyanna) the lower heaven with stars.' Zinah is adornment.",
        notes = "Allah beautifies the sky with stars and hearts with faith."
      ),
      RootMeaningData(
        root = "ت-ا-ج",
        primaryMeaning = "crown",
        extendedMeaning = "Crown and coronation.",
        quranUsage = "Taj is crown. The believers will be crowned in Paradise.",
        notes = "Crowns symbolize honor and authority."
      ),
      RootMeaningData(
        root = "خ-ا-ت-م",
        primaryMeaning = "seal, ring, last",
        extendedMeaning = "Seal, signet ring, and being the last/final.",
        quranUsage = "'Seal (khatam) of the prophets.' Khatam is seal/last. Khatim is ring.",
        notes = "Muhammad is the Khatam (seal/last) of the prophets."
      ),

      // === BUILDINGS AND STRUCTURES ===
      RootMeaningData(
        root = "ب-ن-ي",
        primaryMeaning = "building, to build, son",
        extendedMeaning = "Building, construction, and children (who build the family).",
        quranUsage = "'We built (banayna) above you seven.' Bina' is building. Ibn is son.",
        notes = "Building and having children both involve construction and legacy."
      ),
      RootMeaningData(
        root = "ب-ي-ت",
        primaryMeaning = "house, to spend the night",
        extendedMeaning = "House, dwelling, and spending the night.",
        quranUsage = "'The Ancient House (al-Bayt al-'Atiq).' Bayt is house. Bayat is overnight stay.",
        notes = "The Kaaba is called Bayt Allah (House of Allah)."
      ),
      RootMeaningData(
        root = "د-ا-ر",
        primaryMeaning = "abode, house, realm",
        extendedMeaning = "Dwelling, abode, and realm/domain.",
        quranUsage = "'The Abode (dar) of the Hereafter.' Dar is abode. Diyar is dwellings.",
        notes = "Dar al-Islam (realm of Islam), Dar al-Akhirah (abode of Hereafter)."
      ),
      RootMeaningData(
        root = "م-س-ج-د",
        primaryMeaning = "mosque, place of prostration",
        extendedMeaning = "Place of prostration, mosque.",
        quranUsage = "'The Sacred Mosque (al-Masjid al-Haram).' Masjid is mosque.",
        notes = "Derived from س-ج-د (prostration) - a mosque is where one prostrates."
      ),
      RootMeaningData(
        root = "ق-ص-ر",
        primaryMeaning = "palace, to shorten, limit",
        extendedMeaning = "Palace, shortening, and limitation.",
        quranUsage = "'Lofty palaces (qusur).' Qasr is palace. Qasr also means shortening prayer.",
        notes = "Travelers can shorten (qasr) their prayers."
      ),
      RootMeaningData(
        root = "ب-ر-ج",
        primaryMeaning = "tower, constellation, to appear",
        extendedMeaning = "Tower, zodiac constellation, and appearing prominently.",
        quranUsage = "'By the sky with its constellations (buruj).' Burj is tower/constellation.",
        notes = "Surah Al-Buruj - the constellations/towers."
      ),
      RootMeaningData(
        root = "س-و-ر",
        primaryMeaning = "wall, chapter, to leap",
        extendedMeaning = "Wall (surrounding), chapter of Quran, and leaping over.",
        quranUsage = "'A wall (sur) between them.' Sur is wall. Surah is chapter.",
        notes = "Each surah is like a walled enclosure of meaning."
      ),
      RootMeaningData(
        root = "ب-ا-ب",
        primaryMeaning = "door, gate, chapter",
        extendedMeaning = "Door, gate, and a section/chapter.",
        quranUsage = "'Enter the gate (bab) in prostration.' Bab is door/gate. Abwab is doors.",
        notes = "Paradise has eight gates; Hell has seven."
      ),
      RootMeaningData(
        root = "ج-س-ر",
        primaryMeaning = "bridge",
        extendedMeaning = "Bridge, and courage (bridging fear).",
        quranUsage = "Jisr is bridge. Related to crossing over.",
        notes = "The Sirat bridge crosses over Hell."
      ),
      RootMeaningData(
        root = "ق-ب-ر",
        primaryMeaning = "grave, to bury",
        extendedMeaning = "Grave, tomb, and burial.",
        quranUsage = "'When the graves (qubur) are overturned.' Qabr is grave. Maqbarah is cemetery.",
        notes = "The graves will release their contents on Judgment Day."
      ),

      // === WRITING AND KNOWLEDGE ===
      RootMeaningData(
        root = "ك-ت-ب",
        primaryMeaning = "to write, book, prescribe",
        extendedMeaning = "Writing, a book/scripture, and prescribing/decreeing.",
        quranUsage = "'The Book (kitab) in which there is no doubt.' Kitab is book. Katib is scribe.",
        notes = "Al-Kitab refers to the Quran and previous scriptures."
      ),
      RootMeaningData(
        root = "ق-ل-م",
        primaryMeaning = "pen",
        extendedMeaning = "Pen, the instrument of writing.",
        quranUsage = "'By the pen (qalam) and what they write.' Qalam is pen.",
        notes = "Surah Al-Qalam - Allah swears by the pen. The first creation was the pen."
      ),
      RootMeaningData(
        root = "ص-ح-ف",
        primaryMeaning = "page, scripture, newspaper",
        extendedMeaning = "Pages, written sheets, and scriptures.",
        quranUsage = "'In honored scriptures (suhuf).' Sahifah is page. Suhuf is scriptures.",
        notes = "Suhuf Ibrahim - the scriptures of Ibrahim."
      ),
      RootMeaningData(
        root = "ل-و-ح",
        primaryMeaning = "tablet, board, to appear",
        extendedMeaning = "Tablet, board, and becoming visible.",
        quranUsage = "'In a Preserved Tablet (lawh mahfuz).' Lawh is tablet. Alwah is tablets.",
        notes = "The Lawh al-Mahfuz contains all that was and will be."
      ),
      RootMeaningData(
        root = "ح-ب-ر",
        primaryMeaning = "ink, scholar, to gladden",
        extendedMeaning = "Ink, religious scholar, and joy.",
        quranUsage = "Hibr is ink. Ahbar are scholars (rabbis). Hubur is joy.",
        notes = "Scholars are associated with ink - the tools of knowledge."
      ),
      RootMeaningData(
        root = "ر-ق-م",
        primaryMeaning = "writing, number, to mark",
        extendedMeaning = "Writing, numbering, and marking.",
        quranUsage = "'A written (marqum) register.' Raqm is number/writing.",
        notes = "Our deeds are numbered and recorded precisely."
      ),

      // === WAR AND PEACE ===
      RootMeaningData(
        root = "ح-ر-ب",
        primaryMeaning = "war, to fight",
        extendedMeaning = "War, fighting, and hostility.",
        quranUsage = "'Be informed of war (harb) from Allah.' Harb is war. Muharib is warrior.",
        notes = "War against usury is mentioned - it's that serious."
      ),
      RootMeaningData(
        root = "ق-ت-ل",
        primaryMeaning = "to kill, fighting",
        extendedMeaning = "Killing, fighting, and combat.",
        quranUsage = "'Fight (qatilu) in the way of Allah.' Qatl is killing. Qital is fighting.",
        notes = "Fighting is permitted in self-defense and to stop oppression."
      ),
      RootMeaningData(
        root = "س-ل-م",
        primaryMeaning = "peace, submission, safety",
        extendedMeaning = "Peace, safety, submission, and soundness.",
        quranUsage = "'Enter into peace (silm) completely.' Salam is peace. Islam is submission.",
        notes = "The greeting of peace (salam) is from the root of Islam."
      ),
      RootMeaningData(
        root = "ص-ل-ح",
        primaryMeaning = "peace, reconciliation, reform",
        extendedMeaning = "Making peace, reconciliation, and reform.",
        quranUsage = "'Make peace (sulh) between them.' Sulh is peace treaty. Islah is reform.",
        notes = "Reconciliation between believers is obligatory."
      ),
      RootMeaningData(
        root = "ن-ص-ر",
        primaryMeaning = "victory, help, support",
        extendedMeaning = "Victory, divine help, and support.",
        quranUsage = "'When Allah's help (nasr) comes.' Nasr is victory/help. Ansar are helpers.",
        notes = "The Ansar were the helpers of Madinah who supported the Prophet."
      ),
      RootMeaningData(
        root = "ف-ت-ح",
        primaryMeaning = "to open, victory, conquest",
        extendedMeaning = "Opening, victory, and beginning.",
        quranUsage = "'Indeed We have given you a clear victory (fath).' Fath is opening/victory.",
        notes = "Surah Al-Fath celebrates the Treaty of Hudaybiyyah as a victory."
      ),
      RootMeaningData(
        root = "غ-ل-ب",
        primaryMeaning = "to overcome, defeat",
        extendedMeaning = "Overcoming, defeating, and prevailing.",
        quranUsage = "'They will overcome (sayaghlibun).' Ghalaba is to overcome. Ghalib is victor.",
        notes = "The Romans were defeated but would overcome - a Quranic prophecy fulfilled."
      ),
      RootMeaningData(
        root = "ه-ز-م",
        primaryMeaning = "defeat, to rout",
        extendedMeaning = "Defeat, routing an army.",
        quranUsage = "'They defeated (hazamu) them.' Hazimah is defeat.",
        notes = "Armies are defeated by Allah's permission."
      ),
      RootMeaningData(
        root = "س-ي-ف",
        primaryMeaning = "sword",
        extendedMeaning = "Sword.",
        quranUsage = "Sayf is sword. Suyuf is swords.",
        notes = "The sword represents authority and power."
      ),
      RootMeaningData(
        root = "ر-م-ح",
        primaryMeaning = "spear",
        extendedMeaning = "Spear, lance.",
        quranUsage = "Rumh is spear. Rimah is spears.",
        notes = "Spears were common weapons in early Islamic battles."
      ),
      RootMeaningData(
        root = "د-ر-ع",
        primaryMeaning = "armor, shield",
        extendedMeaning = "Armor, coat of mail.",
        quranUsage = "'We taught him the making of armor (laboos).' Dir' is armor.",
        notes = "Dawud was taught to make armor as a blessing."
      ),
      RootMeaningData(
        root = "ج-ن-د",
        primaryMeaning = "soldier, army",
        extendedMeaning = "Soldiers, army, and troops.",
        quranUsage = "'None knows the soldiers (junud) of your Lord except Him.' Jund is army. Junud is soldiers.",
        notes = "Allah's armies include angels and forces of nature."
      ),

      // === TRADE AND COMMERCE ===
      RootMeaningData(
        root = "ب-ي-ع",
        primaryMeaning = "sale, to sell, pledge",
        extendedMeaning = "Selling, transaction, and pledge of allegiance.",
        quranUsage = "'Allah has purchased from the believers.' Bay' is sale. Bay'ah is pledge.",
        notes = "Trade is permitted; usury is forbidden."
      ),
      RootMeaningData(
        root = "ش-ر-ي",
        primaryMeaning = "to buy, purchase",
        extendedMeaning = "Buying and purchasing.",
        quranUsage = "'They sold (sharaw) him for a small price.' Shira' is buying. Ishtara is to purchase.",
        notes = "Yusuf was sold for a few coins."
      ),
      RootMeaningData(
        root = "ت-ج-ر",
        primaryMeaning = "trade, commerce, merchant",
        extendedMeaning = "Trade, commerce, and business.",
        quranUsage = "'A trade (tijarah) that will never perish.' Tijarah is trade. Tajir is merchant.",
        notes = "The best trade is with Allah - faith for Paradise."
      ),
      RootMeaningData(
        root = "ر-ب-ح",
        primaryMeaning = "profit, gain",
        extendedMeaning = "Profit and gain from trade.",
        quranUsage = "'Their trade did not profit (rabihat) them.' Ribh is profit.",
        notes = "Those who trade guidance for misguidance lose their profit."
      ),
      RootMeaningData(
        root = "خ-س-ر",
        primaryMeaning = "loss, to lose",
        extendedMeaning = "Loss in trade and general loss.",
        quranUsage = "'Those are the losers (khasirun).' Khusr is loss. Khasir is loser.",
        notes = "Surah Al-'Asr declares all humanity in loss except believers."
      ),
      RootMeaningData(
        root = "ر-ب-و",
        primaryMeaning = "usury, interest, to increase",
        extendedMeaning = "Usury/interest, and increasing/growing.",
        quranUsage = "'Allah destroys usury (riba).' Riba is usury. Rabwa is hill (raised land).",
        notes = "Riba (usury/interest) is strictly forbidden."
      ),
      RootMeaningData(
        root = "د-ي-ن",
        primaryMeaning = "religion, debt, judgment",
        extendedMeaning = "Religion, debt (what is owed), and recompense.",
        quranUsage = "'Owner of the Day of Judgment (din).' Din is religion and recompense. Dayn is debt.",
        notes = "Religion is a debt we owe to Allah; Judgment Day is when debts are settled."
      ),
      RootMeaningData(
        root = "ق-ر-ض",
        primaryMeaning = "loan, to lend",
        extendedMeaning = "Loan, lending, and cutting off (a piece).",
        quranUsage = "'Who will loan Allah a goodly loan (qard)?' Qard is loan.",
        notes = "Giving to Allah is described as lending Him - guaranteed return."
      ),
      RootMeaningData(
        root = "م-ت-ع",
        primaryMeaning = "goods, enjoyment, provision",
        extendedMeaning = "Goods, enjoyment, and temporary provision.",
        quranUsage = "'A brief enjoyment (mata').' Mata' is goods/enjoyment. Tamattu' is enjoying.",
        notes = "Worldly enjoyment is brief compared to the Hereafter."
      ),
      RootMeaningData(
        root = "م-ا-ل",
        primaryMeaning = "wealth, property, money",
        extendedMeaning = "Wealth, property, and possessions.",
        quranUsage = "'Wealth (mal) and children are the adornment of life.' Mal is wealth. Amwal is possessions.",
        notes = "Wealth is a test - it must be earned and spent properly."
      ),
      RootMeaningData(
        root = "ك-ن-ز",
        primaryMeaning = "treasure, to hoard",
        extendedMeaning = "Treasure, hoarding wealth.",
        quranUsage = "'Those who hoard (yaknizun) gold and silver.' Kanz is treasure. Iknaz is hoarding.",
        notes = "Hoarding wealth without paying zakat is condemned."
      ),

      // === MOVEMENT AND TRAVEL ===
      RootMeaningData(
        root = "م-ش-ي",
        primaryMeaning = "to walk",
        extendedMeaning = "Walking, going on foot.",
        quranUsage = "'Walk (imshi) in the paths of the earth.' Mashi is walking. Mashsha' is one who walks much.",
        notes = "Walking humbly is praised; walking arrogantly is condemned."
      ),
      RootMeaningData(
        root = "س-ي-ر",
        primaryMeaning = "to travel, journey, conduct",
        extendedMeaning = "Traveling, journeying, and conduct/biography.",
        quranUsage = "'Travel (siru) through the earth.' Sayr is travel. Sirah is biography.",
        notes = "Traveling to reflect on past nations is encouraged."
      ),
      RootMeaningData(
        root = "س-ف-ر",
        primaryMeaning = "journey, to reveal, book",
        extendedMeaning = "Journey, unveiling, and written book.",
        quranUsage = "'Like a donkey carrying books (asfar).' Safar is journey. Sifr is book.",
        notes = "Travel (safar) allows shortening prayers."
      ),
      RootMeaningData(
        root = "ر-ح-ل",
        primaryMeaning = "to depart, journey, saddle",
        extendedMeaning = "Departing, traveling, and loading for journey.",
        quranUsage = "'The journey (rihlah) of winter and summer.' Rihlah is journey. Rahil is departing.",
        notes = "Quraysh's trade journeys to Yemen (winter) and Syria (summer)."
      ),
      RootMeaningData(
        root = "ر-ك-ب",
        primaryMeaning = "to ride, mount, embark",
        extendedMeaning = "Riding, mounting animals or vehicles.",
        quranUsage = "'You will surely ride (latarkabunna) stage after stage.' Rukub is riding. Markab is vehicle.",
        notes = "Riding is a blessing - from animals to ships."
      ),
      RootMeaningData(
        root = "ج-ر-ي",
        primaryMeaning = "to flow, run, occur",
        extendedMeaning = "Flowing, running, and happening.",
        quranUsage = "'Rivers flowing (tajri) beneath them.' Jary is flowing. Jariyah is flowing/ongoing.",
        notes = "Sadaqah jariyah - ongoing charity that keeps flowing."
      ),
      RootMeaningData(
        root = "ط-و-ف",
        primaryMeaning = "to circumambulate, go around",
        extendedMeaning = "Going around, circumambulating, and wandering.",
        quranUsage = "'Let them circumambulate (yattawwafu) the Ancient House.' Tawaf is circumambulation.",
        notes = "Tawaf around the Kaaba is a pillar of Hajj."
      ),
      RootMeaningData(
        root = "س-ع-ي",
        primaryMeaning = "to strive, walk briskly, effort",
        extendedMeaning = "Striving, walking briskly, and making effort.",
        quranUsage = "'Running (sa'y) between Safa and Marwa.' Sa'y is striving/walking.",
        notes = "Sa'y commemorates Hajar's search for water."
      ),
      RootMeaningData(
        root = "ع-د-و",
        primaryMeaning = "to run, enemy, transgress",
        extendedMeaning = "Running fast, enmity, and transgression.",
        quranUsage = "'Running (ya'duna) through the valley.' 'Aduw is enemy. 'Udwan is transgression.",
        notes = "The same root gives running (speed) and enmity (running against)."
      ),
      RootMeaningData(
        root = "ق-ع-د",
        primaryMeaning = "to sit, stay behind",
        extendedMeaning = "Sitting, remaining seated, and staying behind.",
        quranUsage = "'Those who sat behind (qa'adun).' Qu'ud is sitting. Qa'id is one sitting.",
        notes = "Those who stayed behind from battle without excuse are criticized."
      ),
      RootMeaningData(
        root = "ن-و-م",
        primaryMeaning = "sleep",
        extendedMeaning = "Sleep and slumber.",
        quranUsage = "'Neither drowsiness nor sleep (nawm) overtakes Him.' Nawm is sleep. Na'im is sleeper.",
        notes = "Allah never sleeps - He is ever-watchful."
      ),
      RootMeaningData(
        root = "ي-ق-ظ",
        primaryMeaning = "wakefulness, to awaken",
        extendedMeaning = "Being awake, alertness, and awakening.",
        quranUsage = "'You would think them awake (ayqaz).' Yaqazah is wakefulness.",
        notes = "The People of the Cave appeared awake while sleeping."
      ),

      // === EMOTIONS AND STATES ===
      RootMeaningData(
        root = "ف-ر-ح",
        primaryMeaning = "joy, happiness, to rejoice",
        extendedMeaning = "Joy, happiness, and rejoicing.",
        quranUsage = "'Rejoicing (farihin) in what Allah gave them.' Farah is joy. Farih is joyful.",
        notes = "Excessive joy in worldly things is warned against."
      ),
      RootMeaningData(
        root = "ح-ز-ن",
        primaryMeaning = "sadness, grief, to grieve",
        extendedMeaning = "Sadness, grief, and sorrow.",
        quranUsage = "'Do not grieve (tahzan).' Huzn is sadness. Hazin is sad.",
        notes = "The Prophet was told not to grieve over those who reject faith."
      ),
      RootMeaningData(
        root = "غ-ض-ب",
        primaryMeaning = "anger, wrath",
        extendedMeaning = "Anger and divine wrath.",
        quranUsage = "'Those who earned Your anger (ghadab).' Ghadab is anger. Ghadib is angry.",
        notes = "Divine anger is earned by knowing truth and rejecting it."
      ),
      RootMeaningData(
        root = "ر-ض-ي",
        primaryMeaning = "pleasure, satisfaction, contentment",
        extendedMeaning = "Being pleased, satisfaction, and contentment.",
        quranUsage = "'Allah is pleased (radiya) with them.' Rida is pleasure. Mardiy is pleasing.",
        notes = "Allah's pleasure (ridwan) is the greatest reward."
      ),
      RootMeaningData(
        root = "س-خ-ط",
        primaryMeaning = "displeasure, anger",
        extendedMeaning = "Displeasure, divine anger.",
        quranUsage = "'They followed what displeased (askhata) Allah.' Sakhat is displeasure.",
        notes = "Following desires leads to Allah's displeasure."
      ),
      RootMeaningData(
        root = "ح-ب-ب",
        primaryMeaning = "love, to love, seed",
        extendedMeaning = "Love, affection, and seeds/grains.",
        quranUsage = "'Allah loves (yuhibbu) the doers of good.' Hubb is love. Habib is beloved.",
        notes = "Love of Allah and His messenger must exceed all other loves."
      ),
      RootMeaningData(
        root = "ب-غ-ض",
        primaryMeaning = "hatred, to hate",
        extendedMeaning = "Hatred and dislike.",
        quranUsage = "'Hatred (baghda') has appeared from their mouths.' Bughd is hatred.",
        notes = "Hatred for Allah's sake (of evil) is part of faith."
      ),
      RootMeaningData(
        root = "ك-ر-ه",
        primaryMeaning = "dislike, to hate, coercion",
        extendedMeaning = "Disliking, hating, and forcing.",
        quranUsage = "'Perhaps you dislike (takrahu) something good for you.' Kurh is dislike. Ikrah is coercion.",
        notes = "What we dislike may be good; what we love may be harmful."
      ),
      RootMeaningData(
        root = "ش-و-ق",
        primaryMeaning = "longing, yearning",
        extendedMeaning = "Longing, yearning, and eager desire.",
        quranUsage = "Shawq is longing. Mushtaq is one who yearns.",
        notes = "Longing for Allah and Paradise motivates worship."
      ),
      RootMeaningData(
        root = "ن-د-م",
        primaryMeaning = "regret, remorse",
        extendedMeaning = "Regret and remorse.",
        quranUsage = "'They became regretful (nadimin).' Nadm is regret. Nadim is companion.",
        notes = "Regret on Judgment Day will be too late."
      ),
      RootMeaningData(
        root = "ع-ج-ب",
        primaryMeaning = "wonder, amazement, vanity",
        extendedMeaning = "Wonder, amazement, and self-admiration.",
        quranUsage = "'Do you wonder ('ajibta)?' 'Ajab is wonder. 'Ujb is vanity.",
        notes = "Wondering at Allah's signs leads to faith; self-admiration leads to arrogance."
      ),

      // === SENSES AND PERCEPTION ===
      RootMeaningData(
        root = "س-م-ع",
        primaryMeaning = "hearing, to hear, obey",
        extendedMeaning = "Hearing, listening, and obeying.",
        quranUsage = "'We hear (sami'na) and we obey.' Sam' is hearing. Sami' is hearer.",
        notes = "Al-Sami' (The All-Hearing) is one of Allah's names."
      ),
      RootMeaningData(
        root = "ب-ص-ر",
        primaryMeaning = "sight, vision, insight",
        extendedMeaning = "Seeing, vision, and spiritual insight.",
        quranUsage = "'Sights (absar) cannot perceive Him.' Basar is sight. Basir is seeing.",
        notes = "Al-Basir (The All-Seeing) is one of Allah's names."
      ),
      RootMeaningData(
        root = "ش-م-م",
        primaryMeaning = "smell, to smell",
        extendedMeaning = "Smelling, the sense of smell.",
        quranUsage = "Shamm is smelling. The fragrance of Paradise can be smelled from afar.",
        notes = "Paradise has indescribable fragrances."
      ),
      RootMeaningData(
        root = "ذ-و-ق",
        primaryMeaning = "taste, to taste, experience",
        extendedMeaning = "Tasting and experiencing something.",
        quranUsage = "'Taste (dhuqu) the punishment.' Dhawq is taste.",
        notes = "Tasting implies direct, personal experience."
      ),
      RootMeaningData(
        root = "ل-م-س",
        primaryMeaning = "touch, to touch",
        extendedMeaning = "Touching, feeling with hands.",
        quranUsage = "'If they touched (lamasahu) it with their hands.' Lams is touch.",
        notes = "Disbelievers would deny even if they could touch miracles."
      ),
      RootMeaningData(
        root = "ح-س-س",
        primaryMeaning = "sense, feel, perceive",
        extendedMeaning = "Sensing, feeling, and perceiving.",
        quranUsage = "'When Isa sensed (ahassa) disbelief from them.' Hiss is sense. Ihsas is perception.",
        notes = "The prophets had keen spiritual perception."
      ),

      // === CELESTIAL BODIES ===
      RootMeaningData(
        root = "ش-م-س",
        primaryMeaning = "sun",
        extendedMeaning = "The sun.",
        quranUsage = "'By the sun (shams) and its brightness.' Shams is sun.",
        notes = "The sun is a sign of Allah - it rises and sets by His command."
      ),
      RootMeaningData(
        root = "ق-م-ر",
        primaryMeaning = "moon",
        extendedMeaning = "The moon.",
        quranUsage = "'By the moon (qamar) when it follows it.' Qamar is moon.",
        notes = "Surah Al-Qamar recounts the splitting of the moon."
      ),
      RootMeaningData(
        root = "ن-ج-م",
        primaryMeaning = "star, plant, to appear",
        extendedMeaning = "Star, plant emerging, and appearing gradually.",
        quranUsage = "'By the star (najm) when it descends.' Najm is star/plant.",
        notes = "Surah An-Najm - the star. Also means plants without stems."
      ),
      RootMeaningData(
        root = "ك-و-ك-ب",
        primaryMeaning = "planet, star",
        extendedMeaning = "Planet or bright star.",
        quranUsage = "'A brilliant star (kawkab).' Kawkab is planet/star. Kawakib are planets.",
        notes = "Yusuf saw eleven kawkab (stars/planets) prostrating to him."
      ),
      RootMeaningData(
        root = "ف-ل-ك",
        primaryMeaning = "orbit, celestial sphere, ship",
        extendedMeaning = "Orbit, sphere, and ship (which orbits the water).",
        quranUsage = "'Each in an orbit (falak) swimming.' Falak is orbit/sphere.",
        notes = "Celestial bodies swim in their orbits - Quranic precision."
      ),
      RootMeaningData(
        root = "س-ب-ح",
        primaryMeaning = "to swim, glorify",
        extendedMeaning = "Swimming (in water or space), and glorifying Allah.",
        quranUsage = "'Each swimming (yasbahuun) in an orbit.' Sibahah is swimming. Tasbih is glorification.",
        notes = "Celestial bodies 'swim' in their orbits while glorifying Allah."
      ),

      // === HEALTH AND BODY ===
      RootMeaningData(
        root = "م-ر-ض",
        primaryMeaning = "disease, illness, sickness",
        extendedMeaning = "Physical illness and spiritual disease.",
        quranUsage = "'In their hearts is disease (marad).' Marad is disease. Marid is sick.",
        notes = "Hearts can be spiritually diseased with doubt or hypocrisy."
      ),
      RootMeaningData(
        root = "ش-ف-ي",
        primaryMeaning = "healing, cure",
        extendedMeaning = "Healing and curing.",
        quranUsage = "'And He heals (yashfi) me.' Shifa' is healing. Shafi is healer.",
        notes = "Allah is Ash-Shafi (The Healer). The Quran is a healing for hearts."
      ),
      RootMeaningData(
        root = "ص-ح-ح",
        primaryMeaning = "health, correct, authentic",
        extendedMeaning = "Health, correctness, and authenticity.",
        quranUsage = "Sihhah is health. Sahih is correct/authentic.",
        notes = "Sahih hadith are authentic narrations."
      ),
      RootMeaningData(
        root = "ع-م-ي",
        primaryMeaning = "blindness, to be blind",
        extendedMeaning = "Physical and spiritual blindness.",
        quranUsage = "'Blind (a'ma) in this world, blind in the Hereafter.' 'Ama is blindness.",
        notes = "Spiritual blindness is worse than physical blindness."
      ),
      RootMeaningData(
        root = "ص-م-م",
        primaryMeaning = "deafness, to be deaf",
        extendedMeaning = "Physical and spiritual deafness.",
        quranUsage = "'Deaf (summ), dumb, and blind.' Samam is deafness. Asamm is deaf.",
        notes = "Those who refuse to hear truth are spiritually deaf."
      ),
      RootMeaningData(
        root = "ب-ك-م",
        primaryMeaning = "dumbness, mute",
        extendedMeaning = "Being mute, unable to speak.",
        quranUsage = "'Deaf, dumb (bukm), and blind.' Bakam is muteness. Abkam is mute.",
        notes = "Those who won't speak truth are spiritually mute."
      ),
      RootMeaningData(
        root = "ج-ر-ح",
        primaryMeaning = "wound, to wound, to earn",
        extendedMeaning = "Wound, injuring, and earning (acquiring).",
        quranUsage = "'What you have earned (jarahtum) by day.' Jurh is wound. Iktisab is earning.",
        notes = "Earning (good or bad) is like acquiring wounds on one's record."
      ),
      RootMeaningData(
        root = "أ-ل-م",
        primaryMeaning = "pain, suffering",
        extendedMeaning = "Pain and suffering.",
        quranUsage = "'A painful (alim) punishment.' Alam is pain. Alim is painful.",
        notes = "The punishment of Hell is described as intensely painful."
      ),
      RootMeaningData(
        root = "ج-و-ع",
        primaryMeaning = "hunger",
        extendedMeaning = "Hunger and starvation.",
        quranUsage = "'He fed them against hunger (ju').' Ju' is hunger. Ja'i is hungry.",
        notes = "Allah saved Quraysh from hunger through their trade."
      ),
      RootMeaningData(
        root = "ع-ط-ش",
        primaryMeaning = "thirst",
        extendedMeaning = "Thirst.",
        quranUsage = "'They will not suffer thirst ('atash).' 'Atash is thirst. 'Atshan is thirsty.",
        notes = "The people of Paradise will never thirst."
      ),

      // === ADDITIONAL IMPORTANT ROOTS ===
      RootMeaningData(
        root = "ع-ر-ف",
        primaryMeaning = "to know, recognize, custom",
        extendedMeaning = "Knowing, recognizing, and custom/tradition.",
        quranUsage = "'They recognize (ya'rifunahu) it as they recognize their sons.' Ma'rifah is knowledge. 'Urf is custom.",
        notes = "Ma'ruf (the recognized good) comes from this root."
      ),
      RootMeaningData(
        root = "ن-ك-ر",
        primaryMeaning = "to deny, reject, abominable",
        extendedMeaning = "Denying, rejecting, and what is abominable.",
        quranUsage = "'Forbid the wrong (munkar).' Nukr is rejection. Munkar is wrong/evil.",
        notes = "Munkar is the opposite of ma'ruf - the rejected evil."
      ),
      RootMeaningData(
        root = "ح-س-ن",
        primaryMeaning = "beauty, goodness, excellence",
        extendedMeaning = "Beauty, goodness, and doing excellent.",
        quranUsage = "'Allah commands justice and excellence (ihsan).' Husn is beauty. Ihsan is excellence.",
        notes = "Ihsan is worship as if you see Allah, knowing He sees you."
      ),
      RootMeaningData(
        root = "س-و-ء",
        primaryMeaning = "evil, bad, harm",
        extendedMeaning = "Evil, badness, and harm.",
        quranUsage = "'Whoever does evil (su') will be recompensed.' Su' is evil. Sayyi'ah is bad deed.",
        notes = "Evil deeds are recorded and will be recompensed."
      ),
      RootMeaningData(
        root = "ب-ر-ك",
        primaryMeaning = "blessing, to bless, to kneel",
        extendedMeaning = "Blessing, divine favor, and kneeling (camels).",
        quranUsage = "'Blessed (mubarak) is He.' Barakah is blessing. Mubarak is blessed.",
        notes = "Barakah is divine blessing that increases and benefits."
      ),
      RootMeaningData(
        root = "ل-ع-ن",
        primaryMeaning = "curse, to curse",
        extendedMeaning = "Curse, being expelled from mercy.",
        quranUsage = "'The curse (la'nah) of Allah upon the wrongdoers.' La'n is curse. Mal'un is cursed.",
        notes = "Being cursed means being expelled from Allah's mercy."
      ),
      RootMeaningData(
        root = "غ-ف-ر",
        primaryMeaning = "forgiveness, to cover, helmet",
        extendedMeaning = "Forgiving, covering sins, and helmet (which covers).",
        quranUsage = "'He is the Forgiving (Ghafur).' Maghfirah is forgiveness. Ghafur is Most Forgiving.",
        notes = "Forgiveness covers sins as a helmet covers the head."
      ),
      RootMeaningData(
        root = "ع-ف-و",
        primaryMeaning = "pardon, to erase, surplus",
        extendedMeaning = "Pardoning, erasing sins, and surplus.",
        quranUsage = "'He is Pardoning ('Afuw).' 'Afw is pardon. 'Afuw is Most Pardoning.",
        notes = "'Afw erases sins completely, as if they never existed."
      ),
      RootMeaningData(
        root = "ذ-ن-ب",
        primaryMeaning = "sin, tail, consequence",
        extendedMeaning = "Sin, and tail/consequence (sins follow like a tail).",
        quranUsage = "'Forgive us our sins (dhunub).' Dhanb is sin. Dhunub is sins.",
        notes = "Sins follow a person like a tail follows an animal."
      ),
      RootMeaningData(
        root = "خ-ط-ء",
        primaryMeaning = "mistake, error, sin",
        extendedMeaning = "Error, missing the mark, and sin.",
        quranUsage = "'Indeed it was a great sin (khit').' Khata' is mistake. Khati'ah is sin.",
        notes = "Killing children was a grave sin (khit') of the pre-Islamic Arabs."
      ),
      RootMeaningData(
        root = "إ-ث-م",
        primaryMeaning = "sin, guilt, crime",
        extendedMeaning = "Sin and guilt, especially deliberate sin.",
        quranUsage = "'In them is great sin (ithm).' Ithm is sin. Athim is sinner.",
        notes = "Ithm implies sin that leaves guilt on the soul."
      ),
      RootMeaningData(
        root = "ف-ح-ش",
        primaryMeaning = "obscenity, indecency",
        extendedMeaning = "Obscene acts, indecency, and shameful deeds.",
        quranUsage = "'Do not approach obscenities (fawahish).' Fahsha' is obscenity. Fahish is obscene.",
        notes = "Fahishah often refers to sexual sins."
      ),
      RootMeaningData(
        root = "ط-ه-ر",
        primaryMeaning = "purity, to purify",
        extendedMeaning = "Purity, cleanliness, and purification.",
        quranUsage = "'Purified (mutahharah) pages.' Taharah is purity. Tahir is pure.",
        notes = "Both physical and spiritual purity are emphasized."
      ),
      RootMeaningData(
        root = "ن-ج-س",
        primaryMeaning = "impurity, filth",
        extendedMeaning = "Ritual impurity and filth.",
        quranUsage = "'The polytheists are impure (najas).' Najas is impurity. Najis is impure.",
        notes = "Spiritual impurity of shirk is worse than physical impurity."
      ),
      RootMeaningData(
        root = "و-ض-ء",
        primaryMeaning = "ablution, brightness",
        extendedMeaning = "Ablution (wudu) and brightness/clarity.",
        quranUsage = "'When you rise for prayer, wash your faces.' Wudu' is ablution. Wadi' is bright.",
        notes = "Wudu brings both physical cleanliness and spiritual brightness."
      ),
      RootMeaningData(
        root = "غ-س-ل",
        primaryMeaning = "washing, ritual bath",
        extendedMeaning = "Washing and ritual bathing (ghusl).",
        quranUsage = "'If you are in a state of major impurity, purify yourselves.' Ghusl is ritual bath.",
        notes = "Ghusl is required after certain states for prayer."
      ),
      RootMeaningData(
        root = "س-ت-ر",
        primaryMeaning = "covering, concealment, screen",
        extendedMeaning = "Covering, concealing, and veiling.",
        quranUsage = "'From behind a screen (hijab).' Sitr is covering. Satir is one who covers.",
        notes = "Allah is Al-Satir - He covers the faults of His servants."
      ),
      RootMeaningData(
        root = "ك-ش-ف",
        primaryMeaning = "to reveal, uncover, remove",
        extendedMeaning = "Revealing, uncovering, and removing (hardship).",
        quranUsage = "'He removes (yakshifu) the hardship.' Kashf is uncovering. Inkishaf is revelation.",
        notes = "Only Allah can truly remove hardship."
      ),
      RootMeaningData(
        root = "ف-ض-ل",
        primaryMeaning = "grace, favor, surplus",
        extendedMeaning = "Divine grace, favor, and excellence.",
        quranUsage = "'The grace (fadl) of Allah.' Fadl is grace. Fadilah is virtue.",
        notes = "All good is from Allah's fadl (grace), not our deserving."
      ),
      RootMeaningData(
        root = "م-ن-ن",
        primaryMeaning = "favor, to bestow, to remind",
        extendedMeaning = "Bestowing favor, and reminding of favors (negatively).",
        quranUsage = "'Allah has bestowed favor (manna) upon the believers.' Mann is favor.",
        notes = "Allah's mann is blessing; human mann (reminding of favors) is discouraged."
      ),
      RootMeaningData(
        root = "ر-ز-ق",
        primaryMeaning = "provision, sustenance",
        extendedMeaning = "Provision, sustenance, and livelihood.",
        quranUsage = "'Allah provides (yarzuqu) whom He wills.' Rizq is provision. Razzaq is Provider.",
        notes = "Ar-Razzaq (The Provider) is one of Allah's names."
      ),
      RootMeaningData(
        root = "ك-ف-ي",
        primaryMeaning = "sufficiency, to suffice",
        extendedMeaning = "Being sufficient and making something enough.",
        quranUsage = "'Is not Allah sufficient (kafi) for His servant?' Kifayah is sufficiency.",
        notes = "Allah is sufficient for all our needs."
      ),
      RootMeaningData(
        root = "غ-ن-ي",
        primaryMeaning = "rich, self-sufficient",
        extendedMeaning = "Wealth, richness, and being free from need.",
        quranUsage = "'Allah is Free of need (Ghani).' Ghina is wealth. Ghani is rich.",
        notes = "Al-Ghani - Allah is absolutely free of all need."
      ),
      RootMeaningData(
        root = "ف-ق-ر",
        primaryMeaning = "poverty, need",
        extendedMeaning = "Poverty, neediness, and dependence.",
        quranUsage = "'You are the poor (fuqara') in need of Allah.' Faqr is poverty. Faqir is poor.",
        notes = "All creation is faqir (poor/needy) before Allah."
      ),
      RootMeaningData(
        root = "س-أ-ل",
        primaryMeaning = "to ask, question, request",
        extendedMeaning = "Asking, questioning, and requesting.",
        quranUsage = "'Ask (sal) Allah of His bounty.' Su'al is question. Sa'il is asker.",
        notes = "Ask Allah - He is the only one who never tires of giving."
      ),
      RootMeaningData(
        root = "د-ع-و",
        primaryMeaning = "to call, invoke, pray",
        extendedMeaning = "Calling, inviting, supplicating, and claiming.",
        quranUsage = "'Call upon (ud'u) Me, I will respond.' Du'a is supplication. Da'wah is calling.",
        notes = "Du'a is the essence of worship."
      ),
      RootMeaningData(
        root = "ج-و-ب",
        primaryMeaning = "to answer, respond, pierce",
        extendedMeaning = "Answering, responding, and piercing through.",
        quranUsage = "'I respond (ujibu) to the caller.' Ijabah is answering. Jawab is answer.",
        notes = "Allah promises to answer those who call upon Him."
      ),
      RootMeaningData(
        root = "و-ك-ل",
        primaryMeaning = "trust, rely, entrust",
        extendedMeaning = "Trusting, relying upon, and entrusting.",
        quranUsage = "'Upon Allah rely (tawakkalu).' Tawakkul is reliance. Wakil is trustee.",
        notes = "Al-Wakil - Allah is the ultimate Trustee."
      ),
      RootMeaningData(
        root = "ح-ف-ظ",
        primaryMeaning = "preserve, protect, memorize",
        extendedMeaning = "Preserving, protecting, guarding, and memorizing.",
        quranUsage = "'Indeed, We sent down the reminder and We will preserve (hafizun) it.' Hifz is memorization.",
        notes = "Al-Hafiz - Allah preserves all things, especially His Book."
      ),
      RootMeaningData(
        root = "و-ق-ي",
        primaryMeaning = "protect, shield, piety",
        extendedMeaning = "Protection, shielding, and piety (which protects from sin).",
        quranUsage = "'Allah protects (yaqi) whom He wills.' Wiqayah is protection. Taqwa is piety.",
        notes = "Taqwa protects the soul from Allah's punishment."
      ),
      RootMeaningData(
        root = "ع-ص-م",
        primaryMeaning = "protect, prevent, infallibility",
        extendedMeaning = "Protection, prevention from sin, and infallibility.",
        quranUsage = "'There is no protector ('asim) from Allah.' 'Ismah is protection. Ma'sum is protected.",
        notes = "Prophets are ma'sum - protected from major sins."
      ),
      RootMeaningData(
        root = "ن-ج-و",
        primaryMeaning = "salvation, escape, rescue",
        extendedMeaning = "Being saved, escaping danger, and rescue.",
        quranUsage = "'We saved (najjayna) him and his family.' Najah is success. Najat is salvation.",
        notes = "Only Allah can grant true salvation."
      ),
      RootMeaningData(
        root = "ه-ل-ك",
        primaryMeaning = "destruction, perishing",
        extendedMeaning = "Destruction, perishing, and ruin.",
        quranUsage = "'Every soul will perish (halikah).' Halak is destruction. Halaka is to perish.",
        notes = "All creation perishes; only Allah's Face remains."
      ),
      RootMeaningData(
        root = "ب-ق-ي",
        primaryMeaning = "remain, last, eternal",
        extendedMeaning = "Remaining, lasting, and being eternal.",
        quranUsage = "'What is with Allah is lasting (baq).' Baqa' is permanence. Baqi is lasting.",
        notes = "Only Allah is truly Al-Baqi - The Everlasting."
      ),
      RootMeaningData(
        root = "ف-ن-ي",
        primaryMeaning = "perish, cease to exist",
        extendedMeaning = "Perishing, ceasing to exist, and annihilation.",
        quranUsage = "'Everything upon it will perish (fan).' Fana' is annihilation.",
        notes = "All creation is subject to fana' - only Allah is eternal."
      ),
      RootMeaningData(
        root = "خ-ل-د",
        primaryMeaning = "eternity, immortality",
        extendedMeaning = "Eternity, immortality, and everlasting.",
        quranUsage = "'Abiding therein eternally (khalidin).' Khuld is eternity. Khalid is eternal.",
        notes = "Paradise and Hell are described as eternal abodes."
      ),
      RootMeaningData(
        root = "ش-ه-د",
        primaryMeaning = "witness, testify, martyrdom",
        extendedMeaning = "Witnessing, testifying, and martyrdom.",
        quranUsage = "'Allah witnesses (shahida) that there is no god but He.' Shahadah is testimony. Shahid is witness/martyr.",
        notes = "The Shahadah is the testimony of faith. A shahid (martyr) witnesses truth with their life."
      ),
      RootMeaningData(
        root = "ح-ض-ر",
        primaryMeaning = "present, attend, settle",
        extendedMeaning = "Being present, attending, and settling (in cities).",
        quranUsage = "'When death approaches (hadara) one of you.' Hudur is presence. Hadir is present.",
        notes = "Hadari (settled) vs. Badawi (bedouin) - urban vs. desert."
      ),
      RootMeaningData(
        root = "غ-ي-ب",
        primaryMeaning = "unseen, hidden, absent",
        extendedMeaning = "The unseen realm, hidden, and absent.",
        quranUsage = "'Who believe in the unseen (ghayb).' Ghayb is unseen. Gha'ib is absent.",
        notes = "Belief in the ghayb (unseen) is a pillar of faith."
      ),
      RootMeaningData(
        root = "ع-ل-ن",
        primaryMeaning = "open, public, announce",
        extendedMeaning = "Open, public, and announcing.",
        quranUsage = "'What you reveal ('alaniyah) and what you conceal.' 'Alan is public. I'lan is announcement.",
        notes = "Allah knows both the public and the hidden."
      ),
      RootMeaningData(
        root = "س-ر-ر",
        primaryMeaning = "secret, joy, navel",
        extendedMeaning = "Secret, joy/happiness, and the innermost.",
        quranUsage = "'He knows the secret (sirr) and what is more hidden.' Sirr is secret. Surur is joy.",
        notes = "The secret and its joy are connected - hidden treasures bring joy."
      ),

      // === MATERIALS AND METALS ===
      RootMeaningData(
        root = "ذ-ه-ب",
        primaryMeaning = "gold, to go",
        extendedMeaning = "Gold, and going/departing.",
        quranUsage = "'Bracelets of gold (dhahab).' Dhahab is gold. Dhahaba is to go.",
        notes = "Gold adorns the people of Paradise. The connection to 'going' may be from gold's mobility in trade."
      ),
      RootMeaningData(
        root = "ف-ض-ض",
        primaryMeaning = "silver, to scatter",
        extendedMeaning = "Silver, and scattering/dispersing.",
        quranUsage = "'Vessels of silver (fiddah).' Fiddah is silver. Infadda is to scatter.",
        notes = "Silver vessels serve drinks in Paradise."
      ),
      RootMeaningData(
        root = "ح-د-د",
        primaryMeaning = "iron, limit, boundary",
        extendedMeaning = "Iron, limits/boundaries, and sharpness.",
        quranUsage = "'We sent down iron (hadid).' Hadid is iron. Hadd is limit/boundary.",
        notes = "Surah Al-Hadid - iron was sent down with great benefits and might."
      ),
      RootMeaningData(
        root = "ن-ح-س",
        primaryMeaning = "copper, brass, misfortune",
        extendedMeaning = "Copper/brass, and ill omen or misfortune.",
        quranUsage = "'Sent upon them a screaming wind in days of misfortune (nahisat).' Nuhas is copper.",
        notes = "Copper and brass were important metals in ancient times."
      ),
      RootMeaningData(
        root = "ر-ص-ص",
        primaryMeaning = "lead, to compact",
        extendedMeaning = "Lead (metal), and compacting/joining firmly.",
        quranUsage = "'As if they were a compact structure (marsus).' Rasas is lead.",
        notes = "Believers fighting in rows are like a solid, leaded structure."
      ),
      RootMeaningData(
        root = "ح-ج-ر",
        primaryMeaning = "stone, forbidden, mind",
        extendedMeaning = "Stone, that which is forbidden/protected, and intellect.",
        quranUsage = "'Stones (hijarah) prepared for the disbelievers.' Hajar is stone. Hijr is forbidden/sanctuary.",
        notes = "The Hijr of Ismail is the semi-circular area by the Kaaba."
      ),
      RootMeaningData(
        root = "ص-خ-ر",
        primaryMeaning = "rock, boulder",
        extendedMeaning = "Large rock or boulder.",
        quranUsage = "'Even if it be inside a rock (sakhrah).' Sakhr is rock/boulder.",
        notes = "Nothing is hidden from Allah, even inside solid rock."
      ),
      RootMeaningData(
        root = "ط-ي-ن",
        primaryMeaning = "clay, mud",
        extendedMeaning = "Clay and mud.",
        quranUsage = "'We created man from clay (tin).' Tin is clay. Tinat is clay/nature.",
        notes = "Adam was created from clay - our origin is humble."
      ),
      RootMeaningData(
        root = "ت-ر-ب",
        primaryMeaning = "dust, earth, soil",
        extendedMeaning = "Dust, soil, and earth.",
        quranUsage = "'We created you from dust (turab).' Turab is dust/soil. Turbah is grave.",
        notes = "From dust we came and to dust we return."
      ),
      RootMeaningData(
        root = "ر-م-ل",
        primaryMeaning = "sand",
        extendedMeaning = "Sand.",
        quranUsage = "'A heap of sand (ramilun) piled up.' Raml is sand.",
        notes = "Mountains will become like scattered sand on Judgment Day."
      ),
      RootMeaningData(
        root = "ز-ج-ج",
        primaryMeaning = "glass",
        extendedMeaning = "Glass.",
        quranUsage = "'The glass (zujajah) as if it were a brilliant star.' Zujaj is glass.",
        notes = "The famous Light Verse describes a lamp in glass."
      ),
      RootMeaningData(
        root = "خ-ز-ف",
        primaryMeaning = "pottery, clay vessel",
        extendedMeaning = "Pottery and earthenware.",
        quranUsage = "Khazaf is pottery/ceramics.",
        notes = "Pottery-making is an ancient craft."
      ),
      RootMeaningData(
        root = "خ-ش-ب",
        primaryMeaning = "wood, timber",
        extendedMeaning = "Wood and timber.",
        quranUsage = "'As if they were pieces of wood (khushub).' Khashab is wood.",
        notes = "Hypocrites are likened to propped-up pieces of wood."
      ),
      RootMeaningData(
        root = "ق-ط-ن",
        primaryMeaning = "cotton, to reside",
        extendedMeaning = "Cotton, and residing/settling.",
        quranUsage = "Qutn is cotton. Qatan is to reside.",
        notes = "Cotton is a blessing for clothing."
      ),
      RootMeaningData(
        root = "ص-و-ف",
        primaryMeaning = "wool",
        extendedMeaning = "Wool.",
        quranUsage = "'Mountains will be like carded wool (suf).' Suf is wool. Sufi relates to wool-wearers.",
        notes = "Mountains becoming like wool describes their destruction."
      ),
      RootMeaningData(
        root = "ج-ل-د",
        primaryMeaning = "skin, leather, to endure",
        extendedMeaning = "Skin, leather, and patience/endurance.",
        quranUsage = "'Their skins (julud) will testify.' Jild is skin. Jalad is endurance.",
        notes = "On Judgment Day, skins will testify against their owners."
      ),
      RootMeaningData(
        root = "ل-ؤ-ل-ؤ",
        primaryMeaning = "pearl",
        extendedMeaning = "Pearl.",
        quranUsage = "'From them emerge pearls (lu'lu').' Lu'lu' is pearl.",
        notes = "Pearls come from the sea - a sign of Allah's creation."
      ),
      RootMeaningData(
        root = "م-ر-ج",
        primaryMeaning = "coral, to mix, pasture",
        extendedMeaning = "Coral, mixing, and letting loose to pasture.",
        quranUsage = "'Pearls and coral (marjan).' Marjan is coral.",
        notes = "Pearls and coral emerge from the meeting of two seas."
      ),
      RootMeaningData(
        root = "ي-ا-ق",
        primaryMeaning = "ruby, sapphire",
        extendedMeaning = "Ruby and precious gems.",
        quranUsage = "'Like rubies (yaqut) and coral.' Yaqut is ruby.",
        notes = "The women of Paradise are compared to rubies and pearls."
      ),

      // === SOCIAL ROLES AND TITLES ===
      RootMeaningData(
        root = "م-ل-ك",
        primaryMeaning = "king, to own, angel",
        extendedMeaning = "Kingship, ownership, and angels.",
        quranUsage = "'The King (Malik) of mankind.' Malik is king. Mulk is kingdom. Malak is angel.",
        notes = "Allah is the true King. Angels (mala'ikah) are His servants."
      ),
      RootMeaningData(
        root = "س-ل-ط",
        primaryMeaning = "authority, power, sultan",
        extendedMeaning = "Authority, power, and proof/evidence.",
        quranUsage = "'We gave Musa clear authority (sultan).' Sultan is authority/proof.",
        notes = "Sultan means both political authority and clear proof."
      ),
      RootMeaningData(
        root = "أ-م-ر",
        primaryMeaning = "command, prince, matter",
        extendedMeaning = "Commanding, a prince/leader, and an affair.",
        quranUsage = "'Obey Allah and obey the messenger and those in authority (uli al-amr).' Amir is prince.",
        notes = "Amir al-Mu'minin means Commander of the Believers."
      ),
      RootMeaningData(
        root = "و-ز-ر",
        primaryMeaning = "minister, burden, sin",
        extendedMeaning = "Minister/advisor, heavy burden, and sin.",
        quranUsage = "'Appoint for me a minister (wazir) from my family.' Wazir is minister. Wizr is burden/sin.",
        notes = "Harun was Musa's wazir. Everyone carries their own wizr (burden of sin)."
      ),
      RootMeaningData(
        root = "ق-ا-ض",
        primaryMeaning = "judge",
        extendedMeaning = "Judge, one who decides.",
        quranUsage = "'Allah judges (yaqdi) with truth.' Qadi is judge. Qada' is judgment.",
        notes = "The Qadi issues rulings based on Islamic law."
      ),
      RootMeaningData(
        root = "ش-ي-خ",
        primaryMeaning = "elder, old man, scholar",
        extendedMeaning = "Elder, old age, and respected scholar.",
        quranUsage = "'Our father is an old man (shaykh).' Shaykh is elder/scholar.",
        notes = "Shaykh denotes age, wisdom, and religious authority."
      ),
      RootMeaningData(
        root = "غ-ل-م",
        primaryMeaning = "boy, young man, servant",
        extendedMeaning = "Boy, youth, and male servant.",
        quranUsage = "'We gave him glad tidings of a boy (ghulam).' Ghulam is boy/youth.",
        notes = "Ibrahim and Zakariyya were given glad tidings of sons."
      ),
      RootMeaningData(
        root = "ج-ر-ي",
        primaryMeaning = "girl, servant, flow",
        extendedMeaning = "Young girl, female servant, and flowing.",
        quranUsage = "'Youthful servants (ghilman) and houris.' Jariyah is girl/servant.",
        notes = "Also means a flowing ship or ongoing charity."
      ),
      RootMeaningData(
        root = "ع-ر-س",
        primaryMeaning = "bride, wedding",
        extendedMeaning = "Bride, wedding, and marriage celebration.",
        quranUsage = "'Arus is bride. 'Urs is wedding.",
        notes = "Marriage is a celebration and completion of faith."
      ),
      RootMeaningData(
        root = "ض-ي-ف",
        primaryMeaning = "guest",
        extendedMeaning = "Guest and hospitality.",
        quranUsage = "'Has the story of Ibrahim's honored guests (dayf) reached you?' Dayf is guest.",
        notes = "Honoring guests is a prophetic tradition."
      ),
      RootMeaningData(
        root = "ج-ا-ر",
        primaryMeaning = "neighbor, to protect",
        extendedMeaning = "Neighbor, and granting protection.",
        quranUsage = "'Be good to the neighbor (jar).' Jar is neighbor. Ijarah is protection/rent.",
        notes = "Rights of neighbors are heavily emphasized."
      ),
      RootMeaningData(
        root = "ص-ا-ح-ب",
        primaryMeaning = "companion, friend, owner",
        extendedMeaning = "Companion, friend, and owner/possessor.",
        quranUsage = "'The companion (sahib) of the fish.' Sahib is companion. Ashab are companions.",
        notes = "Sahib can mean friend, companion, or owner of something."
      ),
      RootMeaningData(
        root = "خ-ل-ل",
        primaryMeaning = "friend, intimate, to penetrate",
        extendedMeaning = "Close friend, intimacy, and penetrating.",
        quranUsage = "'Allah took Ibrahim as a close friend (khalil).' Khalil is intimate friend.",
        notes = "Ibrahim is Khalilullah - the intimate friend of Allah."
      ),
      RootMeaningData(
        root = "ع-د-و",
        primaryMeaning = "enemy, to transgress, run",
        extendedMeaning = "Enemy, transgression, and running/haste.",
        quranUsage = "'Satan is an enemy ('aduw) to you.' 'Aduw is enemy. 'Udwan is transgression.",
        notes = "Satan is humanity's clear enemy."
      ),
      RootMeaningData(
        root = "ف-ر-ع",
        primaryMeaning = "Pharaoh, branch, height",
        extendedMeaning = "Pharaoh (title), branch, and being elevated.",
        quranUsage = "'To Pharaoh (Fir'awn) and his chiefs.' Fir'awn is Pharaoh. Far' is branch.",
        notes = "Pharaoh is the archetype of tyranny in the Quran."
      ),
      RootMeaningData(
        root = "ق-ر-ن",
        primaryMeaning = "generation, horn, companion",
        extendedMeaning = "Generation/century, horn, and being paired.",
        quranUsage = "'How many generations (qurun) We destroyed.' Qarn is generation. Qarin is companion.",
        notes = "Each person has a qarin (companion) from the jinn."
      ),

      // === GEOGRAPHICAL FEATURES ===
      RootMeaningData(
        root = "و-د-ي",
        primaryMeaning = "valley",
        extendedMeaning = "Valley, riverbed.",
        quranUsage = "'In the sacred valley (wadi) of Tuwa.' Wadi is valley.",
        notes = "Musa was called in the valley of Tuwa."
      ),
      RootMeaningData(
        root = "س-ه-ل",
        primaryMeaning = "plain, easy",
        extendedMeaning = "Plain/flat land, and ease.",
        quranUsage = "'Carve out houses from the mountains.' Sahl is plain/easy.",
        notes = "Allah makes difficult things easy (sahl)."
      ),
      RootMeaningData(
        root = "ص-ح-ر",
        primaryMeaning = "desert",
        extendedMeaning = "Desert, wilderness.",
        quranUsage = "Sahra' is desert. The desert Arabs (A'rab) lived in the sahra'.",
        notes = "The Arabian desert shaped early Islamic history."
      ),
      RootMeaningData(
        root = "ج-ز-ر",
        primaryMeaning = "island, to slaughter",
        extendedMeaning = "Island, and slaughtering (animals).",
        quranUsage = "Jazirah is island/peninsula. Arabia is 'Jazirat al-'Arab.'",
        notes = "The Arabian Peninsula is called the Island of the Arabs."
      ),
      RootMeaningData(
        root = "س-ا-ح-ل",
        primaryMeaning = "coast, shore",
        extendedMeaning = "Coastline, seashore.",
        quranUsage = "'On the shore (sahil) of the sea.' Sahil is coast.",
        notes = "Musa's basket was found on the shore."
      ),
      RootMeaningData(
        root = "ع-ي-ن",
        primaryMeaning = "spring, eye, essence",
        extendedMeaning = "Water spring, eye, and the essence of something.",
        quranUsage = "'Springs ('uyun) flowing.' 'Ayn is spring/eye.",
        notes = "Paradise has springs of various drinks."
      ),
      RootMeaningData(
        root = "ب-ء-ر",
        primaryMeaning = "well",
        extendedMeaning = "Well for water.",
        quranUsage = "'A well (bi'r) abandoned.' Bi'r is well.",
        notes = "The well of Zamzam is blessed."
      ),
      RootMeaningData(
        root = "ك-ه-ف",
        primaryMeaning = "cave",
        extendedMeaning = "Cave.",
        quranUsage = "'When the youths retreated to the cave (kahf).' Kahf is cave.",
        notes = "Surah Al-Kahf tells of the sleepers in the cave."
      ),
      RootMeaningData(
        root = "غ-ا-ر",
        primaryMeaning = "cave, cavern",
        extendedMeaning = "Cave, especially a small cave.",
        quranUsage = "'When they were in the cave (ghar).' Ghar is cave.",
        notes = "The Prophet and Abu Bakr hid in the Cave of Thawr."
      ),
      RootMeaningData(
        root = "ط-و-ر",
        primaryMeaning = "mountain, stage, manner",
        extendedMeaning = "Mountain (especially Sinai), stage, and manner.",
        quranUsage = "'By the Mount (Tur)!' Tur is Mount Sinai. Tawr is stage.",
        notes = "Mount Tur/Sinai is where Musa received revelation."
      ),

      // === PARTS OF DAY AND TIME ===
      RootMeaningData(
        root = "ف-ج-ر",
        primaryMeaning = "dawn, to break forth",
        extendedMeaning = "Dawn, breaking forth, and wickedness (breaking moral bounds).",
        quranUsage = "'By the dawn (fajr)!' Fajr is dawn. Fujur is wickedness.",
        notes = "Fajr prayer is at dawn. The same root gives 'wickedness' (breaking limits)."
      ),
      RootMeaningData(
        root = "ص-ب-ح",
        primaryMeaning = "morning, to become",
        extendedMeaning = "Morning, and becoming/entering a state.",
        quranUsage = "'By the morning (subh)!' Subh is morning. Asbaha is to become.",
        notes = "Subh and fajr both refer to early morning."
      ),
      RootMeaningData(
        root = "ض-ح-ي",
        primaryMeaning = "forenoon, sacrifice",
        extendedMeaning = "Mid-morning, and the sacrifice (done at that time).",
        quranUsage = "'By the morning brightness (duha)!' Duha is forenoon. Udhiyah is sacrifice.",
        notes = "Surah Ad-Duha. Eid sacrifice is called Udhiyah (done in the duha time)."
      ),
      RootMeaningData(
        root = "ظ-ه-ر",
        primaryMeaning = "noon, back, apparent",
        extendedMeaning = "Noon (when sun is at its zenith/back), back, and being apparent.",
        quranUsage = "'At noon (zahirah).' Zuhr is noon prayer. Zahir is apparent.",
        notes = "Zuhr prayer is when the sun passes its zenith."
      ),
      RootMeaningData(
        root = "ع-ص-ر",
        primaryMeaning = "afternoon, age, juice",
        extendedMeaning = "Afternoon, time/era, and pressing (juice).",
        quranUsage = "'By time ('asr)!' 'Asr is time/afternoon. Ma'sarah is press.",
        notes = "Surah Al-'Asr swears by time. 'Asr prayer is in the afternoon."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "sunset, west, strange",
        extendedMeaning = "Sunset, the west, and being strange/foreign.",
        quranUsage = "'Before sunset (ghurub).' Ghurub is setting. Maghrib is sunset/west.",
        notes = "Maghrib prayer is at sunset."
      ),
      RootMeaningData(
        root = "ع-ش-ء",
        primaryMeaning = "evening, night",
        extendedMeaning = "Evening, nightfall.",
        quranUsage = "'In the evening ('isha').' 'Isha' is evening/night.",
        notes = "'Isha' prayer is the night prayer."
      ),
      RootMeaningData(
        root = "ل-ي-ل",
        primaryMeaning = "night",
        extendedMeaning = "Night, nighttime.",
        quranUsage = "'By the night (layl) when it covers!' Layl is night. Laylah is a night.",
        notes = "Laylat al-Qadr - the Night of Power."
      ),
      RootMeaningData(
        root = "ن-ه-ر",
        primaryMeaning = "day, river, to rebuke",
        extendedMeaning = "Daytime, river, and rebuking.",
        quranUsage = "'The night and the day (nahar).' Nahar is daytime. Nahr is river.",
        notes = "Day and night alternate as signs of Allah."
      ),
      RootMeaningData(
        root = "ي-و-م",
        primaryMeaning = "day",
        extendedMeaning = "Day, a specific day, era.",
        quranUsage = "'The Day (yawm) of Judgment.' Yawm is day. Ayyam is days.",
        notes = "Yawm al-Qiyamah is the Day of Resurrection."
      ),
      RootMeaningData(
        root = "ش-ه-ر",
        primaryMeaning = "month, to publicize",
        extendedMeaning = "Month, and making known/famous.",
        quranUsage = "'The month (shahr) of Ramadan.' Shahr is month. Mashhur is famous.",
        notes = "The Islamic calendar has twelve months."
      ),
      RootMeaningData(
        root = "س-ن-ه",
        primaryMeaning = "year",
        extendedMeaning = "Year.",
        quranUsage = "'A thousand years (sanah).' Sanah is year. Sanawat is years.",
        notes = "Nuh called his people for 950 years."
      ),
      RootMeaningData(
        root = "ع-ا-م",
        primaryMeaning = "year, general",
        extendedMeaning = "Year, and being general/public.",
        quranUsage = "'In a few years ('am).' 'Am is year. 'Amm is general.",
        notes = "'Am al-Fil - the Year of the Elephant."
      ),

      // === ACTIONS AND VERBS ===
      RootMeaningData(
        root = "ف-ت-ح",
        primaryMeaning = "to open, conquer, begin",
        extendedMeaning = "Opening, conquering, and beginning.",
        quranUsage = "'Indeed, We have opened (fatahna) for you a clear conquest.' Fath is opening/victory.",
        notes = "Al-Fattah - The Opener - one of Allah's names."
      ),
      RootMeaningData(
        root = "غ-ل-ق",
        primaryMeaning = "to close, lock",
        extendedMeaning = "Closing, locking, and shutting.",
        quranUsage = "'She closed (ghallaqat) the doors.' Ghalq is closing. Mughallaq is locked.",
        notes = "Contrast with ف-ت-ح (opening)."
      ),
      RootMeaningData(
        root = "ك-س-ر",
        primaryMeaning = "to break",
        extendedMeaning = "Breaking, shattering.",
        quranUsage = "'Ibrahim broke (kasara) the idols.' Kasr is breaking. Inkisar is being broken.",
        notes = "Ibrahim broke the idols to prove their powerlessness."
      ),
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "to gather, collect, unite",
        extendedMeaning = "Gathering, collecting, and uniting.",
        quranUsage = "'He gathered (jama'a) wealth.' Jam' is gathering. Jami' is comprehensive. Jumu'ah is Friday.",
        notes = "Friday (Jumu'ah) is the day of gathering for prayer."
      ),
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "to separate, distinguish",
        extendedMeaning = "Separating, distinguishing, and dividing.",
        quranUsage = "'We parted (faraqna) the sea.' Farq is separation. Furqan is criterion.",
        notes = "The Quran is Al-Furqan - it distinguishes truth from falsehood."
      ),
      RootMeaningData(
        root = "ب-د-ل",
        primaryMeaning = "to change, substitute",
        extendedMeaning = "Changing, substituting, and exchanging.",
        quranUsage = "'We will change (nubaddilu) their skins.' Tabdil is change. Badal is substitute.",
        notes = "Allah can change conditions and creations."
      ),
      RootMeaningData(
        root = "ص-ن-ع",
        primaryMeaning = "to make, manufacture, craft",
        extendedMeaning = "Making, manufacturing, and craftsmanship.",
        quranUsage = "'The making (sun') of Allah.' San'ah is craft. Sina'ah is industry.",
        notes = "Allah's creation is perfect craftsmanship."
      ),
      RootMeaningData(
        root = "ب-ن-ي",
        primaryMeaning = "to build, construct",
        extendedMeaning = "Building, construction, and structure.",
        quranUsage = "'We built (banayna) above you seven strong.' Bina' is building.",
        notes = "The seven heavens are built with power."
      ),
      RootMeaningData(
        root = "ه-د-م",
        primaryMeaning = "to demolish, destroy",
        extendedMeaning = "Demolishing, tearing down.",
        quranUsage = "'Destroyed (huddimat) would have been monasteries.' Hadm is demolition.",
        notes = "Without Allah's protection, places of worship would be destroyed."
      ),
      RootMeaningData(
        root = "ق-ط-ع",
        primaryMeaning = "to cut, sever",
        extendedMeaning = "Cutting, severing, and interrupting.",
        quranUsage = "'Cut off (qata'a) the hands.' Qat' is cutting. Qati'ah is a piece.",
        notes = "Severing ties of kinship is a major sin."
      ),
      RootMeaningData(
        root = "و-ص-ل",
        primaryMeaning = "to connect, join, arrive",
        extendedMeaning = "Connecting, joining, and arriving.",
        quranUsage = "'Those who join (yasilun) what Allah commanded to be joined.' Wasl is connection. Silah is maintaining ties.",
        notes = "Silat ar-Rahim - maintaining family ties - is commanded."
      ),
      RootMeaningData(
        root = "ر-ب-ط",
        primaryMeaning = "to tie, bind, strengthen",
        extendedMeaning = "Tying, binding, and strengthening.",
        quranUsage = "'We strengthened (rabatna) her heart.' Rabt is tying. Ribat is bond/garrison.",
        notes = "Allah ties and strengthens the hearts of believers."
      ),
      RootMeaningData(
        root = "ح-ل-ل",
        primaryMeaning = "to untie, permit, settle",
        extendedMeaning = "Untying, permitting, and settling in a place.",
        quranUsage = "'Made lawful (uhilla) for you.' Hill is untying. Halal is permitted.",
        notes = "Halal is what is untied from prohibition."
      ),
      RootMeaningData(
        root = "أ-خ-ذ",
        primaryMeaning = "to take, seize",
        extendedMeaning = "Taking, seizing, and grasping.",
        quranUsage = "'Take (khudh) what We have given you with strength.' Akhdh is taking.",
        notes = "Allah seizes the wrongdoers with His punishment."
      ),
      RootMeaningData(
        root = "ع-ط-ي",
        primaryMeaning = "to give, grant",
        extendedMeaning = "Giving, granting, and bestowing.",
        quranUsage = "'We have given you (a'taynaka) Al-Kawthar.' 'Ata' is giving. 'Atiyyah is gift.",
        notes = "Allah is Al-Mu'ti - The Giver."
      ),
      RootMeaningData(
        root = "م-ن-ع",
        primaryMeaning = "to prevent, withhold",
        extendedMeaning = "Preventing, withholding, and forbidding.",
        quranUsage = "'What prevented (mana'a) you from prostrating?' Man' is prevention. Mani' is protector.",
        notes = "Al-Mani' - The Preventer - protects from harm."
      ),
      RootMeaningData(
        root = "ر-م-ي",
        primaryMeaning = "to throw, cast",
        extendedMeaning = "Throwing, casting, and shooting.",
        quranUsage = "'You did not throw (ramayta) when you threw.' Ramy is throwing.",
        notes = "Allah guided the Prophet's throw at Badr."
      ),
      RootMeaningData(
        root = "ض-ر-ب",
        primaryMeaning = "to strike, give example",
        extendedMeaning = "Striking, giving examples, and traveling.",
        quranUsage = "'Allah strikes (yadribu) examples for people.' Darb is striking. Mathal is example.",
        notes = "The Quran strikes parables to explain truths."
      ),
      RootMeaningData(
        root = "ل-م-س",
        primaryMeaning = "to touch, seek",
        extendedMeaning = "Touching, feeling, and seeking.",
        quranUsage = "'We touched (lamasna) the heaven.' Lams is touch. Iltimas is seeking.",
        notes = "The jinn sought to reach heaven but found it guarded."
      ),
      RootMeaningData(
        root = "م-س-س",
        primaryMeaning = "to touch, afflict",
        extendedMeaning = "Touching, and being afflicted.",
        quranUsage = "'If good touches (massathu) him.' Mass is touch/affliction.",
        notes = "Used for both physical touch and being afflicted by conditions."
      ),
      RootMeaningData(
        root = "ح-م-ل",
        primaryMeaning = "to carry, bear, conceive",
        extendedMeaning = "Carrying, bearing burdens, and pregnancy.",
        quranUsage = "'We carried (hamalna) him on planks.' Haml is carrying. Hamil is carrier.",
        notes = "Used for physical carrying and carrying sin or responsibility."
      ),
      RootMeaningData(
        root = "ر-ف-ع",
        primaryMeaning = "to raise, elevate",
        extendedMeaning = "Raising, elevating, and lifting.",
        quranUsage = "'We raised (rafa'na) him to a high station.' Raf' is raising. Rafi' is high.",
        notes = "'Isa was raised to Allah. Good deeds raise one's rank."
      ),
      RootMeaningData(
        root = "خ-ف-ض",
        primaryMeaning = "to lower, humble",
        extendedMeaning = "Lowering, humbling, and being gentle.",
        quranUsage = "'Lower (ikhfid) your wing to the believers.' Khafd is lowering.",
        notes = "Lowering one's wing means being humble and gentle."
      ),
      RootMeaningData(
        root = "ط-ر-ح",
        primaryMeaning = "to throw, cast away",
        extendedMeaning = "Throwing down, casting away.",
        quranUsage = "'Cast (itrahuh) Yusuf into the well.' Tarh is throwing/casting.",
        notes = "Yusuf's brothers cast him into the well."
      ),
      RootMeaningData(
        root = "ل-ق-ي",
        primaryMeaning = "to meet, encounter, receive",
        extendedMeaning = "Meeting, encountering, and receiving.",
        quranUsage = "'Whoever hopes to meet (liqa') his Lord.' Liqa' is meeting. Laqiya is to meet.",
        notes = "Meeting Allah is the ultimate encounter."
      ),
      RootMeaningData(
        root = "و-ج-د",
        primaryMeaning = "to find, feel",
        extendedMeaning = "Finding, feeling emotion, and existence.",
        quranUsage = "'He found (wajada) you lost and guided.' Wujud is finding/existence.",
        notes = "Allah found the Prophet and guided him."
      ),
      RootMeaningData(
        root = "ف-ق-د",
        primaryMeaning = "to lose, miss",
        extendedMeaning = "Losing, missing, and lacking.",
        quranUsage = "'They said: We are missing (nafqidu) the king's cup.' Faqd is loss.",
        notes = "Opposite of و-ج-د (finding)."
      ),
      RootMeaningData(
        root = "ظ-ن-ن",
        primaryMeaning = "to think, suppose, suspect",
        extendedMeaning = "Thinking, supposing, and suspicion.",
        quranUsage = "'They thought (zannu) they would never return.' Zann is thought/suspicion.",
        notes = "Some zann is sin - suspecting others wrongly."
      ),
      RootMeaningData(
        root = "ي-ق-ن",
        primaryMeaning = "certainty, to be certain",
        extendedMeaning = "Certainty, conviction, and being sure.",
        quranUsage = "'Worship your Lord until certainty (yaqin) comes to you.' Yaqin is certainty.",
        notes = "Yaqin is the highest level of knowledge - absolute certainty."
      ),
      RootMeaningData(
        root = "ش-ك-ك",
        primaryMeaning = "doubt, to doubt",
        extendedMeaning = "Doubt, uncertainty, and skepticism.",
        quranUsage = "'If you are in doubt (shakk).' Shakk is doubt.",
        notes = "Doubt in faith is dangerous; seeking knowledge removes doubt."
      ),
      RootMeaningData(
        root = "ذ-ك-ر",
        primaryMeaning = "to remember, mention, male",
        extendedMeaning = "Remembering, mentioning, and the male gender.",
        quranUsage = "'Remember (udhkur) Me, I will remember you.' Dhikr is remembrance. Dhakar is male.",
        notes = "Dhikr of Allah brings peace to hearts."
      ),
      RootMeaningData(
        root = "ن-س-ي",
        primaryMeaning = "to forget",
        extendedMeaning = "Forgetting, neglecting.",
        quranUsage = "'They forgot (nasu) Allah, so He forgot them.' Nisyan is forgetting.",
        notes = "Forgetting Allah leads to being forgotten by Him."
      ),
      RootMeaningData(
        root = "ع-ل-م",
        primaryMeaning = "to know, teach",
        extendedMeaning = "Knowing, teaching, and science.",
        quranUsage = "'He taught ('allama) Adam the names.' 'Ilm is knowledge. 'Alim is knower.",
        notes = "Al-'Alim - The All-Knowing - is Allah's name."
      ),
      RootMeaningData(
        root = "ج-ه-ل",
        primaryMeaning = "ignorance, not to know",
        extendedMeaning = "Ignorance, not knowing, and acting ignorantly.",
        quranUsage = "'Turn away from the ignorant (jahilin).' Jahl is ignorance. Jahil is ignorant.",
        notes = "Pre-Islamic Arabia was called Jahiliyyah - the Age of Ignorance."
      ),

      // === PLANTS AND VEGETATION ===
      RootMeaningData(
        root = "ن-ب-ت",
        primaryMeaning = "plant, to grow",
        extendedMeaning = "Plant, vegetation, and growth.",
        quranUsage = "'We caused to grow (anbatna) therein gardens.' Nabat is plant. Nabt is growth.",
        notes = "Allah causes plants to grow from the earth as a sign."
      ),
      RootMeaningData(
        root = "ز-ر-ع",
        primaryMeaning = "crops, to plant, cultivation",
        extendedMeaning = "Crops, planting, and agriculture.",
        quranUsage = "'Is it you who cultivates (tazra'una) it?' Zar' is crops. Zira'ah is agriculture.",
        notes = "Agriculture is a blessing - Allah causes growth, not humans."
      ),
      RootMeaningData(
        root = "ح-ب-ب",
        primaryMeaning = "grain, seed, love",
        extendedMeaning = "Grain, seed, and love.",
        quranUsage = "'The splitter of grain (habb) and seeds.' Habb is grain. Hubb is love.",
        notes = "Grain sustains life; love sustains the heart."
      ),
      RootMeaningData(
        root = "س-ن-ب-ل",
        primaryMeaning = "ear of grain",
        extendedMeaning = "Ear/spike of grain.",
        quranUsage = "'Seven ears (sanabil) of grain.' Sunbulah is ear of grain.",
        notes = "Seven ears in Yusuf's dream and the parable of charity."
      ),
      RootMeaningData(
        root = "ن-خ-ل",
        primaryMeaning = "palm tree, to sift",
        extendedMeaning = "Date palm, and sifting.",
        quranUsage = "'Lofty palm trees (nakhil).' Nakhl is palm trees. Nakhlah is a palm.",
        notes = "Palm trees are mentioned frequently - their fruits sustain people."
      ),
      RootMeaningData(
        root = "ع-ن-ب",
        primaryMeaning = "grape",
        extendedMeaning = "Grape.",
        quranUsage = "'Gardens of grapes ('inab).' 'Inab is grapes.",
        notes = "Grapes are among the blessings of Paradise."
      ),
      RootMeaningData(
        root = "ت-ي-ن",
        primaryMeaning = "fig",
        extendedMeaning = "Fig.",
        quranUsage = "'By the fig (tin) and the olive!' Tin is fig.",
        notes = "Surah At-Tin swears by the fig and olive."
      ),
      RootMeaningData(
        root = "ز-ي-ت",
        primaryMeaning = "olive, oil",
        extendedMeaning = "Olive and olive oil.",
        quranUsage = "'A blessed tree, an olive (zaytunah).' Zayt is oil. Zaytun is olive.",
        notes = "Olive oil is blessed - it lights lamps and is beneficial."
      ),
      RootMeaningData(
        root = "ر-م-ن",
        primaryMeaning = "pomegranate",
        extendedMeaning = "Pomegranate.",
        quranUsage = "'And pomegranates (rumman).' Rumman is pomegranate.",
        notes = "Pomegranates are among the fruits of Paradise."
      ),
      RootMeaningData(
        root = "م-و-ز",
        primaryMeaning = "banana",
        extendedMeaning = "Banana.",
        quranUsage = "'And banana trees (talh) layered.' Mawz is banana (talh also means banana trees).",
        notes = "Banana trees in Paradise with clustered fruits."
      ),
      RootMeaningData(
        root = "س-د-ر",
        primaryMeaning = "lote tree",
        extendedMeaning = "Lote tree (a blessed tree).",
        quranUsage = "'At the Lote Tree (sidrah) of the utmost boundary.' Sidr is lote tree.",
        notes = "Sidrat al-Muntaha marks the boundary of creation."
      ),
      RootMeaningData(
        root = "ش-و-ك",
        primaryMeaning = "thorn, spike",
        extendedMeaning = "Thorn, spike.",
        quranUsage = "'No food except thorny plants (dari').' Shawk is thorn.",
        notes = "The food of Hell includes thorny plants."
      ),
      RootMeaningData(
        root = "ح-ش-ش",
        primaryMeaning = "grass, herbs, dry vegetation",
        extendedMeaning = "Grass, herbs, and dried plants.",
        quranUsage = "'Then makes it dry stubble (hashim).' Hashish is grass/herbs.",
        notes = "Life's cycle: green, then dry stubble."
      ),
      RootMeaningData(
        root = "و-ر-ق",
        primaryMeaning = "leaf, paper",
        extendedMeaning = "Leaf, foliage, and paper.",
        quranUsage = "'They began to cover themselves with leaves (waraq).' Waraq is leaves/paper.",
        notes = "Adam and Hawwa covered themselves with Paradise leaves."
      ),
      RootMeaningData(
        root = "ج-ذ-ع",
        primaryMeaning = "trunk, stem",
        extendedMeaning = "Trunk of a tree, stem.",
        quranUsage = "'We will crucify you on the trunks (judhoo') of palm trees.' Jidh' is trunk.",
        notes = "Pharaoh's magicians were threatened with crucifixion on palm trunks."
      ),
      RootMeaningData(
        root = "ج-ذ-ر",
        primaryMeaning = "root",
        extendedMeaning = "Root of a plant.",
        quranUsage = "'Its root (asluh) firm.' Jadhr is root. (Also: asl)",
        notes = "A good word is like a good tree - firm roots and reaching branches."
      ),
      RootMeaningData(
        root = "ف-ر-ع",
        primaryMeaning = "branch, Pharaoh",
        extendedMeaning = "Branch, and being high/mighty (Pharaoh).",
        quranUsage = "'Its branches (far') in the sky.' Far' is branch.",
        notes = "The good tree has branches reaching the sky."
      ),

      // === MORE ANIMALS ===
      RootMeaningData(
        root = "أ-س-د",
        primaryMeaning = "lion",
        extendedMeaning = "Lion.",
        quranUsage = "'As if they were fleeing from a lion (qaswarah/asad).' Asad is lion.",
        notes = "Disbelievers flee from the message like prey from a lion."
      ),
      RootMeaningData(
        root = "ذ-ء-ب",
        primaryMeaning = "wolf",
        extendedMeaning = "Wolf.",
        quranUsage = "'A wolf (dhi'b) will eat him.' Dhi'b is wolf.",
        notes = "Yusuf's brothers claimed a wolf ate him."
      ),
      RootMeaningData(
        root = "ث-ع-ب",
        primaryMeaning = "snake, serpent",
        extendedMeaning = "Snake, large serpent.",
        quranUsage = "'It became a serpent (thu'ban).' Thu'ban is large snake.",
        notes = "Musa's staff became a great serpent."
      ),
      RootMeaningData(
        root = "ح-ي-ي",
        primaryMeaning = "snake, life, modesty",
        extendedMeaning = "Snake (hayyah), life, and modesty.",
        quranUsage = "'It became a snake (hayyah) moving.' Hayyah is snake. Hayah is life. Haya' is modesty.",
        notes = "The same root connects life, snakes (life-like movement), and modesty."
      ),
      RootMeaningData(
        root = "ض-ف-د-ع",
        primaryMeaning = "frog",
        extendedMeaning = "Frog.",
        quranUsage = "'We sent upon them the flood, locusts, lice, frogs (dafadi').' Difda' is frog.",
        notes = "Frogs were among the plagues sent to Pharaoh."
      ),
      RootMeaningData(
        root = "ج-ر-د",
        primaryMeaning = "locust, to strip",
        extendedMeaning = "Locust, and stripping bare.",
        quranUsage = "'We sent upon them locusts (jarad).' Jarad is locusts.",
        notes = "Locusts stripped Egypt as a plague."
      ),
      RootMeaningData(
        root = "ق-م-ل",
        primaryMeaning = "lice",
        extendedMeaning = "Lice.",
        quranUsage = "'We sent upon them lice (qummal).' Qummal is lice.",
        notes = "Lice were among Pharaoh's plagues."
      ),
      RootMeaningData(
        root = "ب-ع-ض",
        primaryMeaning = "mosquito, some",
        extendedMeaning = "Mosquito, gnat, and some/part.",
        quranUsage = "'Allah is not ashamed to strike an example of a mosquito (ba'udah).' Ba'udah is mosquito.",
        notes = "Allah strikes examples even with the smallest creatures."
      ),
      RootMeaningData(
        root = "ذ-ب-ب",
        primaryMeaning = "fly",
        extendedMeaning = "Fly (insect).",
        quranUsage = "'Even if a fly (dhubab) should steal from them.' Dhubab is fly.",
        notes = "Idols cannot retrieve what a fly takes - showing their weakness."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "crow, raven, west",
        extendedMeaning = "Crow/raven, and the west.",
        quranUsage = "'Allah sent a crow (ghurab).' Ghurab is crow.",
        notes = "A crow taught Qabil how to bury his brother."
      ),
      RootMeaningData(
        root = "ه-د-ه-د",
        primaryMeaning = "hoopoe",
        extendedMeaning = "Hoopoe bird.",
        quranUsage = "'He inspected the birds and said: Why do I not see the hoopoe (hudhud)?' Hudhud is hoopoe.",
        notes = "The hoopoe brought Sulayman news of the Queen of Sheba."
      ),
      RootMeaningData(
        root = "ص-ر-د",
        primaryMeaning = "cold, a bird (shrike)",
        extendedMeaning = "Cold, and a type of bird.",
        quranUsage = "Surad is a type of bird (shrike).",
        notes = "Various birds serve as signs in creation."
      ),
      RootMeaningData(
        root = "ح-م-م",
        primaryMeaning = "dove, pigeon, hot",
        extendedMeaning = "Dove, and heat.",
        quranUsage = "Hamamah is dove/pigeon. Hamim is hot water.",
        notes = "Doves nested at the cave during the Hijrah."
      ),
      RootMeaningData(
        root = "ب-غ-ل",
        primaryMeaning = "mule",
        extendedMeaning = "Mule.",
        quranUsage = "'And horses and mules (bighal).' Baghl is mule.",
        notes = "Mules are mentioned among riding animals."
      ),
      RootMeaningData(
        root = "ح-م-ر",
        primaryMeaning = "donkey, red",
        extendedMeaning = "Donkey, and the color red.",
        quranUsage = "'As if they were wild donkeys (humur).' Himar is donkey.",
        notes = "Disbelievers flee like startled donkeys."
      ),
      RootMeaningData(
        root = "خ-ر-ف",
        primaryMeaning = "sheep, autumn",
        extendedMeaning = "Sheep, and autumn/harvest.",
        quranUsage = "Kharuf is sheep/lamb. Kharif is autumn.",
        notes = "Sheep are among the livestock Allah created."
      ),
      RootMeaningData(
        root = "م-ع-ز",
        primaryMeaning = "goat",
        extendedMeaning = "Goat.",
        quranUsage = "'And of the goats (ma'z), two.' Ma'z is goat.",
        notes = "Goats are mentioned among the eight pairs of livestock."
      ),

      // === HOUSEHOLD AND TOOLS ===
      RootMeaningData(
        root = "س-ر-ج",
        primaryMeaning = "lamp, saddle",
        extendedMeaning = "Lamp, and saddle for riding.",
        quranUsage = "'And made the sun a lamp (sirajan).' Siraj is lamp.",
        notes = "The sun is described as a brilliant lamp."
      ),
      RootMeaningData(
        root = "م-ص-ب-ح",
        primaryMeaning = "lamp",
        extendedMeaning = "Lamp, lantern.",
        quranUsage = "'The example of His light is like a niche with a lamp (misbah).' Misbah is lamp.",
        notes = "The famous Light Verse uses misbah."
      ),
      RootMeaningData(
        root = "ق-ن-د-ل",
        primaryMeaning = "lamp, chandelier",
        extendedMeaning = "Oil lamp, chandelier.",
        quranUsage = "Qindil is lamp/chandelier.",
        notes = "Lamps illuminate mosques and homes."
      ),
      RootMeaningData(
        root = "ف-ر-ش",
        primaryMeaning = "bed, carpet, spread",
        extendedMeaning = "Bedding, carpet, and spreading.",
        quranUsage = "'Reclining on beds (furush) lined with brocade.' Firash is bed. Farsh is carpet.",
        notes = "The earth is spread like a carpet for humanity."
      ),
      RootMeaningData(
        root = "و-س-د",
        primaryMeaning = "pillow, cushion",
        extendedMeaning = "Pillow, cushion.",
        quranUsage = "'Reclining on green cushions (rafraf).' Wisadah is pillow.",
        notes = "Paradise has cushions for reclining."
      ),
      RootMeaningData(
        root = "ك-أ-س",
        primaryMeaning = "cup, goblet",
        extendedMeaning = "Drinking cup, goblet.",
        quranUsage = "'A cup (ka's) from a flowing spring.' Ka's is cup.",
        notes = "Cups of pure drink circulate in Paradise."
      ),
      RootMeaningData(
        root = "إ-ب-ر-ق",
        primaryMeaning = "pitcher, jug",
        extendedMeaning = "Pitcher, water jug.",
        quranUsage = "'With pitchers (abariq) and cups.' Ibriq is pitcher.",
        notes = "Pitchers serve drinks in Paradise."
      ),
      RootMeaningData(
        root = "ص-ح-ف",
        primaryMeaning = "dish, plate, page",
        extendedMeaning = "Dish, plate, and written page.",
        quranUsage = "'Dishes (sihaf) of gold.' Sahfah is dish. Sahifah is page.",
        notes = "Gold dishes serve food in Paradise."
      ),
      RootMeaningData(
        root = "ق-د-ر",
        primaryMeaning = "pot, power, measure",
        extendedMeaning = "Cooking pot, power/ability, and measure.",
        quranUsage = "'Pots (qudur) firmly anchored.' Qidr is pot. Qadr is measure/power.",
        notes = "Laylat al-Qadr - Night of Power/Measure."
      ),
      RootMeaningData(
        root = "ج-ف-ن",
        primaryMeaning = "large bowl",
        extendedMeaning = "Large serving bowl.",
        quranUsage = "'Large bowls (jifan) like reservoirs.' Jafnah is large bowl.",
        notes = "Sulayman had large bowls for feeding many people."
      ),
      RootMeaningData(
        root = "س-ك-ن",
        primaryMeaning = "knife, to dwell, calm",
        extendedMeaning = "Knife, dwelling, and being calm/still.",
        quranUsage = "'She gave each a knife (sikkin).' Sikkin is knife. Sakan is dwelling. Sakinah is tranquility.",
        notes = "The women cut their hands when they saw Yusuf's beauty."
      ),
      RootMeaningData(
        root = "ف-أ-س",
        primaryMeaning = "axe",
        extendedMeaning = "Axe, hatchet.",
        quranUsage = "Fa's is axe. Ibrahim used it on the idols.",
        notes = "Ibrahim broke idols, possibly with an axe."
      ),
      RootMeaningData(
        root = "م-ن-ج-ل",
        primaryMeaning = "sickle",
        extendedMeaning = "Sickle for harvesting.",
        quranUsage = "Minjal is sickle.",
        notes = "Sickles harvest the crops Allah grows."
      ),
      RootMeaningData(
        root = "م-ف-ت-ح",
        primaryMeaning = "key",
        extendedMeaning = "Key.",
        quranUsage = "'To Him belong the keys (mafatih) of the heavens.' Miftah is key.",
        notes = "Allah holds the keys to all treasures."
      ),
      RootMeaningData(
        root = "ق-ف-ل",
        primaryMeaning = "lock, to lock",
        extendedMeaning = "Lock, locking.",
        quranUsage = "'Or are there locks (aqfal) upon their hearts?' Qufl is lock.",
        notes = "Hearts can be locked from understanding."
      ),
      RootMeaningData(
        root = "ح-ب-ل",
        primaryMeaning = "rope, cord",
        extendedMeaning = "Rope, cord, and connection.",
        quranUsage = "'Hold firmly to the rope (habl) of Allah.' Habl is rope.",
        notes = "The Quran and faith are Allah's rope connecting us to Him."
      ),
      RootMeaningData(
        root = "س-ل-س-ل",
        primaryMeaning = "chain",
        extendedMeaning = "Chain, series.",
        quranUsage = "'A chain (silsilah) of seventy cubits.' Silsilah is chain.",
        notes = "Chains bind the people of Hell."
      ),
      RootMeaningData(
        root = "غ-ل-ل",
        primaryMeaning = "shackle, to penetrate",
        extendedMeaning = "Shackle, and penetrating through.",
        quranUsage = "'Shackles (aghlal) on their necks.' Ghull is shackle/yoke.",
        notes = "The Prophet removed the shackles of ignorance."
      ),
      RootMeaningData(
        root = "ق-ي-د",
        primaryMeaning = "chain, fetter, restrict",
        extendedMeaning = "Chain, shackle, and restriction.",
        quranUsage = "'In chains (asfad).' Qayd is fetter/restriction.",
        notes = "The criminals will be bound in chains."
      ),

      // === ABSTRACT CONCEPTS ===
      RootMeaningData(
        root = "ع-ل-و",
        primaryMeaning = "height, exaltation",
        extendedMeaning = "Height, being high, and exaltation.",
        quranUsage = "'Exalted ('aliy) is He, the Great.' 'Uluw is height. 'Ali is high.",
        notes = "Al-'Ali (The Most High) and Al-Muta'ali (The Supremely Exalted)."
      ),
      RootMeaningData(
        root = "س-ف-ل",
        primaryMeaning = "lowness, below",
        extendedMeaning = "Being low, the lower part.",
        quranUsage = "'We returned him to the lowest (asfal) of the low.' Sufla is lower. Asfal is lowest.",
        notes = "Humans can sink to the lowest of the low through disbelief."
      ),
      RootMeaningData(
        root = "ك-ث-ر",
        primaryMeaning = "abundance, many, much",
        extendedMeaning = "Abundance, being many, and multiplying.",
        quranUsage = "'We have given you abundance (kawthar).' Kathir is many. Kawthar is abundance.",
        notes = "Surah Al-Kawthar - the river of abundance in Paradise."
      ),
      RootMeaningData(
        root = "ق-ل-ل",
        primaryMeaning = "few, little, scarcity",
        extendedMeaning = "Being few, little, and scarcity.",
        quranUsage = "'Except a few (qalil).' Qalil is few. Qillah is scarcity.",
        notes = "Believers are often few compared to disbelievers."
      ),
      RootMeaningData(
        root = "ك-ب-ر",
        primaryMeaning = "greatness, large, old",
        extendedMeaning = "Being great/large, growing old, and arrogance.",
        quranUsage = "'Allahu Akbar - Allah is Greatest.' Kabir is great. Kibar is elders.",
        notes = "Takbir (saying Allahu Akbar) glorifies Allah's greatness."
      ),
      RootMeaningData(
        root = "ص-غ-ر",
        primaryMeaning = "small, young, humiliation",
        extendedMeaning = "Being small, young, and being humiliated.",
        quranUsage = "'Small (saghir) or great.' Saghir is small. Saghar is humiliation.",
        notes = "No deed is too small to be recorded."
      ),
      RootMeaningData(
        root = "ق-و-ي",
        primaryMeaning = "strength, power",
        extendedMeaning = "Strength, power, and might.",
        quranUsage = "'Indeed, Allah is Powerful (Qawiy).' Quwwah is strength. Qawiy is strong.",
        notes = "Al-Qawiy (The All-Powerful) is Allah's name."
      ),
      RootMeaningData(
        root = "ض-ع-ف",
        primaryMeaning = "weakness, double",
        extendedMeaning = "Weakness, and doubling.",
        quranUsage = "'Man was created weak (da'if).' Da'f is weakness. Di'f is double.",
        notes = "Humans are inherently weak and need Allah."
      ),
      RootMeaningData(
        root = "س-ر-ع",
        primaryMeaning = "speed, fast",
        extendedMeaning = "Speed, being fast, and haste.",
        quranUsage = "'Swift (sari') in reckoning.' Sur'ah is speed. Sari' is fast.",
        notes = "Allah is swift in reckoning."
      ),
      RootMeaningData(
        root = "ب-ط-ء",
        primaryMeaning = "slowness, delay",
        extendedMeaning = "Being slow, delay.",
        quranUsage = "'Among you is he who lags behind (yubatti').' But' is slowness.",
        notes = "Some lagged behind from battle."
      ),
      RootMeaningData(
        root = "س-ه-ل",
        primaryMeaning = "easy, plain",
        extendedMeaning = "Being easy, and flat plain.",
        quranUsage = "'We will ease (sayassiru) him toward ease.' Sahl is easy. Taysir is facilitation.",
        notes = "Allah makes things easy for the righteous."
      ),
      RootMeaningData(
        root = "ص-ع-ب",
        primaryMeaning = "difficult, hard",
        extendedMeaning = "Being difficult, hardship.",
        quranUsage = "Sa'b is difficult. Allah does not burden souls beyond capacity.",
        notes = "Difficulties are tests that can be overcome."
      ),
      RootMeaningData(
        root = "ق-ر-ب",
        primaryMeaning = "nearness, close",
        extendedMeaning = "Being near, closeness, and offering.",
        quranUsage = "'We are nearer (aqrab) to him than his jugular vein.' Qurb is nearness. Qurban is offering.",
        notes = "Allah is nearer than we imagine."
      ),
      RootMeaningData(
        root = "ب-ع-د",
        primaryMeaning = "distance, far",
        extendedMeaning = "Being far, distance.",
        quranUsage = "'Away (bu'dan) with the wrongdoing people!' Bu'd is distance.",
        notes = "Distance from Allah is spiritual destruction."
      ),
      RootMeaningData(
        root = "ط-و-ل",
        primaryMeaning = "length, long, favor",
        extendedMeaning = "Length, being long, and bestowing favor.",
        quranUsage = "'Owner of favor (tawl).' Tul is length. Tawil is long.",
        notes = "Al-Tawl refers to Allah's abundant favor."
      ),
      RootMeaningData(
        root = "ق-ص-ر",
        primaryMeaning = "shortness, palace, limit",
        extendedMeaning = "Being short, palace, and limiting/shortening.",
        quranUsage = "'Shorten (taqsuru) the prayer.' Qasr is palace/shortening.",
        notes = "Travelers can shorten their prayers."
      ),
      RootMeaningData(
        root = "ع-ر-ض",
        primaryMeaning = "width, to present",
        extendedMeaning = "Width, presenting, and this world (transient).",
        quranUsage = "'Its width ('ard) is the heavens and earth.' 'Ard is width. 'Arad is worldly gain.",
        notes = "Paradise's width is like the heavens and earth."
      ),
      RootMeaningData(
        root = "ع-م-ق",
        primaryMeaning = "depth, deep",
        extendedMeaning = "Depth, being deep.",
        quranUsage = "'From every deep (amiq) mountain pass.' 'Umq is depth. 'Amiq is deep.",
        notes = "Pilgrims come from every deep valley."
      ),
      RootMeaningData(
        root = "ث-ق-ل",
        primaryMeaning = "weight, heavy",
        extendedMeaning = "Being heavy, weighty.",
        quranUsage = "'If it be the weight (mithqal) of a mustard seed.' Thiql is weight. Thaqil is heavy.",
        notes = "Even an atom's weight of good or evil is recorded."
      ),
      RootMeaningData(
        root = "خ-ف-ف",
        primaryMeaning = "lightness, light",
        extendedMeaning = "Being light (in weight), and easing.",
        quranUsage = "'Allah wants to lighten (yukhaffif) for you.' Khiffah is lightness.",
        notes = "Allah lightens burdens for His servants."
      ),
      RootMeaningData(
        root = "ح-ق-ر",
        primaryMeaning = "contempt, despise",
        extendedMeaning = "Being contemptible, despising.",
        quranUsage = "Haqir is contemptible. Ihtiqar is despising.",
        notes = "Looking down on others is a sign of arrogance."
      ),
      RootMeaningData(
        root = "ع-ظ-م",
        primaryMeaning = "greatness, bone, reverence",
        extendedMeaning = "Being great, bones, and showing reverence.",
        quranUsage = "'Allah is the Greatest (al-'Azim).' 'Azm is bone. Ta'zim is reverence.",
        notes = "Al-'Azim - The Magnificent - is Allah's name."
      ),

      // === MORE THEOLOGICAL/RELIGIOUS TERMS ===
      RootMeaningData(
        root = "ج-ن-ن",
        primaryMeaning = "jinn, garden, cover",
        extendedMeaning = "Jinn, garden (covered by trees), and madness.",
        quranUsage = "'We created jinn (jann) from smokeless fire.' Jinn are hidden beings. Jannah is garden.",
        notes = "Jinn are unseen beings; Jannah is the covered/hidden garden."
      ),
      RootMeaningData(
        root = "إ-ن-س",
        primaryMeaning = "human, to be sociable",
        extendedMeaning = "Human beings, and being sociable.",
        quranUsage = "'We created jinn and mankind (ins).' Ins is humanity. Insan is human. Uns is familiarity.",
        notes = "Humans are social creatures who find comfort in company."
      ),
      RootMeaningData(
        root = "ش-ي-ط",
        primaryMeaning = "Satan, to be far",
        extendedMeaning = "Satan (the far one), and being remote from good.",
        quranUsage = "'Satan (Shaytan) is your enemy.' Shaytan is Satan. Shayt is distance.",
        notes = "Satan is far from Allah's mercy."
      ),
      RootMeaningData(
        root = "إ-ب-ل-س",
        primaryMeaning = "Iblis, despair",
        extendedMeaning = "Iblis (the devil), and despair.",
        quranUsage = "'Except Iblis; he refused.' Iblis is the devil's personal name.",
        notes = "Iblis despaired of Allah's mercy through arrogance."
      ),
      RootMeaningData(
        root = "و-س-و-س",
        primaryMeaning = "whisper, tempt",
        extendedMeaning = "Whispering evil suggestions.",
        quranUsage = "'From the evil of the whisperer (waswas).' Waswasah is evil whispering.",
        notes = "Satan whispers evil thoughts into hearts."
      ),
      RootMeaningData(
        root = "ن-ف-خ",
        primaryMeaning = "to blow, breath",
        extendedMeaning = "Blowing, breathing into.",
        quranUsage = "'We breathed (nafakhna) into him of Our spirit.' Nafkh is blowing.",
        notes = "Allah breathed His spirit into Adam."
      ),
      RootMeaningData(
        root = "س-و-ي",
        primaryMeaning = "equal, fashion, level",
        extendedMeaning = "Making equal, fashioning, and leveling.",
        quranUsage = "'Who created and fashioned (sawwa).' Taswiyah is fashioning. Sawa' is equal.",
        notes = "Allah fashions creation with perfect proportion."
      ),
      RootMeaningData(
        root = "ف-ط-ر",
        primaryMeaning = "create, originate, break fast",
        extendedMeaning = "Original creation, natural disposition, and breaking fast.",
        quranUsage = "'The natural disposition (fitrah) of Allah.' Fitr is breaking fast. Fitrah is innate nature.",
        notes = "Every child is born on the fitrah (natural disposition to recognize Allah)."
      ),
      RootMeaningData(
        root = "ب-ر-ء",
        primaryMeaning = "create, free from",
        extendedMeaning = "Creating (from nothing), and being free/innocent.",
        quranUsage = "'The Creator (Bari') of the heavens.' Al-Bari' is The Creator. Bara'ah is innocence.",
        notes = "Al-Bari' creates without any prior model."
      ),
      RootMeaningData(
        root = "ص-و-ر",
        primaryMeaning = "form, image, shape",
        extendedMeaning = "Form, image, and shaping.",
        quranUsage = "'He formed (sawwara) you and perfected your forms.' Surah is form. Musawwir is Fashioner.",
        notes = "Al-Musawwir (The Fashioner) gives each creation its unique form."
      ),
      RootMeaningData(
        root = "ر-و-ح",
        primaryMeaning = "spirit, soul, rest",
        extendedMeaning = "Spirit, soul, and rest/comfort.",
        quranUsage = "'They ask you about the spirit (ruh).' Ruh is spirit. Rawh is rest.",
        notes = "The ruh is from Allah's command; its nature is known only to Him."
      ),
      RootMeaningData(
        root = "ن-ف-س",
        primaryMeaning = "self, soul, breath",
        extendedMeaning = "Self, soul, and breath.",
        quranUsage = "'Every soul (nafs) will taste death.' Nafs is self/soul. Nafas is breath.",
        notes = "The nafs has levels: commanding evil, self-reproaching, at peace."
      ),
      RootMeaningData(
        root = "ق-ب-ض",
        primaryMeaning = "grasp, contract, take",
        extendedMeaning = "Grasping, contracting, and taking (the soul).",
        quranUsage = "'Allah takes (yaqbidu) the souls at death.' Qabd is grasping. Qabdah is handful.",
        notes = "The Angel of Death grasps souls at their appointed time."
      ),
      RootMeaningData(
        root = "ب-س-ط",
        primaryMeaning = "expand, spread, extend",
        extendedMeaning = "Expanding, spreading out, and extending.",
        quranUsage = "'Allah expands (yabsutu) provision.' Bast is expansion. Basit is extended.",
        notes = "Allah contracts and expands provision as He wills."
      ),
      RootMeaningData(
        root = "ح-ي-ي",
        primaryMeaning = "life, to live, greet",
        extendedMeaning = "Life, living, and greeting (wishing life).",
        quranUsage = "'He gives life (yuhyi) and causes death.' Hayah is life. Tahiyyah is greeting.",
        notes = "Al-Hayy (The Ever-Living) is one of Allah's greatest names."
      ),
      RootMeaningData(
        root = "م-و-ت",
        primaryMeaning = "death, to die",
        extendedMeaning = "Death and dying.",
        quranUsage = "'Every soul will taste death (mawt).' Mawt is death. Mayyit is dead.",
        notes = "Death is certain; only its time is hidden."
      ),
      RootMeaningData(
        root = "ح-ش-ر",
        primaryMeaning = "gathering, resurrection",
        extendedMeaning = "Gathering together, especially for judgment.",
        quranUsage = "'The Day of Gathering (hashr).' Hashr is gathering.",
        notes = "All creatures will be gathered for judgment."
      ),
      RootMeaningData(
        root = "ن-ش-ر",
        primaryMeaning = "spread, publish, resurrect",
        extendedMeaning = "Spreading out, publishing, and resurrection.",
        quranUsage = "'When the pages are spread out (nushirat).' Nashr is spreading. Nushur is resurrection.",
        notes = "The scrolls of deeds will be spread open."
      ),
      RootMeaningData(
        root = "ح-ي-ء",
        primaryMeaning = "life, bring to life",
        extendedMeaning = "Bringing to life, revival.",
        quranUsage = "'He brings the dead to life (yuhyi).' Ihya' is bringing to life.",
        notes = "Allah revives dead hearts and will revive dead bodies."
      ),
      RootMeaningData(
        root = "ق-ب-ر",
        primaryMeaning = "grave, bury",
        extendedMeaning = "Grave, burial.",
        quranUsage = "'When the graves (qubur) are scattered.' Qabr is grave.",
        notes = "The graves will release their contents on Judgment Day."
      ),
      RootMeaningData(
        root = "ص-ر-ط",
        primaryMeaning = "path, way",
        extendedMeaning = "Clear path, straight way.",
        quranUsage = "'Guide us to the straight path (sirat al-mustaqim).' Sirat is path.",
        notes = "The Sirat is both the path of guidance and the bridge over Hell."
      ),
      RootMeaningData(
        root = "م-ز-ن",
        primaryMeaning = "balance, scales",
        extendedMeaning = "Balance, scales for weighing.",
        quranUsage = "'We will set up the scales (mawazin) of justice.' Mizan is scale/balance.",
        notes = "Deeds will be weighed on precise scales."
      ),

      // === MARITIME AND WATER TERMS ===
      RootMeaningData(
        root = "س-ف-ن",
        primaryMeaning = "ship, to peel",
        extendedMeaning = "Ship, vessel, and peeling/scraping.",
        quranUsage = "'We carried them in the laden ship (safinah).' Safinah is ship.",
        notes = "Nuh's ship (safinah) saved the believers from the flood."
      ),
      RootMeaningData(
        root = "ف-ل-ك",
        primaryMeaning = "ship, orbit, sphere",
        extendedMeaning = "Ship, celestial orbit, and sphere.",
        quranUsage = "'And the ships (fulk) sailing through the sea.' Fulk is ship. Falak is orbit.",
        notes = "Ships sailing and stars orbiting are both signs of Allah."
      ),
      RootMeaningData(
        root = "ج-ر-ي",
        primaryMeaning = "to flow, run, sail",
        extendedMeaning = "Flowing, running, and sailing.",
        quranUsage = "'Ships that sail (tajri) in the sea.' Jariyah is flowing/sailing.",
        notes = "Rivers flow and ships sail by Allah's permission."
      ),
      RootMeaningData(
        root = "م-و-ج",
        primaryMeaning = "wave",
        extendedMeaning = "Wave, surge.",
        quranUsage = "'Waves (mawj) like mountains.' Mawj is wave.",
        notes = "The flood came with mountain-like waves."
      ),
      RootMeaningData(
        root = "غ-ر-ق",
        primaryMeaning = "to drown, sink",
        extendedMeaning = "Drowning, sinking.",
        quranUsage = "'We drowned (aghraqna) those who denied.' Gharaq is drowning.",
        notes = "Pharaoh and his army drowned in the sea."
      ),
      RootMeaningData(
        root = "س-ب-ح",
        primaryMeaning = "to swim, float, glorify",
        extendedMeaning = "Swimming, floating, and glorifying Allah.",
        quranUsage = "'Each floating (yasbahun) in an orbit.' Sibahah is swimming. Tasbih is glorification.",
        notes = "Celestial bodies swim in their orbits while glorifying Allah."
      ),
      RootMeaningData(
        root = "ر-س-و",
        primaryMeaning = "to anchor, be firm",
        extendedMeaning = "Anchoring, being firmly established.",
        quranUsage = "'And it came to rest (istawat) on Mount Judi.' Rasa is to anchor. Rawasi are mountains (anchors).",
        notes = "Mountains are like anchors for the earth."
      ),
      RootMeaningData(
        root = "ش-ط-ء",
        primaryMeaning = "shore, bank",
        extendedMeaning = "Shore, riverbank, coast.",
        quranUsage = "'On the right side (shati') of the mount.' Shati' is shore/bank.",
        notes = "Musa was called from the shore of the valley."
      ),
      RootMeaningData(
        root = "ي-م-م",
        primaryMeaning = "sea, to intend",
        extendedMeaning = "Sea, and intending/heading towards.",
        quranUsage = "'We caused the sea (yamm) to engulf them.' Yamm is sea. Tayammum is dry ablution.",
        notes = "Pharaoh was cast into the yamm (sea)."
      ),
      RootMeaningData(
        root = "ل-ج-ج",
        primaryMeaning = "deep sea, persist",
        extendedMeaning = "Deep sea, and persisting stubbornly.",
        quranUsage = "'Darkness in a deep sea (lujji).' Lujjah is deep sea.",
        notes = "Disbelief is like being in deep, dark waters."
      ),
      RootMeaningData(
        root = "ز-ب-د",
        primaryMeaning = "foam, froth",
        extendedMeaning = "Foam, froth, scum.",
        quranUsage = "'As for the foam (zabad), it vanishes.' Zabad is foam.",
        notes = "Falsehood is like foam - it appears but vanishes."
      ),

      // === SPEECH AND COMMUNICATION ===
      RootMeaningData(
        root = "ك-ل-م",
        primaryMeaning = "speech, word, to speak",
        extendedMeaning = "Speech, words, and speaking.",
        quranUsage = "'Allah spoke (kallama) to Musa directly.' Kalam is speech. Kalimah is word.",
        notes = "Musa is Kalimullah - the one Allah spoke to directly."
      ),
      RootMeaningData(
        root = "ق-و-ل",
        primaryMeaning = "to say, speech, statement",
        extendedMeaning = "Saying, speech, and statement.",
        quranUsage = "'Say (qul): He is Allah, One.' Qawl is saying. Maqulah is statement.",
        notes = "Qul (Say) appears over 300 times as a command to the Prophet."
      ),
      RootMeaningData(
        root = "ن-ط-ق",
        primaryMeaning = "to speak, articulate",
        extendedMeaning = "Speaking, articulating, logical speech.",
        quranUsage = "'He does not speak (yantiq) from desire.' Nutq is speech. Mantiq is logic.",
        notes = "The Prophet's speech is revelation, not personal desire."
      ),
      RootMeaningData(
        root = "ل-ف-ظ",
        primaryMeaning = "to utter, word",
        extendedMeaning = "Uttering, pronouncing words.",
        quranUsage = "'He does not utter (yalfizu) any word.' Lafz is utterance.",
        notes = "Every word we utter is recorded by angels."
      ),
      RootMeaningData(
        root = "ص-م-ت",
        primaryMeaning = "silence, to be silent",
        extendedMeaning = "Silence, being quiet.",
        quranUsage = "Samt is silence. Samit is silent.",
        notes = "Silence can be wisdom or can be sinful when truth must be spoken."
      ),
      RootMeaningData(
        root = "ص-ر-خ",
        primaryMeaning = "to scream, cry out",
        extendedMeaning = "Screaming, crying for help.",
        quranUsage = "'They will cry out (yastasrikhun) therein.' Surakh is scream.",
        notes = "The people of Hell will scream for help."
      ),
      RootMeaningData(
        root = "ن-د-ي",
        primaryMeaning = "to call, assembly",
        extendedMeaning = "Calling out, assembly, and club.",
        quranUsage = "'Call (nadi) your associates.' Nida' is call. Nadi is assembly.",
        notes = "Dar al-Nadwah was the assembly hall of Quraysh."
      ),
      RootMeaningData(
        root = "ص-ي-ح",
        primaryMeaning = "to shout, cry",
        extendedMeaning = "Shouting, loud cry.",
        quranUsage = "'A single shout (sayhah) seized them.' Sayhah is shout/blast.",
        notes = "Many nations were destroyed by a single shout."
      ),
      RootMeaningData(
        root = "ه-ت-ف",
        primaryMeaning = "to call out, cheer",
        extendedMeaning = "Calling out, cheering.",
        quranUsage = "Hatif is one who calls. Hatf is calling out.",
        notes = "The unseen caller guides or warns."
      ),
      RootMeaningData(
        root = "ه-م-س",
        primaryMeaning = "whisper, faint sound",
        extendedMeaning = "Whispering, hushed sound.",
        quranUsage = "'You will hear only whispers (hams).' Hams is whisper.",
        notes = "On Judgment Day, voices will be hushed before Allah."
      ),
      RootMeaningData(
        root = "س-ر-ر",
        primaryMeaning = "secret, whisper",
        extendedMeaning = "Secret talk, whispering.",
        quranUsage = "'They conspired in secret (najwa).' Sirr is secret. Israr is confiding.",
        notes = "Secret conversations should not be for sin."
      ),
      RootMeaningData(
        root = "ج-ه-ر",
        primaryMeaning = "to speak aloud, public",
        extendedMeaning = "Speaking aloud, being public.",
        quranUsage = "'Whether you speak aloud (tajhar).' Jahr is speaking aloud.",
        notes = "Allah knows what is spoken aloud and what is hidden."
      ),
      RootMeaningData(
        root = "خ-ف-ت",
        primaryMeaning = "to lower voice, be soft",
        extendedMeaning = "Lowering voice, speaking softly.",
        quranUsage = "'Do not be loud in your prayer nor silent, but seek between.' Khaft is lowering voice.",
        notes = "Moderation in voice during prayer is recommended."
      ),
      RootMeaningData(
        root = "ل-غ-و",
        primaryMeaning = "vain talk, nonsense",
        extendedMeaning = "Vain, useless speech, and nullification.",
        quranUsage = "'They turn away from vain talk (laghw).' Laghw is vain speech.",
        notes = "Believers avoid laghw - idle, meaningless talk."
      ),
      RootMeaningData(
        root = "ب-ي-ن",
        primaryMeaning = "clear, between, explain",
        extendedMeaning = "Being clear, between things, and explaining.",
        quranUsage = "'A clear (mubin) Arabic tongue.' Bayan is explanation. Bayn is between.",
        notes = "The Quran is a clear explanation of all things."
      ),
      RootMeaningData(
        root = "ف-س-ر",
        primaryMeaning = "to explain, interpret",
        extendedMeaning = "Explaining, interpreting.",
        quranUsage = "'We bring you the truth and the best explanation (tafsir).' Tafsir is explanation.",
        notes = "Tafsir is the science of Quranic interpretation."
      ),
      RootMeaningData(
        root = "أ-و-ل",
        primaryMeaning = "interpretation, first, return",
        extendedMeaning = "Interpretation, being first, and returning.",
        quranUsage = "'None knows its interpretation (ta'wil) except Allah.' Ta'wil is deeper interpretation.",
        notes = "Ta'wil goes deeper than tafsir into hidden meanings."
      ),

      // === MENTAL AND PSYCHOLOGICAL STATES ===
      RootMeaningData(
        root = "ع-ق-ل",
        primaryMeaning = "reason, mind, intellect",
        extendedMeaning = "Reason, intellect, and restraining.",
        quranUsage = "'Do you not reason (ta'qilun)?' 'Aql is intellect. 'Aqil is rational.",
        notes = "The Quran repeatedly appeals to human reason."
      ),
      RootMeaningData(
        root = "ل-ب-ب",
        primaryMeaning = "core, understanding, heart",
        extendedMeaning = "Core, understanding, and pure intellect.",
        quranUsage = "'A reminder for those of understanding (uli al-albab).' Lubb is core/intellect.",
        notes = "Uli al-albab are people of deep understanding."
      ),
      RootMeaningData(
        root = "ح-ل-م",
        primaryMeaning = "forbearance, dream, puberty",
        extendedMeaning = "Forbearance, dreams, and reaching maturity.",
        quranUsage = "'When children reach puberty (hulm).' Hilm is forbearance. Hulm is dream.",
        notes = "Al-Halim (The Forbearing) is Allah's name."
      ),
      RootMeaningData(
        root = "س-ف-ه",
        primaryMeaning = "foolishness, ignorance",
        extendedMeaning = "Foolishness, lack of wisdom.",
        quranUsage = "'The foolish (sufaha') among the people.' Safah is foolishness. Safih is fool.",
        notes = "Fools waste their wealth and reject guidance."
      ),
      RootMeaningData(
        root = "ج-ن-ن",
        primaryMeaning = "madness, jinn, cover",
        extendedMeaning = "Madness, jinn (hidden), and covering.",
        quranUsage = "'They said: You are possessed (majnun)!' Junun is madness. Majnun is mad.",
        notes = "Prophets were often accused of madness by their people."
      ),
      RootMeaningData(
        root = "ر-ش-د",
        primaryMeaning = "right guidance, maturity",
        extendedMeaning = "Right guidance, maturity, and proper conduct.",
        quranUsage = "'Perhaps my Lord will guide me to right conduct (rushd).' Rushd is right guidance.",
        notes = "Ar-Rashid (The Guide to Right Path) is Allah's name."
      ),
      RootMeaningData(
        root = "غ-ي-ي",
        primaryMeaning = "misguidance, error",
        extendedMeaning = "Misguidance, going astray.",
        quranUsage = "'Those who have gone astray (dallin).' Ghayy is misguidance.",
        notes = "Ghayy is the opposite of rushd (right guidance)."
      ),
      RootMeaningData(
        root = "و-ه-م",
        primaryMeaning = "illusion, imagination",
        extendedMeaning = "Illusion, false imagination.",
        quranUsage = "Wahm is illusion. Tawahhum is imagining falsely.",
        notes = "False imagination leads people astray."
      ),
      RootMeaningData(
        root = "ش-ع-ر",
        primaryMeaning = "to feel, poetry, hair",
        extendedMeaning = "Feeling, awareness, poetry, and hair.",
        quranUsage = "'If only they were aware (yash'urun).' Shi'r is poetry. Sha'r is hair.",
        notes = "Poets feel deeply; awareness is a form of feeling."
      ),
      RootMeaningData(
        root = "ح-س-س",
        primaryMeaning = "to sense, feel, perceive",
        extendedMeaning = "Sensing, feeling, perceiving.",
        quranUsage = "'When Isa sensed (ahassa) disbelief.' Hiss is sense. Ihsas is sensing.",
        notes = "Prophets had keen spiritual perception."
      ),
      RootMeaningData(
        root = "ظ-ن-ن",
        primaryMeaning = "to think, suppose, suspect",
        extendedMeaning = "Thinking, supposing, and suspicion.",
        quranUsage = "'Avoid much suspicion (zann).' Zann is thought/suspicion.",
        notes = "Some suspicion is sin - it leads to false accusation."
      ),
      RootMeaningData(
        root = "ح-س-ب",
        primaryMeaning = "to think, reckon, account",
        extendedMeaning = "Thinking, reckoning, and accounting.",
        quranUsage = "'Do they think (yahsabu) that they will be left?' Husban is reckoning.",
        notes = "People wrongly think they won't be held accountable."
      ),
      RootMeaningData(
        root = "خ-ي-ل",
        primaryMeaning = "imagination, horse, pride",
        extendedMeaning = "Imagination, horses, and pride.",
        quranUsage = "'It seemed (yukhayyalu) to him from their magic.' Khayal is imagination. Khayl is horses.",
        notes = "Magic creates illusions that seem real."
      ),
      RootMeaningData(
        root = "غ-ف-ل",
        primaryMeaning = "heedlessness, neglect",
        extendedMeaning = "Heedlessness, neglecting, being unaware.",
        quranUsage = "'They are heedless (ghafilun).' Ghaflah is heedlessness. Ghafil is heedless.",
        notes = "Heedlessness of Allah's signs leads to destruction."
      ),
      RootMeaningData(
        root = "س-ه-و",
        primaryMeaning = "forgetfulness, distraction",
        extendedMeaning = "Forgetfulness, distraction, negligence.",
        quranUsage = "'Those who are heedless (sahun) of their prayer.' Sahw is forgetfulness.",
        notes = "Being negligent about prayer is condemned."
      ),
      RootMeaningData(
        root = "ذ-ه-ل",
        primaryMeaning = "to forget, be distracted",
        extendedMeaning = "Forgetting from distraction or shock.",
        quranUsage = "'Every nursing mother will forget (tadhhal).' Dhuhul is distraction.",
        notes = "The terror of Judgment Day will make mothers forget their infants."
      ),

      // === QUALITIES AND CHARACTERISTICS ===
      RootMeaningData(
        root = "ج-م-ل",
        primaryMeaning = "beauty, camel, entirety",
        extendedMeaning = "Beauty, camel, and completeness.",
        quranUsage = "'Patience is beautiful (jamil).' Jamal is beauty. Jamal is camel.",
        notes = "Beautiful patience (sabr jamil) is patience without complaint."
      ),
      RootMeaningData(
        root = "ق-ب-ح",
        primaryMeaning = "ugliness, bad",
        extendedMeaning = "Ugliness, badness.",
        quranUsage = "'How evil (qubh) is that.' Qubh is ugliness. Qabih is ugly/bad.",
        notes = "Moral ugliness is worse than physical ugliness."
      ),
      RootMeaningData(
        root = "ط-ي-ب",
        primaryMeaning = "good, pure, pleasant",
        extendedMeaning = "Goodness, purity, and pleasantness.",
        quranUsage = "'A good (tayyib) word.' Tayyib is good/pure. Tib is perfume.",
        notes = "A good word is like a good tree with firm roots."
      ),
      RootMeaningData(
        root = "خ-ب-ث",
        primaryMeaning = "evil, impure, wicked",
        extendedMeaning = "Evil, impurity, and wickedness.",
        quranUsage = "'The evil (khabith) and the good.' Khabith is evil/impure.",
        notes = "Khabith is the opposite of tayyib (good/pure)."
      ),
      RootMeaningData(
        root = "ص-ف-و",
        primaryMeaning = "pure, clear, chosen",
        extendedMeaning = "Purity, clarity, and being chosen.",
        quranUsage = "'Allah chose (istafa) Adam.' Safiy is pure. Mustafa is chosen.",
        notes = "Al-Mustafa (The Chosen One) is the Prophet's title."
      ),
      RootMeaningData(
        root = "ك-د-ر",
        primaryMeaning = "murky, turbid",
        extendedMeaning = "Murkiness, being unclear.",
        quranUsage = "Kadar is murkiness. Kadira is to be turbid.",
        notes = "The opposite of clarity and purity."
      ),
      RootMeaningData(
        root = "ن-ق-ي",
        primaryMeaning = "pure, clean",
        extendedMeaning = "Purity, cleanliness.",
        quranUsage = "Naqiy is pure. Tanqiyah is purification.",
        notes = "Spiritual and physical purity go together."
      ),
      RootMeaningData(
        root = "و-س-خ",
        primaryMeaning = "dirty, filth",
        extendedMeaning = "Dirt, filth.",
        quranUsage = "Wasakh is dirt. Wasikh is dirty.",
        notes = "Sin is spiritual dirt that needs cleansing."
      ),
      RootMeaningData(
        root = "ص-ل-ب",
        primaryMeaning = "hard, firm, crucify",
        extendedMeaning = "Hardness, firmness, and crucifixion.",
        quranUsage = "'I will crucify (la-usallibannakum) you.' Salb is crucifixion. Sulb is hard/loins.",
        notes = "Pharaoh threatened to crucify those who believed."
      ),
      RootMeaningData(
        root = "ل-ي-ن",
        primaryMeaning = "soft, gentle, flexible",
        extendedMeaning = "Softness, gentleness.",
        quranUsage = "'We softened (alanna) iron for him.' Layin is soft. Layyina is to soften.",
        notes = "Iron was softened for Dawud to make armor."
      ),
      RootMeaningData(
        root = "ح-ا-د",
        primaryMeaning = "sharp, hot, severe",
        extendedMeaning = "Sharpness, severity.",
        quranUsage = "'Sharp (hadid) in tongue.' Hadd is sharp. Haddah is sharpness.",
        notes = "Sharp words can wound like sharp objects."
      ),
      RootMeaningData(
        root = "ث-ق-ل",
        primaryMeaning = "heavy, weighty",
        extendedMeaning = "Heaviness, weightiness.",
        quranUsage = "'We will cast upon you a heavy (thaqil) word.' Thaqil is heavy. Athqal is burdens.",
        notes = "The Quran is a weighty word with heavy responsibility."
      ),
      RootMeaningData(
        root = "ر-ق-ق",
        primaryMeaning = "thin, delicate",
        extendedMeaning = "Thinness, delicacy.",
        quranUsage = "Raqiq is thin. Riqqah is softness of heart.",
        notes = "A soft, thin heart is receptive to truth."
      ),
      RootMeaningData(
        root = "غ-ل-ظ",
        primaryMeaning = "thick, harsh, rough",
        extendedMeaning = "Thickness, harshness.",
        quranUsage = "'If you had been harsh (ghalizh).' Ghilzah is harshness. Ghaliz is harsh.",
        notes = "The Prophet was not harsh - it would have driven people away."
      ),
      RootMeaningData(
        root = "ر-ط-ب",
        primaryMeaning = "moist, fresh, ripe dates",
        extendedMeaning = "Moisture, freshness, and ripe dates.",
        quranUsage = "'Fresh ripe dates (rutab) will fall upon you.' Rutab is ripe dates.",
        notes = "Maryam was told to shake the palm for fresh dates."
      ),
      RootMeaningData(
        root = "ي-ب-س",
        primaryMeaning = "dry, withered",
        extendedMeaning = "Dryness, being withered.",
        quranUsage = "'Green or dry (yabis).' Yabis is dry. Yubs is dryness.",
        notes = "Nothing green or dry escapes Allah's knowledge."
      ),
      RootMeaningData(
        root = "ب-ا-ر-د",
        primaryMeaning = "cold, cool",
        extendedMeaning = "Coldness, coolness.",
        quranUsage = "'We said: O fire, be coolness (bard).' Bard is cold/cool.",
        notes = "Fire became cool and safe for Ibrahim."
      ),
      RootMeaningData(
        root = "ح-ا-ر",
        primaryMeaning = "hot, warm",
        extendedMeaning = "Heat, warmth.",
        quranUsage = "'A hot (har) wind.' Harr is heat. Har is hot.",
        notes = "Hell's heat is beyond imagination."
      ),

      // === MORE BODY PARTS AND PHYSICAL ===
      RootMeaningData(
        root = "ج-ب-ه",
        primaryMeaning = "forehead, face",
        extendedMeaning = "Forehead, front of face.",
        quranUsage = "'Seized by the foreheads (nawasi).' Jabhah is forehead.",
        notes = "Prostration is done on the forehead."
      ),
      RootMeaningData(
        root = "ج-ف-ن",
        primaryMeaning = "eyelid",
        extendedMeaning = "Eyelid.",
        quranUsage = "Jafn is eyelid. Ajfan is eyelids.",
        notes = "Sleep involves the closing of eyelids."
      ),
      RootMeaningData(
        root = "ح-ا-ج-ب",
        primaryMeaning = "eyebrow, barrier",
        extendedMeaning = "Eyebrow, and barrier/screen.",
        quranUsage = "Hajib is eyebrow/barrier. The eyebrow protects the eye.",
        notes = "A barrier (hijab) screens and protects."
      ),
      RootMeaningData(
        root = "ش-ف-ه",
        primaryMeaning = "lip",
        extendedMeaning = "Lip.",
        quranUsage = "'Two lips (shafatayn).' Shafah is lip.",
        notes = "Lips are among the blessings Allah has given."
      ),
      RootMeaningData(
        root = "ل-ث-ه",
        primaryMeaning = "gum (mouth)",
        extendedMeaning = "Gums in the mouth.",
        quranUsage = "Lithah is gum. Part of the mouth's structure.",
        notes = "The teeth and gums are part of Allah's creation."
      ),
      RootMeaningData(
        root = "س-ن-ن",
        primaryMeaning = "tooth, law, way",
        extendedMeaning = "Tooth, established way, and law.",
        quranUsage = "'A tooth (sinn) for a tooth.' Sinn is tooth. Sunnah is way.",
        notes = "Sunnah means established way - the Prophet's practice."
      ),
      RootMeaningData(
        root = "ح-ن-ك",
        primaryMeaning = "palate, jaw",
        extendedMeaning = "Palate, upper jaw.",
        quranUsage = "Hanak is palate. Related to the mouth.",
        notes = "Part of the intricate design of the mouth."
      ),
      RootMeaningData(
        root = "ح-ن-ج-ر",
        primaryMeaning = "throat, larynx",
        extendedMeaning = "Throat, voice box.",
        quranUsage = "'When it reaches the throat (hanajir).' Hanjara is throat.",
        notes = "At death, the soul rises to the throat."
      ),
      RootMeaningData(
        root = "ع-ن-ق",
        primaryMeaning = "neck",
        extendedMeaning = "Neck.",
        quranUsage = "'Shackles on their necks ('a'naq).' 'Unuq is neck.",
        notes = "Shackles will be on the necks of criminals."
      ),
      RootMeaningData(
        root = "ك-ت-ف",
        primaryMeaning = "shoulder",
        extendedMeaning = "Shoulder.",
        quranUsage = "Katif is shoulder. Aktaf is shoulders.",
        notes = "Shoulders bear burdens."
      ),
      RootMeaningData(
        root = "ذ-ر-ع",
        primaryMeaning = "arm, cubit, forearm",
        extendedMeaning = "Arm, forearm, and cubit (unit of measure).",
        quranUsage = "'A chain of seventy cubits (dhira').' Dhira' is arm/cubit.",
        notes = "The cubit was measured by the forearm."
      ),
      RootMeaningData(
        root = "م-ر-ف-ق",
        primaryMeaning = "elbow, facility",
        extendedMeaning = "Elbow, and useful facility.",
        quranUsage = "'Your elbows (marafiq).' Mirfaq is elbow. Marfaq is facility.",
        notes = "Elbows are washed in wudu."
      ),
      RootMeaningData(
        root = "ك-ف-ف",
        primaryMeaning = "palm, to refrain",
        extendedMeaning = "Palm of hand, and refraining.",
        quranUsage = "'Restrain (kuffu) your hands.' Kaff is palm. Kaff is to restrain.",
        notes = "The palm is used in greeting and restraint."
      ),
      RootMeaningData(
        root = "إ-ص-ب-ع",
        primaryMeaning = "finger",
        extendedMeaning = "Finger.",
        quranUsage = "'They put their fingers (asabi') in their ears.' Isba' is finger.",
        notes = "Covering ears with fingers symbolizes rejecting truth."
      ),
      RootMeaningData(
        root = "ظ-ف-ر",
        primaryMeaning = "nail, victory",
        extendedMeaning = "Fingernail, and victory.",
        quranUsage = "'Every animal with claws (zufur).' Zufr is nail/claw. Zafar is victory.",
        notes = "Certain animals with claws were forbidden to Jews."
      ),
      RootMeaningData(
        root = "ف-خ-ذ",
        primaryMeaning = "thigh",
        extendedMeaning = "Thigh.",
        quranUsage = "Fakhidh is thigh. Related to the lower body.",
        notes = "Part of the human body's structure."
      ),
      RootMeaningData(
        root = "ر-ك-ب",
        primaryMeaning = "knee, to ride",
        extendedMeaning = "Knee, and riding.",
        quranUsage = "'Crawling on their knees (juthiyyan).' Rukbah is knee. Rukub is riding.",
        notes = "People will be on their knees on Judgment Day."
      ),
      RootMeaningData(
        root = "س-ا-ق",
        primaryMeaning = "shin, leg, drive",
        extendedMeaning = "Shin/leg, and driving.",
        quranUsage = "'The day the shin (saq) will be uncovered.' Saq is shin/leg.",
        notes = "A day of great severity is described."
      ),
      RootMeaningData(
        root = "ك-ع-ب",
        primaryMeaning = "ankle, cube, Kaaba",
        extendedMeaning = "Ankle, cube shape, and the Kaaba.",
        quranUsage = "'To the ankles (ka'bayn).' Ka'b is ankle. Ka'bah is cube-shaped.",
        notes = "The Kaaba is cube-shaped. Feet are washed to the ankles."
      ),
      RootMeaningData(
        root = "ع-ق-ب",
        primaryMeaning = "heel, consequence, follow",
        extendedMeaning = "Heel, consequence, and following after.",
        quranUsage = "'They turned on their heels ('aqibayhim).' 'Aqib is heel. 'Aqibah is consequence.",
        notes = "Turning on heels means retreating from truth."
      ),

      // === MORE WORSHIP AND RELIGIOUS PRACTICE ===
      RootMeaningData(
        root = "ت-ه-ج-د",
        primaryMeaning = "night prayer, vigil",
        extendedMeaning = "Night prayer, keeping vigil.",
        quranUsage = "'And during the night pray (tahajjad).' Tahajjud is night prayer.",
        notes = "Tahajjud is voluntary night prayer after sleeping."
      ),
      RootMeaningData(
        root = "ق-ن-ت",
        primaryMeaning = "devotion, obedience",
        extendedMeaning = "Devout obedience, standing in prayer.",
        quranUsage = "'Be devoutly obedient (qanitin).' Qunut is devotion/supplication.",
        notes = "Qunut can also be a supplication in prayer."
      ),
      RootMeaningData(
        root = "خ-ش-ع",
        primaryMeaning = "humility, reverence",
        extendedMeaning = "Deep humility, reverent fear.",
        quranUsage = "'Successful are the believers who are humble (khashi'un).' Khushu' is humility.",
        notes = "Khushu' in prayer means the heart is present and humble."
      ),
      RootMeaningData(
        root = "خ-ض-ع",
        primaryMeaning = "to submit, lower",
        extendedMeaning = "Submission, lowering oneself.",
        quranUsage = "'Their necks would be submitting (khadi'in).' Khudu' is submission.",
        notes = "Lowering oneself in submission to Allah."
      ),
      RootMeaningData(
        root = "ن-ذ-ر",
        primaryMeaning = "vow, warning",
        extendedMeaning = "Making a vow, and warning.",
        quranUsage = "'I have vowed (nadhartu) to the Most Merciful.' Nadhr is vow. Nadhir is warner.",
        notes = "Vows to Allah must be fulfilled. Prophets are warners."
      ),
      RootMeaningData(
        root = "ي-م-ن",
        primaryMeaning = "oath, right, blessing",
        extendedMeaning = "Oath, right hand, and blessing.",
        quranUsage = "'By the right hand (yamin).' Yamin is oath/right. Ayman is oaths.",
        notes = "Oaths are sworn; the right side is blessed."
      ),
      RootMeaningData(
        root = "ح-ل-ف",
        primaryMeaning = "to swear, oath",
        extendedMeaning = "Swearing an oath.",
        quranUsage = "'They swear (yahlifun) by Allah.' Half is oath. Hilf is alliance.",
        notes = "False oaths are a serious sin."
      ),
      RootMeaningData(
        root = "ق-س-م",
        primaryMeaning = "to swear, divide, share",
        extendedMeaning = "Swearing, dividing, and sharing.",
        quranUsage = "'I swear (uqsimu) by this city.' Qasam is oath. Qism is share.",
        notes = "Allah swears by His creation to emphasize truths."
      ),
      RootMeaningData(
        root = "ز-ك-و",
        primaryMeaning = "purification, growth, zakat",
        extendedMeaning = "Purification, growth, and obligatory charity.",
        quranUsage = "'Give the purifying charity (zakat).' Zakah is purity/charity. Tazkiyah is purification.",
        notes = "Zakat purifies wealth and helps it grow."
      ),
      RootMeaningData(
        root = "ص-د-ق",
        primaryMeaning = "charity, truth, sincerity",
        extendedMeaning = "Voluntary charity, and truthfulness.",
        quranUsage = "'The men and women who give charity (sadaqat).' Sadaqah is charity. Sidq is truth.",
        notes = "Sadaqah comes from sidq - true generosity."
      ),
      RootMeaningData(
        root = "إ-ن-ف-ق",
        primaryMeaning = "to spend, expend",
        extendedMeaning = "Spending, especially in charity.",
        quranUsage = "'Spend (anfiqu) from what We have provided.' Infaq is spending. Nafaqah is expenditure.",
        notes = "Spending in Allah's cause is highly rewarded."
      ),
      RootMeaningData(
        root = "ع-ش-ر",
        primaryMeaning = "tithe, tenth, ten",
        extendedMeaning = "Tithe, tenth part.",
        quranUsage = "'Ushr is tithe. Related to ten ('ashrah).",
        notes = "A tithe is one-tenth of produce."
      ),
      RootMeaningData(
        root = "ك-ف-ر",
        primaryMeaning = "expiation, cover, disbelief",
        extendedMeaning = "Expiation (covering sins), and disbelief.",
        quranUsage = "'An expiation (kaffarah) for it.' Kaffarah is expiation. Kufr is disbelief.",
        notes = "Kaffarah covers sins like kafir covers truth."
      ),
      RootMeaningData(
        root = "ع-ت-ق",
        primaryMeaning = "to free, emancipate",
        extendedMeaning = "Freeing slaves, emancipation.",
        quranUsage = "'Freeing ('itq) a slave.' 'Itq is emancipation. 'Atiq is freed.",
        notes = "Freeing slaves is highly meritorious in Islam."
      ),

      // === LEGAL AND JUDICIAL TERMS ===
      RootMeaningData(
        root = "ح-ك-م",
        primaryMeaning = "judgment, wisdom, rule",
        extendedMeaning = "Judging, wisdom, and ruling.",
        quranUsage = "'Judgment (hukm) belongs only to Allah.' Hukm is judgment. Hikmah is wisdom.",
        notes = "Al-Hakam (The Judge) and Al-Hakim (The Wise) are Allah's names."
      ),
      RootMeaningData(
        root = "ق-ض-ي",
        primaryMeaning = "to decree, judge, fulfill",
        extendedMeaning = "Decreeing, judging, and fulfilling.",
        quranUsage = "'When He decrees (qada) a matter.' Qada' is decree. Qadi is judge.",
        notes = "Allah's decree cannot be overturned."
      ),
      RootMeaningData(
        root = "ف-ت-و",
        primaryMeaning = "religious verdict, youth",
        extendedMeaning = "Religious ruling, and youth.",
        quranUsage = "'They ask you for a verdict (yastaftunaka).' Fatwa is verdict. Fata is youth.",
        notes = "Muftis issue fatwas (religious verdicts)."
      ),
      RootMeaningData(
        root = "ح-ل-ل",
        primaryMeaning = "lawful, permit, untie",
        extendedMeaning = "Making lawful, permitting.",
        quranUsage = "'Made lawful (ahalla) for you.' Halal is lawful. Tahlil is making lawful.",
        notes = "Halal is the opposite of haram."
      ),
      RootMeaningData(
        root = "ح-ر-م",
        primaryMeaning = "forbidden, sacred",
        extendedMeaning = "Forbidding, making sacred.",
        quranUsage = "'He has forbidden (harrama) it.' Haram is forbidden. Hurmah is sanctity.",
        notes = "The same root gives 'forbidden' and 'sacred' - both are set apart."
      ),
      RootMeaningData(
        root = "و-ج-ب",
        primaryMeaning = "obligatory, necessary",
        extendedMeaning = "Being obligatory, necessary, and falling due.",
        quranUsage = "'When they fall (wajabat) on their sides.' Wajib is obligatory.",
        notes = "Wajib acts are obligatory in Islamic law."
      ),
      RootMeaningData(
        root = "س-ن-ن",
        primaryMeaning = "established practice, law",
        extendedMeaning = "Established way, practice, and law.",
        quranUsage = "'The established way (sunnah) of Allah.' Sunnah is established practice.",
        notes = "Sunnah refers to both Allah's way and the Prophet's practice."
      ),
      RootMeaningData(
        root = "ب-د-ع",
        primaryMeaning = "innovation, originate",
        extendedMeaning = "Innovation, creating something new.",
        quranUsage = "'Originator (Badi') of heavens and earth.' Bid'ah is innovation.",
        notes = "Allah originates without precedent. Religious innovation (bid'ah) is cautioned against."
      ),
      RootMeaningData(
        root = "ش-ه-د",
        primaryMeaning = "witness, testify, martyr",
        extendedMeaning = "Witnessing, testifying, and martyrdom.",
        quranUsage = "'Be witnesses (shuhada').' Shahid is witness. Shahadah is testimony.",
        notes = "Shahid means both witness and martyr."
      ),
      RootMeaningData(
        root = "ب-ي-ن",
        primaryMeaning = "evidence, clear proof",
        extendedMeaning = "Clear evidence, proof.",
        quranUsage = "'Clear evidence (bayyinah) has come to you.' Bayyinah is clear proof.",
        notes = "Surah Al-Bayyinah - The Clear Evidence."
      ),
      RootMeaningData(
        root = "ح-ج-ج",
        primaryMeaning = "argument, proof, pilgrimage",
        extendedMeaning = "Argument, proof, and pilgrimage.",
        quranUsage = "'Do you argue (tuhajjunna) with us about Allah?' Hujjah is proof. Hajj is pilgrimage.",
        notes = "Presenting a hujjah (proof) is part of discourse."
      ),
      RootMeaningData(
        root = "ب-ر-ه-ن",
        primaryMeaning = "proof, evidence",
        extendedMeaning = "Proof, clear evidence.",
        quranUsage = "'Bring your proof (burhan).' Burhan is proof.",
        notes = "Claims require burhan (proof)."
      ),
      RootMeaningData(
        root = "د-ل-ل",
        primaryMeaning = "proof, guide, indicate",
        extendedMeaning = "Proof, guidance, and indication.",
        quranUsage = "'A sign (dalil) guiding them.' Dalil is proof/guide. Dalalah is indication.",
        notes = "Evidence (dalil) guides to truth."
      ),
      RootMeaningData(
        root = "ع-ق-ب",
        primaryMeaning = "punishment, consequence",
        extendedMeaning = "Punishment, consequence, and following after.",
        quranUsage = "'Severe in punishment ('iqab).' 'Uqubah is punishment. 'Aqibah is consequence.",
        notes = "Every action has a consequence ('aqibah)."
      ),
      RootMeaningData(
        root = "ج-ز-ء",
        primaryMeaning = "recompense, reward",
        extendedMeaning = "Recompense, reward or punishment.",
        quranUsage = "'The recompense (jaza') of evil is evil like it.' Jaza' is recompense.",
        notes = "Jaza' can be reward or punishment - fair recompense."
      ),
      RootMeaningData(
        root = "ق-ص-ص",
        primaryMeaning = "retaliation, narration, trace",
        extendedMeaning = "Retaliation (equal punishment), and narration.",
        quranUsage = "'In retaliation (qisas) there is life.' Qisas is retaliation. Qissah is story.",
        notes = "Qisas ensures justice and deters crime."
      ),
      RootMeaningData(
        root = "د-ي-ت",
        primaryMeaning = "blood money, compensation",
        extendedMeaning = "Blood money for manslaughter.",
        quranUsage = "'Blood money (diyah) to his family.' Diyah is compensation.",
        notes = "Diyah is paid for accidental killing."
      ),

      // === MORE NATURE AND ENVIRONMENT ===
      RootMeaningData(
        root = "س-م-ء",
        primaryMeaning = "sky, heaven, rain",
        extendedMeaning = "Sky, heaven, and what descends from it.",
        quranUsage = "'We built above you seven strong (heavens).' Sama' is sky. Samawi is heavenly.",
        notes = "Seven heavens are layered above us."
      ),
      RootMeaningData(
        root = "أ-ف-ق",
        primaryMeaning = "horizon",
        extendedMeaning = "Horizon, region.",
        quranUsage = "'On the clear horizon (ufuq).' Ufuq is horizon. Afaq are horizons/regions.",
        notes = "The Prophet saw Jibril on the clear horizon."
      ),
      RootMeaningData(
        root = "ف-ض-ء",
        primaryMeaning = "space, open area",
        extendedMeaning = "Space, open area.",
        quranUsage = "Fada' is open space. The heavens are vast spaces.",
        notes = "The universe's vastness is a sign."
      ),
      RootMeaningData(
        root = "ج-و-و",
        primaryMeaning = "atmosphere, air, interior",
        extendedMeaning = "Atmosphere, air, and interior.",
        quranUsage = "'Birds in the atmosphere (jaww) of the sky.' Jaww is atmosphere.",
        notes = "Birds fly in the atmosphere by Allah's permission."
      ),
      RootMeaningData(
        root = "ه-و-ء",
        primaryMeaning = "air, atmosphere, desire",
        extendedMeaning = "Air, empty space, and desire.",
        quranUsage = "'Their hearts are empty (hawa').' Hawa' is air/void. Hawa is desire.",
        notes = "Following desire (hawa) leads astray."
      ),
      RootMeaningData(
        root = "غ-ب-ر",
        primaryMeaning = "dust, past",
        extendedMeaning = "Dust, and what has passed.",
        quranUsage = "'Covered with dust (ghabarah).' Ghubar is dust.",
        notes = "Faces will be covered with dust on Judgment Day."
      ),
      RootMeaningData(
        root = "د-خ-ن",
        primaryMeaning = "smoke",
        extendedMeaning = "Smoke.",
        quranUsage = "'When the sky brings visible smoke (dukhan).' Dukhan is smoke.",
        notes = "Surah Ad-Dukhan mentions smoke as a sign."
      ),
      RootMeaningData(
        root = "ض-ب-ب",
        primaryMeaning = "fog, mist",
        extendedMeaning = "Fog, mist.",
        quranUsage = "Dabab is fog/mist. Obscures vision like doubt obscures truth.",
        notes = "Fog represents uncertainty and obscurity."
      ),
      RootMeaningData(
        root = "ن-د-ي",
        primaryMeaning = "dew, moisture",
        extendedMeaning = "Dew, moisture.",
        quranUsage = "Nada is dew. Morning moisture is a blessing.",
        notes = "Dew refreshes the earth gently."
      ),
      RootMeaningData(
        root = "ص-ق-ع",
        primaryMeaning = "region, cold, frost",
        extendedMeaning = "Region, and extreme cold.",
        quranUsage = "Saq' is frost. Saqi' is cold region.",
        notes = "Different regions have different climates."
      ),
      RootMeaningData(
        root = "ظ-ل-ل",
        primaryMeaning = "shade, shadow",
        extendedMeaning = "Shade, shadow.",
        quranUsage = "'We shaded (zallalna) you with clouds.' Zill is shade. Zilal are shadows.",
        notes = "Allah shaded the Israelites with clouds in the desert."
      ),
      RootMeaningData(
        root = "ف-ي-ء",
        primaryMeaning = "shade, return, booty",
        extendedMeaning = "Afternoon shade (returning), and war booty.",
        quranUsage = "'Their shadows (afya') incline.' Fay' is shade that shifts. Fay' is also booty.",
        notes = "Shadows return (shift) as the sun moves."
      ),

      // === MISCELLANEOUS IMPORTANT ROOTS ===
      RootMeaningData(
        root = "ك-و-ن",
        primaryMeaning = "to be, exist, universe",
        extendedMeaning = "Being, existence, and the universe.",
        quranUsage = "'Be (kun) and it is.' Kawn is existence. Takwin is creation.",
        notes = "Allah says 'Be' and it is - instant creation."
      ),
      RootMeaningData(
        root = "و-ج-د",
        primaryMeaning = "existence, to find",
        extendedMeaning = "Existence, finding, and being.",
        quranUsage = "'He found (wajada) you lost.' Wujud is existence.",
        notes = "Allah's existence is necessary; all else is contingent."
      ),
      RootMeaningData(
        root = "ع-د-م",
        primaryMeaning = "nonexistence, lack",
        extendedMeaning = "Nonexistence, nothingness.",
        quranUsage = "'Adam is nonexistence. Before creation, there was nothing.",
        notes = "Allah creates from nonexistence."
      ),
      RootMeaningData(
        root = "ح-د-ث",
        primaryMeaning = "to happen, new, speak",
        extendedMeaning = "Happening, being new, and speaking.",
        quranUsage = "'Perhaps Allah will bring about (yuhdith) a matter.' Hadith is new/speech.",
        notes = "Hadith refers to the Prophet's sayings (new guidance)."
      ),
      RootMeaningData(
        root = "ق-د-م",
        primaryMeaning = "ancient, foot, advance",
        extendedMeaning = "Being ancient, foot, and advancing.",
        quranUsage = "'Foot (qadam) of truth.' Qadim is ancient. Qadam is foot.",
        notes = "Allah is Al-Qadim - eternal without beginning."
      ),
      RootMeaningData(
        root = "ج-د-د",
        primaryMeaning = "new, renew, serious",
        extendedMeaning = "Being new, renewing, and being serious.",
        quranUsage = "'A new (jadid) creation.' Jadid is new. Tajdid is renewal.",
        notes = "Allah can create anew - resurrection is a new creation."
      ),
      RootMeaningData(
        root = "ب-ل-ي",
        primaryMeaning = "old, worn, test",
        extendedMeaning = "Being old/worn, and testing.",
        quranUsage = "'When we are worn bones (baliy)?' Bala' is test. Baliy is worn.",
        notes = "Life is a test (bala') that wears us down."
      ),
      RootMeaningData(
        root = "غ-ي-ر",
        primaryMeaning = "other, change",
        extendedMeaning = "Other, different, and change.",
        quranUsage = "'Other (ghayr) than Allah.' Ghayr is other. Taghyir is change.",
        notes = "Everything other than Allah is creation."
      ),
      RootMeaningData(
        root = "م-ث-ل",
        primaryMeaning = "likeness, example, similar",
        extendedMeaning = "Likeness, example, and similarity.",
        quranUsage = "'Nothing is like (mithl) Him.' Mithl is likeness. Mathal is example.",
        notes = "Nothing resembles Allah - He is unique."
      ),
      RootMeaningData(
        root = "ش-ب-ه",
        primaryMeaning = "resemblance, similar",
        extendedMeaning = "Resemblance, similarity.",
        quranUsage = "'It was made to appear (shubbiha) to them.' Shubhah is doubt/resemblance.",
        notes = "Isa was not killed - it was made to appear so."
      ),
      RootMeaningData(
        root = "ف-ر-د",
        primaryMeaning = "single, alone, unique",
        extendedMeaning = "Being single, alone, and unique.",
        quranUsage = "'You have come to Us alone (furada).' Fard is single. Munfarid is alone.",
        notes = "Each person will come alone on Judgment Day."
      ),
      RootMeaningData(
        root = "ز-و-ج",
        primaryMeaning = "pair, spouse, type",
        extendedMeaning = "Pair, spouse, and type/kind.",
        quranUsage = "'We created you in pairs (azwaj).' Zawj is pair/spouse.",
        notes = "Creation in pairs is a universal sign."
      ),
      RootMeaningData(
        root = "ش-ف-ع",
        primaryMeaning = "intercession, even number, pair",
        extendedMeaning = "Intercession, and being paired/even.",
        quranUsage = "'Who can intercede (yashfa') except by His permission?' Shafa'ah is intercession.",
        notes = "Intercession requires Allah's permission."
      ),
      RootMeaningData(
        root = "و-ت-ر",
        primaryMeaning = "odd, single, string",
        extendedMeaning = "Being odd (number), single, and bowstring.",
        quranUsage = "'By the odd (watr) and the even.' Witr is odd. Watar is string.",
        notes = "Witr prayer has an odd number of units."
      ),
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "gather, collect, combine",
        extendedMeaning = "Gathering, collecting, and combining.",
        quranUsage = "'When people are gathered (jumi'a).' Jam' is gathering. Majmu' is total.",
        notes = "Yawm al-Jam' - the Day of Gathering."
      ),
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "separate, divide, distinguish",
        extendedMeaning = "Separating, dividing, and distinguishing.",
        quranUsage = "'Those who divide (farraqu) their religion.' Tafriq is division.",
        notes = "Dividing religion into sects is condemned."
      ),
      RootMeaningData(
        root = "ص-ل-ي",
        primaryMeaning = "burn, roast, enter fire",
        extendedMeaning = "Burning, entering fire.",
        quranUsage = "'They will burn (yaslaw) in the Fire.' Salla is to burn. Saly is roasting.",
        notes = "Different from ص-ل-و (prayer) - this relates to fire."
      ),
      RootMeaningData(
        root = "ح-ر-ق",
        primaryMeaning = "burn, fire",
        extendedMeaning = "Burning, setting fire.",
        quranUsage = "'We said: Burn (harriqu) him!' Harq is burning. Hariq is fire.",
        notes = "They tried to burn Ibrahim but fire became cool."
      ),
      RootMeaningData(
        root = "ط-ف-ء",
        primaryMeaning = "extinguish",
        extendedMeaning = "Extinguishing fire.",
        quranUsage = "'They want to extinguish (yutfi'u) Allah's light.' Itfa' is extinguishing.",
        notes = "Allah's light cannot be extinguished."
      ),
      RootMeaningData(
        root = "ن-و-ر",
        primaryMeaning = "light, illuminate",
        extendedMeaning = "Light, illumination, and enlightenment.",
        quranUsage = "'Allah is the Light (Nur) of the heavens and earth.' Nur is light.",
        notes = "Surah An-Nur contains the famous Light Verse."
      ),
      RootMeaningData(
        root = "ض-و-ء",
        primaryMeaning = "light, brightness",
        extendedMeaning = "Light, radiance, brightness.",
        quranUsage = "'The sun with brightness (diya').' Daw' is light. Diya' is radiance.",
        notes = "The sun emits diya' (radiance), moon reflects nur (light)."
      ),
      RootMeaningData(
        root = "ظ-ل-م",
        primaryMeaning = "darkness, wrong, oppress",
        extendedMeaning = "Darkness, wrongdoing, and oppression.",
        quranUsage = "'Darkness (zulumat) upon darkness.' Zulm is wrong. Zulmah is darkness.",
        notes = "Wrongdoing is spiritual darkness."
      ),
      RootMeaningData(
        root = "س-و-د",
        primaryMeaning = "black, master",
        extendedMeaning = "Black color, and being a master.",
        quranUsage = "'Faces will turn black (taswaddu).' Aswad is black. Sayyid is master.",
        notes = "Faces blacken from shame and grief."
      ),
      RootMeaningData(
        root = "ب-ي-ض",
        primaryMeaning = "white, bright",
        extendedMeaning = "White, brightness.",
        quranUsage = "'Faces will turn white (tabyaddu).' Abyad is white. Bayad is whiteness.",
        notes = "Faces brighten from joy and blessing."
      ),

      // === CRAFTS AND MANUFACTURING ===
      RootMeaningData(
        root = "ن-س-ج",
        primaryMeaning = "weave, fabric",
        extendedMeaning = "Weaving, fabric, textile.",
        quranUsage = "Nasj is weaving. Nasij is woven fabric.",
        notes = "Weaving is an ancient craft creating intricate patterns."
      ),
      RootMeaningData(
        root = "غ-ز-ل",
        primaryMeaning = "spin, thread",
        extendedMeaning = "Spinning thread, courtship.",
        quranUsage = "'Like her who unravels her spinning (ghazl).' Ghazl is spinning.",
        notes = "Breaking oaths is like unraveling spun thread."
      ),
      RootMeaningData(
        root = "خ-ي-ط",
        primaryMeaning = "thread, sew",
        extendedMeaning = "Thread, sewing, stitching.",
        quranUsage = "'Until the white thread (khayt) is distinct from the black.' Khayt is thread.",
        notes = "Dawn is when white and black threads of light are distinguishable."
      ),
      RootMeaningData(
        root = "ح-ب-ك",
        primaryMeaning = "weave tightly, firm",
        extendedMeaning = "Tight weaving, firmness.",
        quranUsage = "'The sky with its tight weaving (hubuk).' Hubuk is tight weaving.",
        notes = "The sky is described as tightly woven/constructed."
      ),
      RootMeaningData(
        root = "ص-ب-غ",
        primaryMeaning = "dye, color, baptism",
        extendedMeaning = "Dyeing, coloring, and baptism.",
        quranUsage = "'The coloring (sibghah) of Allah.' Sibghah is dye/coloring.",
        notes = "Islam is Allah's sibghah - His coloring of the soul."
      ),
      RootMeaningData(
        root = "د-ب-غ",
        primaryMeaning = "tan leather",
        extendedMeaning = "Tanning hides, leather working.",
        quranUsage = "Dabgh is tanning. Dabbagh is tanner.",
        notes = "Leather tanning transforms raw hides."
      ),
      RootMeaningData(
        root = "ح-د-د",
        primaryMeaning = "blacksmith, iron, sharpen",
        extendedMeaning = "Working with iron, sharpening, defining limits.",
        quranUsage = "'We sent down iron (hadid).' Haddad is blacksmith.",
        notes = "Blacksmiths shape iron with fire and hammer."
      ),
      RootMeaningData(
        root = "ن-ج-ر",
        primaryMeaning = "carpentry, wood",
        extendedMeaning = "Carpentry, woodworking.",
        quranUsage = "Najjar is carpenter. Najjarah is carpentry.",
        notes = "Carpenters craft useful items from wood."
      ),
      RootMeaningData(
        root = "ب-ن-ي",
        primaryMeaning = "build, construct, mason",
        extendedMeaning = "Building, construction, masonry.",
        quranUsage = "'We built (banayna) above you seven.' Banna' is builder.",
        notes = "Building requires skill and planning."
      ),
      RootMeaningData(
        root = "ف-خ-ر",
        primaryMeaning = "pottery, pride, boast",
        extendedMeaning = "Pottery, ceramics, and pride.",
        quranUsage = "'From clay like pottery (fakhkhar).' Fakhkhar is pottery. Fakhr is pride.",
        notes = "Pottery is shaped from humble clay."
      ),
      RootMeaningData(
        root = "ص-ه-ر",
        primaryMeaning = "melt, smelt, in-law",
        extendedMeaning = "Melting metal, smelting, and marriage relations.",
        quranUsage = "'It will melt (yusharu) what is in their bellies.' Sahr is melting.",
        notes = "In-laws (ashar) are bonded like melted metals."
      ),
      RootMeaningData(
        root = "س-ب-ك",
        primaryMeaning = "cast metal, mold",
        extendedMeaning = "Casting metal, molding.",
        quranUsage = "Sabk is casting. Masbuk is cast/molded.",
        notes = "Molten metal is cast into shapes."
      ),
      RootMeaningData(
        root = "ط-ر-ق",
        primaryMeaning = "hammer, knock, path",
        extendedMeaning = "Hammering, knocking, and path/way.",
        quranUsage = "'By the sky and the night-comer (tariq).' Tariq is knocker/night-comer.",
        notes = "The tariq (star) knocks at night's door."
      ),

      // === COOKING AND FOOD PREPARATION ===
      RootMeaningData(
        root = "ط-ب-خ",
        primaryMeaning = "cook",
        extendedMeaning = "Cooking, preparing food.",
        quranUsage = "Tabkh is cooking. Tabbakh is cook.",
        notes = "Cooking transforms raw ingredients."
      ),
      RootMeaningData(
        root = "ش-و-ي",
        primaryMeaning = "roast, grill",
        extendedMeaning = "Roasting, grilling.",
        quranUsage = "'A roasted (hanidh) calf.' Shawy is roasting.",
        notes = "Ibrahim's guests were served roasted calf."
      ),
      RootMeaningData(
        root = "غ-ل-ي",
        primaryMeaning = "boil",
        extendedMeaning = "Boiling.",
        quranUsage = "'It will boil (taghli) in the bellies.' Ghalayan is boiling.",
        notes = "Hell's drink boils in the stomachs."
      ),
      RootMeaningData(
        root = "ح-م-ي",
        primaryMeaning = "heat, protect",
        extendedMeaning = "Heating, and protecting.",
        quranUsage = "'It will be heated (yuhma) in the fire of Hell.' Hamy is heating. Himayah is protection.",
        notes = "Gold and silver hoarded will be heated and used to brand."
      ),
      RootMeaningData(
        root = "ب-ر-د",
        primaryMeaning = "cool, cold",
        extendedMeaning = "Cooling, coldness.",
        quranUsage = "'Be coolness (bard) and safety.' Barid is cold.",
        notes = "Fire became cool for Ibrahim."
      ),
      RootMeaningData(
        root = "ع-ج-ن",
        primaryMeaning = "knead dough",
        extendedMeaning = "Kneading dough.",
        quranUsage = "'Ajin is dough. 'Ajn is kneading.",
        notes = "Bread-making begins with kneading dough."
      ),
      RootMeaningData(
        root = "خ-ب-ز",
        primaryMeaning = "bread, bake",
        extendedMeaning = "Bread, baking.",
        quranUsage = "'I saw myself carrying bread (khubz).' Khubz is bread.",
        notes = "The king's baker dreamed of carrying bread."
      ),
      RootMeaningData(
        root = "ط-ح-ن",
        primaryMeaning = "grind, mill",
        extendedMeaning = "Grinding grain, milling.",
        quranUsage = "Tahn is grinding. Tahhan is miller.",
        notes = "Grain is ground into flour for bread."
      ),
      RootMeaningData(
        root = "ذ-ب-ح",
        primaryMeaning = "slaughter, sacrifice",
        extendedMeaning = "Ritual slaughter, sacrifice.",
        quranUsage = "'A great sacrifice (dhibh).' Dhabh is slaughter. Dhabiha is sacrifice.",
        notes = "Ibrahim was tested to sacrifice his son."
      ),
      RootMeaningData(
        root = "ن-ح-ر",
        primaryMeaning = "sacrifice camel, throat",
        extendedMeaning = "Slaughtering camels, the throat area.",
        quranUsage = "'Pray and sacrifice (wanhar).' Nahr is sacrificing camels.",
        notes = "Camels are sacrificed by cutting the throat area."
      ),
      RootMeaningData(
        root = "س-ق-ي",
        primaryMeaning = "give drink, irrigate",
        extendedMeaning = "Giving drink, watering, irrigation.",
        quranUsage = "'We gave you drink (asqaynakum).' Saqy is giving drink. Saqi is cupbearer.",
        notes = "Allah provides drink from clouds and springs."
      ),
      RootMeaningData(
        root = "ع-ص-ر",
        primaryMeaning = "press, squeeze, juice",
        extendedMeaning = "Pressing, squeezing for juice.",
        quranUsage = "'I saw myself pressing (a'siru) wine.' 'Asir is pressing.",
        notes = "The king's cupbearer dreamed of pressing grapes."
      ),
      RootMeaningData(
        root = "م-ز-ج",
        primaryMeaning = "mix, blend",
        extendedMeaning = "Mixing drinks, blending.",
        quranUsage = "'A cup mixed (mizaj) with ginger.' Mazj is mixing.",
        notes = "Paradise drinks are mixed with special flavors."
      ),

      // === HUNTING AND GATHERING ===
      RootMeaningData(
        root = "ص-ي-د",
        primaryMeaning = "hunt, game",
        extendedMeaning = "Hunting, game animals.",
        quranUsage = "'When you have left ihram, then hunt (istadu).' Sayd is hunting/game.",
        notes = "Hunting is forbidden during ihram pilgrimage state."
      ),
      RootMeaningData(
        root = "ق-ن-ص",
        primaryMeaning = "hunt, catch",
        extendedMeaning = "Hunting, catching prey.",
        quranUsage = "Qanas is hunting. Qannas is hunter.",
        notes = "Skilled hunters catch elusive prey."
      ),
      RootMeaningData(
        root = "ف-خ-خ",
        primaryMeaning = "trap, snare",
        extendedMeaning = "Trap, snare for catching.",
        quranUsage = "Fakhkh is trap. Setting traps for game.",
        notes = "Traps catch unwary prey."
      ),
      RootMeaningData(
        root = "ش-ب-ك",
        primaryMeaning = "net, network, interlock",
        extendedMeaning = "Net, network, interlocking.",
        quranUsage = "Shabakah is net. Tashabbuk is interlocking.",
        notes = "Nets catch fish and birds."
      ),
      RootMeaningData(
        root = "ج-ن-ي",
        primaryMeaning = "harvest, pick fruit",
        extendedMeaning = "Harvesting, picking fruits.",
        quranUsage = "'Its harvest (janaha) is near.' Jany is picking fruit.",
        notes = "Paradise fruits are easy to pick."
      ),
      RootMeaningData(
        root = "ح-ص-د",
        primaryMeaning = "harvest, reap",
        extendedMeaning = "Harvesting crops, reaping.",
        quranUsage = "'Except a little which you will store (tahsudun).' Hasad is harvest.",
        notes = "Seven years of harvest stored for famine."
      ),
      RootMeaningData(
        root = "د-ر-س",
        primaryMeaning = "thresh, study, efface",
        extendedMeaning = "Threshing grain, studying, and effacing.",
        quranUsage = "'That you may study (tadrusu) it.' Dars is lesson. Idris relates to study.",
        notes = "Threshing separates grain from chaff; studying separates knowledge."
      ),
      RootMeaningData(
        root = "ذ-ر-ي",
        primaryMeaning = "winnow, scatter",
        extendedMeaning = "Winnowing grain, scattering.",
        quranUsage = "'Scattering (dhariyat) winds.' Dharw is winnowing.",
        notes = "Wind winnows and scatters."
      ),

      // === POSITIONS AND POSTURES ===
      RootMeaningData(
        root = "ق-ع-د",
        primaryMeaning = "sit, stay",
        extendedMeaning = "Sitting, staying, remaining behind.",
        quranUsage = "'Those who sat (qa'adun) behind.' Qu'ud is sitting. Maq'ad is seat.",
        notes = "Some stayed behind from battle without excuse."
      ),
      RootMeaningData(
        root = "ج-ل-س",
        primaryMeaning = "sit, session",
        extendedMeaning = "Sitting, gathering, session.",
        quranUsage = "'Make room in assemblies (majalis).' Julus is sitting. Majlis is assembly.",
        notes = "Make room for others in gatherings."
      ),
      RootMeaningData(
        root = "ض-ج-ع",
        primaryMeaning = "lie down, recline",
        extendedMeaning = "Lying down, reclining.",
        quranUsage = "'Their sides forsake their beds (madaji').' Daj' is lying down. Madja' is bed.",
        notes = "The righteous leave their beds for night prayer."
      ),
      RootMeaningData(
        root = "ن-و-م",
        primaryMeaning = "sleep",
        extendedMeaning = "Sleep, slumber.",
        quranUsage = "'Neither drowsiness nor sleep (nawm) overtakes Him.' Nawm is sleep.",
        notes = "Allah never sleeps - He is ever-watchful."
      ),
      RootMeaningData(
        root = "ر-ق-د",
        primaryMeaning = "sleep, lie down",
        extendedMeaning = "Sleeping, lying down.",
        quranUsage = "'Who has raised us from our sleeping place (marqad)?' Ruqud is sleep.",
        notes = "The dead will be raised from their sleeping places."
      ),
      RootMeaningData(
        root = "س-ج-د",
        primaryMeaning = "prostrate",
        extendedMeaning = "Prostrating, bowing down in worship.",
        quranUsage = "'Prostrate (usjud) and draw near.' Sujud is prostration. Masjid is place of prostration.",
        notes = "Prostration is the closest a servant gets to Allah."
      ),
      RootMeaningData(
        root = "ر-ك-ع",
        primaryMeaning = "bow",
        extendedMeaning = "Bowing, especially in prayer.",
        quranUsage = "'Bow (irka'u) with those who bow.' Ruku' is bowing.",
        notes = "Bowing in prayer shows humility."
      ),
      RootMeaningData(
        root = "ق-و-م",
        primaryMeaning = "stand, rise, establish",
        extendedMeaning = "Standing, rising, and establishing.",
        quranUsage = "'Establish (aqim) the prayer.' Qiyam is standing. Qawm is people.",
        notes = "Standing in prayer and standing for justice."
      ),
      RootMeaningData(
        root = "ج-ث-و",
        primaryMeaning = "kneel, crouch",
        extendedMeaning = "Kneeling, crouching on knees.",
        quranUsage = "'Every nation kneeling (jathiyah).' Juthuuw is kneeling.",
        notes = "Surah Al-Jathiyah - nations kneeling before judgment."
      ),
      RootMeaningData(
        root = "ح-ب-و",
        primaryMeaning = "crawl, creep",
        extendedMeaning = "Crawling, creeping on belly.",
        quranUsage = "'Some crawl (yamshi) on their bellies.' Habw is crawling.",
        notes = "Some creatures crawl, some walk on two or four legs."
      ),
      RootMeaningData(
        root = "ز-ح-ف",
        primaryMeaning = "crawl, advance slowly",
        extendedMeaning = "Crawling, advancing slowly like an army.",
        quranUsage = "'When you meet those who disbelieve advancing (zahfan).' Zahf is crawling advance.",
        notes = "Armies advance slowly like crawling."
      ),

      // === AGRICULTURE EXPANDED ===
      RootMeaningData(
        root = "ح-ر-ث",
        primaryMeaning = "plow, till, cultivate",
        extendedMeaning = "Plowing, tilling, cultivation.",
        quranUsage = "'Whoever desires the harvest (harth) of the Hereafter.' Harth is tillage/harvest.",
        notes = "Life is a field we cultivate for the Hereafter."
      ),
      RootMeaningData(
        root = "ف-ل-ح",
        primaryMeaning = "succeed, cultivate, farmer",
        extendedMeaning = "Success, cultivation, and farming.",
        quranUsage = "'Successful (aflaha) are the believers.' Falah is success. Fallah is farmer.",
        notes = "The call to prayer says 'Come to success (falah).'"
      ),
      RootMeaningData(
        root = "س-ق-ي",
        primaryMeaning = "irrigate, water",
        extendedMeaning = "Irrigation, watering crops.",
        quranUsage = "'We water (nasqi) with it gardens.' Saqy is watering.",
        notes = "Rain waters the earth for cultivation."
      ),
      RootMeaningData(
        root = "غ-ر-س",
        primaryMeaning = "plant, implant",
        extendedMeaning = "Planting, implanting.",
        quranUsage = "Ghars is planting. Ghirasah is cultivation.",
        notes = "Planting seeds is an act of faith in future harvest."
      ),
      RootMeaningData(
        root = "ب-ذ-ر",
        primaryMeaning = "seed, scatter, waste",
        extendedMeaning = "Seeds, scattering seed, and wasting.",
        quranUsage = "'Do not waste (tubaddhir) wastefully.' Badhr is seed. Tabdhir is wasting.",
        notes = "Don't scatter resources wastefully."
      ),
      RootMeaningData(
        root = "ن-ب-ت",
        primaryMeaning = "sprout, grow, plant",
        extendedMeaning = "Sprouting, growing, vegetation.",
        quranUsage = "'We cause to grow (nunbitu) gardens.' Nabat is plant. Nabt is growth.",
        notes = "Allah causes plants to sprout from the earth."
      ),
      RootMeaningData(
        root = "ي-ن-ع",
        primaryMeaning = "ripen",
        extendedMeaning = "Ripening of fruit.",
        quranUsage = "'Look at its fruit when it ripens (yani').' Yan' is ripening.",
        notes = "Fruit ripening is a sign of Allah's power."
      ),
      RootMeaningData(
        root = "ق-ط-ف",
        primaryMeaning = "pick, pluck",
        extendedMeaning = "Picking fruit, plucking.",
        quranUsage = "'Its clusters (qutuf) hanging low.' Qatf is picking. Qutuf are clusters.",
        notes = "Paradise fruit hangs low for easy picking."
      ),
      RootMeaningData(
        root = "ج-د-د",
        primaryMeaning = "cut, new, serious",
        extendedMeaning = "Cutting (harvest), being new, seriousness.",
        quranUsage = "'Paths of different colors (judad).' Jadid is new. Jadd is seriousness.",
        notes = "Harvesting cuts the old for new growth."
      ),
      RootMeaningData(
        root = "ذ-ر-ع",
        primaryMeaning = "sow, plant, arm",
        extendedMeaning = "Sowing seeds, planting.",
        quranUsage = "'Is it you who sow (tazra'un) it?' Zar' is sowing/crops.",
        notes = "Humans sow but Allah causes growth."
      ),

      // === EMOTIONS EXPANDED ===
      RootMeaningData(
        root = "ر-ه-ب",
        primaryMeaning = "fear, awe, monk",
        extendedMeaning = "Fear, awe, and monasticism.",
        quranUsage = "'They feared (yarhabun) none but Allah.' Rahbah is fear. Rahib is monk.",
        notes = "Monks (ruhban) dedicate themselves from fear of Allah."
      ),
      RootMeaningData(
        root = "و-ج-ل",
        primaryMeaning = "fear, tremble",
        extendedMeaning = "Fear that causes trembling.",
        quranUsage = "'Their hearts tremble (wajilat) when Allah is mentioned.' Wajal is trembling fear.",
        notes = "Hearts of believers tremble at Allah's mention."
      ),
      RootMeaningData(
        root = "ف-ز-ع",
        primaryMeaning = "terror, fright",
        extendedMeaning = "Sudden terror, fright.",
        quranUsage = "'The greatest terror (faza') will not grieve them.' Faza' is terror.",
        notes = "Believers are safe from the terror of Judgment Day."
      ),
      RootMeaningData(
        root = "ر-ع-ب",
        primaryMeaning = "terror, frighten",
        extendedMeaning = "Terror, frightening.",
        quranUsage = "'We will cast terror (ru'b) into their hearts.' Ru'b is terror.",
        notes = "Allah cast terror into enemy hearts at Badr."
      ),
      RootMeaningData(
        root = "ه-ل-ع",
        primaryMeaning = "anxiety, impatience",
        extendedMeaning = "Anxiety, impatience, fretfulness.",
        quranUsage = "'Man was created anxious (halu'an).' Hala' is anxiety.",
        notes = "Humans are naturally anxious except those who pray."
      ),
      RootMeaningData(
        root = "ج-ز-ع",
        primaryMeaning = "impatience, distress",
        extendedMeaning = "Impatience, distress, anguish.",
        quranUsage = "'When evil touches him, he is impatient (jazu'an).' Jaza' is impatience.",
        notes = "Impatience is the opposite of sabr (patience)."
      ),
      RootMeaningData(
        root = "ض-ج-ر",
        primaryMeaning = "boredom, annoyance",
        extendedMeaning = "Boredom, being annoyed.",
        quranUsage = "Dajar is boredom. Patience prevents boredom in worship.",
        notes = "The Prophet never showed boredom in worship."
      ),
      RootMeaningData(
        root = "س-ء-م",
        primaryMeaning = "weariness, boredom",
        extendedMeaning = "Weariness, getting tired of something.",
        quranUsage = "'Man does not weary (yas'am) of asking for good.' Sa'amah is weariness.",
        notes = "Humans never tire of asking for good things."
      ),
      RootMeaningData(
        root = "ط-م-ء-ن",
        primaryMeaning = "tranquility, reassure",
        extendedMeaning = "Tranquility, being reassured, peace of heart.",
        quranUsage = "'Hearts find rest (tatma'innu) in remembrance of Allah.' Tuma'ninah is tranquility.",
        notes = "True peace comes from remembering Allah."
      ),
      RootMeaningData(
        root = "س-ك-ن",
        primaryMeaning = "dwell, calm, tranquil",
        extendedMeaning = "Dwelling, being calm, tranquility.",
        quranUsage = "'He sent down tranquility (sakinah).' Sukun is calm. Sakinah is tranquility.",
        notes = "Allah sends sakinah (divine tranquility) to believers."
      ),
      RootMeaningData(
        root = "أ-ن-س",
        primaryMeaning = "intimacy, friendliness",
        extendedMeaning = "Intimacy, being sociable, comfort.",
        quranUsage = "'Perhaps I will bring you a brand from it (for warmth/guidance).' Uns is intimacy.",
        notes = "Humans (ins) are social and seek companionship."
      ),
      RootMeaningData(
        root = "و-ح-ش",
        primaryMeaning = "loneliness, wild",
        extendedMeaning = "Loneliness, wildness, being untamed.",
        quranUsage = "'Wild beasts (wuhush) are gathered.' Wahsh is wild beast. Wahshah is loneliness.",
        notes = "Wild animals will be gathered on Judgment Day."
      ),
      RootMeaningData(
        root = "غ-ي-ظ",
        primaryMeaning = "rage, fury, anger",
        extendedMeaning = "Intense anger, fury, rage.",
        quranUsage = "'Those who restrain their anger (ghayz).' Ghayz is rage.",
        notes = "Controlling rage is praiseworthy."
      ),
      RootMeaningData(
        root = "ح-ن-ق",
        primaryMeaning = "intense anger, spite",
        extendedMeaning = "Intense anger, spite, resentment.",
        quranUsage = "Hanaq is intense anger. Hanaqa is to enrage.",
        notes = "Spite and resentment poison the heart."
      ),
      RootMeaningData(
        root = "س-خ-ر",
        primaryMeaning = "mock, ridicule, subjugate",
        extendedMeaning = "Mocking, ridiculing, and subjugating.",
        quranUsage = "'Do not mock (taskhar) a people.' Sukhriyah is mockery. Taskhir is subjugation.",
        notes = "Mocking others is forbidden. Allah subjugates creation for humans."
      ),
      RootMeaningData(
        root = "ه-ز-ء",
        primaryMeaning = "mock, jest",
        extendedMeaning = "Mocking, jesting, making fun.",
        quranUsage = "'They took them in mockery (huzuwan).' Istihza' is mockery.",
        notes = "Taking Allah's signs as mockery is disbelief."
      ),
      RootMeaningData(
        root = "ع-ج-ب",
        primaryMeaning = "wonder, amazement",
        extendedMeaning = "Wonder, amazement, self-admiration.",
        quranUsage = "'Do you wonder ('ajibta)?' 'Ajab is wonder. 'Ujb is self-admiration.",
        notes = "Wonder at Allah's signs is good; self-admiration is dangerous."
      ),
      RootMeaningData(
        root = "د-ه-ش",
        primaryMeaning = "astonishment, bewilderment",
        extendedMeaning = "Astonishment, being bewildered.",
        quranUsage = "Dahshah is astonishment. Madhush is astonished.",
        notes = "The Day of Judgment will leave people astonished."
      ),
      RootMeaningData(
        root = "ح-ي-ر",
        primaryMeaning = "confusion, bewilderment",
        extendedMeaning = "Confusion, being bewildered, perplexed.",
        quranUsage = "'Confused (hayran) in the earth.' Hayrah is confusion. Ha'ir is confused.",
        notes = "Without guidance, people wander confused."
      ),

      // === THINKING AND COGNITION ===
      RootMeaningData(
        root = "ف-ك-ر",
        primaryMeaning = "think, reflect, ponder",
        extendedMeaning = "Thinking, reflecting, pondering.",
        quranUsage = "'Do they not reflect (yatafakkarun)?' Fikr is thought. Tafakkur is reflection.",
        notes = "Reflection on creation strengthens faith."
      ),
      RootMeaningData(
        root = "ت-د-ب-ر",
        primaryMeaning = "ponder, reflect deeply",
        extendedMeaning = "Deep reflection, contemplating consequences.",
        quranUsage = "'Do they not ponder (yatadabbarun) the Quran?' Tadabbur is deep reflection.",
        notes = "Tadabbur is reflecting on the Quran's meanings."
      ),
      RootMeaningData(
        root = "ت-أ-م-ل",
        primaryMeaning = "contemplate, meditate",
        extendedMeaning = "Contemplation, meditation, hoping.",
        quranUsage = "Ta'ammul is contemplation. Amma is to hope.",
        notes = "Contemplation of creation reveals Allah's wisdom."
      ),
      RootMeaningData(
        root = "ن-ظ-ر",
        primaryMeaning = "look, see, consider",
        extendedMeaning = "Looking, seeing, and considering.",
        quranUsage = "'Do they not look (yanzurun) at the camels?' Nazar is looking. Nazariyyah is theory.",
        notes = "Looking at creation leads to understanding."
      ),
      RootMeaningData(
        root = "ب-ص-ر",
        primaryMeaning = "see, perceive, insight",
        extendedMeaning = "Seeing, perception, and insight.",
        quranUsage = "'I call to Allah with insight (basirah).' Basar is sight. Basirah is insight.",
        notes = "Physical sight leads to spiritual insight."
      ),
      RootMeaningData(
        root = "ر-أ-ي",
        primaryMeaning = "see, opinion, view",
        extendedMeaning = "Seeing, opinion, and viewpoint.",
        quranUsage = "'Have you seen (ara'ayta) he who denies?' Ra'y is opinion. Ru'yah is vision.",
        notes = "The Prophet's visions were true revelations."
      ),
      RootMeaningData(
        root = "ع-ر-ف",
        primaryMeaning = "know, recognize, custom",
        extendedMeaning = "Knowing, recognizing, and custom.",
        quranUsage = "'They recognize (ya'rifunahu) it as their sons.' Ma'rifah is knowledge. 'Urf is custom.",
        notes = "People of the Book recognized the Prophet as they recognized their own sons."
      ),
      RootMeaningData(
        root = "ج-ه-ل",
        primaryMeaning = "ignorance, not know",
        extendedMeaning = "Ignorance, not knowing.",
        quranUsage = "'Is it the judgment of ignorance (jahiliyyah) they seek?' Jahl is ignorance.",
        notes = "The pre-Islamic era was the Age of Ignorance."
      ),
      RootMeaningData(
        root = "ن-س-ي",
        primaryMeaning = "forget",
        extendedMeaning = "Forgetting, neglecting.",
        quranUsage = "'They forgot (nasu) Allah, so He forgot them.' Nisyan is forgetting.",
        notes = "Forgetting Allah leads to being forgotten."
      ),
      RootMeaningData(
        root = "ذ-ك-ر",
        primaryMeaning = "remember, mention, male",
        extendedMeaning = "Remembering, mentioning, and male gender.",
        quranUsage = "'Remember (udhkur) Me, I will remember you.' Dhikr is remembrance.",
        notes = "Dhikr brings peace to the heart."
      ),
      RootMeaningData(
        root = "ح-ف-ظ",
        primaryMeaning = "memorize, preserve, guard",
        extendedMeaning = "Memorizing, preserving, and guarding.",
        quranUsage = "'Indeed, We will preserve (hafizun) it.' Hifz is memorization. Hafiz is guardian.",
        notes = "Allah preserves the Quran from corruption."
      ),
      RootMeaningData(
        root = "ف-ه-م",
        primaryMeaning = "understand, comprehend",
        extendedMeaning = "Understanding, comprehension.",
        quranUsage = "'We gave Sulayman understanding (fahhamna).' Fahm is understanding.",
        notes = "Sulayman was given special understanding of a case."
      ),
      RootMeaningData(
        root = "د-ر-ي",
        primaryMeaning = "know, be aware",
        extendedMeaning = "Knowing, being aware.",
        quranUsage = "'What will make you know (yudrika)?' Dirayah is knowledge. Idrak is perception.",
        notes = "Full perception of some matters is beyond humans."
      ),
      RootMeaningData(
        root = "ش-ع-ر",
        primaryMeaning = "feel, perceive, poetry",
        extendedMeaning = "Feeling, perceiving, and poetry.",
        quranUsage = "'If only they perceived (yash'urun)!' Shu'ur is feeling. Shi'r is poetry.",
        notes = "Poets feel deeply and express through verse."
      ),
      RootMeaningData(
        root = "ح-د-س",
        primaryMeaning = "guess, intuition",
        extendedMeaning = "Guessing, intuition.",
        quranUsage = "Hads is intuition. Hudus is guessing.",
        notes = "Intuition can guide but must be verified."
      ),

      // === KINSHIP EXPANDED ===
      RootMeaningData(
        root = "ن-س-ب",
        primaryMeaning = "lineage, genealogy, relate",
        extendedMeaning = "Lineage, genealogy, and relating to.",
        quranUsage = "'They made between Him and the jinn a lineage (nasab).' Nasab is lineage.",
        notes = "False claims of lineage between Allah and jinn are rejected."
      ),
      RootMeaningData(
        root = "س-ل-ل",
        primaryMeaning = "lineage, extract, draw out",
        extendedMeaning = "Lineage (drawn from ancestors), extracting.",
        quranUsage = "'From an extract (sulalah) of clay.' Sulalah is extract/lineage.",
        notes = "Humans are created from an extract of clay."
      ),
      RootMeaningData(
        root = "ذ-ر-ر",
        primaryMeaning = "offspring, descendants, atoms",
        extendedMeaning = "Offspring, descendants, and tiny particles.",
        quranUsage = "'Their offspring (dhurriyyah) after them.' Dhurriyyah is offspring. Dharrah is atom.",
        notes = "Our offspring continue our legacy."
      ),
      RootMeaningData(
        root = "ع-ق-ب",
        primaryMeaning = "descendants, consequence, heel",
        extendedMeaning = "Descendants, consequence, and heel.",
        quranUsage = "'In his descendants ('aqibihi).' 'Aqib is descendant. 'Uqba is consequence.",
        notes = "Ibrahim's message continued in his descendants."
      ),
      RootMeaningData(
        root = "س-ل-ف",
        primaryMeaning = "ancestors, predecessors",
        extendedMeaning = "Ancestors, predecessors, what has passed.",
        quranUsage = "'What has passed (salafa) is forgiven.' Salaf are predecessors.",
        notes = "The righteous Salaf are the early generations of Muslims."
      ),
      RootMeaningData(
        root = "خ-ل-ف",
        primaryMeaning = "succeed, come after",
        extendedMeaning = "Succeeding, coming after, being different.",
        quranUsage = "'They succeeded (khalafa) them.' Khalaf are successors. Khalifah is vicegerent.",
        notes = "Each generation succeeds the previous."
      ),
      RootMeaningData(
        root = "و-ر-ث",
        primaryMeaning = "inherit",
        extendedMeaning = "Inheriting, heritage.",
        quranUsage = "'Allah is the Inheritor (Warith).' Irth is inheritance. Mirath is heritage.",
        notes = "Allah inherits the earth after all perish."
      ),
      RootMeaningData(
        root = "ت-ر-ك",
        primaryMeaning = "leave, abandon, bequeath",
        extendedMeaning = "Leaving behind, abandoning, bequeathing.",
        quranUsage = "'What your parents left (taraka).' Tarikah is estate.",
        notes = "Inheritance is what the deceased leaves behind."
      ),
      RootMeaningData(
        root = "ر-ح-م",
        primaryMeaning = "womb, mercy, kinship",
        extendedMeaning = "Womb, mercy, and blood relations.",
        quranUsage = "'Fear Allah and the wombs (arham).' Rahim is womb. Rahmah is mercy.",
        notes = "The womb (rahim) is the source of kinship (rahm) and mercy (rahmah)."
      ),
      RootMeaningData(
        root = "ص-ه-ر",
        primaryMeaning = "in-law, melt",
        extendedMeaning = "Marriage relations, in-laws.",
        quranUsage = "'He made relations by blood and marriage (sihr).' Sihr is in-law relations.",
        notes = "Marriage creates bonds like blood relations."
      ),
      RootMeaningData(
        root = "ح-م-و",
        primaryMeaning = "father-in-law, protect",
        extendedMeaning = "Father-in-law, and protection.",
        quranUsage = "Hamu is father-in-law. Hama is protection.",
        notes = "In-laws have rights and responsibilities."
      ),
      RootMeaningData(
        root = "ك-ن-ن",
        primaryMeaning = "daughter-in-law, protect",
        extendedMeaning = "Daughter-in-law, protection, concealment.",
        quranUsage = "Kannah is daughter-in-law. Kanna is to protect/conceal.",
        notes = "Marriage creates new family bonds."
      ),
      RootMeaningData(
        root = "ر-ب-ب",
        primaryMeaning = "lord, raise, nurture",
        extendedMeaning = "Lord, raising, nurturing children.",
        quranUsage = "'My Lord (Rabbi) is Allah.' Rabb is Lord/Nurturer. Tarbiyah is upbringing.",
        notes = "Allah is Rabb - He raises and nurtures all creation."
      ),
      RootMeaningData(
        root = "ك-ف-ل",
        primaryMeaning = "sponsor, guardian, guarantee",
        extendedMeaning = "Sponsorship, guardianship, guaranteeing.",
        quranUsage = "'Zakariyya took charge (kaffalaha) of her.' Kafalah is sponsorship.",
        notes = "Zakariyya was Maryam's guardian."
      ),
      RootMeaningData(
        root = "ر-ض-ع",
        primaryMeaning = "breastfeed, suckle",
        extendedMeaning = "Breastfeeding, suckling.",
        quranUsage = "'Mothers breastfeed (yurdi'na) their children.' Rada'ah is breastfeeding.",
        notes = "Breastfeeding creates milk-kinship (rada'ah)."
      ),
      RootMeaningData(
        root = "ف-ط-م",
        primaryMeaning = "wean",
        extendedMeaning = "Weaning from breastfeeding.",
        quranUsage = "'Weaning (fisal) in two years.' Fitam is weaning.",
        notes = "Weaning completes after about two years."
      ),

      // === QUANTITIES AND MEASUREMENTS ===
      RootMeaningData(
        root = "ق-د-ر",
        primaryMeaning = "measure, power, decree",
        extendedMeaning = "Measuring, power, and divine decree.",
        quranUsage = "'In due measure (qadar).' Qadr is measure/power. Taqdir is decree.",
        notes = "Laylat al-Qadr - the Night of Decree/Power."
      ),
      RootMeaningData(
        root = "ح-د-د",
        primaryMeaning = "limit, boundary, define",
        extendedMeaning = "Limits, boundaries, defining.",
        quranUsage = "'These are the limits (hudud) of Allah.' Hadd is limit. Hudud are boundaries.",
        notes = "Allah's limits must not be transgressed."
      ),
      RootMeaningData(
        root = "ح-ص-ي",
        primaryMeaning = "count, enumerate",
        extendedMeaning = "Counting, enumerating.",
        quranUsage = "'We have enumerated (ahsaynahu) everything.' Ihsa' is enumeration.",
        notes = "Allah counts and records everything."
      ),
      RootMeaningData(
        root = "ع-د-د",
        primaryMeaning = "number, count, prepare",
        extendedMeaning = "Number, counting, and preparing.",
        quranUsage = "'A known number ('adad).' 'Adad is number. I'dad is preparation.",
        notes = "Everything is in precise numbers with Allah."
      ),
      RootMeaningData(
        root = "ك-ث-ر",
        primaryMeaning = "many, abundant",
        extendedMeaning = "Being many, abundance.",
        quranUsage = "'We have given you abundance (kawthar).' Kathrah is abundance.",
        notes = "Surah Al-Kawthar promises abundance."
      ),
      RootMeaningData(
        root = "ق-ل-ل",
        primaryMeaning = "few, little",
        extendedMeaning = "Being few, scarcity.",
        quranUsage = "'Except a few (qalil).' Qillah is scarcity.",
        notes = "Believers are often few among the masses."
      ),
      RootMeaningData(
        root = "ز-ي-د",
        primaryMeaning = "increase, add",
        extendedMeaning = "Increasing, adding more.",
        quranUsage = "'We increased (zidnahum) them in guidance.' Ziyadah is increase.",
        notes = "Allah increases guidance for the guided."
      ),
      RootMeaningData(
        root = "ن-ق-ص",
        primaryMeaning = "decrease, reduce, lack",
        extendedMeaning = "Decreasing, reducing.",
        quranUsage = "'We will reduce (nanqusu) it from its edges.' Naqs is decrease.",
        notes = "The earth is gradually reduced at its edges."
      ),
      RootMeaningData(
        root = "ك-م-ل",
        primaryMeaning = "complete, perfect",
        extendedMeaning = "Completion, perfection.",
        quranUsage = "'Today I have completed (akmaltu) your religion.' Kamal is perfection.",
        notes = "Islam was perfected and completed."
      ),
      RootMeaningData(
        root = "ت-م-م",
        primaryMeaning = "complete, finish",
        extendedMeaning = "Completing, finishing.",
        quranUsage = "'Complete (atimmu) the Hajj and Umrah.' Tamam is completion. Itmam is completing.",
        notes = "Worship should be completed properly."
      ),
      RootMeaningData(
        root = "ن-ص-ف",
        primaryMeaning = "half, justice",
        extendedMeaning = "Half, and giving justice (fairness).",
        quranUsage = "'Half (nisf) of what they left.' Nisf is half. Insaf is fairness.",
        notes = "Spouses inherit half in certain cases."
      ),
      RootMeaningData(
        root = "ث-ل-ث",
        primaryMeaning = "third, three",
        extendedMeaning = "One-third, three.",
        quranUsage = "'Two-thirds (thuluthayn) of what he left.' Thuluth is one-third.",
        notes = "Inheritance has specific fractions."
      ),
      RootMeaningData(
        root = "ر-ب-ع",
        primaryMeaning = "fourth, four, spring",
        extendedMeaning = "One-fourth, four, and springtime.",
        quranUsage = "'One-fourth (rubu').' Rubu' is quarter. Rabi' is spring.",
        notes = "Spouses inherit one-fourth in certain cases."
      ),
      RootMeaningData(
        root = "س-د-س",
        primaryMeaning = "sixth, six",
        extendedMeaning = "One-sixth, six.",
        quranUsage = "'One-sixth (sudus).' Sudus is one-sixth.",
        notes = "Parents inherit one-sixth when there are children."
      ),
      RootMeaningData(
        root = "ث-م-ن",
        primaryMeaning = "eighth, eight, price",
        extendedMeaning = "One-eighth, eight, and price.",
        quranUsage = "'One-eighth (thumun).' Thumun is one-eighth. Thaman is price.",
        notes = "Wives inherit one-eighth when there are children."
      ),
      RootMeaningData(
        root = "ض-ع-ف",
        primaryMeaning = "double, weak",
        extendedMeaning = "Doubling, and weakness.",
        quranUsage = "'Allah will multiply (yuda'if) it.' Di'f is double. Da'f is weakness.",
        notes = "Good deeds are multiplied many times."
      ),
      RootMeaningData(
        root = "م-ث-ل",
        primaryMeaning = "like, similar, example",
        extendedMeaning = "Likeness, similarity, and example.",
        quranUsage = "'The likeness (mathal) of those who spend.' Mithl is like. Mathal is parable.",
        notes = "The Quran uses parables (amthal) to explain."
      ),

      // === STATES OF MATTER AND CHANGE ===
      RootMeaningData(
        root = "ج-م-د",
        primaryMeaning = "solid, frozen, still",
        extendedMeaning = "Being solid, frozen, motionless.",
        quranUsage = "'You see the mountains thinking them solid (jamidah).' Jumud is solidity.",
        notes = "Mountains appear solid but will move on Judgment Day."
      ),
      RootMeaningData(
        root = "س-ي-ل",
        primaryMeaning = "flow, liquid",
        extendedMeaning = "Flowing, liquid state.",
        quranUsage = "'A flood (sayl) came upon them.' Sayl is flood/flow. Sayil is flowing.",
        notes = "The flood of 'Arim destroyed the dam."
      ),
      RootMeaningData(
        root = "ذ-و-ب",
        primaryMeaning = "melt, dissolve",
        extendedMeaning = "Melting, dissolving.",
        quranUsage = "'It will melt (yadhub).' Dhawban is melting.",
        notes = "Boiling water causes things to melt."
      ),
      RootMeaningData(
        root = "ب-خ-ر",
        primaryMeaning = "evaporate, steam",
        extendedMeaning = "Evaporation, steam.",
        quranUsage = "Bukhar is steam/vapor. Water evaporates and rises.",
        notes = "The water cycle involves evaporation."
      ),
      RootMeaningData(
        root = "ت-ص-ع-د",
        primaryMeaning = "ascend, rise up",
        extendedMeaning = "Ascending, rising up.",
        quranUsage = "'To Him ascends (yas'adu) the good word.' Su'ud is ascending.",
        notes = "Good words and deeds ascend to Allah."
      ),
      RootMeaningData(
        root = "ه-ب-ط",
        primaryMeaning = "descend, go down",
        extendedMeaning = "Descending, going down.",
        quranUsage = "'Descend (ihbitu) from it.' Hubut is descent.",
        notes = "Adam and Hawwa were told to descend from Paradise."
      ),
      RootMeaningData(
        root = "ت-غ-ي-ر",
        primaryMeaning = "change, alter",
        extendedMeaning = "Changing, altering.",
        quranUsage = "'Allah does not change (yughayyir) a people's condition.' Taghyir is change.",
        notes = "Allah doesn't change a people until they change themselves."
      ),
      RootMeaningData(
        root = "ث-ب-ت",
        primaryMeaning = "firm, stable, confirm",
        extendedMeaning = "Being firm, stable, and confirming.",
        quranUsage = "'Allah keeps firm (yuthabbit) those who believe.' Thabat is firmness.",
        notes = "Allah firms the believers with the firm word."
      ),
      RootMeaningData(
        root = "ز-ل-ل",
        primaryMeaning = "slip, slide, err",
        extendedMeaning = "Slipping, sliding, making errors.",
        quranUsage = "'Satan made them slip (azallahuma).' Zallah is slip/error.",
        notes = "Satan caused Adam and Hawwa to slip."
      ),
      RootMeaningData(
        root = "ح-ر-ك",
        primaryMeaning = "move, motion",
        extendedMeaning = "Movement, motion.",
        quranUsage = "'Do not move (tuharrik) your tongue hastily.' Harakah is movement.",
        notes = "The Prophet was told not to rush in reciting revelation."
      ),
      RootMeaningData(
        root = "س-ك-ن",
        primaryMeaning = "still, calm, dwell",
        extendedMeaning = "Being still, calm, dwelling.",
        quranUsage = "'The night to be still (sakanan).' Sukun is stillness.",
        notes = "Night is for rest and stillness."
      ),
      RootMeaningData(
        root = "د-و-ر",
        primaryMeaning = "rotate, turn, house",
        extendedMeaning = "Rotating, turning, and dwelling.",
        quranUsage = "'Their eyes rotating (tadur).' Dawr is rotation. Dar is house.",
        notes = "Eyes rotate from terror on Judgment Day."
      ),
      RootMeaningData(
        root = "ق-ل-ب",
        primaryMeaning = "turn, heart, overturn",
        extendedMeaning = "Turning over, heart (which turns), and overturning.",
        quranUsage = "'We will turn (nuqallib) their hearts.' Qalb is heart. Inqilab is revolution.",
        notes = "The heart turns between states; Allah turns hearts."
      ),

      // === MORE RELIGIOUS CONCEPTS ===
      RootMeaningData(
        root = "ع-ب-د",
        primaryMeaning = "worship, servant, slave",
        extendedMeaning = "Worship, servitude, and being a servant.",
        quranUsage = "'Worship (u'budu) Allah.' 'Ibadah is worship. 'Abd is servant.",
        notes = "True freedom is being a servant only of Allah."
      ),
      RootMeaningData(
        root = "ش-ر-ك",
        primaryMeaning = "associate, partner, share",
        extendedMeaning = "Associating partners, sharing.",
        quranUsage = "'Do not associate (tushrik) anything with Allah.' Shirk is polytheism.",
        notes = "Shirk is the greatest sin - associating partners with Allah."
      ),
      RootMeaningData(
        root = "ك-ف-ر",
        primaryMeaning = "disbelieve, cover, reject",
        extendedMeaning = "Disbelief, covering truth, rejection.",
        quranUsage = "'Those who disbelieve (kafaru).' Kufr is disbelief. Kafir is disbeliever.",
        notes = "Kufr literally means covering the truth."
      ),
      RootMeaningData(
        root = "ن-ف-ق",
        primaryMeaning = "hypocrisy, spend, tunnel",
        extendedMeaning = "Hypocrisy (two-facedness), spending, and tunnel.",
        quranUsage = "'The hypocrites (munafiqun).' Nifaq is hypocrisy.",
        notes = "Hypocrites have two faces - different inside and out."
      ),
      RootMeaningData(
        root = "ز-ن-د-ق",
        primaryMeaning = "heresy, atheism",
        extendedMeaning = "Heresy, atheism, irreligion.",
        quranUsage = "Zandaqah is heresy. Zindiq is heretic.",
        notes = "Denying fundamental beliefs is zandaqah."
      ),
      RootMeaningData(
        root = "إ-ل-ح-د",
        primaryMeaning = "deviate, atheism",
        extendedMeaning = "Deviation from truth, atheism.",
        quranUsage = "'Those who deviate (yulhidun) in His names.' Ilhad is deviation/atheism.",
        notes = "Ilhad is deviating from the truth about Allah."
      ),
      RootMeaningData(
        root = "ض-ل-ل",
        primaryMeaning = "stray, misguidance",
        extendedMeaning = "Going astray, misguidance.",
        quranUsage = "'Not those who went astray (dallin).' Dalal is misguidance. Dalil is proof.",
        notes = "Al-Fatiha asks for protection from misguidance."
      ),
      RootMeaningData(
        root = "غ-و-ي",
        primaryMeaning = "deviate, seduce, error",
        extendedMeaning = "Deviation, seduction into error.",
        quranUsage = "'By Your might, I will mislead (la-ughwiyannahum) them.' Ghawayah is deviation.",
        notes = "Satan vowed to mislead humanity."
      ),
      RootMeaningData(
        root = "ف-ت-ن",
        primaryMeaning = "trial, test, temptation",
        extendedMeaning = "Trial, testing, and temptation.",
        quranUsage = "'We tested (fatanna) them.' Fitnah is trial. Fattan is tempter.",
        notes = "Life is full of trials (fitan) that test faith."
      ),
      RootMeaningData(
        root = "ب-ل-و",
        primaryMeaning = "test, afflict, wear",
        extendedMeaning = "Testing, afflicting, and wearing out.",
        quranUsage = "'We tested (balawna) them.' Bala' is trial. Ibtila' is testing.",
        notes = "Tests reveal the true nature of faith."
      ),
      RootMeaningData(
        root = "إ-م-ت-ح-ن",
        primaryMeaning = "test, examine",
        extendedMeaning = "Testing, examining.",
        quranUsage = "'Allah tests (yamtahinu) your hearts.' Imtihan is examination.",
        notes = "Allah tests hearts to reveal what's inside."
      ),
      RootMeaningData(
        root = "ث-و-ب",
        primaryMeaning = "reward, return, garment",
        extendedMeaning = "Reward, returning, and garment.",
        quranUsage = "'A great reward (thawab).' Thawab is reward. Thawb is garment.",
        notes = "Good deeds earn thawab (reward)."
      ),
      RootMeaningData(
        root = "أ-ج-ر",
        primaryMeaning = "reward, wage, hire",
        extendedMeaning = "Reward, wages, and hiring.",
        quranUsage = "'Their reward (ajr) is with their Lord.' Ajr is reward. Ijara is hiring.",
        notes = "Believers will receive their full reward."
      ),
      RootMeaningData(
        root = "ف-و-ز",
        primaryMeaning = "success, salvation",
        extendedMeaning = "Success, triumph, salvation.",
        quranUsage = "'That is the great success (fawz).' Fawz is success. Fa'iz is successful.",
        notes = "True success is entering Paradise."
      ),
      RootMeaningData(
        root = "خ-س-ر",
        primaryMeaning = "loss, failure",
        extendedMeaning = "Loss, failure, perdition.",
        quranUsage = "'Those are the losers (khasirun).' Khusran is loss.",
        notes = "Surah Al-'Asr says all are in loss except believers."
      ),
      RootMeaningData(
        root = "ه-د-ي",
        primaryMeaning = "guide, guidance, gift",
        extendedMeaning = "Guiding, guidance, and gift.",
        quranUsage = "'Guide us (ihdina) to the straight path.' Huda is guidance. Hadiyah is gift.",
        notes = "Guidance is the greatest gift from Allah."
      ),
      RootMeaningData(
        root = "ر-ش-د",
        primaryMeaning = "right path, maturity",
        extendedMeaning = "Right guidance, maturity, integrity.",
        quranUsage = "'That they might be guided to the right (rushd).' Rushd is right guidance.",
        notes = "Rashid means rightly guided."
      ),

      // === SOUNDS AND NOISES ===
      RootMeaningData(
        root = "ص-و-ت",
        primaryMeaning = "sound, voice, vote",
        extendedMeaning = "Sound, voice, and voting.",
        quranUsage = "'Lower your voice (sawt).' Sawt is sound/voice.",
        notes = "Lowering one's voice shows respect and humility."
      ),
      RootMeaningData(
        root = "ن-ع-ق",
        primaryMeaning = "croak, bleat, call",
        extendedMeaning = "Animal sounds, croaking, bleating.",
        quranUsage = "'Like one who calls (yan'iqu) to that which hears nothing.' Na'iq is one who calls.",
        notes = "Calling disbelievers is like calling animals that don't understand."
      ),
      RootMeaningData(
        root = "ص-ر-ر",
        primaryMeaning = "squeak, creak, persist",
        extendedMeaning = "Squeaking sound, and persisting.",
        quranUsage = "'In a cry (sarrah).' Sarrah is cry/scream. Israr is persistence.",
        notes = "The old woman cried out in amazement."
      ),
      RootMeaningData(
        root = "ط-ن-ن",
        primaryMeaning = "ring, buzz, resound",
        extendedMeaning = "Ringing, buzzing sound.",
        quranUsage = "Tanin is ringing/buzzing. The ears ring from loud sounds.",
        notes = "The trumpet blast will ring throughout creation."
      ),
      RootMeaningData(
        root = "د-و-ي",
        primaryMeaning = "echo, resound",
        extendedMeaning = "Echoing, resounding.",
        quranUsage = "Dawiy is echo/resounding. Sounds echo in valleys.",
        notes = "Voices echo and return - like deeds returning."
      ),
      RootMeaningData(
        root = "ه-م-هم",
        primaryMeaning = "murmur, mutter",
        extendedMeaning = "Murmuring, muttering low sounds.",
        quranUsage = "Hamhamah is murmuring. Low indistinct sounds.",
        notes = "Plotting often involves murmuring."
      ),
      RootMeaningData(
        root = "غ-م-غ-م",
        primaryMeaning = "mumble, indistinct speech",
        extendedMeaning = "Mumbling, unclear speech.",
        quranUsage = "Ghamghamah is mumbling. Unclear, confused speech.",
        notes = "Clear speech is better than mumbling."
      ),
      RootMeaningData(
        root = "ز-ف-ر",
        primaryMeaning = "sigh, exhale, groan",
        extendedMeaning = "Sighing, exhaling, groaning.",
        quranUsage = "'For them therein is sighing (zafir).' Zafir is sighing/groaning.",
        notes = "The people of Hell sigh and groan."
      ),
      RootMeaningData(
        root = "ش-ه-ق",
        primaryMeaning = "inhale, gasp, bray",
        extendedMeaning = "Inhaling, gasping, braying of donkey.",
        quranUsage = "'For them therein is inhaling (shahiq).' Shahiq is gasping/inhaling.",
        notes = "The people of Hell gasp for breath."
      ),
      RootMeaningData(
        root = "ن-ح-ب",
        primaryMeaning = "sob, weep, vow",
        extendedMeaning = "Sobbing, weeping loudly.",
        quranUsage = "'Some fulfilled their vow (nahb).' Nahib is sobbing. Nahb is vow fulfilled.",
        notes = "Some believers fulfilled their pledge through martyrdom."
      ),
      RootMeaningData(
        root = "ب-ك-ي",
        primaryMeaning = "weep, cry",
        extendedMeaning = "Weeping, crying.",
        quranUsage = "'They fall down weeping (bukiyyan).' Buka' is weeping.",
        notes = "The righteous weep when they hear Allah's words."
      ),
      RootMeaningData(
        root = "ض-ح-ك",
        primaryMeaning = "laugh, smile",
        extendedMeaning = "Laughing, smiling.",
        quranUsage = "'She laughed (dahikat).' Dahk is laughter.",
        notes = "Sarah laughed when given news of a son in old age."
      ),
      RootMeaningData(
        root = "ق-ه-ق-ه",
        primaryMeaning = "loud laughter",
        extendedMeaning = "Loud, boisterous laughter.",
        quranUsage = "Qahqahah is loud laughter.",
        notes = "Excessive loud laughter is discouraged."
      ),
      RootMeaningData(
        root = "ت-ب-س-م",
        primaryMeaning = "smile",
        extendedMeaning = "Smiling.",
        quranUsage = "'He smiled (tabassama) laughing.' Tabassama is to smile.",
        notes = "Sulayman smiled at the ant's words."
      ),

      // === TEXTURES AND SURFACES ===
      RootMeaningData(
        root = "ن-ع-م",
        primaryMeaning = "soft, smooth, blessing",
        extendedMeaning = "Softness, smoothness, and blessings.",
        quranUsage = "'In soft (na'im) life.' Na'im is soft/blissful. Ni'mah is blessing.",
        notes = "Paradise life is soft, smooth, and blissful."
      ),
      RootMeaningData(
        root = "خ-ش-ن",
        primaryMeaning = "rough, coarse",
        extendedMeaning = "Roughness, coarseness.",
        quranUsage = "Khushuunah is roughness. Khashin is rough.",
        notes = "Rough cloth was worn by the ascetic."
      ),
      RootMeaningData(
        root = "م-ل-س",
        primaryMeaning = "smooth, slippery",
        extendedMeaning = "Smoothness, being slippery.",
        quranUsage = "Amlas is smooth. Muluusah is smoothness.",
        notes = "Some surfaces are smooth and slippery."
      ),
      RootMeaningData(
        root = "و-ع-ر",
        primaryMeaning = "rough terrain, difficult",
        extendedMeaning = "Rough, uneven terrain, difficulty.",
        quranUsage = "Wa'r is rough terrain. Wu'urah is roughness.",
        notes = "Difficult paths require perseverance."
      ),
      RootMeaningData(
        root = "ص-ق-ل",
        primaryMeaning = "polish, burnish",
        extendedMeaning = "Polishing, making smooth and shiny.",
        quranUsage = "Saqal is to polish. Masqul is polished.",
        notes = "Hearts are polished through remembrance."
      ),
      RootMeaningData(
        root = "ص-د-ء",
        primaryMeaning = "rust, tarnish",
        extendedMeaning = "Rust, tarnishing.",
        quranUsage = "'Rust (ran) has covered their hearts.' Sada' is rust.",
        notes = "Sin causes hearts to rust and tarnish."
      ),
      RootMeaningData(
        root = "ل-م-ع",
        primaryMeaning = "shine, glitter, flash",
        extendedMeaning = "Shining, glittering.",
        quranUsage = "Lam' is shining. Lami' is shiny.",
        notes = "Truth shines and falsehood fades."
      ),
      RootMeaningData(
        root = "ب-ر-ق",
        primaryMeaning = "shine, lightning, gleam",
        extendedMeaning = "Shining like lightning, gleaming.",
        quranUsage = "'When vision is dazzled (bariqa).' Bariq is shining.",
        notes = "Eyes will be dazzled on Judgment Day."
      ),

      // === SHAPES AND FORMS ===
      RootMeaningData(
        root = "د-و-ر",
        primaryMeaning = "circle, round, rotate",
        extendedMeaning = "Circle, roundness, rotation.",
        quranUsage = "'Their eyes rotating (tadur).' Dawrah is circle. Mudawwar is round.",
        notes = "Orbits are circular; history cycles."
      ),
      RootMeaningData(
        root = "م-ر-ب-ع",
        primaryMeaning = "square, four-sided",
        extendedMeaning = "Square, quadrilateral.",
        quranUsage = "Murabba' is square. Related to four (arba'ah).",
        notes = "The Kaaba is roughly cube-shaped."
      ),
      RootMeaningData(
        root = "م-ث-ل-ث",
        primaryMeaning = "triangle, three-sided",
        extendedMeaning = "Triangle, three-sided shape.",
        quranUsage = "Muthallath is triangle. Related to three (thalathah).",
        notes = "Triangular shapes appear in nature."
      ),
      RootMeaningData(
        root = "ط-و-ل",
        primaryMeaning = "long, length",
        extendedMeaning = "Length, being long.",
        quranUsage = "'A long (tawil) period.' Tul is length. Tawil is long.",
        notes = "Some surahs are long (tiwal)."
      ),
      RootMeaningData(
        root = "ق-ص-ر",
        primaryMeaning = "short, shorten, palace",
        extendedMeaning = "Shortness, being short.",
        quranUsage = "'Shorten (taqsuru) the prayer.' Qasir is short.",
        notes = "Prayers can be shortened during travel."
      ),
      RootMeaningData(
        root = "ع-ر-ض",
        primaryMeaning = "wide, width, present",
        extendedMeaning = "Width, being wide.",
        quranUsage = "'Its width ('ard) is the heavens.' 'Arid is wide.",
        notes = "Paradise is as wide as the heavens and earth."
      ),
      RootMeaningData(
        root = "ض-ي-ق",
        primaryMeaning = "narrow, tight, distress",
        extendedMeaning = "Narrowness, tightness, and distress.",
        quranUsage = "'His chest becomes tight (dayiq).' Diq is narrowness.",
        notes = "Disbelief makes the chest tight; faith expands it."
      ),
      RootMeaningData(
        root = "و-س-ع",
        primaryMeaning = "wide, spacious, encompass",
        extendedMeaning = "Wideness, spaciousness, encompassing.",
        quranUsage = "'Allah is Vast (Wasi').' Si'ah is spaciousness.",
        notes = "Al-Wasi' (The Vast) - Allah's mercy encompasses all."
      ),
      RootMeaningData(
        root = "ع-م-ق",
        primaryMeaning = "deep, depth",
        extendedMeaning = "Depth, being deep.",
        quranUsage = "'From every deep (amiq) pass.' 'Umq is depth.",
        notes = "Pilgrims come from every distant, deep valley."
      ),
      RootMeaningData(
        root = "س-ط-ح",
        primaryMeaning = "flat, surface, spread",
        extendedMeaning = "Flat surface, spreading out.",
        quranUsage = "'How it was spread out (sutihat).' Sath is surface.",
        notes = "The earth was spread out as a surface."
      ),
      RootMeaningData(
        root = "ح-د-ب",
        primaryMeaning = "hump, convex, slope",
        extendedMeaning = "Hump, convexity, sloping.",
        quranUsage = "'From every elevation (hadab).' Hadab is elevation/slope.",
        notes = "People will come from every hill and slope."
      ),
      RootMeaningData(
        root = "ق-ع-ر",
        primaryMeaning = "bottom, depth, hollow",
        extendedMeaning = "Bottom, depths, hollow.",
        quranUsage = "'To the bottom (qa'r) of the pit.' Qa'r is bottom.",
        notes = "Hell has deep bottoms."
      ),
      RootMeaningData(
        root = "ذ-ر-و",
        primaryMeaning = "peak, summit, top",
        extendedMeaning = "Peak, summit, highest point.",
        quranUsage = "Dhirwah is peak. The highest point.",
        notes = "Mountains have peaks that reach the sky."
      ),

      // === MORE ACTION VERBS ===
      RootMeaningData(
        root = "ف-ع-ل",
        primaryMeaning = "do, act, deed",
        extendedMeaning = "Doing, acting, and deeds.",
        quranUsage = "'Allah does (yaf'al) what He wills.' Fi'l is deed. Fa'il is doer.",
        notes = "Every deed will be accounted for."
      ),
      RootMeaningData(
        root = "ع-م-ل",
        primaryMeaning = "work, act, deed",
        extendedMeaning = "Working, acting, and deeds.",
        quranUsage = "'Those who believe and do good deeds ('amilu).' 'Amal is work/deed.",
        notes = "Faith must be combined with good deeds."
      ),
      RootMeaningData(
        root = "ص-ن-ع",
        primaryMeaning = "make, craft, manufacture",
        extendedMeaning = "Making, crafting, manufacturing.",
        quranUsage = "'The making (sun') of Allah.' San'ah is craft.",
        notes = "Allah's craftsmanship is perfect."
      ),
      RootMeaningData(
        root = "ج-ع-ل",
        primaryMeaning = "make, appoint, place",
        extendedMeaning = "Making, appointing, placing.",
        quranUsage = "'We made (ja'alna) you into nations.' Ja'l is making/appointing.",
        notes = "Allah appoints and places everything with wisdom."
      ),
      RootMeaningData(
        root = "خ-ل-ق",
        primaryMeaning = "create, character",
        extendedMeaning = "Creating, character, nature.",
        quranUsage = "'He who created (khalaqa) you.' Khalq is creation. Khuluq is character.",
        notes = "Allah creates and shapes moral character."
      ),
      RootMeaningData(
        root = "ب-ر-ء",
        primaryMeaning = "create, originate, innocent",
        extendedMeaning = "Creating from nothing, being innocent.",
        quranUsage = "'The Originator (Bari').' Bara'ah is innocence.",
        notes = "Al-Bari' creates without prior model."
      ),
      RootMeaningData(
        root = "ذ-ر-ء",
        primaryMeaning = "create, scatter, multiply",
        extendedMeaning = "Creating and scattering, multiplying.",
        quranUsage = "'He scattered (dhara'a) in it creatures.' Dhar' is scattering/creating.",
        notes = "Allah scattered diverse creatures across the earth."
      ),
      RootMeaningData(
        root = "أ-ت-ي",
        primaryMeaning = "come, bring, give",
        extendedMeaning = "Coming, bringing, giving.",
        quranUsage = "'The command of Allah will come (ata).' Ityan is coming.",
        notes = "The Hour will surely come."
      ),
      RootMeaningData(
        root = "ج-ي-ء",
        primaryMeaning = "come, arrive",
        extendedMeaning = "Coming, arriving.",
        quranUsage = "'When the help of Allah comes (ja'a).' Maji' is coming.",
        notes = "Victory comes from Allah alone."
      ),
      RootMeaningData(
        root = "ذ-ه-ب",
        primaryMeaning = "go, depart, gold",
        extendedMeaning = "Going, departing, and gold.",
        quranUsage = "'They went (dhahabu) away.' Dhahab is gold/going.",
        notes = "The brothers went away after selling Yusuf."
      ),
      RootMeaningData(
        root = "ر-ج-ع",
        primaryMeaning = "return, come back",
        extendedMeaning = "Returning, coming back.",
        quranUsage = "'To Allah is the return (ruju').' Ruju' is return.",
        notes = "All return to Allah for judgment."
      ),
      RootMeaningData(
        root = "و-ل-ي",
        primaryMeaning = "turn, follow, guardian",
        extendedMeaning = "Turning, following, and guardianship.",
        quranUsage = "'Allah is the Guardian (Wali).' Wilayah is guardianship.",
        notes = "Al-Wali - Allah is the Guardian of believers."
      ),
      RootMeaningData(
        root = "ت-ب-ع",
        primaryMeaning = "follow, succeed",
        extendedMeaning = "Following, succeeding.",
        quranUsage = "'Follow (ittabi') the best of what was revealed.' Tabi' is follower.",
        notes = "Following the Prophet's way is essential."
      ),
      RootMeaningData(
        root = "ق-د-م",
        primaryMeaning = "advance, precede, foot",
        extendedMeaning = "Advancing, preceding, and foot.",
        quranUsage = "'What your hands have put forward (qaddamat).' Taqaddama is to advance.",
        notes = "Deeds precede us to the Hereafter."
      ),
      RootMeaningData(
        root = "أ-خ-ر",
        primaryMeaning = "delay, postpone, last",
        extendedMeaning = "Delaying, postponing, being last.",
        quranUsage = "'What you delayed (akhkharta).' Ta'khir is delay.",
        notes = "Allah forgives past and future sins."
      ),
      RootMeaningData(
        root = "س-ب-ق",
        primaryMeaning = "precede, race, surpass",
        extendedMeaning = "Preceding, racing, surpassing.",
        quranUsage = "'Race (sabiqu) toward forgiveness.' Sabq is precedence.",
        notes = "Race toward good deeds before death."
      ),
      RootMeaningData(
        root = "ل-ح-ق",
        primaryMeaning = "catch up, join, follow",
        extendedMeaning = "Catching up, joining, following.",
        quranUsage = "'Those who have not yet joined (yalhaquu) them.' Luhooq is catching up.",
        notes = "Later generations will join the earlier ones."
      ),
      RootMeaningData(
        root = "و-ص-ل",
        primaryMeaning = "connect, arrive, join",
        extendedMeaning = "Connecting, arriving, joining.",
        quranUsage = "'Join (tasilu) what Allah commanded to be joined.' Silah is connection.",
        notes = "Maintaining family ties (silah) is obligatory."
      ),
      RootMeaningData(
        root = "ف-ص-ل",
        primaryMeaning = "separate, detail, decide",
        extendedMeaning = "Separating, detailing, deciding.",
        quranUsage = "'A decisive (fasl) word.' Fasl is separation/decision. Tafsil is detail.",
        notes = "The Quran distinguishes truth from falsehood."
      ),
      RootMeaningData(
        root = "ق-ط-ع",
        primaryMeaning = "cut, sever, cross",
        extendedMeaning = "Cutting, severing, crossing.",
        quranUsage = "'Cut off (qata'a) the way.' Qat' is cutting. Munqati' is interrupted.",
        notes = "Highway robbery (cutting roads) is severely punished."
      ),
      RootMeaningData(
        root = "ش-ق-ق",
        primaryMeaning = "split, divide, oppose",
        extendedMeaning = "Splitting, dividing, opposing.",
        quranUsage = "'We split (shaqaqna) the earth.' Shaqq is splitting. Shiqaq is discord.",
        notes = "Allah splits the earth for plants to grow."
      ),
      RootMeaningData(
        root = "ف-ل-ق",
        primaryMeaning = "split, cleave, dawn",
        extendedMeaning = "Splitting, cleaving, daybreak.",
        quranUsage = "'Splitter (Faliq) of the seed.' Falaq is daybreak/splitting.",
        notes = "Surah Al-Falaq - The Daybreak. Seeds split to grow."
      ),
      RootMeaningData(
        root = "ف-ر-ج",
        primaryMeaning = "open, relieve, gap",
        extendedMeaning = "Opening, relief, gap.",
        quranUsage = "'We opened (farrajna) it.' Faraj is relief. Farj is opening/gap.",
        notes = "Allah provides relief from every difficulty."
      ),
      RootMeaningData(
        root = "س-د-د",
        primaryMeaning = "close, block, correct",
        extendedMeaning = "Closing, blocking, being correct.",
        quranUsage = "'A barrier (sadd) behind them.' Sadd is barrier. Sadid is correct.",
        notes = "Barriers block and correct speech hits the mark."
      ),
      RootMeaningData(
        root = "ح-ج-ب",
        primaryMeaning = "veil, screen, prevent",
        extendedMeaning = "Veiling, screening, preventing.",
        quranUsage = "'They are screened (mahjubun).' Hijab is veil/screen.",
        notes = "The wicked will be screened from seeing Allah."
      ),
      RootMeaningData(
        root = "ك-ش-ف",
        primaryMeaning = "uncover, reveal, remove",
        extendedMeaning = "Uncovering, revealing, removing.",
        quranUsage = "'If We remove (kashafna) the punishment.' Kashf is uncovering.",
        notes = "Only Allah can remove affliction."
      ),
      RootMeaningData(
        root = "س-ت-ر",
        primaryMeaning = "cover, conceal, screen",
        extendedMeaning = "Covering, concealing, screening.",
        quranUsage = "'Allah is Concealer (Satir).' Sitr is covering.",
        notes = "Allah conceals the faults of His servants."
      ),
      RootMeaningData(
        root = "ع-ر-ي",
        primaryMeaning = "bare, naked, strip",
        extendedMeaning = "Being bare, naked, stripped.",
        quranUsage = "'That your nakedness ('awrah) be not exposed.' 'Uryan is naked.",
        notes = "Covering nakedness is obligatory."
      ),
      RootMeaningData(
        root = "ك-س-و",
        primaryMeaning = "clothe, dress",
        extendedMeaning = "Clothing, dressing.",
        quranUsage = "'We clothed (kasawna) the bones with flesh.' Kiswa is clothing.",
        notes = "Allah clothed bones with flesh in creation."
      ),
      RootMeaningData(
        root = "ل-ب-س",
        primaryMeaning = "wear, clothe, confuse",
        extendedMeaning = "Wearing, clothing, and confusing.",
        quranUsage = "'Do not confuse (talbisu) truth with falsehood.' Libs is wearing. Labs is confusion.",
        notes = "Don't dress truth in the garb of falsehood."
      ),
      RootMeaningData(
        root = "ن-ز-ع",
        primaryMeaning = "remove, extract, pluck",
        extendedMeaning = "Removing, extracting, plucking out.",
        quranUsage = "'He removed (naza'a) from them their clothing.' Naz' is removing.",
        notes = "Satan stripped Adam and Hawwa of Paradise clothing."
      ),
      RootMeaningData(
        root = "ج-ذ-ب",
        primaryMeaning = "pull, attract, draw",
        extendedMeaning = "Pulling, attracting, drawing toward.",
        quranUsage = "Jadhb is pulling. Jadhibah is attraction.",
        notes = "Hearts are attracted to truth."
      ),
      RootMeaningData(
        root = "د-ف-ع",
        primaryMeaning = "push, repel, prevent",
        extendedMeaning = "Pushing, repelling, preventing.",
        quranUsage = "'Allah repels (yadfa'u) by some people.' Daf' is pushing/repelling.",
        notes = "Allah uses some people to repel corruption from others."
      ),
      RootMeaningData(
        root = "ش-د-د",
        primaryMeaning = "strengthen, tighten, intense",
        extendedMeaning = "Strengthening, tightening, intensity.",
        quranUsage = "'We strengthened (shadadna) their hearts.' Shadid is intense/severe.",
        notes = "Allah strengthened the hearts of the People of the Cave."
      ),
      RootMeaningData(
        root = "ر-خ-و",
        primaryMeaning = "loosen, relax, soft",
        extendedMeaning = "Loosening, relaxing, being soft.",
        quranUsage = "Rukhawah is looseness. Rakhi is loose/relaxed.",
        notes = "After hardship comes ease and relaxation."
      ),
      RootMeaningData(
        root = "م-د-د",
        primaryMeaning = "extend, stretch, supply",
        extendedMeaning = "Extending, stretching, supplying.",
        quranUsage = "'We extended (madadna) the earth.' Madd is extension. Madad is supply.",
        notes = "Allah spread out the earth and supplies provision."
      ),
      RootMeaningData(
        root = "ق-ب-ض",
        primaryMeaning = "grasp, contract, seize",
        extendedMeaning = "Grasping, contracting, seizing.",
        quranUsage = "'The earth entirely in His grasp (qabdatihi).' Qabd is grasping.",
        notes = "The entire earth will be in Allah's grasp."
      ),
      RootMeaningData(
        root = "ب-س-ط",
        primaryMeaning = "spread, extend, expand",
        extendedMeaning = "Spreading, extending, expanding.",
        quranUsage = "'Allah expands (yabsutu) provision.' Bast is expansion.",
        notes = "Allah contracts and expands as He wills."
      ),

      // === SOCIAL AND COMMUNITY ===
      RootMeaningData(
        root = "ج-م-ع",
        primaryMeaning = "gather, unite, Friday",
        extendedMeaning = "Gathering, uniting, and Friday.",
        quranUsage = "'The Day of Gathering (jam').' Jumu'ah is Friday. Jama'ah is congregation.",
        notes = "Friday is the day of congregational gathering."
      ),
      RootMeaningData(
        root = "ف-ر-ق",
        primaryMeaning = "divide, separate, group",
        extendedMeaning = "Dividing, separating, and a group.",
        quranUsage = "'Do not be of those who divided (farraqu).' Firqah is group/sect.",
        notes = "Dividing religion into sects is condemned."
      ),
      RootMeaningData(
        root = "ح-ز-ب",
        primaryMeaning = "party, faction, portion",
        extendedMeaning = "Party, faction, and portion.",
        quranUsage = "'The party (hizb) of Allah.' Hizb is party. Ahzab are factions.",
        notes = "The party of Allah will be successful."
      ),
      RootMeaningData(
        root = "ش-ي-ع",
        primaryMeaning = "sect, follower, spread",
        extendedMeaning = "Sect, followers, and spreading.",
        quranUsage = "'Into sects (shiya').' Shi'ah is sect/party. Ishaah is spreading.",
        notes = "Humanity divided into different groups."
      ),
      RootMeaningData(
        root = "ق-و-م",
        primaryMeaning = "people, nation, stand",
        extendedMeaning = "People, nation, and standing.",
        quranUsage = "'O my people (qawm)!' Qawm is people/nation.",
        notes = "Each prophet addressed his own people."
      ),
      RootMeaningData(
        root = "أ-م-م",
        primaryMeaning = "nation, community, mother",
        extendedMeaning = "Nation, community, and mother.",
        quranUsage = "'You are the best nation (ummah).' Ummah is nation. Umm is mother.",
        notes = "The Muslim ummah is one global community."
      ),
      RootMeaningData(
        root = "ش-ع-ب",
        primaryMeaning = "people, branch, divide",
        extendedMeaning = "People, branch, and dividing.",
        quranUsage = "'We made you into peoples (shu'ub) and tribes.' Sha'b is people.",
        notes = "Diversity of peoples is for knowing each other."
      ),
      RootMeaningData(
        root = "ق-ب-ل",
        primaryMeaning = "tribe, accept, before",
        extendedMeaning = "Tribe, acceptance, and before.",
        quranUsage = "'Peoples and tribes (qaba'il).' Qabilah is tribe. Qabul is acceptance.",
        notes = "Tribes identify lineage but don't determine worth."
      ),
      RootMeaningData(
        root = "ع-ش-ر",
        primaryMeaning = "associate, ten, clan",
        extendedMeaning = "Associating, companionship, and clan.",
        quranUsage = "'Warn your closest clan ('ashirah).' 'Ashirah is clan. Mu'asharah is companionship.",
        notes = "The Prophet first warned his closest relatives."
      ),
      RootMeaningData(
        root = "أ-ه-ل",
        primaryMeaning = "family, people, worthy",
        extendedMeaning = "Family, people of, and being worthy.",
        quranUsage = "'People (ahl) of the Book.' Ahl is family/people. Ahliyyah is worthiness.",
        notes = "Ahl al-Kitab are Jews and Christians."
      ),
      RootMeaningData(
        root = "ص-ح-ب",
        primaryMeaning = "companion, accompany",
        extendedMeaning = "Companionship, accompanying.",
        quranUsage = "'His companion (sahib) said to him.' Sahib is companion. Suhbah is companionship.",
        notes = "The Prophet's companions are called Sahabah."
      ),
      RootMeaningData(
        root = "ر-ف-ق",
        primaryMeaning = "companion, gentle, elbow",
        extendedMeaning = "Companion, gentleness, and elbow.",
        quranUsage = "'Good companions (rafiq).' Rifq is gentleness. Rafiq is companion.",
        notes = "The Prophet will be with the prophets as rafiq."
      ),
      RootMeaningData(
        root = "ج-و-ر",
        primaryMeaning = "neighbor, injustice, deviate",
        extendedMeaning = "Neighbor, injustice, and deviation.",
        quranUsage = "'The neighbor (jar).' Jar is neighbor. Jawr is injustice.",
        notes = "Neighbors have great rights in Islam."
      ),
      RootMeaningData(
        root = "ق-ر-ب",
        primaryMeaning = "near, relative, offering",
        extendedMeaning = "Nearness, relatives, and offering.",
        quranUsage = "'Relatives (aqribun).' Qarib is near/relative. Qurban is offering.",
        notes = "Kindness to relatives is emphasized."
      ),
      RootMeaningData(
        root = "ب-ع-د",
        primaryMeaning = "far, distant, after",
        extendedMeaning = "Being far, distant.",
        quranUsage = "'Far removed (ba'idan).' Bu'd is distance. Ba'id is far.",
        notes = "Being far from Allah is spiritual destruction."
      ),
      RootMeaningData(
        root = "غ-ر-ب",
        primaryMeaning = "stranger, west, strange",
        extendedMeaning = "Being strange, foreign, and the west.",
        quranUsage = "Gharib is stranger. Ghurbah is being foreign.",
        notes = "Islam began strange and will return strange."
      ),

      // === ETHICAL AND MORAL CONCEPTS ===
      RootMeaningData(
        root = "ح-س-ن",
        primaryMeaning = "good, beautiful, excellence",
        extendedMeaning = "Goodness, beauty, and excellence.",
        quranUsage = "'Allah commands justice and excellence (ihsan).' Husn is beauty. Hasanah is good deed.",
        notes = "Ihsan is worshipping as if you see Allah."
      ),
      RootMeaningData(
        root = "س-و-ء",
        primaryMeaning = "evil, bad, harm",
        extendedMeaning = "Evil, badness, and harm.",
        quranUsage = "'Whoever does evil (su').' Sayyi'ah is bad deed. Su' is evil.",
        notes = "Evil deeds harm the doer first."
      ),
      RootMeaningData(
        root = "ب-ر-ر",
        primaryMeaning = "righteousness, piety, land",
        extendedMeaning = "Righteousness, piety, and dry land.",
        quranUsage = "'Righteousness (birr) is not...' Birr is righteousness. Barr is land/righteous.",
        notes = "True birr is comprehensive righteousness."
      ),
      RootMeaningData(
        root = "ف-ج-ر",
        primaryMeaning = "wickedness, dawn, burst",
        extendedMeaning = "Wickedness, dawn, and bursting forth.",
        quranUsage = "'The wicked (fujjar).' Fujur is wickedness. Fajr is dawn.",
        notes = "The wicked and the righteous have different fates."
      ),
      RootMeaningData(
        root = "ت-ق-و",
        primaryMeaning = "piety, fear, protect",
        extendedMeaning = "Piety, God-consciousness, protection.",
        quranUsage = "'Those who have piety (taqwa).' Taqwa is God-consciousness. Muttaqi is pious.",
        notes = "Taqwa is the best provision for the journey."
      ),
      RootMeaningData(
        root = "و-ر-ع",
        primaryMeaning = "piety, scrupulousness",
        extendedMeaning = "Piety, being scrupulous about religion.",
        quranUsage = "Wara' is scrupulous piety. Avoiding doubtful matters.",
        notes = "Wara' is leaving what is doubtful."
      ),
      RootMeaningData(
        root = "ز-ه-د",
        primaryMeaning = "asceticism, renunciation",
        extendedMeaning = "Asceticism, renouncing worldly pleasures.",
        quranUsage = "'They sold him for a small price, showing no interest (zahidin).' Zuhd is asceticism.",
        notes = "Zuhd is not wanting what you don't have."
      ),
      RootMeaningData(
        root = "ط-م-ع",
        primaryMeaning = "greed, covet, hope",
        extendedMeaning = "Greed, coveting, eager hope.",
        quranUsage = "'They hope (yatma'un) in His mercy.' Tama' is greed/hope.",
        notes = "Greed for worldly things is blameworthy; hope in Allah's mercy is praiseworthy."
      ),
      RootMeaningData(
        root = "ش-ح-ح",
        primaryMeaning = "stinginess, miserliness",
        extendedMeaning = "Stinginess, miserliness.",
        quranUsage = "'Whoever is protected from the stinginess (shuhh) of his soul.' Shuhh is miserliness.",
        notes = "Stinginess of the soul must be overcome."
      ),
      RootMeaningData(
        root = "ج-و-د",
        primaryMeaning = "generosity, excellence",
        extendedMeaning = "Generosity, excellence, quality.",
        quranUsage = "Jud is generosity. Jawad is generous. Jayyid is excellent.",
        notes = "The Prophet was the most generous of people."
      ),
      RootMeaningData(
        root = "ب-ذ-ل",
        primaryMeaning = "give freely, expend",
        extendedMeaning = "Giving freely, expending without hesitation.",
        quranUsage = "Badhl is giving freely. Badhil is one who gives.",
        notes = "Giving freely in Allah's cause is rewarded."
      ),
      RootMeaningData(
        root = "ع-ف-و",
        primaryMeaning = "pardon, forgive, excess",
        extendedMeaning = "Pardoning, forgiving, and excess.",
        quranUsage = "'Pardon (a'fu) them.' 'Afw is pardon. 'Afuw is Most Pardoning.",
        notes = "Pardoning erases sins as if they never existed."
      ),
      RootMeaningData(
        root = "ص-ف-ح",
        primaryMeaning = "forgive, turn away, page",
        extendedMeaning = "Forgiving by turning away from offense.",
        quranUsage = "'Turn away (safh) graciously.' Safh is gracious forgiveness.",
        notes = "Gracious forgiveness ignores the offense entirely."
      ),
      RootMeaningData(
        root = "ح-ق-د",
        primaryMeaning = "grudge, rancor",
        extendedMeaning = "Holding grudges, rancor.",
        quranUsage = "Hiqd is grudge. Haqud is one who holds grudges.",
        notes = "Believers have no grudges against fellow believers."
      ),
      RootMeaningData(
        root = "ح-س-د",
        primaryMeaning = "envy, jealousy",
        extendedMeaning = "Envy, jealousy, wishing others lose blessings.",
        quranUsage = "'From the evil of an envier (hasid) when he envies.' Hasad is envy.",
        notes = "Surah Al-Falaq seeks refuge from envy."
      ),
      RootMeaningData(
        root = "غ-ب-ط",
        primaryMeaning = "good envy, admiration",
        extendedMeaning = "Wishing for the same blessing without wanting others to lose it.",
        quranUsage = "Ghibtah is positive envy/admiration.",
        notes = "Ghibtah is permissible - admiring without malice."
      ),
      RootMeaningData(
        root = "ك-ذ-ب",
        primaryMeaning = "lie, deny, falsify",
        extendedMeaning = "Lying, denying, falsifying.",
        quranUsage = "'Those who lie (kadhabu).' Kidhb is lying. Kadhib is liar.",
        notes = "Lying is one of the worst sins."
      ),
      RootMeaningData(
        root = "ص-د-ق",
        primaryMeaning = "truth, sincere, confirm",
        extendedMeaning = "Truth, sincerity, and confirmation.",
        quranUsage = "'The truthful (sadiqun).' Sidq is truth. Siddiq is most truthful.",
        notes = "Abu Bakr was called As-Siddiq for his truthfulness."
      ),
      RootMeaningData(
        root = "خ-و-ن",
        primaryMeaning = "betray, treachery",
        extendedMeaning = "Betrayal, treachery.",
        quranUsage = "'Do not betray (takhun) Allah and the Messenger.' Khiyanah is betrayal.",
        notes = "Betraying trusts is a sign of hypocrisy."
      ),
      RootMeaningData(
        root = "و-ف-ي",
        primaryMeaning = "fulfill, loyal, complete",
        extendedMeaning = "Fulfilling promises, loyalty.",
        quranUsage = "'Fulfill (awfu) the covenant.' Wafa' is fulfillment. Wafi is loyal.",
        notes = "Allah always fulfills His promises."
      ),
      RootMeaningData(
        root = "غ-د-ر",
        primaryMeaning = "treachery, betray",
        extendedMeaning = "Treachery, betraying agreements.",
        quranUsage = "'If you fear treachery (khiyanah) from a people.' Ghadr is treachery.",
        notes = "Breaking treaties is a form of treachery."
      ),
      RootMeaningData(
        root = "أ-م-ن",
        primaryMeaning = "trust, security, faith",
        extendedMeaning = "Trust, security, and faith.",
        quranUsage = "'Those who believe (amanu).' Aman is security. Amanah is trust.",
        notes = "Iman (faith) brings inner security."
      ),

      // === NATURAL PHENOMENA EXPANDED ===
      RootMeaningData(
        root = "ف-ي-ض",
        primaryMeaning = "overflow, flood, abundance",
        extendedMeaning = "Overflowing, flooding, abundance.",
        quranUsage = "'Their eyes overflow (tafidu) with tears.' Fayd is overflow.",
        notes = "Eyes overflow with tears from emotion."
      ),
      RootMeaningData(
        root = "ج-ف-ف",
        primaryMeaning = "dry up, wither",
        extendedMeaning = "Drying up, withering.",
        quranUsage = "Jafaf is dryness. Jaffah is to dry up.",
        notes = "Rivers and plants can dry up."
      ),
      RootMeaningData(
        root = "ي-ب-س",
        primaryMeaning = "dry, withered",
        extendedMeaning = "Being dry, withered.",
        quranUsage = "'Green or dry (yabis).' Yabis is dry.",
        notes = "Everything green eventually dries."
      ),
      RootMeaningData(
        root = "ر-ط-ب",
        primaryMeaning = "moist, fresh, ripe",
        extendedMeaning = "Moisture, freshness.",
        quranUsage = "'Fresh dates (rutab).' Rutubah is moisture.",
        notes = "Fresh dates were given to Maryam."
      ),
      RootMeaningData(
        root = "ن-د-ي",
        primaryMeaning = "moist, dew, generous",
        extendedMeaning = "Moisture, dew, and generosity.",
        quranUsage = "Nada is dew/moisture. Nadiy is generous.",
        notes = "Morning dew refreshes the earth."
      ),
      RootMeaningData(
        root = "ث-ل-ج",
        primaryMeaning = "snow, ice, cool",
        extendedMeaning = "Snow, ice.",
        quranUsage = "Thalj is snow. The Prophet prayed to be purified with snow.",
        notes = "Snow is pure and purifying."
      ),
      RootMeaningData(
        root = "ج-م-د",
        primaryMeaning = "freeze, solid, still",
        extendedMeaning = "Freezing, being solid.",
        quranUsage = "'You see the mountains thinking them solid (jamidah).' Jumud is solidity.",
        notes = "Mountains seem solid but will move."
      ),
      RootMeaningData(
        root = "ذ-و-ب",
        primaryMeaning = "melt, dissolve",
        extendedMeaning = "Melting, dissolving.",
        quranUsage = "'It will melt (yadhub).' Dhawaban is melting.",
        notes = "Hell's heat melts everything."
      ),
      RootMeaningData(
        root = "ف-و-ر",
        primaryMeaning = "boil, erupt, immediate",
        extendedMeaning = "Boiling, erupting, immediately.",
        quranUsage = "'When Hell boils over (farat).' Fawran is immediately. Fawr is boiling.",
        notes = "Hell boils and erupts with fury."
      ),
      RootMeaningData(
        root = "ه-ي-ج",
        primaryMeaning = "stir up, excite, wither",
        extendedMeaning = "Stirring up, exciting, and withering.",
        quranUsage = "'Then it withers (yahij).' Hayj is excitement. Hayyaj is one who stirs.",
        notes = "Plants grow green then wither to yellow."
      ),
      RootMeaningData(
        root = "ص-ف-ر",
        primaryMeaning = "yellow, empty, whistle",
        extendedMeaning = "Yellowness, emptiness.",
        quranUsage = "'You see it turn yellow (musfarran).' Safar is yellow. Safir is whistle.",
        notes = "Vegetation turns yellow before dying."
      ),
      RootMeaningData(
        root = "خ-ض-ر",
        primaryMeaning = "green, verdant",
        extendedMeaning = "Greenness, verdure.",
        quranUsage = "'Green (khudr) garments.' Khadir is green/verdant.",
        notes = "Green symbolizes life and Paradise."
      ),
      RootMeaningData(
        root = "ن-ض-ر",
        primaryMeaning = "bloom, flourish, radiant",
        extendedMeaning = "Blooming, flourishing, radiance.",
        quranUsage = "'Radiant (nadirah) faces.' Nadrah is radiance/bloom.",
        notes = "Faces of the blessed will be radiant."
      ),
      RootMeaningData(
        root = "ذ-ب-ل",
        primaryMeaning = "wilt, wither, fade",
        extendedMeaning = "Wilting, withering, fading.",
        quranUsage = "Dhubul is wilting. Dhabala is to wilt.",
        notes = "Flowers wilt; worldly beauty fades."
      ),

      // === MORE ABSTRACT AND PHILOSOPHICAL ===
      RootMeaningData(
        root = "ح-ق-ق",
        primaryMeaning = "truth, reality, right",
        extendedMeaning = "Truth, reality, and rights.",
        quranUsage = "'The truth (haqq) has come.' Haqq is truth. Haqiqah is reality.",
        notes = "Al-Haqq (The Truth) is Allah's name."
      ),
      RootMeaningData(
        root = "ب-ط-ل",
        primaryMeaning = "false, vain, nullify",
        extendedMeaning = "Falsehood, vanity, nullification.",
        quranUsage = "'Falsehood (batil) has vanished.' Batil is false. Ibtal is nullification.",
        notes = "Falsehood is inherently perishable."
      ),
      RootMeaningData(
        root = "ص-ح-ح",
        primaryMeaning = "correct, healthy, authentic",
        extendedMeaning = "Correctness, health, authenticity.",
        quranUsage = "Sahih is correct/authentic. Sihhah is health.",
        notes = "Sahih hadiths are authentically verified."
      ),
      RootMeaningData(
        root = "خ-ط-ء",
        primaryMeaning = "mistake, error, sin",
        extendedMeaning = "Mistake, error, and sin.",
        quranUsage = "'A great sin (khit').' Khata' is mistake. Khati'ah is sin.",
        notes = "Mistakes can be forgiven; deliberate sins require repentance."
      ),
      RootMeaningData(
        root = "ص-و-ب",
        primaryMeaning = "correct, right, pour",
        extendedMeaning = "Correctness, being right.",
        quranUsage = "Sawab is correctness. Musib is one who is right.",
        notes = "Seeking the correct opinion is important."
      ),
      RootMeaningData(
        root = "ع-و-ج",
        primaryMeaning = "crooked, bent, deviation",
        extendedMeaning = "Crookedness, deviation from straight.",
        quranUsage = "'No crookedness ('iwaj) in it.' 'Iwaj is crookedness.",
        notes = "The straight path has no crookedness."
      ),
      RootMeaningData(
        root = "ق-و-م",
        primaryMeaning = "straight, upright, stand",
        extendedMeaning = "Straightness, being upright.",
        quranUsage = "'The straight (mustaqim) path.' Qawam is uprightness. Istiqamah is steadfastness.",
        notes = "The straight path is the path of Islam."
      ),
      RootMeaningData(
        root = "م-ي-ل",
        primaryMeaning = "incline, lean, deviate",
        extendedMeaning = "Inclining, leaning, deviation.",
        quranUsage = "'Those who follow desires want you to incline (tamilu).' Mayl is inclination.",
        notes = "Inclining toward falsehood leads astray."
      ),
      RootMeaningData(
        root = "ع-د-ل",
        primaryMeaning = "justice, balance, equal",
        extendedMeaning = "Justice, balance, and equality.",
        quranUsage = "'Be just ('adilu).' 'Adl is justice. 'Adil is just.",
        notes = "Justice is commanded even toward enemies."
      ),
      RootMeaningData(
        root = "ج-و-ر",
        primaryMeaning = "injustice, tyranny, neighbor",
        extendedMeaning = "Injustice, tyranny.",
        quranUsage = "'Your Lord is not unjust (jawr).' Jawr is injustice.",
        notes = "Allah is never unjust to His servants."
      ),
      RootMeaningData(
        root = "ظ-ل-م",
        primaryMeaning = "wrong, oppress, darkness",
        extendedMeaning = "Wrongdoing, oppression, darkness.",
        quranUsage = "'Do not wrong (tazlimun) one another.' Zulm is oppression.",
        notes = "Zulm is putting something in the wrong place."
      ),
      RootMeaningData(
        root = "ق-س-ط",
        primaryMeaning = "equity, justice, portion",
        extendedMeaning = "Equity, fairness, proper portion.",
        quranUsage = "'Allah loves those who are equitable (muqsitin).' Qist is equity.",
        notes = "Al-Muqsit (The Equitable) is Allah's name."
      ),
      RootMeaningData(
        root = "و-ز-ن",
        primaryMeaning = "weigh, balance, measure",
        extendedMeaning = "Weighing, balance, measurement.",
        quranUsage = "'Give full measure and weigh (zinu) with justice.' Wazn is weight. Mizan is scale.",
        notes = "Fair weights and measures are obligatory."
      ),
      RootMeaningData(
        root = "ك-ي-ل",
        primaryMeaning = "measure, volume",
        extendedMeaning = "Measuring by volume.",
        quranUsage = "'Give full measure (kayl).' Kayl is measure. Mikyal is measuring vessel.",
        notes = "Cheating in measure is a grave sin."
      ),
      RootMeaningData(
        root = "ط-ف-ف",
        primaryMeaning = "give less, cheat",
        extendedMeaning = "Giving less than due, cheating in measure.",
        quranUsage = "'Woe to those who give less (mutaffifin).' Tatfif is cheating in measure.",
        notes = "Surah Al-Mutaffifin condemns cheating merchants."
      )
    )

    for (data in rootMeanings) {
      try {
        db.execSQL(
          """INSERT OR REPLACE INTO root_meanings (root, primary_meaning, extended_meaning, quran_usage, notes)
             VALUES (?, ?, ?, ?, ?)""",
          arrayOf(data.root, data.primaryMeaning, data.extendedMeaning, data.quranUsage, data.notes)
        )
      } catch (e: Exception) {
        // Ignore individual insert errors
      }
    }
  }

  private data class RootMeaningData(
    val root: String,
    val primaryMeaning: String,
    val extendedMeaning: String?,
    val quranUsage: String?,
    val notes: String?
  )
}
