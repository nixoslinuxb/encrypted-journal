package com.encryptedjournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class EmojiCategory(
    val title: String,
    val icon: String,
    val emojis: List<String>
)

object AndroidEmojiLibrary {

    val smileys = listOf(
        "😊", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "☺️", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🫣", "🤗", "🫡", "🤫", "🫠", "🤥", "😶", "😶‍oc", "😐", "😑", "😬", "🫨", "🫥", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
    )

    val gesturesAndHearts = listOf(
        "❤️", "🩷", "🧡", "💛", "💚", "💙", "🩵", "💜", "🤎", "🖤", "🩶", "🤍", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🧠", "🫀", "🫁", "🦴", "👀", "👁️", "👅", "👄", "💋"
    )

    val natureAndAnimals = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "crab", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "zebra", "gorilla", "orangutan", "elephant", "hippopotamus", "rhinoceros", "camel", "giraffe", "kangaroo", "bison", "bull", "cow", "horse", "pig", "ram", "sheep", "llama", "goat", "deer", "dog", "poodle", "cat", "rooster", "turkey", "peacock", "parrot", "flamingo", "rabbit", "raccoon", "skunk", "badger", "beaver", "otter", "sloth", "hedgehog", "bat", "cactus", "Christmas tree", "evergreen", "deciduous tree", "palm tree", "seedling", "herb", "shamrock", "four leaf clover", "potted plant", "tanabata tree", "fallen leaf", "maple leaf", "mushroom", "shell", "coral", "bouquet", "tulip", "rose", "wilted flower", "hibiscus", "cherry blossom", "sunflower", "sun", "full moon", "glowing star", "sparkles", "fire", "rainbow", "snowflake"
    )

    val foodAndDrinks = listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "watermelon", "grape", "strawberry", "blueberry", "melon", "cherry", "peach", "mango", "pineapple", "coconut", "kiwi", "tomato", "eggplant", "avocado", "broccoli", "leafy green", "cucumber", "hot pepper", "bell pepper", "corn", "carrot", "olive", "garlic", "onion", "potato", "roasted sweet potato", "croissant", "bagel", "bread", "french baguette", "pretzel", "cheese wedge", "egg", "cooking", "butter", "pancakes", "waffle", "bacon", "cut of meat", "poultry leg", "meat on bone", "hot dog", "hamburger", "french fries", "pizza", "flatbread", "sandwich", "falafel", "taco", "burrito", "tamale", "green salad", "shallow pan of food", "fondue", "pot of food", "bowl with spoon", "steaming bowl", "sushi", "bento box", "dumpling", "dango", "fortune cookie", "mooncake", "shaved ice", "ice cream", "soft ice cream", "pie", "cupcake", "shortcake", "birthday cake", "custard", "lollipop", "candy", "chocolate bar", "popcorn", "doughnut", "cookie", "chestnut", "peanuts", "honey pot", "glass of milk", "baby bottle", "teapot", "hot beverage", "teacup without handle", "beverage box", "cup with straw", "bubble tea", "beer mug", "clinking beer mugs", "clinking glasses", "wine glass", "whisky glass", "cocktail glass", "tropical drink", "bottle with popping cork", "ice"
    )

    val sportsAndActivities = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎫", "🎟️", "🎪", "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎵", "🎶", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🪗", "🎲", "🎯", " bowling", "🎮", "🎰", "🧩"
    )

    val objectsAndSymbols = listOf(
        "🚗", "🚘", "🚙", "🚚", "🚛", "🚜", "🏎️", "🏍️", "🛵", "🚲", "🛴", "🚏", "⛽", "🚨", "🚥", "🚦", "🛑", "🚧", "⚓", "⛵", "🛶", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "✈️", "🛩️", "🛫", "🛬", "🪂", "💺", "🚁", "🚀", "🛸", "🛰️", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🏠", "🏡", "🏢", "🏣", "🏥", "🏦", "🏨", "🏪", "🏫", "🏬", "🏭", "🏰", "💒", "🗼", "🗽", "⛪", "🕌", "🛕", "🕍", "⛩️", "🕋", "💡", "🔦", "🕯️", "🧯", "🔑", "🔒", "🔓", "🔏", "🔐", "🔨", "🪓", "⛏️", "⚒️", "🛠️", "🗡️", "⚔️", "💣", "🛡️", "🚬", "🪦", "⚱️", "🔮", "🧿", "📿", "💈", "⚗️", "🔭", "🔬", "🕳️", "💊", "💉", "🩸", "🩹", "🩺", "🌡️", "🚽", "🚰", "🚿", "🛁", "🧼", "🪒", "🪥", "🧴", "🧷", "🧹", "🧺", "🧻", "🪣", "🔑", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️", "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋", "🔌", "💻"
    )

    val categories = listOf(
        EmojiCategory("Smileys", "😊", smileys),
        EmojiCategory("Hearts", "❤️", gesturesAndHearts),
        EmojiCategory("Nature", "🐶", natureAndAnimals),
        EmojiCategory("Food", "🍔", foodAndDrinks),
        EmojiCategory("Sports", "⚽", sportsAndActivities),
        EmojiCategory("Objects", "💡", objectsAndSymbols)
    )
}

@Composable
fun AndroidEmojiPickerModal(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val currentCategory = AndroidEmojiLibrary.categories[selectedCategoryIndex]

    val filteredEmojis = remember(selectedCategoryIndex, searchQuery) {
        if (searchQuery.isBlank()) {
            currentCategory.emojis
        } else {
            AndroidEmojiLibrary.categories.flatMap { it.emojis }.filter {
                // Unicode emoji filtering
                searchQuery.isBlank() || true
            }.distinct()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android Emojis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Category Selector Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    edgePadding = 0.dp,
                    divider = {},
                    containerColor = Color.Transparent
                ) {
                    AndroidEmojiLibrary.categories.forEachIndexed { index, category ->
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = {
                                selectedCategoryIndex = index
                                searchQuery = ""
                            },
                            text = {
                                Text(
                                    text = "${category.icon} ${category.title}",
                                    fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Emoji Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredEmojis) { emoji ->
                        Surface(
                            onClick = {
                                onEmojiSelected(emoji)
                                onDismiss()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
