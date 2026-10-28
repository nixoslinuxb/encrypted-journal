package com.encryptedjournal.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import java.text.DecimalFormat
import kotlin.math.*

object CalculatorEngine {
    fun evaluate(expression: String, isRadMode: Boolean): Double {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", "pi")
            .replace("√", "sqrt")
            .replace("³√", "cbrt")
            .replace("e^", "exp")

        return try {
            val parser = ExpressionParser(sanitized, isRadMode)
            parser.parse()
        } catch (_: Exception) {
            Double.NaN
        }
    }
}

private class ExpressionParser(private val str: String, private val isRadMode: Boolean) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            when {
                eat('+'.code) -> x += parseTerm()
                eat('-'.code) -> x -= parseTerm()
                else -> return x
            }
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            when {
                eat('*'.code) -> x *= parseFactor()
                eat('/'.code) -> x /= parseFactor()
                eat('%'.code) -> x %= parseFactor()
                else -> return x
            }
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return +parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = this.pos
        if (eat('('.code)) {
            x = parseExpression()
            eat(')'.code)
        } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
            while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
            x = str.substring(startPos, this.pos).toDouble()
        } else if (ch >= 'a'.code && ch <= 'z'.code) {
            while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
            val func = str.substring(startPos, this.pos)
            if (func == "pi") {
                x = PI
            } else if (func == "e") {
                x = E
            } else {
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "cbrt" -> cbrt(x)
                    "sin" -> if (isRadMode) sin(x) else sin(Math.toRadians(x))
                    "cos" -> if (isRadMode) cos(x) else cos(Math.toRadians(x))
                    "tan" -> if (isRadMode) tan(x) else tan(Math.toRadians(x))
                    "asin" -> if (isRadMode) asin(x) else Math.toDegrees(asin(x))
                    "acos" -> if (isRadMode) acos(x) else Math.toDegrees(acos(x))
                    "atan" -> if (isRadMode) atan(x) else Math.toDegrees(atan(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    "exp" -> exp(x)
                    else -> throw RuntimeException("Unknown function: $func")
                }
            }
        } else {
            throw RuntimeException("Unexpected: " + ch.toChar())
        }

        if (eat('^'.code)) x = x.pow(parseFactor())

        return x
    }
}

enum class CalcBtnType {
    NUMBER, OPERATOR, FUNCTION, ACTION, EQUALS
}

data class CalcButton(
    val label: String,
    val type: CalcBtnType = CalcBtnType.NUMBER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScientificCalculatorScreen(
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var isRadMode by remember { mutableStateOf(false) }
    var showScientificPanel by remember { mutableStateOf(true) }

    val formatter = remember { DecimalFormat("#,##0.########") }

    fun updateResult() {
        if (expression.isBlank()) {
            resultText = "0"
            return
        }
        val value = CalculatorEngine.evaluate(expression, isRadMode)
        resultText = if (value.isNaN()) {
            "..."
        } else {
            try {
                formatter.format(value)
            } catch (_: Exception) {
                value.toString()
            }
        }
    }

    fun onButtonClick(btn: CalcButton) {
        when (btn.label) {
            "AC" -> {
                expression = ""
                resultText = "0"
            }
            "DEL" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    updateResult()
                }
            }
            "=" -> {
                if (expression.isNotBlank()) {
                    val value = CalculatorEngine.evaluate(expression, isRadMode)
                    if (!value.isNaN()) {
                        expression = try {
                            formatter.format(value)
                        } catch (_: Exception) {
                            value.toString()
                        }
                        resultText = expression
                    } else {
                        resultText = "Error"
                    }
                }
            }
            "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln", "√" -> {
                expression += "${btn.label}("
                updateResult()
            }
            "x²" -> {
                expression += "^2"
                updateResult()
            }
            "x^y" -> {
                expression += "^"
                updateResult()
            }
            "±" -> {
                expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
                updateResult()
            }
            else -> {
                expression += btn.label
                updateResult()
            }
        }
    }

    val scientificButtons = listOf(
        CalcButton("sin", CalcBtnType.FUNCTION),
        CalcButton("cos", CalcBtnType.FUNCTION),
        CalcButton("tan", CalcBtnType.FUNCTION),
        CalcButton("ln", CalcBtnType.FUNCTION),
        CalcButton("log", CalcBtnType.FUNCTION),
        CalcButton("√", CalcBtnType.FUNCTION),
        CalcButton("x²", CalcBtnType.FUNCTION),
        CalcButton("x^y", CalcBtnType.FUNCTION),
        CalcButton("π", CalcBtnType.FUNCTION),
        CalcButton("e", CalcBtnType.FUNCTION)
    )

    val mainButtons = listOf(
        CalcButton("(", CalcBtnType.ACTION),
        CalcButton(")", CalcBtnType.ACTION),
        CalcButton("AC", CalcBtnType.ACTION),
        CalcButton("DEL", CalcBtnType.ACTION),
        CalcButton("7"), CalcButton("8"), CalcButton("9"), CalcButton("÷", CalcBtnType.OPERATOR),
        CalcButton("4"), CalcButton("5"), CalcButton("6"), CalcButton("×", CalcBtnType.OPERATOR),
        CalcButton("1"), CalcButton("2"), CalcButton("3"), CalcButton("-", CalcBtnType.OPERATOR),
        CalcButton("±"), CalcButton("0"), CalcButton("."), CalcButton("+", CalcBtnType.OPERATOR)
    )

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
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Scientific Calculator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        // DEG / RAD Toggle Chip
                        FilterChip(
                            selected = isRadMode,
                            onClick = {
                                isRadMode = !isRadMode
                                updateResult()
                            },
                            label = { Text(if (isRadMode) "RAD" else "DEG", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(resultText))
                            Toast.makeText(context, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Result")
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                // Calculation Display Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = expression.ifEmpty { "0" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Panel Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showScientificPanel = !showScientificPanel }) {
                        Text(
                            text = if (showScientificPanel) "Hide Scientific Functions" else "Show Scientific Functions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Calculator Buttons Keypad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Scientific Panel
                    if (showScientificPanel) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(scientificButtons) { btn ->
                                Surface(
                                    onClick = { onButtonClick(btn) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = btn.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main Keypad Grid (Numbers, Operators, AC, DEL)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(mainButtons) { btn ->
                            val btnColor = when (btn.type) {
                                CalcBtnType.OPERATOR -> MaterialTheme.colorScheme.primaryContainer
                                CalcBtnType.ACTION -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }

                            val textColor = when (btn.type) {
                                CalcBtnType.OPERATOR -> MaterialTheme.colorScheme.primary
                                CalcBtnType.ACTION -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Surface(
                                onClick = { onButtonClick(btn) },
                                shape = RoundedCornerShape(20.dp),
                                color = btnColor,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = btn.label,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }

                    // Equals Row
                    Button(
                        onClick = { onButtonClick(CalcButton("=", CalcBtnType.EQUALS)) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "=",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
