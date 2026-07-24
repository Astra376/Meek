package com.example.aichat.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

private const val ExpandSuffix = "\u2026 More"
private const val CollapseSuffix = " \u2026Less"

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 2,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary
) {
    require(collapsedMaxLines > 0) { "collapsedMaxLines must be positive." }

    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var availableWidth by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    val resolvedStyle = style.merge(TextStyle(color = color))

    val collapsedPrefix = remember(
        text,
        availableWidth,
        collapsedMaxLines,
        resolvedStyle
    ) {
        if (availableWidth <= 0 || text.isBlank()) {
            null
        } else {
            val constraints = Constraints(maxWidth = availableWidth)
            val fullResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = resolvedStyle,
                maxLines = collapsedMaxLines,
                overflow = TextOverflow.Clip,
                constraints = constraints
            )
            if (!fullResult.hasVisualOverflow) {
                null
            } else {
                findLargestFittingPrefix(
                    text = text,
                    suffix = ExpandSuffix,
                    fits = { candidate ->
                        !textMeasurer.measure(
                            text = AnnotatedString(candidate),
                            style = resolvedStyle,
                            maxLines = collapsedMaxLines,
                            overflow = TextOverflow.Clip,
                            constraints = constraints
                        ).hasVisualOverflow
                    }
                )
            }
        }
    }
    val canExpand = collapsedPrefix != null
    val displayText = remember(text, expanded, collapsedPrefix, actionColor) {
        when {
            expanded && canExpand -> annotatedActionText(
                body = text,
                suffix = CollapseSuffix,
                actionColor = actionColor
            )

            canExpand -> annotatedActionText(
                body = collapsedPrefix.orEmpty(),
                suffix = ExpandSuffix,
                actionColor = actionColor
            )

            else -> AnnotatedString(text)
        }
    }

    Text(
        text = displayText,
        modifier = modifier
            .onSizeChanged { availableWidth = it.width }
            .then(
                if (canExpand) {
                    Modifier
                        .clickable(
                            onClickLabel = if (expanded) "Less" else "More"
                        ) {
                            expanded = !expanded
                        }
                        .semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }
                } else {
                    Modifier
                }
            ),
        style = resolvedStyle,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
        overflow = TextOverflow.Clip
    )
}

private fun annotatedActionText(
    body: String,
    suffix: String,
    actionColor: Color
): AnnotatedString = buildAnnotatedString {
    append(body)
    pushStyle(SpanStyle(color = actionColor))
    append(suffix)
    pop()
}

private fun findLargestFittingPrefix(
    text: String,
    suffix: String,
    fits: (String) -> Boolean
): String {
    var low = 0
    var high = text.length
    var best = ""
    while (low <= high) {
        val midpoint = (low + high) ushr 1
        val prefix = safePrefix(text, midpoint)
        if (fits(prefix + suffix)) {
            best = prefix
            low = midpoint + 1
        } else {
            high = midpoint - 1
        }
    }
    return best
}

private fun safePrefix(text: String, endExclusive: Int): String {
    var safeEnd = endExclusive.coerceIn(0, text.length)
    if (
        safeEnd in 1 until text.length &&
        Character.isHighSurrogate(text[safeEnd - 1]) &&
        Character.isLowSurrogate(text[safeEnd])
    ) {
        safeEnd -= 1
    }
    return text.substring(0, safeEnd).trimEnd()
}
