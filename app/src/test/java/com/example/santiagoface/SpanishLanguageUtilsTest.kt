package com.example.santiagoface

import com.example.santiagoface.speech.SpanishLanguageUtils
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
}
