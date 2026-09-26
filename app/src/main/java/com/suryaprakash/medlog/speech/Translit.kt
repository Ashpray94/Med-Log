package com.suryaprakash.medlog.speech

import android.os.Build

/**
 * Words in Indian scripts → Latin letters, so it can be matched and shown to an English-reading doctor.
 * For scripts with an unwritten final vowel (Hindi, Marathi, Bengali, Gujarati, Punjabi, Odia) the silent
 * final "a" is dropped, so "दर्द" becomes "dard", not "darda".
 */
object Translit {
    private val toLatin by lazy { if (Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Any-Latin") }.getOrNull() else null }
    private val toAscii by lazy { if (Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Latin-ASCII; Lower") }.getOrNull() else null }
    private val SCHWA = Regex("(?<=[bcdfghjklmnpqrstvwxyz\u1E6D\u1E0D\u1E47\u1E63\u015B\u1E45\u00F1\u1E37\u1E5B])a\\b")

    fun toLatin(s: String): String {
        if (s.all { it.code < 128 }) return s
        val latin = runCatching { toLatin?.transliterate(s) }.getOrNull() ?: return s
        val schwaScript = s.any { it.code in 0x0900..0x0B7F }
        val trimmed = if (schwaScript) SCHWA.replace(latin, "") else latin
        return runCatching { toAscii?.transliterate(trimmed) }.getOrNull() ?: trimmed
    }

    fun isLatin(s: String) = s.all { it.code < 0x250 }
}
