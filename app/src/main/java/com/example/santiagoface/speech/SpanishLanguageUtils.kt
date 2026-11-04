package com.example.santiagoface.speech

import java.util.Locale

object SpanishLanguageUtils {
    fun isSpanishText(text: String): Boolean {
        if (text.isBlank()) return true

        // Spanish specific characters & punctuation
        val spanishChars = setOf('á', 'é', 'í', 'ó', 'ú', 'ñ', 'ü', '¿', '¡', 'Á', 'É', 'Í', 'Ó', 'Ú', 'Ñ', 'Ü')
        if (text.any { it in spanishChars }) return true

        // Common Spanish words
        val commonSpanishWords = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "y", "a", "por", "para",
            "con", "sin", "no", "si", "sí", "es", "son", "soy", "eres", "somos", "estoy", "estás", "está", "estamos",
            "hola", "buenos", "días", "tardes", "noches", "gracias", "favor", "cómo", "como", "qué", "que", "cuál",
            "quién", "dónde", "donde", "cuándo", "cuando", "por qué", "porque", "bien", "mal", "ayuda", "ayudarte",
            "puedo", "puedes", "hacer", "tengo", "tienes", "robot", "santiago", "amigo", "mucho", "gusto", "esto", "esta",
            "este", "estos", "estas", "otro", "otra", "otros", "otras", "todo", "toda", "todos", "todas",
        )

        val words = text.lowercase(Locale.ROOT).split(Regex("[\\s\\p{Punct}]+"))
        val matchCount = words.count { it in commonSpanishWords }
        return (matchCount > 0) || words.isEmpty()
    }
}
