package com.encryptedjournal.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class RssArticle(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String,
    val category: String,
    val sourceName: String,
    val imageUrl: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val pubDateMillis: Long = System.currentTimeMillis(),
    val videoUrl: String? = null
)

object RssDateParser {
    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    )

    fun parseToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (format in dateFormats) {
            try {
                val date = format.parse(dateStr.trim())
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}

object RssParser {
    suspend fun fetchFeed(feedUrl: String, category: String, sourceName: String): List<RssArticle> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<RssArticle>()
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) Chrome/120.0.0.0")

            val inputStream = connection.inputStream
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentTitle = ""
            var currentDesc = ""
            var currentContent = ""
            var currentLink = ""
            var currentPubDate = ""
            var currentImage: String? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            insideItem = true
                            currentTitle = ""
                            currentDesc = ""
                            currentContent = ""
                            currentLink = ""
                            currentPubDate = ""
                            currentImage = null
                        } else if (insideItem) {
                            when (tagName.lowercase()) {
                                "title" -> currentTitle = readTagText(parser)
                                "description", "summary" -> currentDesc = readTagText(parser)
                                "content", "content:encoded" -> currentContent = readTagText(parser)
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val textLink = readTagText(parser)
                                    currentLink = if (!href.isNullOrBlank()) href else textLink.trim()
                                }
                                "pubdate", "published", "updated" -> currentPubDate = readTagText(parser)
                                "media:content", "media:thumbnail", "enclosure", "og:image", "image" -> {
                                    val urlAttr = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue(null, "href")
                                        ?: parser.getAttributeValue(null, "src")
                                    if (!urlAttr.isNullOrBlank()) {
                                        val cleanUrl = if (urlAttr.startsWith("//")) "https:$urlAttr" else urlAttr
                                        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                                            if (currentImage == null) {
                                                currentImage = cleanUrl
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            insideItem = false
                            if (currentTitle.isNotBlank()) {
                                val rawText = if (currentContent.length > currentDesc.length) currentContent else currentDesc
                                val cleanFullText = parseHtmlToText(rawText)
                                val extractedImages = extractImageUrlsFromHtml(rawText)
                                val heroImage = currentImage ?: extractedImages.firstOrNull()
                                val allMediaList = (listOfNotNull(heroImage) + extractedImages).distinct()
                                val parsedMillis = RssDateParser.parseToMillis(currentPubDate)
                                val videoUrl = extractVideoUrlFromHtml(rawText) ?: extractVideoUrlFromHtml(currentContent)

                                articles.add(
                                    RssArticle(
                                        title = parseHtmlToText(currentTitle),
                                        description = cleanFullText,
                                        link = currentLink,
                                        pubDate = if (currentPubDate.length > 16) currentPubDate.take(16) else currentPubDate.ifEmpty { "Today" },
                                        category = category,
                                        sourceName = sourceName,
                                        imageUrl = heroImage,
                                        mediaUrls = allMediaList,
                                        pubDateMillis = parsedMillis,
                                        videoUrl = videoUrl
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        articles.distinctBy { "${it.title.trim().lowercase()}_${it.link.trim().lowercase()}" }
    }

    private fun extractVideoUrlFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val iframeRegex = Regex("(?i)<iframe[^>]+src=[\"']([^\"'\\s]+)[\"']")
        val iframeMatch = iframeRegex.find(html)
        if (iframeMatch != null) {
            val src = iframeMatch.groupValues[1].trim()
            if (src.contains("youtube") || src.contains("vimeo") || src.contains("video") || src.contains("embed")) {
                return if (src.startsWith("//")) "https:$src" else src
            }
        }
        val videoRegex = Regex("(?i)<video[^>]+src=[\"']([^\"'\\s]+)[\"']")
        val videoMatch = videoRegex.find(html)
        if (videoMatch != null) {
            val src = videoMatch.groupValues[1].trim()
            return if (src.startsWith("//")) "https:$src" else src
        }
        val mp4Regex = Regex("(?i)https?://[^\"'\\s]+\\.(mp4|webm|m3u8)")
        val mp4Match = mp4Regex.find(html)
        if (mp4Match != null) {
            return mp4Match.value
        }
        return null
    }

    private fun extractImageUrlsFromHtml(html: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val regex = Regex("(?i)<img[^>]+(?:src|data-src|srcset)=[\"']([^\"'\\s]+)[\"']")
            regex.findAll(html).forEach { match ->
                var url = match.groupValues[1].trim()
                if (url.startsWith("//")) {
                    url = "https:$url"
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    list.add(url)
                }
            }
        } catch (_: Exception) {}
        return list.distinct()
    }

    private fun readTagText(parser: XmlPullParser): String {
        return try {
            parser.nextText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseHtmlToText(html: String): String {
        return try {
            val cleanedHtml = html
                .replace(Regex("(?i)<script[\\s\\S]*?</script>"), "")
                .replace(Regex("(?i)<style[\\s\\S]*?</style>"), "")
                .replace(Regex("(?i)<img[^>]*>"), "")
                .replace(Regex("(?i)<object[\\s\\S]*?</object>"), "")
                .replace(Regex("(?i)<iframe[\\s\\S]*?</iframe>"), "")

            val spans = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(cleanedHtml)
            }

            var textResult = spans.toString()

            textResult = textResult
                .replace("\uFFFC", "")
                .replace("OBJ", "")
                .replace("￼", "")
                .replace(Regex("(?i)\\(adsbygoogle\\s*=\\s*window\\.adsbygoogle\\s*\\|\\|\\s*\\[\\]\\)\\.push\\(\\{.*?\\}\\);?"), "")
                .replace(Regex("(?i)adsbygoogle.*?push\\(\\{.*?\\}\\);?"), "")
                .replace(Regex("(?i)window\\.adsbygoogle.*?\\};?"), "")
                .replace(Regex("\n\n+"), "\n\n")
                .trim()

            textResult
        } catch (_: Exception) {
            html.replace(Regex("<[^>]*>"), " ")
                .replace(Regex("(?i)\\(adsbygoogle.*?\\);?"), "")
                .replace("\uFFFC", "")
                .replace("OBJ", "")
                .replace("￼", "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}

data class OpmlFeed(
    val title: String,
    val xmlUrl: String,
    val category: String = "Imported"
)

object OpmlParser {
    suspend fun parseOpml(context: Context, uri: Uri): List<OpmlFeed> = withContext(Dispatchers.IO) {
        val feeds = mutableListOf<OpmlFeed>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext feeds
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentCategory = "Imported"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    if (tagName.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "url")
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                            ?: "Imported Feed"

                        if (!xmlUrl.isNullOrBlank()) {
                            feeds.add(OpmlFeed(title = title, xmlUrl = xmlUrl, category = currentCategory))
                        } else {
                            val text = parser.getAttributeValue(null, "text")
                            if (!text.isNullOrBlank()) {
                                currentCategory = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        feeds
    }
}

data class CustomRssFeed(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val category: String
)

object RssStorage {
    private const val PREF_NAME = "rss_feed_prefs"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_FEEDS = "saved_feeds"

    val DEFAULT_CATEGORIES = listOf("Inspiration", "News", "Tech", "World", "Self Growth")

    fun getCategories(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedString = prefs.getString(KEY_CATEGORIES, null)
        if (savedString.isNullOrBlank()) {
            return listOf("All") + DEFAULT_CATEGORIES
        }
        return try {
            val jsonArray = JSONArray(savedString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val cat = jsonArray.getString(i)
                if (cat != "All" && cat.isNotBlank()) list.add(cat)
            }
            listOf("All") + list.distinct()
        } catch (_: Exception) {
            listOf("All") + DEFAULT_CATEGORIES
        }
    }

    fun saveCategories(context: Context, categories: List<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        categories.filter { it != "All" && it.isNotBlank() }.distinct().forEach {
            jsonArray.put(it)
        }
        prefs.edit().putString(KEY_CATEGORIES, jsonArray.toString()).apply()
    }

    fun getSavedFeeds(context: Context): List<CustomRssFeed> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedString = prefs.getString(KEY_FEEDS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(savedString)
            val feeds = mutableListOf<CustomRssFeed>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                feeds.add(
                    CustomRssFeed(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Custom Feed"),
                        url = obj.optString("url", ""),
                        category = obj.optString("category", "General")
                    )
                )
            }
            feeds
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFeeds(context: Context, feeds: List<CustomRssFeed>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        feeds.forEach { feed ->
            val obj = JSONObject()
            obj.put("id", feed.id)
            obj.put("name", feed.name)
            obj.put("url", feed.url)
            obj.put("category", feed.category)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_FEEDS, jsonArray.toString()).apply()
    }

    private const val KEY_SAVED_ARTICLES = "saved_articles"

    fun getSavedArticles(context: Context): List<RssArticle> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SAVED_ARTICLES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<RssArticle>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RssArticle(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        description = obj.optString("description", ""),
                        link = obj.optString("link", ""),
                        pubDate = obj.optString("pubDate", ""),
                        category = obj.optString("category", "Saved"),
                        sourceName = obj.optString("sourceName", "Saved Feed"),
                        imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSavedArticles(context: Context, articles: List<RssArticle>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        articles.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("description", item.description)
            obj.put("link", item.link)
            obj.put("pubDate", item.pubDate)
            obj.put("category", item.category)
            obj.put("sourceName", item.sourceName)
            obj.put("imageUrl", item.imageUrl)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ARTICLES, array.toString()).apply()
    }

    private const val KEY_READ_ARTICLE_IDS = "read_article_ids"

    fun getReadArticleIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_ARTICLE_IDS, emptySet()) ?: emptySet()
    }

    fun markArticleAsRead(context: Context, articleId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentSet = getReadArticleIds(context).toMutableSet()
        currentSet.add(articleId)
        prefs.edit().putStringSet(KEY_READ_ARTICLE_IDS, currentSet).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedScreen(
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf(RssStorage.getCategories(context)) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var sectionToEdit by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    var articles by remember { mutableStateOf<List<RssArticle>>(emptyList()) }
    var savedArticles by remember { mutableStateOf(RssStorage.getSavedArticles(context)) }
    var readArticleIds by remember { mutableStateOf(RssStorage.getReadArticleIds(context)) }
    val clipboardManager = LocalClipboardManager.current

    fun markArticleRead(articleId: String) {
        if (!readArticleIds.contains(articleId)) {
            val updated = readArticleIds + articleId
            readArticleIds = updated
            RssStorage.markArticleAsRead(context, articleId)
        }
    }

    val unreadCountsByCategory = remember(articles, readArticleIds) {
        articles
            .filter { !readArticleIds.contains(it.id) }
            .groupBy { it.category }
            .mapValues { it.value.size }
    }

    var isLoading by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var articleToRead by remember { mutableStateOf<RssArticle?>(null) }
    var expandedArticleId by remember { mutableStateOf<String?>(null) }
    var showAddFeedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!categories.contains("Saved")) {
            val updatedCats = categories + "Saved"
            categories = updatedCats
            RssStorage.saveCategories(context, updatedCats)
        }
        val savedFeeds = RssStorage.getSavedFeeds(context)
        if (savedFeeds.isNotEmpty()) {
            isLoading = true
            val loadedArticles = mutableListOf<RssArticle>()
            for (feed in savedFeeds) {
                val items = RssParser.fetchFeed(feed.url, feed.category, feed.name)
                loadedArticles.addAll(items)
            }
            articles = loadedArticles
            isLoading = false
        }
    }

    fun saveArticleToSavedSection(article: RssArticle) {
        if (savedArticles.none { it.id == article.id }) {
            val updated = listOf(article.copy(category = "Saved")) + savedArticles
            savedArticles = updated
            RssStorage.saveSavedArticles(context, updated)
            if (!categories.contains("Saved")) {
                val updatedCats = categories + "Saved"
                categories = updatedCats
                RssStorage.saveCategories(context, updatedCats)
            }
            if (articles.none { it.id == article.id }) {
                articles = listOf(article.copy(category = "Saved")) + articles
            }
            Toast.makeText(context, "Saved to 'Saved' Section", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Already in Saved Section", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeArticleFromSavedSection(article: RssArticle) {
        val updated = savedArticles.filter { it.id != article.id }
        savedArticles = updated
        RssStorage.saveSavedArticles(context, updated)
        articles = articles.filter { !(it.id == article.id && it.category == "Saved") }
        Toast.makeText(context, "Removed from Saved Section", Toast.LENGTH_SHORT).show()
    }

    fun copyArticleContent(article: RssArticle) {
        val textToCopy = "${article.title}\n\n${article.description}"
        clipboardManager.setText(AnnotatedString(textToCopy))
        Toast.makeText(context, "Copied article content to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun copyArticleLink(article: RssArticle) {
        val linkToCopy = article.link.ifEmpty { article.title }
        clipboardManager.setText(AnnotatedString(linkToCopy))
        Toast.makeText(context, "Copied article link to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun refreshAllFeeds() {
        scope.launch {
            val savedFeeds = RssStorage.getSavedFeeds(context)
            if (savedFeeds.isNotEmpty()) {
                isLoading = true
                val freshlyFetched = mutableListOf<RssArticle>()
                for (feed in savedFeeds) {
                    val items = RssParser.fetchFeed(feed.url, feed.category, feed.name)
                    freshlyFetched.addAll(items)
                }
                if (freshlyFetched.isNotEmpty()) {
                    articles = freshlyFetched
                    Toast.makeText(context, "Refreshed feeds (${freshlyFetched.size} articles)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Refreshed feeds", Toast.LENGTH_SHORT).show()
                }
                isLoading = false
            } else {
                Toast.makeText(context, "No RSS feeds saved to refresh", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val opmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                val opmlFeeds = OpmlParser.parseOpml(context, uri)
                if (opmlFeeds.isNotEmpty()) {
                    val importedArticles = mutableListOf<RssArticle>()
                    val newCategories = opmlFeeds.map { it.category }.filter { it.isNotBlank() }
                    val updatedCatList = (categories + newCategories).distinct()
                    categories = updatedCatList
                    RssStorage.saveCategories(context, updatedCatList)

                    val savedList = RssStorage.getSavedFeeds(context).toMutableList()

                    for (feed in opmlFeeds.take(12)) {
                        savedList.add(CustomRssFeed(name = feed.title, url = feed.xmlUrl, category = feed.category))
                        val items = RssParser.fetchFeed(feed.xmlUrl, feed.category, feed.title)
                        importedArticles.addAll(items)
                    }

                    RssStorage.saveFeeds(context, savedList)

                    if (importedArticles.isNotEmpty()) {
                        articles = importedArticles + articles
                        Toast.makeText(context, "Imported ${opmlFeeds.size} feeds from OPML!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Parsed ${opmlFeeds.size} feeds from OPML file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No valid OPML feeds found in file", Toast.LENGTH_SHORT).show()
                }
                isLoading = false
                showAddFeedDialog = false
            }
        }
    }

    val filteredArticles = remember(articles, savedArticles, selectedCategory, searchQuery) {
        val targetList = if (selectedCategory == "Saved") {
            (savedArticles + articles.filter { it.category == "Saved" })
        } else {
            articles
        }

        targetList
            .distinctBy { "${it.title.trim().lowercase()}_${it.link.trim().lowercase()}" }
            .filter { item ->
                val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
                val matchesQuery = searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.description.contains(searchQuery, ignoreCase = true) ||
                        item.sourceName.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesQuery
            }
            .sortedByDescending { it.pubDateMillis }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RssFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "RSS Journal Reader",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily inspiration & global news",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshAllFeeds() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Feeds")
                        }
                        IconButton(onClick = { showAddFeedDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Feed")
                        }
                    }
                )

                // Search Bar & Categories
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search articles or feeds...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(categories) { category ->
                            val unreadCount = if (category == "All") {
                                articles.count { !readArticleIds.contains(it.id) }
                            } else {
                                unreadCountsByCategory[category] ?: 0
                            }

                            if (category == "All") {
                                FilterChip(
                                    selected = selectedCategory == "All",
                                    onClick = { selectedCategory = "All" },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("All", fontWeight = FontWeight.Medium)
                                            if (unreadCount > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (selectedCategory == "All") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (selectedCategory == "All") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            } else {
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(category, fontWeight = FontWeight.Medium)
                                            if (unreadCount > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (selectedCategory == category) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Section Feeds",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    sectionToEdit = category
                                                }
                                        )
                                    }
                                )
                            }
                        }
                        item {
                            AssistChip(
                                onClick = { showAddCategoryDialog = true },
                                label = { Text("+ Section", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            )
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Article List
                if (filteredArticles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RssFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No articles match \"$searchQuery\"" else "No RSS Feeds Added Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Try searching for a different keyword" else "Tap '+' above to add a custom RSS feed URL or import an OPML file.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (articles.isEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { showAddFeedDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add RSS Feed")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredArticles, key = { it.id }) { article ->
                            val isExpanded = expandedArticleId == article.id
                            val isSavedCategory = selectedCategory == "Saved" || article.category == "Saved"

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            saveArticleToSavedSection(article)
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            if (isSavedCategory) {
                                                removeArticleFromSavedSection(article)
                                            } else {
                                                copyArticleContent(article)
                                            }
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> if (isSavedCategory) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    }

                                    val icon = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Bookmark
                                        SwipeToDismissBoxValue.EndToStart -> if (isSavedCategory) Icons.Default.Delete else Icons.Default.ContentCopy
                                        SwipeToDismissBoxValue.Settled -> null
                                    }

                                    val alignment = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        SwipeToDismissBoxValue.Settled -> Alignment.Center
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = alignment
                                    ) {
                                        icon?.let {
                                            Icon(
                                                imageVector = it,
                                                contentDescription = null,
                                                tint = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                                    MaterialTheme.colorScheme.primary
                                                else if (isSavedCategory)
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    shadowElevation = if (isExpanded) 6.dp else 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = article.sourceName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Text(
                                                text = article.pubDate,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = article.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (article.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = article.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 22.sp
                                            )
                                        }

                                        val youtubeId = remember(article.videoUrl, article.link, article.description) {
                                            extractYouTubeVideoId(article.videoUrl ?: "")
                                                ?: extractYouTubeVideoId(article.link)
                                                ?: extractYouTubeVideoId(article.description)
                                        }

                                        // Dedicated YouTube Video Player or Video Player or Hero Image
                                        if (youtubeId != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            YouTubeVideoPlayerCard(videoId = youtubeId)
                                        } else if (!article.videoUrl.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            FeedVideoPlayer(videoUrl = article.videoUrl)
                                        } else if (!article.imageUrl.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            AsyncImage(
                                                model = article.imageUrl,
                                                contentDescription = article.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                            )
                                        }

                                        if (article.mediaUrls.size > 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                items(article.mediaUrls.take(6)) { imgUrl ->
                                                    AsyncImage(
                                                        model = imgUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(70.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    markArticleRead(article.id)
                                                    expandedArticleId = if (expandedArticleId == article.id) null else article.id
                                                }
                                        ) {
                                            Text(
                                                text = if (isExpanded) "Tap to Collapse" else "Tap to Read • Double-tap link",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
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

    // Native In-App Article Reader Dialog
    if (articleToRead != null) {
        val currentArticle = articleToRead!!
        val clipboardManager = LocalClipboardManager.current
        var selectedTab by remember { mutableIntStateOf(0) }

        LaunchedEffect(currentArticle.id) {
            markArticleRead(currentArticle.id)
        }

        Dialog(
            onDismissRequest = { articleToRead = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentArticle.sourceName.ifEmpty { "RSS Feed" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentArticle.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { articleToRead = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString("${currentArticle.title}\n\n${currentArticle.description}"))
                                Toast.makeText(context, "Copied article content", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text")
                            }
                            if (currentArticle.link.isNotBlank()) {
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentArticle.link))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }) {
                                    Icon(Icons.Default.Language, contentDescription = "Open Link")
                                }
                            }
                        }
                    )

                    if (currentArticle.link.isNotBlank()) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            SegmentedButton(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Clean Text")
                            }
                            SegmentedButton(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Web View")
                            }
                        }
                    }

                    if (selectedTab == 0 || currentArticle.link.isBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = currentArticle.category,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                Text(
                                    text = currentArticle.pubDate,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = currentArticle.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 32.sp
                            )

                            val articleImages = remember(currentArticle) {
                                (listOfNotNull(currentArticle.imageUrl) + currentArticle.mediaUrls).distinct()
                            }

                            if (articleImages.isNotEmpty()) {
                                if (articleImages.size == 1) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                    ) {
                                        AsyncImage(
                                            model = articleImages.first(),
                                            contentDescription = "Article Hero Image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(articleImages) { imgUrl ->
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                shadowElevation = 4.dp,
                                                modifier = Modifier
                                                    .width(280.dp)
                                                    .height(200.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                            ) {
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = "Article Gallery Image",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = currentArticle.description.ifEmpty { "No text content available for this feed entry." },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 26.sp
                            )
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    webViewClient = WebViewClient()
                                    loadUrl(currentArticle.link)
                                }
                            },
                            update = { webView ->
                                webView.loadUrl(currentArticle.link)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Custom Feed Add Dialog
    if (showAddFeedDialog) {
        var newUrl by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        val nonAllCategories = remember(categories) { categories.filter { it != "All" } }
        var selectedSection by remember { mutableStateOf(nonAllCategories.firstOrNull() ?: "General") }

        AlertDialog(
            onDismissRequest = { showAddFeedDialog = false },
            title = { Text("Add RSS Feed or Import OPML") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Import OPML File Button
                    OutlinedButton(
                        onClick = {
                            opmlPickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import OPML File (.opml / .xml)")
                    }

                    HorizontalDivider()

                    Text("Or enter feed details manually:", style = MaterialTheme.typography.labelMedium)

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Feed Name") },
                        placeholder = { Text("e.g. Tech Blog, Daily News") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("RSS Feed URL") },
                        placeholder = { Text("https://example.com/rss.xml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Assign to Section:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(nonAllCategories) { cat ->
                            FilterChip(
                                selected = selectedSection == cat,
                                onClick = { selectedSection = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUrl.isNotBlank()) {
                            scope.launch {
                                isLoading = true
                                val list = RssParser.fetchFeed(newUrl, selectedSection, newName.ifEmpty { "Custom Feed" })
                                if (list.isNotEmpty()) {
                                    articles = list + articles
                                    val saved = RssStorage.getSavedFeeds(context).toMutableList()
                                    saved.add(CustomRssFeed(name = newName.ifEmpty { "Custom Feed" }, url = newUrl, category = selectedSection))
                                    RssStorage.saveFeeds(context, saved)
                                    Toast.makeText(context, "Added ${list.size} articles to '$selectedSection'", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not load RSS feed", Toast.LENGTH_SHORT).show()
                                }
                                isLoading = false
                                showAddFeedDialog = false
                            }
                        }
                    },
                    enabled = newUrl.isNotBlank()
                ) {
                    Text("Add Feed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Category Section Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false; newCategoryInput = "" },
            title = { Text("Add New Section") },
            text = {
                OutlinedTextField(
                    value = newCategoryInput,
                    onValueChange = { newCategoryInput = it },
                    label = { Text("Section Name") },
                    placeholder = { Text("e.g. Finance, Sports, Gaming") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryInput.trim()
                        if (trimmed.isNotBlank() && !categories.contains(trimmed)) {
                            val updated = categories + trimmed
                            categories = updated
                            RssStorage.saveCategories(context, updated)
                            selectedCategory = trimmed
                            Toast.makeText(context, "Added '$trimmed' section", Toast.LENGTH_SHORT).show()
                        }
                        showAddCategoryDialog = false;
                        newCategoryInput = ""
                    },
                    enabled = newCategoryInput.isNotBlank()
                ) {
                    Text("Create Section")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false; newCategoryInput = "" }) { Text("Cancel") }
            }
        )
    }

    // Delete Category Confirmation Dialog
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Section") },
            text = { Text("Are you sure you want to delete the '$categoryToDelete' section? Feeds in this category will also be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDelete?.let { cat ->
                            val updated = categories.filter { it != cat }
                            categories = updated
                            RssStorage.saveCategories(context, updated)
                            val savedFeeds = RssStorage.getSavedFeeds(context).filter { it.category != cat }
                            RssStorage.saveFeeds(context, savedFeeds)
                            articles = articles.filter { it.category != cat }
                            if (selectedCategory == cat) {
                                selectedCategory = "All"
                            }
                            Toast.makeText(context, "Deleted '$cat' section", Toast.LENGTH_SHORT).show()
                        }
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Section Feeds Dialog
    sectionToEdit?.let { category ->
        EditSectionFeedsDialog(
            category = category,
            context = context,
            onFeedsUpdated = {
                refreshAllFeeds()
            },
            onDeleteSection = {
                categoryToDelete = category
            },
            onDismiss = {
                sectionToEdit = null
            }
        )
    }
}

@Composable
fun EditSectionFeedsDialog(
    category: String,
    context: Context,
    onFeedsUpdated: () -> Unit,
    onDeleteSection: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var savedFeeds by remember { mutableStateOf(RssStorage.getSavedFeeds(context).filter { it.category.equals(category, ignoreCase = true) }) }
    var newFeedName by remember { mutableStateOf("") }
    var newFeedUrl by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Manage '$category' Feeds",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // List of Feeds in this section
                Text(
                    text = "Feeds in $category (${savedFeeds.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (savedFeeds.isEmpty()) {
                    Text(
                        text = "No custom feeds added to this section yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedFeeds.forEach { feed ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = feed.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = feed.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val allFeeds = RssStorage.getSavedFeeds(context).filter { it.id != feed.id }
                                            RssStorage.saveFeeds(context, allFeeds)
                                            savedFeeds = allFeeds.filter { it.category.equals(category, ignoreCase = true) }
                                            onFeedsUpdated()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Feed",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Add Feed to this Section
                Text(
                    text = "Add New Feed to $category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = newFeedName,
                    onValueChange = { newFeedName = it },
                    label = { Text("Feed Name") },
                    placeholder = { Text("e.g. BBC $category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newFeedUrl,
                    onValueChange = { newFeedUrl = it },
                    label = { Text("Feed RSS URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val trimmedUrl = newFeedUrl.trim()
                        val trimmedName = newFeedName.ifBlank { "$category Feed" }.trim()
                        if (trimmedUrl.isNotBlank()) {
                            isAdding = true
                            scope.launch {
                                val allFeeds = RssStorage.getSavedFeeds(context).toMutableList()
                                allFeeds.add(CustomRssFeed(name = trimmedName, url = trimmedUrl, category = category))
                                RssStorage.saveFeeds(context, allFeeds)
                                savedFeeds = allFeeds.filter { it.category.equals(category, ignoreCase = true) }
                                newFeedName = ""
                                newFeedUrl = ""
                                isAdding = false
                                onFeedsUpdated()
                                Toast.makeText(context, "Feed added to $category!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Please enter a valid RSS URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isAdding && newFeedUrl.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Feed to $category")
                    }
                }

                if (category != "Saved") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OutlinedButton(
                        onClick = {
                            onDeleteSection()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Entire '$category' Section")
                    }
                }
            }
        }
    }
}

@Composable
fun FeedVideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(false) }
    val normalizedUrl = remember(videoUrl) {
        var clean = videoUrl.trim()
        if (clean.contains("watch?v=")) {
            val id = clean.substringAfter("watch?v=").substringBefore("&")
            "https://www.youtube.com/embed/$id"
        } else if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("?")
            "https://www.youtube.com/embed/$id"
        } else {
            clean
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (!isPlaying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isPlaying = true }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Play Video",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(60.dp)
                )
                Text(
                    text = "Tap to Play Video",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        
                        val htmlData = if (normalizedUrl.contains("youtube") || normalizedUrl.contains("embed") || normalizedUrl.contains("vimeo")) {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>body { margin: 0; padding: 0; background-color: #000; display: flex; align-items: center; justify-content: center; height: 100vh; } iframe { width: 100%; height: 100%; border: none; }</style>
                            </head>
                            <body>
                            <iframe src="$normalizedUrl?autoplay=1&playsinline=1" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                            </body>
                            </html>
                            """.trimIndent()
                        } else {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>body { margin: 0; padding: 0; background-color: #000; display: flex; align-items: center; justify-content: center; height: 100vh; } video { width: 100%; height: 100%; object-fit: contain; }</style>
                            </head>
                            <body>
                            <video src="$normalizedUrl" controls autoplay playsinline></video>
                            </body>
                            </html>
                            """.trimIndent()
                        }
                        loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun extractYouTubeVideoId(text: String): String? {
    if (text.isBlank()) return null
    val pattern = Regex("(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/" +
            "|.*[?&]v=)|youtu\\.be\\/|youtube\\.com\\/shorts\\/)([^\"'&?\\/\\s]{11})")
    val match = pattern.find(text)
    return match?.groupValues?.getOrNull(1)
}

@Composable
fun YouTubeVideoPlayerCard(videoId: String, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(false) }
    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (!isPlaying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isPlaying = true }
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "YouTube Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Red,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play YouTube Video",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        
                        val embedHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                body, html { width: 100%; height: 100%; background-color: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; height: 100vh; }
                                .iframe-container { position: relative; width: 100%; height: 100%; }
                                iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
                            </style>
                            </head>
                            <body>
                            <div class="iframe-container">
                                <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&rel=0" 
                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                        allowfullscreen></iframe>
                            </div>
                            </body>
                            </html>
                        """.trimIndent()
                        
                        loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
