package io.github.tmarsteel.flyingnarrator.ui

import java.util.Locale

class LocalizedString(
    val values: Map<Locale, String>
) {
    init {
        check(values.isNotEmpty())
    }

    val value: String get() {
        val l = Locale.filter(LANGUAGE_RANGE, values.keys)
        return values.getValue(l.firstOrNull() ?: values.keys.first())
    }

    companion object {
        private var DEFAULT_DISPLAY_LOCALE: Locale? = null
        private lateinit var languageRangeCache: List<Locale.LanguageRange>
        private val LANGUAGE_RANGE: List<Locale.LanguageRange> get() {
            val currentDisplayLocale = Locale.getDefault(Locale.Category.DISPLAY)
            if (DEFAULT_DISPLAY_LOCALE != currentDisplayLocale) {
                DEFAULT_DISPLAY_LOCALE = currentDisplayLocale
                languageRangeCache = listOf(Locale.LanguageRange(currentDisplayLocale.toLanguageTag()))
            }

            return languageRangeCache
        }
    }
}