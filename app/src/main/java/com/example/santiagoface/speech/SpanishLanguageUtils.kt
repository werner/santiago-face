package com.example.santiagoface.speech

import java.util.Locale

object SpanishLanguageUtils {

    private val spanishSpecialChars = setOf('á', 'é', 'í', 'ó', 'ú', 'ñ', 'ü', '¿', '¡', 'Á', 'É', 'Í', 'Ó', 'Ú', 'Ñ', 'Ü')

    private val commonSpanishWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "y", "a", "por", "para",
        "con", "sin", "no", "si", "sí", "es", "son", "soy", "eres", "somos", "estoy", "estás", "está", "estamos",
        "están", "fue", "fueron", "ser", "estar", "hacer", "tengo", "tienes", "tiene", "tenemos", "tienen",
        "hola", "buenos", "días", "tardes", "noches", "gracias", "favor", "cómo", "como", "qué", "que", "cuál",
        "cual", "quién", "quien", "dónde", "donde", "cuándo", "cuando", "por qué", "porque", "cuánto", "cuanta",
        "bien", "mal", "ayuda", "ayudarte", "puedo", "puedes", "puede", "podemos", "pueden", "robot", "santiago",
        "amigo", "amiga", "mucho", "gusto", "esto", "esta", "este", "estos", "estas", "otro", "otra", "otros",
        "otras", "todo", "toda", "todos", "todas", "mi", "mis", "tu", "tus", "su", "sus", "nuestro", "nuestra",
        "me", "te", "se", "nos", "os", "lo", "le", "les", "aquí", "allí", "ahora", "luego",
        "siempre", "nunca", "también", "tampoco", "más", "menos", "muy", "poco", "bueno", "malo",
        "sobre", "entre", "hasta", "desde", "hacia", "según", "durante", "mediante"
    )

    private val questionStarters = setOf(
        "cómo", "como", "qué", "que", "cuál", "cual", "quién", "quien", "dónde", "donde",
        "cuándo", "cuando", "por qué", "por que", "porque", "cuánto", "cuanta", "cuántos", "cuantas",
        "es", "son", "está", "estás", "puedes", "puedo", "podrías", "sabes", "tienes", "haces"
    )

    private val exclamationStarters = setOf(
        "hola", "buenos días", "buenas tardes", "buenas noches", "bienvenido", "bienvenida",
        "genial", "excelente", "fantástico", "increíble", "bravo", "adiós"
    )

    fun isSpanishText(text: String): Boolean {
        if (text.isBlank()) return true
        return scoreSpanishText(text) >= 0.25
    }

    /**
     * Calculates a normalized Spanish confidence score for a candidate string.
     */
    fun scoreSpanishText(text: String): Double {
        if (text.isBlank()) return 0.0

        var score = 0.0
        val lowerText = text.lowercase(Locale.ROOT)

        // Bonus for explicit Spanish characters
        val charCount = text.count { it in spanishSpecialChars }
        if (charCount > 0) {
            score += charCount * 0.5
        }

        val words = lowerText.split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0

        val matchingWords = words.count { it in commonSpanishWords }
        val wordRatio = matchingWords.toDouble() / words.size.toDouble()

        score += wordRatio * 1.5

        // Question structure bonus only if Spanish question starters or Spanish words are present
        val hasSpanishQuestionStarter = questionStarters.any { lowerText.startsWith(it) }
        if (hasSpanishQuestionStarter || (matchingWords > 0 && text.endsWith("?"))) {
            score += 0.3
        }

        return score
    }

    /**
     * Selects the best candidate string from a list of SpeechRecognizer matches based on Spanish scoring.
     */
    fun selectBestSpanishCandidate(candidates: List<String>?): String {
        if (candidates.isNullOrEmpty()) return ""
        if (candidates.size == 1) return candidates[0]

        return candidates.maxByOrNull { scoreSpanishText(it) } ?: candidates[0]
    }

    /**
     * Formats recognized Spanish text with proper capitalization, accents, and inverted punctuation marks (¿, ¡).
     */
    fun formatSpanishText(text: String): String {
        var trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // Normalize common missing accents in question phrases
        trimmed = normalizeSpanishAccents(trimmed)

        // Capitalize first letter
        if (trimmed.isNotEmpty() && trimmed[0].isLowerCase()) {
            trimmed = trimmed.substring(0, 1).uppercase(Locale.getDefault()) + trimmed.substring(1)
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // Add inverted question mark '¿' if ending in '?' or starting with question words without '¿'
        val isQuestion = trimmed.endsWith("?") || questionStarters.any { starter ->
            lower.startsWith(starter) || lower.startsWith("¿$starter")
        }

        if (isQuestion && !trimmed.startsWith("¿")) {
            trimmed = "¿$trimmed"
            if (!trimmed.endsWith("?")) {
                trimmed = "$trimmed?"
            }
        }

        // Add inverted exclamation mark '¡' if ending in '!' or starting with exclamations without '¡'
        val isExclamation = trimmed.endsWith("!") || exclamationStarters.any { starter ->
            lower.startsWith(starter) || lower.startsWith("¡$starter")
        }

        if (isExclamation && !trimmed.startsWith("¡") && !trimmed.startsWith("¿")) {
            trimmed = "¡$trimmed"
            if (!trimmed.endsWith("!")) {
                trimmed = "$trimmed!"
            }
        }

        return trimmed
    }

    private fun normalizeSpanishAccents(text: String): String {
        var result = text
        val lower = result.lowercase(Locale.ROOT)

        if (lower.startsWith("como estas") || lower.startsWith("como esta")) {
            result = result.replaceFirst(Regex("(?i)como estas"), "cómo estás")
                .replaceFirst(Regex("(?i)como esta"), "cómo está")
        } else if (lower.startsWith("como te llamas")) {
            result = result.replaceFirst(Regex("(?i)como te llamas"), "cómo te llamas")
        } else if (lower.startsWith("que tal")) {
            result = result.replaceFirst(Regex("(?i)que tal"), "qué tal")
        } else if (lower.startsWith("donde estas") || lower.startsWith("donde esta")) {
            result = result.replaceFirst(Regex("(?i)donde estas"), "dónde estás")
                .replaceFirst(Regex("(?i)donde esta"), "dónde está")
        } else if (lower.startsWith("por que")) {
            result = result.replaceFirst(Regex("(?i)por que"), "por qué")
        }

        return result
    }
}

