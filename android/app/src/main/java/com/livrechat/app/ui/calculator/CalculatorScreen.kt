package com.livrechat.app.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp

@Composable
fun CalculatorScreen(
    onUnlock: (isDuress: Boolean) -> Unit
) {
    val prefs = remember { LivreChatApp.instance.preferencesManager }
    val haptic = LocalHapticFeedback.current

    var displayExpression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var secretBuffer by remember { mutableStateOf("") }

    val realPin = prefs.appLockPin
    val duressPin = prefs.duressPin

    fun checkSecretUnlock() {
        if (!realPin.isNullOrBlank() && secretBuffer == realPin) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUnlock(false)
            return
        }
        if (!duressPin.isNullOrBlank() && secretBuffer == duressPin) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUnlock(true)
            return
        }
        // Fallback default secret code if user hasn't set a PIN yet
        if (realPin.isNullOrBlank() && (secretBuffer == "1234" || secretBuffer == "0000")) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUnlock(false)
            return
        }
    }

    fun onDigit(d: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        displayExpression += d
        secretBuffer += d
        if (secretBuffer.length > 10) {
            secretBuffer = secretBuffer.takeLast(10)
        }
        try {
            val eval = evaluateExpression(displayExpression)
            resultText = eval
        } catch (_: Exception) {}
    }

    fun onOperator(op: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (displayExpression.isNotEmpty() && !displayExpression.last().isDigit()) {
            displayExpression = displayExpression.dropLast(1) + op
        } else if (displayExpression.isNotEmpty()) {
            displayExpression += op
        }
    }

    fun onClear() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        displayExpression = ""
        resultText = ""
        secretBuffer = ""
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (displayExpression.isNotEmpty()) {
            displayExpression = displayExpression.dropLast(1)
        }
        if (secretBuffer.isNotEmpty()) {
            secretBuffer = secretBuffer.dropLast(1)
        }
        try {
            resultText = if (displayExpression.isNotEmpty()) evaluateExpression(displayExpression) else ""
        } catch (_: Exception) {}
    }

    fun onEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        checkSecretUnlock()
        if (displayExpression.isNotBlank()) {
            try {
                val eval = evaluateExpression(displayExpression)
                displayExpression = eval
                resultText = ""
            } catch (_: Exception) {
                resultText = "Erro"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17171C))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = if (displayExpression.isBlank()) "0" else displayExpression,
                fontSize = if (displayExpression.length > 9) 36.sp else 52.sp,
                color = Color.White,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            if (resultText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "= $resultText",
                    fontSize = 28.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Keypad buttons
        val rows = listOf(
            listOf("C", "⌫", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("00", "0", ".", "=")
        )

        for (row in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (btn in row) {
                    val isOp = btn in listOf("÷", "×", "-", "+", "=")
                    val isAction = btn in listOf("C", "⌫", "%")
                    val bgColor = when {
                        btn == "=" -> MaterialTheme.colorScheme.primary
                        isOp -> Color(0xFF4B5EFC)
                        isAction -> Color(0xFF4E505F)
                        else -> Color(0xFF2E2F3E)
                    }
                    val textColor = Color.White

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable {
                                when (btn) {
                                    "C" -> onClear()
                                    "⌫" -> onBackspace()
                                    "%" -> onOperator("%")
                                    "÷" -> onOperator("/")
                                    "×" -> onOperator("*")
                                    "-" -> onOperator("-")
                                    "+" -> onOperator("+")
                                    "=" -> onEquals()
                                    else -> onDigit(btn)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (btn == "⌫") {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Apagar",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = btn,
                                fontSize = 24.sp,
                                fontWeight = if (isOp) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateExpression(expr: String): String {
    val clean = expr.replace("×", "*").replace("÷", "/")
    if (clean.isBlank()) return ""
    return try {
        // Simple evaluation supporting basic operators +, -, *, /, %
        val tokens = mutableListOf<String>()
        var curNumber = StringBuilder()
        for (ch in clean) {
            if (ch.isDigit() || ch == '.') {
                curNumber.append(ch)
            } else if (ch in listOf('+', '-', '*', '/', '%')) {
                if (curNumber.isNotEmpty()) {
                    tokens.add(curNumber.toString())
                    curNumber = StringBuilder()
                }
                tokens.add(ch.toString())
            }
        }
        if (curNumber.isNotEmpty()) {
            tokens.add(curNumber.toString())
        }

        if (tokens.isEmpty()) return ""

        // Process * and / and %
        var i = 0
        val intermediate = mutableListOf<String>()
        while (i < tokens.size) {
            val t = tokens[i]
            if ((t == "*" || t == "/" || t == "%") && intermediate.isNotEmpty() && i + 1 < tokens.size) {
                val left = intermediate.removeAt(intermediate.size - 1).toDoubleOrNull() ?: 0.0
                val right = tokens[i + 1].toDoubleOrNull() ?: 1.0
                val res = when (t) {
                    "*" -> left * right
                    "/" -> if (right != 0.0) left / right else 0.0
                    else -> left * (right / 100.0)
                }
                intermediate.add(res.toString())
                i += 2
            } else {
                intermediate.add(t)
                i++
            }
        }

        // Process + and -
        var result = intermediate.firstOrNull()?.toDoubleOrNull() ?: 0.0
        var opIdx = 1
        while (opIdx < intermediate.size) {
            val op = intermediate[opIdx]
            val nextVal = intermediate.getOrNull(opIdx + 1)?.toDoubleOrNull() ?: 0.0
            if (op == "+") {
                result += nextVal
            } else if (op == "-") {
                result -= nextVal
            }
            opIdx += 2
        }

        if (result % 1.0 == 0.0) {
            result.toLong().toString()
        } else {
            "%.4f".format(result).trimEnd('0').trimEnd('.')
        }
    } catch (_: Exception) {
        ""
    }
}
