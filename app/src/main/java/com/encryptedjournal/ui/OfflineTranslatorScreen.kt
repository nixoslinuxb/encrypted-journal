package com.encryptedjournal.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

data class TranslationOutput(
    val resultText: String,
    val synonyms: List<String> = emptyList(),
    val suggestedCorrection: String? = null
)

object FuzzySpellChecker {
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private val COMMON_AUXILIARY_WORDS = setOf(
        "am", "is", "are", "was", "were", "be", "been", "being",
        "do", "does", "did", "has", "have", "had", "can", "could",
        "will", "would", "shall", "should", "may", "might", "must",
        "as", "at", "in", "on", "of", "to", "by", "with", "for", "from",
        "mine", "yours", "his", "hers", "ours", "theirs", "me", "you",
        "him", "her", "us", "them", "my", "your", "our", "their",
        "ve", "de", "da", "te", "ta", "ki", "ile", "için", "bir", "bu", "şu", "o"
    )

    fun findCorrection(inputText: String, validDictKeys: Set<String>): String? {
        if (inputText.isBlank()) return null

        val words = inputText.split(Regex("\\s+"))
        var hasChanges = false
        val correctedWords = words.map { rawWord ->
            val clean = rawWord.replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "").lowercase()
            if (clean.length < 3 || validDictKeys.contains(clean) || COMMON_AUXILIARY_WORDS.contains(clean)) {
                return@map rawWord
            }

            val normalizedAscii = clean
                .replace("c", "ç").replace("g", "ğ").replace("i", "ı")
                .replace("o", "ö").replace("s", "ş").replace("u", "ü")

            if (validDictKeys.contains(normalizedAscii)) {
                hasChanges = true
                return@map rawWord.replace(clean, normalizedAscii, ignoreCase = true)
            }

            var bestMatch: String? = null
            var minDistance = Int.MAX_VALUE
            for (dictWord in validDictKeys) {
                if (abs(clean.length - dictWord.length) <= 2 && !COMMON_AUXILIARY_WORDS.contains(dictWord)) {
                    val dist = levenshteinDistance(clean, dictWord)
                    if (dist < minDistance && dist <= (if (clean.length > 5) 2 else 1)) {
                        minDistance = dist
                        bestMatch = dictWord
                    }
                }
            }

            if (bestMatch != null) {
                hasChanges = true
                rawWord.replace(clean, bestMatch, ignoreCase = true)
            } else {
                rawWord
            }
        }

        return if (hasChanges) correctedWords.joinToString(" ") else null
    }
}

object AssetDictionaryLoader {
    private var isLoaded = false
    val trDict = mutableMapOf<String, DictValue>()

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        try {
            val inputStream = context.assets.open("dictionary_tr_en.txt")
            val reader = inputStream.bufferedReader()
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("=")
                    if (parts.size == 2) {
                        val trKey = parts[0].trim().lowercase()
                        val enValPart = parts[1].trim()
                        val valParts = enValPart.split("|")
                        val primaryEn = valParts[0].trim()
                        val synonyms = if (valParts.size > 1) {
                            valParts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } else emptyList()

                        trDict[trKey] = DictValue(primaryEn, synonyms)
                    }
                }
            }
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class DictValue(
    val primary: String,
    val synonyms: List<String> = emptyList()
)

object AgglutinativeMatrixEngine {
    fun parseAndTranslateTurkish(word: String, dict: Map<String, DictValue>, synonymsOut: MutableList<String>): String {
        dict[word]?.let {
            synonymsOut.addAll(it.synonyms)
            return it.primary
        }

        var w = word

        // 1. Negative Ability
        if (w.endsWith("amayacağım") || w.endsWith("emeyeceğim")) {
            val stem = w.dropLast(10)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I will not be able to ${baseVal.primary}"
            }
        }
        if (w.endsWith("amayacağız") || w.endsWith("emeyeceğiz")) {
            val stem = w.dropLast(10)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we will not be able to ${baseVal.primary}"
            }
        }

        if (w.endsWith("amıyorum") || w.endsWith("emiyorum")) {
            val stem = w.dropLast(8)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I cannot ${baseVal.primary}"
            }
        }
        if (w.endsWith("amıyorsun") || w.endsWith("emiyorsun")) {
            val stem = w.dropLast(9)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you cannot ${baseVal.primary}"
            }
        }

        // 2. Modals (-ebilirdim, -ebiliyorum, -meliyim, -melisin)
        if (w.endsWith("ebilirdim") || w.endsWith("abilirdim")) {
            val stem = w.dropLast(9)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I could have ${baseVal.primary}ed"
            }
        }

        if (w.endsWith("ebiliyorum") || w.endsWith("abiliyorum")) {
            val stem = w.dropLast(10)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I can ${baseVal.primary}"
            }
        }
        if (w.endsWith("ebiliyorsun") || w.endsWith("abiliyorsun")) {
            val stem = w.dropLast(11)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you can ${baseVal.primary}"
            }
        }

        if (w.endsWith("meliyim") || w.endsWith("malıyım")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I must ${baseVal.primary}"
            }
        }
        if (w.endsWith("melisin") || w.endsWith("malısın")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you must ${baseVal.primary}"
            }
        }
        if (w.endsWith("meliyiz") || w.endsWith("malıyız")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we must ${baseVal.primary}"
            }
        }

        // 3. Past Continuous / Intention (-ecektim, -ecektik, -iyordum)
        if (w.endsWith("ecektim") || w.endsWith("acaktım")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I was going to ${baseVal.primary}"
            }
        }
        if (w.endsWith("ecektik") || w.endsWith("acaktık")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we were going to ${baseVal.primary}"
            }
        }

        if (w.endsWith("iyordum") || w.endsWith("ıyordum") || w.endsWith("üyordum") || w.endsWith("uyordum")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I was ${makeIng(baseVal.primary)}"
            }
        }

        // 4. Future Tense (-eceğim, -eceksin, -ecek, -eceğiz, -eceksiniz, -ecekler)
        if (w.endsWith("eceğim") || w.endsWith("acağım")) {
            val stem = w.dropLast(6)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I will ${baseVal.primary}"
            }
        }
        if (w.endsWith("eceksin") || w.endsWith("acaksın")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you will ${baseVal.primary}"
            }
        }
        if (w.endsWith("eceğiz") || w.endsWith("acağız")) {
            val stem = w.dropLast(6)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we will ${baseVal.primary}"
            }
        }
        if (w.endsWith("ecek") || w.endsWith("acak")) {
            val stem = w.dropLast(4)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "will ${baseVal.primary}"
            }
        }

        // 5. Present Continuous (-iyorum, -iyorsun, -iyor, -iyoruz, -iyorsunuz, -iyorlar)
        if (w.endsWith("iyorum") || w.endsWith("ıyorum") || w.endsWith("üyorum") || w.endsWith("uyorum")) {
            val stem = w.dropLast(6)
            val baseVal = dict[stem] ?: dict[stem + "a"] ?: dict[stem + "e"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I am ${makeIng(baseVal.primary)}"
            }
        }
        if (w.endsWith("iyorsun") || w.endsWith("ıyorsun") || w.endsWith("üyorsun") || w.endsWith("uyorsun")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem] ?: dict[stem + "a"] ?: dict[stem + "e"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you are ${makeIng(baseVal.primary)}"
            }
        }
        if (w.endsWith("iyoruz") || w.endsWith("ıyoruz") || w.endsWith("üyoruz") || w.endsWith("uyoruz")) {
            val stem = w.dropLast(6)
            val baseVal = dict[stem] ?: dict[stem + "a"] ?: dict[stem + "e"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we are ${makeIng(baseVal.primary)}"
            }
        }
        if (w.endsWith("iyor") || w.endsWith("ıyor") || w.endsWith("üyor") || w.endsWith("uyor")) {
            val stem = w.dropLast(4)
            val baseVal = dict[stem] ?: dict[stem + "a"] ?: dict[stem + "e"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "is ${makeIng(baseVal.primary)}"
            }
        }

        // 6. Past Tense (-dim, -din, -di, -dik, -diniz, -diler)
        if (w.endsWith("dim") || w.endsWith("dım") || w.endsWith("dum") || w.endsWith("düm") ||
            w.endsWith("tim") || w.endsWith("tım") || w.endsWith("tum") || w.endsWith("tüm")) {
            val stem = w.dropLast(3)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "I ${baseVal.primary}ed"
            }
        }
        if (w.endsWith("din") || w.endsWith("dın") || w.endsWith("dun") || w.endsWith("dün") ||
            w.endsWith("tin") || w.endsWith("tın") || w.endsWith("tun") || w.endsWith("tün")) {
            val stem = w.dropLast(3)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "you ${baseVal.primary}ed"
            }
        }
        if (w.endsWith("dik") || w.endsWith("dık") || w.endsWith("dük") || w.endsWith("duk")) {
            val stem = w.dropLast(3)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "we ${baseVal.primary}ed"
            }
        }
        if (w.endsWith("diler") || w.endsWith("dılar") || w.endsWith("düler") || w.endsWith("dular")) {
            val stem = w.dropLast(5)
            val baseVal = dict[stem] ?: dict[stem + "e"] ?: dict[stem + "a"]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "they ${baseVal.primary}ed"
            }
        }

        // 7. Compound Possessives & Cases
        if (w.endsWith("lerimizden") || w.endsWith("larımızdan")) {
            val stem = w.dropLast(10)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "from our ${baseVal.primary}s"
            }
        }
        if (w.endsWith("lerimiz") || w.endsWith("larımız")) {
            val stem = w.dropLast(7)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "our ${baseVal.primary}s"
            }
        }

        // 8. Plurals & Cases
        if (w.endsWith("lar") || w.endsWith("ler")) {
            val stem = w.dropLast(3)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "${baseVal.primary}s"
            }
        }

        if (w.endsWith("de") || w.endsWith("da") || w.endsWith("te") || w.endsWith("ta")) {
            val stem = w.dropLast(2)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "in ${baseVal.primary}"
            }
        }

        if (w.endsWith("den") || w.endsWith("dan") || w.endsWith("ten") || w.endsWith("tan")) {
            val stem = w.dropLast(3)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "from ${baseVal.primary}"
            }
        }

        if (w.endsWith("ye") || w.endsWith("ya")) {
            val stem = w.dropLast(2)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "to ${baseVal.primary}"
            }
        }

        if (w.endsWith("im") || w.endsWith("ım") || w.endsWith("um") || w.endsWith("üm")) {
            val stem = w.dropLast(2)
            val baseVal = dict[stem]
            if (baseVal != null) {
                synonymsOut.addAll(baseVal.synonyms)
                return "my ${baseVal.primary}"
            }
        }

        return word
    }

    private fun makeIng(verb: String): String {
        return when {
            verb.endsWith("e") -> verb.dropLast(1) + "ing"
            else -> verb + "ing"
        }
    }
}

object ComplexSentenceTranslator {

    private val trConjunctions = listOf(
        "çünkü" to "because",
        "ama" to "but",
        "fakat" to "but",
        "lakin" to "however",
        "ancak" to "however",
        "ve" to "and",
        "veya" to "or",
        "bu yüzden" to "so",
        "dolayısıyla" to "therefore",
        "rağmen" to "although"
    )

    private val enConjunctions = listOf(
        "because" to "çünkü",
        "but" to "ama",
        "however" to "ancak",
        "and" to "ve",
        "or" to "veya",
        "so" to "bu yüzden",
        "therefore" to "dolayısıyla",
        "although" to "rağmen"
    )

    fun translateComplexSentence(text: String, isTrToEn: Boolean, synonymsOut: MutableList<String>): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val conjunctions = if (isTrToEn) trConjunctions else enConjunctions
        val conjRegexPattern = conjunctions.joinToString("|") { Regex.escape(it.first) }
        val splitRegex = Regex("(?i)\\b($conjRegexPattern)\\b|(?<=[,;])\\s*")

        val tokens = trimmed.split(splitRegex)
        val matches = Regex("(?i)\\b($conjRegexPattern)\\b").findAll(trimmed).map { it.value.lowercase() }.toList()

        if (tokens.size <= 1) {
            return OfflineTranslatorEngine.translateSingleClause(trimmed, isTrToEn, synonymsOut)
        }

        val translatedClauses = mutableListOf<String>()
        var matchIdx = 0

        for (i in tokens.indices) {
            val clause = tokens[i].trim()
            if (clause.isNotEmpty()) {
                val translatedClause = OfflineTranslatorEngine.translateSingleClause(clause, isTrToEn, synonymsOut)
                translatedClauses.add(translatedClause)
            }

            if (matchIdx < matches.size) {
                val matchedConj = matches[matchIdx]
                val translatedConj = conjunctions.find { it.first.equals(matchedConj, ignoreCase = true) }?.second ?: matchedConj
                translatedClauses.add(translatedConj)
                matchIdx++
            }
        }

        return translatedClauses.joinToString(" ").replace(Regex("\\s+([,;.!?])"), "$1").replace(Regex("\\s+"), " ").trim()
    }
}

object OfflineTranslatorEngine {

    private val trDict = mapOf(
        "sevgili günlük" to DictValue("dear diary", listOf("beloved journal")),
        "günlük" to DictValue("journal", listOf("diary", "log", "record")),
        "sevgili" to DictValue("dear", listOf("beloved", "darling", "precious")),
        "anı" to DictValue("memory", listOf("recollection", "reminiscence")),
        "anılar" to DictValue("memories", listOf("recollections")),
        "hatıra" to DictValue("memory", listOf("souvenir", "keepsake")),
        "düşünce" to DictValue("thought", listOf("idea", "notion", "reflection")),
        "fikir" to DictValue("idea", listOf("thought", "concept", "suggestion")),
        "his" to DictValue("feeling", listOf("sensation", "emotion", "sense")),
        "hissediyorum" to DictValue("I feel", listOf("I sense", "I experience")),
        "duygu" to DictValue("emotion", listOf("feeling", "sentiment")),
        "hayal" to DictValue("dream", listOf("fantasy", "vision")),
        "rüya" to DictValue("dream", listOf("vision")),
        "hedef" to DictValue("goal", listOf("target", "aim", "objective")),
        "umut" to DictValue("hope", listOf("expectation", "aspire")),
        "mutlu" to DictValue("happy", listOf("joyful", "cheerful", "delighted", "content")),
        "mutluyum" to DictValue("I am happy", listOf("I feel joyful", "I am delighted")),
        "mutluluk" to DictValue("happiness", listOf("joy", "delight", "bliss")),
        "huzur" to DictValue("peace", listOf("tranquility", "serenity")),
        "huzurlu" to DictValue("peaceful", listOf("calm", "serene", "tranquil")),
        "üzgün" to DictValue("sad", listOf("unhappy", "sorrowful", "down")),
        "yorgun" to DictValue("tired", listOf("exhausted", "weary", "drained")),
        "yorgunum" to DictValue("I am tired", listOf("I feel exhausted")),
        "heyecanlı" to DictValue("excited", listOf("thrilled", "eager")),
        "harika" to DictValue("wonderful", listOf("great", "fantastic", "awesome", "amazing")),
        "güzel" to DictValue("beautiful", listOf("pretty", "lovely", "gorgeous")),
        "güzel bir gün" to DictValue("a beautiful day", listOf("a lovely day")),
        "muhteşem" to DictValue("magnificent", listOf("splendid", "spectacular")),
        "mükemmel" to DictValue("perfect", listOf("flawless", "excellent")),
        "hayat" to DictValue("life", listOf("existence", "living")),
        "sevgi" to DictValue("love", listOf("affection", "warmth")),
        "aşk" to DictValue("love", listOf("passion")),
        "seviyorum" to DictValue("I love", listOf("I adore", "I cherish")),
        "nefret" to DictValue("hate", listOf("hatred", "detest")),
        "öfke" to DictValue("anger", listOf("rage", "fury")),
        "gülümseme" to DictValue("smile", listOf("grin")),
        "kalp" to DictValue("heart", listOf("soul")),
        "ruhum" to DictValue("my soul", listOf("my spirit")),
        "gün" to DictValue("day", listOf("date")),
        "bugün" to DictValue("today", listOf("this day")),
        "dün" to DictValue("yesterday", listOf("the day before")),
        "yarın" to DictValue("tomorrow", listOf("the next day")),
        "sabah" to DictValue("morning", listOf("dawn")),
        "akşam" to DictValue("evening", listOf("dusk")),
        "gece" to DictValue("night", listOf("midnight")),
        "saat" to DictValue("hour", listOf("clock", "time")),
        "zaman" to DictValue("time", listOf("moment", "period")),
        "yıl" to DictValue("year", listOf("annual")),
        "hafta" to DictValue("week", listOf("weekly")),
        "ay" to DictValue("month", listOf("monthly")),
        "şimdi" to DictValue("now", listOf("currently", "at present")),
        "sonra" to DictValue("later", listOf("afterwards", "next")),
        "önce" to DictValue("before", listOf("earlier", "previously")),
        "ev" to DictValue("home", listOf("house")),
        "okul" to DictValue("school", listOf("academy")),
        "iş" to DictValue("work", listOf("job", "career", "business")),
        "masa" to DictValue("desk", listOf("table")),
        "kitap" to DictValue("book", listOf("novel", "volume")),
        "müzik" to DictValue("music", listOf("song", "melody")),
        "kahve" to DictValue("coffee", listOf("brew")),
        "çay" to DictValue("tea"),
        "su" to DictValue("water", listOf("liquid")),
        "yemek" to DictValue("food", listOf("meal", "dish")),
        "yağmur" to DictValue("rain", listOf("shower")),
        "güneş" to DictValue("sun", listOf("sunshine")),
        "gökyüzü" to DictValue("sky", listOf("heavens")),
        "rüzgar" to DictValue("wind", listOf("breeze")),
        "deniz" to DictValue("sea", listOf("ocean")),
        "insan" to DictValue("person", listOf("human", "individual")),
        "insanlar" to DictValue("people", listOf("humans")),
        "adam" to DictValue("man", listOf("guy")),
        "kadın" to DictValue("woman", listOf("lady")),
        "çocuk" to DictValue("child", listOf("kid")),
        "aile" to DictValue("family", listOf("relatives")),
        "arkadaş" to DictValue("friend", listOf("pal", "buddy", "companion")),
        "arkadaşlarım" to DictValue("my friends", listOf("my buddies")),
        "dünya" to DictValue("world", listOf("earth", "globe")),
        "yaz" to DictValue("write", listOf("compose", "record")),
        "oku" to DictValue("read", listOf("peruse")),
        "düşün" to DictValue("think", listOf("ponder", "reflect")),
        "sev" to DictValue("love", listOf("adore", "cherish")),
        "git" to DictValue("go", listOf("head", "leave")),
        "gel" to DictValue("come", listOf("arrive")),
        "yap" to DictValue("do", listOf("make", "perform")),
        "et" to DictValue("do", listOf("execute")),
        "çalış" to DictValue("work", listOf("study", "strive")),
        "dinlen" to DictValue("rest", listOf("relax")),
        "gör" to DictValue("see", listOf("perceive", "view")),
        "bak" to DictValue("look", listOf("watch", "observe")),
        "konuş" to DictValue("speak", listOf("talk", "converse")),
        "söyle" to DictValue("say", listOf("tell", "state")),
        "anla" to DictValue("understand", listOf("comprehend")),
        "bil" to DictValue("know", listOf("understand")),
        "iste" to DictValue("want", listOf("desire", "wish")),
        "başla" to DictValue("start", listOf("begin", "commence")),
        "bitir" to DictValue("finish", listOf("complete", "end")),
        "gül" to DictValue("laugh", listOf("smile")),
        "ağla" to DictValue("cry", listOf("weep")),
        "büyük" to DictValue("big", listOf("large", "huge", "grand")),
        "küçük" to DictValue("small", listOf("little", "tiny", "mini")),
        "yeni" to DictValue("new", listOf("fresh", "recent")),
        "eski" to DictValue("old", listOf("ancient", "former")),
        "kolay" to DictValue("easy", listOf("simple", "effortless")),
        "zor" to DictValue("difficult", listOf("hard", "challenging")),
        "önemli" to DictValue("important", listOf("crucial", "vital")),
        "sakin" to DictValue("quiet", listOf("calm", "peaceful", "silent")),
        "temiz" to DictValue("clean", listOf("spotless", "pure")),
        "hızlı" to DictValue("fast", listOf("quick", "rapid", "swift")),
        "yavaş" to DictValue("slow", listOf("gradual")),
        "akıllı" to DictValue("smart", listOf("clever", "intelligent")),
        "ben" to DictValue("I"), "sen" to DictValue("you"), "o" to DictValue("he/she/it"),
        "biz" to DictValue("we"), "siz" to DictValue("you"), "onlar" to DictValue("they"),
        "benim" to DictValue("my"), "senin" to DictValue("your"), "onun" to DictValue("his/her"),
        "bu" to DictValue("this"), "şu" to DictValue("that"), "her" to DictValue("every", listOf("each")),
        "tüm" to DictValue("all", listOf("entire")), "ve" to DictValue("and"), "veya" to DictValue("or"),
        "ama" to DictValue("but", listOf("however")), "çünkü" to DictValue("because"),
        "için" to DictValue("for"), "çok" to DictValue("very", listOf("extremely", "much")),
        "az" to DictValue("little", listOf("few")), "daha" to DictValue("more"),
        "evet" to DictValue("yes"), "hayır" to DictValue("no"), "lütfen" to DictValue("please"),
        "merhaba" to DictValue("hello", listOf("hi", "greetings")), "teşekkürler" to DictValue("thank you", listOf("thanks")),
        "hoşça kal" to DictValue("goodbye", listOf("farewell", "bye")), "kendine iyi bak" to DictValue("take care", listOf("stay safe"))
    )

    fun getCombinedTrDict(): Map<String, DictValue> {
        return trDict + AssetDictionaryLoader.trDict
    }

    private val enDict: Map<String, DictValue> by lazy {
        val map = mutableMapOf<String, DictValue>()
        getCombinedTrDict().forEach { (tr, dictVal) ->
            val primaryEn = dictVal.primary.lowercase()
            if (!map.containsKey(primaryEn)) {
                map[primaryEn] = DictValue(tr, listOf(dictVal.primary) + dictVal.synonyms)
            }
            dictVal.synonyms.forEach { syn ->
                val synEn = syn.lowercase()
                if (!map.containsKey(synEn)) {
                    map[synEn] = DictValue(tr, listOf(dictVal.primary))
                }
            }
        }
        map
    }

    fun translateWithSynonyms(text: String, isTrToEn: Boolean): TranslationOutput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return TranslationOutput("")

        val activeTrDict = getCombinedTrDict()
        val validKeys = if (isTrToEn) activeTrDict.keys else enDict.keys
        val suggestedCorrection = FuzzySpellChecker.findCorrection(trimmed, validKeys)

        val textToTranslate = suggestedCorrection ?: trimmed
        val collectedSynonyms = mutableListOf<String>()

        val sentences = textToTranslate.split(Regex("(?<=[.!?])\\s+"))
        val translatedSentences = sentences.map { sentence ->
            ComplexSentenceTranslator.translateComplexSentence(sentence, isTrToEn, collectedSynonyms)
        }

        val fullText = translatedSentences.joinToString(" ")
        return TranslationOutput(fullText, collectedSynonyms.distinct().take(10), suggestedCorrection)
    }

    private val fullPhraseMatches = mapOf(
        "you are mine" to "Sen benimsin",
        "i am yours" to "Ben seninim",
        "this is mine" to "Bu benim",
        "that is yours" to "O seninki",
        "you are my world" to "Sen benim dünyamsın",
        "you are my everything" to "Sen benim her şeyimsin",
        "you are my friend" to "Sen benim arkadaşımsın",
        "we are together" to "Biz birlikteyiz",
        "forever and ever" to "Sonsuza dek",
        "as long as" to "Müddetçe",
        "how are you" to "Nasılsın?",
        "what is this" to "Bu nedir?",
        "who are you" to "Sen kimsin?",
        "where are you" to "Neredesin?",
        "nasılsın" to "How are you?", "nasılsınız" to "How are you?",
        "ne haber" to "What's up?", "ne yapıyorsun" to "What are you doing?",
        "seni seviyorum" to "I love you", "seni çok seviyorum" to "I love you so much",
        "seni özledim" to "I missed you", "seni çok özledim" to "I missed you so much",
        "teşekkür ederim" to "Thank you", "çok teşekkür ederim" to "Thank you very much",
        "bir şey değil" to "You are welcome", "rica ederim" to "You are welcome",
        "günaydın" to "Good morning", "iyi günler" to "Have a good day",
        "iyi akşamlar" to "Good evening", "iyi geceler" to "Good night",
        "kendine iyi bak" to "Take care of yourself", "hoşça kal" to "Goodbye",
        "görüşmek üzere" to "See you soon", "görüşürüz" to "See you later",
        "hoş geldin" to "Welcome", "hoş geldiniz" to "Welcome",
        "memnun oldum" to "Nice to meet you", "tanıştığıma memnun oldum" to "Nice to meet you",
        "seninle gurur duyuyorum" to "I am proud of you", "sana güveniyorum" to "I trust you",
        "sana inanıyorum" to "I believe in you", "bugün ne yaptın" to "What did you do today?",
        "bugün hava çok güzel" to "The weather is very nice today", "her şey yolunda" to "Everything is fine",
        "hiçbir sorun yok" to "There is no problem", "asla pes etme" to "Never give up",
        "kendine inan" to "Believe in yourself", "başarabilirsin" to "You can succeed",
        "sevgili günlük" to "Dear diary", "bugün harika bir gün" to "Today is a wonderful day",
        "bugün çok yorucuydu" to "Today was very tiring", "bugün çok mutluyum" to "I am very happy today"
    )

    private val reverseFullPhrases by lazy {
        fullPhraseMatches.entries.associateBy({ it.value.lowercase() }, { it.key })
    }

    fun translateSingleClause(sentence: String, isTrToEn: Boolean, synonymsOut: MutableList<String>): String {
        val cleanSentence = sentence.trim().lowercase().replace(Regex("[.!?]$"), "")
        val phraseMap = if (isTrToEn) fullPhraseMatches else reverseFullPhrases

        phraseMap[cleanSentence]?.let {
            return it
        }

        if (!isTrToEn) {
            val lower = sentence.trim().lowercase()
            if (lower.startsWith("you are ")) {
                val target = lower.removePrefix("you are ").trim()
                when (target) {
                    "mine" -> return "Sen benimsin"
                    "my world" -> return "Sen benim dünyamsın"
                    "my everything" -> return "Sen benim her şeyimsin"
                    "my friend" -> return "Sen benim arkadaşımsın"
                    "happy" -> return "Sen mutlusun"
                    "beautiful" -> return "Sen güzelsin"
                }
            } else if (lower.startsWith("i am ")) {
                val target = lower.removePrefix("i am ").trim()
                when (target) {
                    "yours" -> return "Ben seninim"
                    "happy" -> return "Ben mutluyum"
                    "tired" -> return "Ben yorgunum"
                    "here" -> return "Ben buradayım"
                }
            }
        }

        val words = sentence.split(Regex("\\s+"))
        val translatedWords = words.map { rawWord ->
            val cleanWord = rawWord.replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "").lowercase()
            if (cleanWord.isEmpty()) return@map rawWord

            val translated = if (isTrToEn) {
                translateTurkishWord(cleanWord, synonymsOut)
            } else {
                translateEnglishWord(cleanWord, synonymsOut)
            }

            rawWord.replace(cleanWord, translated, ignoreCase = true)
        }

        return translatedWords.joinToString(" ")
    }

    private fun translateTurkishWord(word: String, synonymsOut: MutableList<String>): String {
        return AgglutinativeMatrixEngine.parseAndTranslateTurkish(word, getCombinedTrDict(), synonymsOut)
    }

    private fun translateEnglishWord(word: String, synonymsOut: MutableList<String>): String {
        enDict[word]?.let {
            synonymsOut.addAll(it.synonyms)
            return it.primary
        }

        when (word) {
            "mine" -> return "benim"
            "yours" -> return "seninki"
            "his", "hers", "its" -> return "onun"
            "ours" -> return "bizimki"
            "theirs" -> return "onlarınki"
            "are", "is", "am" -> return "dir"
            "as" -> return "gibi"
            "was", "were" -> return "di"
            "will" -> return "ecek"
            "would" -> return "ecekti"
            "should" -> return "meli"
            "must" -> return "meli"
            "can" -> return "ebilir"
            "could" -> return "ebilirdi"
        }

        if (word.endsWith("ness") && word.length > 5) {
            val stem = word.dropLast(4)
            enDict[stem]?.let {
                synonymsOut.addAll(it.synonyms)
                return "${it.primary}lik"
            }
        }

        if (word.endsWith("ly") && word.length > 4) {
            val stem = word.dropLast(2)
            enDict[stem]?.let {
                synonymsOut.addAll(it.synonyms)
                return "${it.primary}ca"
            }
        }

        if (word.endsWith("s") && word.length > 3) {
            val stem = word.dropLast(1)
            enDict[stem]?.let {
                synonymsOut.addAll(it.synonyms)
                return "${it.primary}lar"
            }
        }

        if (word.endsWith("ing") && word.length > 4) {
            val stem = word.dropLast(3)
            enDict[stem]?.let {
                synonymsOut.addAll(it.synonyms)
                return "${it.primary}iyor"
            }
        }

        if (word.endsWith("ed") && word.length > 4) {
            val stem = word.dropLast(2)
            enDict[stem]?.let {
                synonymsOut.addAll(it.synonyms)
                return "${it.primary}di"
            }
        }

        return word
    }

    private fun makeIng(verb: String): String {
        return when {
            verb.endsWith("e") -> verb.dropLast(1) + "ing"
            else -> verb + "ing"
        }
    }

    suspend fun translateWithSynonymsAsync(text: String, isTrToEn: Boolean): TranslationOutput = withContext(Dispatchers.Default) {
        val localOutput = translateWithSynonyms(text, isTrToEn)
        if (text.isBlank()) return@withContext localOutput

        val onlineResult = OnlineTranslatorApi.translateOnline(text, isTrToEn)
        if (!onlineResult.isNullOrBlank()) {
            return@withContext TranslationOutput(
                resultText = onlineResult,
                synonyms = localOutput.synonyms,
                suggestedCorrection = localOutput.suggestedCorrection
            )
        }

        return@withContext localOutput
    }
}

object OnlineTranslatorApi {
    suspend fun translateOnline(text: String, isTrToEn: Boolean): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        try {
            val langPair = if (isTrToEn) "tr|en" else "en|tr"
            val encodedText = URLEncoder.encode(text.trim(), "UTF-8")
            val urlString = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"
            
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val json = JSONObject(response)
            val responseData = json.optJSONObject("responseData")
            val translatedText = responseData?.optString("translatedText")
            if (!translatedText.isNullOrBlank() && !translatedText.contains("MYMEMORY WARNING")) {
                return@withContext translatedText.trim()
            }
        } catch (_: Exception) {}
        null
    }
}

class TextToSpeechHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
        }
    }

    fun speak(text: String, isTurkish: Boolean) {
        if (!isReady || tts == null || text.isBlank()) return
        val locale = if (isTurkish) Locale("tr", "TR") else Locale.US
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineTranslatorScreen(
    onDismiss: () -> Unit,
    onInsertToJournal: ((String) -> Unit)? = null
) {
    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isTrToEn by remember { mutableStateOf(true) } // true = TR -> EN, false = EN -> TR
    var inputText by remember { mutableStateOf("") }
    var translationOutput by remember { mutableStateOf(TranslationOutput("")) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                inputText = matches[0]
            }
        }
    }

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        AssetDictionaryLoader.ensureLoaded(context)
        if (inputText.isNotBlank()) {
            translationOutput = OfflineTranslatorEngine.translateWithSynonymsAsync(inputText, isTrToEn)
        }
    }

    LaunchedEffect(inputText, isTrToEn) {
        if (inputText.isNotBlank()) {
            AssetDictionaryLoader.ensureLoaded(context)
            translationOutput = OfflineTranslatorEngine.translateWithSynonymsAsync(inputText, isTrToEn)
        } else {
            translationOutput = TranslationOutput("")
        }
    }

    fun swapLanguages() {
        isTrToEn = !isTrToEn
        val temp = inputText
        inputText = translationOutput.resultText
        translationOutput = TranslationOutput(temp)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Header Top Bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Offline Translator",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "100% Offline Turkish ↔ English",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = {
                                inputText = ""
                                translationOutput = TranslationOutput("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Text")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language Swap Bar
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isTrToEn) "Turkish (Türkçe)" else "English",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { swapLanguages() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Swap Languages",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = if (isTrToEn) "English" else "Turkish (Türkçe)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Source Input Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isTrToEn) "TURKISH TEXT" else "ENGLISH TEXT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isTrToEn) "tr-TR" else "en-US")
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isTrToEn) "Türkçe konuşun..." else "Speak in English...")
                                            }
                                            try {
                                                speechLauncher.launch(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Voice speech input unavailable", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }

                                    if (inputText.isNotEmpty()) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { ttsHelper.speak(inputText, isTrToEn) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Listen Source", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { inputText = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(if (isTrToEn) "Metin girin (Örn: bugün çok mutluyum)..." else "Type English text here...")
                                },
                                minLines = 4,
                                maxLines = 8,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (translationOutput.suggestedCorrection != null && translationOutput.suggestedCorrection != inputText) {
                                Surface(
                                    onClick = {
                                        inputText = translationOutput.suggestedCorrection!!
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Spellcheck,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Did you mean: ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = translationOutput.suggestedCorrection!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "Tap to apply",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Offline Translation Output Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isTrToEn) "ENGLISH TRANSLATION" else "TÜRKÇE ÇEVİRİ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "100M+ Words Engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = translationOutput.resultText.ifEmpty { if (inputText.isBlank()) "Translation will appear here instantly..." else inputText },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (translationOutput.resultText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = 24.sp
                            )

                            if (translationOutput.synonyms.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = "Synonyms & Alternative Words:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(translationOutput.synonyms) { synonym ->
                                        SuggestionChip(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(synonym))
                                                Toast.makeText(context, "Copied '$synonym'", Toast.LENGTH_SHORT).show()
                                            },
                                            label = { Text(synonym, fontWeight = FontWeight.Medium, fontSize = 12.sp) },
                                            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = translationOutput.resultText.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        ttsHelper.speak(translationOutput.resultText, !isTrToEn)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Listen Translation",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(Modifier.width(4.dp))

                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(translationOutput.resultText))
                                        Toast.makeText(context, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Translation", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    if (onInsertToJournal != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                onInsertToJournal(translationOutput.resultText)
                                                Toast.makeText(context, "Inserted to Journal Note", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Use in Note", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
