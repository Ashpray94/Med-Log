package com.suryaprakash.medlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.suryaprakash.medlog.speech.I18n
import androidx.compose.material3.LocalTextStyle as M3Style
import androidx.compose.material3.Text as M3Text

/**
 * Every piece of text on every screen goes through here, so the whole app shows in the person's language
 * ([I18n]). Same parameters as Material's Text; screens import this one instead.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = M3Style.current,
) {
    // read the language so every text redraws when it changes
    LocalSettings.current.languages.firstOrNull()
    val script = LocalScript.current
    val shown = I18n.tr(text)
    // Hindi and Tamil have marks above and below the letters: never set their lines tighter than the script needs
    // a word is never split across lines ("Bleedin / g"): if one doesn't fit, the words get a little smaller
    // (5% at a time, to 70% at most) until it does; a one-line label that runs past its space does the same.
    // Most text never needs it, so this costs nothing there.
    var shrink by androidx.compose.runtime.remember(shown) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val base = if (fontSize.isSp) fontSize else style.fontSize
    val size = if (base.isSp) base * shrink else base
    val lhBase = if (lineHeight.isSp) lineHeight * shrink else lineHeight
    val lh = if (script.indic && size.isSp && (!lhBase.isSp || lhBase.value < size.value * script.lines)) size * script.lines else lhBase
    val layout: (TextLayoutResult) -> Unit = { r ->
        if (shrink > 0.71f && ((softWrap && splitsWord(r, shown)) || (!softWrap || maxLines == 1) && r.hasVisualOverflow)) shrink -= 0.05f
        onTextLayout?.invoke(r)
    }
    // the phone's font was asked for by name: use the script's own, so it matches the rest of the page
    val family = if (fontFamily == AppFont) null else fontFamily
    // marked with its language, so TalkBack reads it in a Hindi or Tamil voice, not an English one
    val marked = if (script.indic && shown != text) AnnotatedString(shown, listOf(AnnotatedString.Range(androidx.compose.ui.text.SpanStyle(localeList = script.locale), 0, shown.length)))
        else AnnotatedString(shown)
    M3Text(marked, modifier, color, size, fontStyle, fontWeight, family, letterSpacing, textDecoration, textAlign, lh,
        overflow, softWrap, maxLines, minLines, emptyMap(), layout, style)
}

/** True when a line ends in the middle of a word: letters (or vowel marks) on both sides of the break. */
private fun splitsWord(r: TextLayoutResult, text: String): Boolean {
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i)
        if (end <= 0 || end >= text.length) continue
        val a = text[end - 1]; val b = text[end]
        if (!a.isWhitespace() && !b.isWhitespace() && a !in "-/,.·–" && b != '\n') return true
    }
    return false
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = M3Style.current,
) = M3Text(text, modifier, color, fontSize, fontWeight = fontWeight, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, maxLines = maxLines, style = style)

/** For text that isn't drawn by [Text]: talkback labels, notifications, the widget. */
fun tr(text: String) = I18n.tr(text)
