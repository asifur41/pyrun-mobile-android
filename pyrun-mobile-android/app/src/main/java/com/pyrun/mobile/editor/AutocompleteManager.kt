package com.pyrun.mobile.editor

class AutocompleteManager(private val maxResults: Int = 8) {
    private val wordRegex = Regex("[A-Za-z_][A-Za-z0-9_]{2,}")

    fun currentPrefix(text: CharSequence, cursor: Int): String {
        if (cursor <= 0 || cursor > text.length) return ""
        var s = cursor
        while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
        return text.substring(s, cursor)
    }

    /** Document words (by frequency) first, then keywords/builtins. Never suggests the exact prefix. */
    fun suggest(text: String, cursor: Int): List<String> {
        val prefix = currentPrefix(text, cursor)
        if (prefix.length < 2 || prefix[0].isDigit()) return emptyList()
        val freq = HashMap<String, Int>()
        for (m in wordRegex.findAll(text)) freq.merge(m.value, 1, Int::plus)
        val fromDoc = freq.entries
            .filter { it.key.startsWith(prefix) && it.key != prefix }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        val fromVocab = PythonVocabulary.ALL
            .filter { it.startsWith(prefix) && it != prefix && it !in freq }
            .sorted()
        return (fromDoc + fromVocab).take(maxResults)
    }
}
