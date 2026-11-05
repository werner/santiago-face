package com.example.santiagoface

import com.example.santiagoface.speech.SpanishLanguageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishLanguageUtilsTest {

    @Test
    fun isSpanishText_detectsSpanishPhrases() {
        assertTrue(SpanishLanguageUtils.isSpanishText("Hola, ¿cómo estás?"))
        assertTrue(SpanishLanguageUtils.isSpanishText("Buenas tardes Santiago"))
        assertTrue(SpanishLanguageUtils.isSpanishText("Este es un mensaje en español"))
    }

    @Test
    fun isSpanishText_detectsEnglishPhrases() {
        assertFalse(SpanishLanguageUtils.isSpanishText("Hello, how are you?"))
        assertFalse(SpanishLanguageUtils.isSpanishText("This is an English sentence."))
    }

    @Test
    fun selectBestSpanishCandidate_picksSpanishOverEnglishCandidate() {
        val candidates = listOf(
            "hello my friend",
            "hola mi amigo cómo estás",
            "something completely different"
        )
        val best = SpanishLanguageUtils.selectBestSpanishCandidate(candidates)
        assertEquals("hola mi amigo cómo estás", best)
    }

    @Test
    fun formatSpanishText_addsInvertedQuestionMarks() {
        val formatted1 = SpanishLanguageUtils.formatSpanishText("como estas")
        assertEquals("¿Cómo estás?", formatted1)

        val formatted2 = SpanishLanguageUtils.formatSpanishText("que tal santiago")
        assertEquals("¿Qué tal santiago?", formatted2)
    }

    @Test
    fun formatSpanishText_addsInvertedExclamationMarks() {
        val formatted = SpanishLanguageUtils.formatSpanishText("hola santiago")
        assertEquals("¡Hola santiago!", formatted)
    }
}

