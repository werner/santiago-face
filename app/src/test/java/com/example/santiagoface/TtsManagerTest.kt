package com.example.santiagoface

import com.example.santiagoface.speech.SpanishLanguageUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsManagerTest {

    @Test
    fun SpanishLanguageUtils_correctlyIdentifiesSpanishText() {
        assertTrue(SpanishLanguageUtils.isSpanishText("Hola, ¿cómo estás?"))
        assertTrue(SpanishLanguageUtils.isSpanishText("Buenas tardes"))
    }
}
