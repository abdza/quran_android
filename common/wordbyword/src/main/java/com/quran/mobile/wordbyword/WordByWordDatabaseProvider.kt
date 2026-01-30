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
