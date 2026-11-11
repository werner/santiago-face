package com.example.santiagoface.camera

object VisionDecisionHelper {

    /**
     * Keywords indicating visual intent (asking to see, inspect, read, locate, or describe).
     */
    private val VISUAL_INTENT_KEYWORDS = listOf(
        // Spanish
        "mira", "mirá", "mirar", "miren", "miras", "mirás",
        "viendo", "estás viendo", "estas viendo", "está viendo", "esta viendo",
        "mirando", "estás mirando", "estas mirando",
        "qué ves", "que ves", "ves algo", "qué hay", "que hay",
        "puedes ver", "logras ver", "llegas a ver",
        "describí", "describe", "describeme", "descríbeme",
        "qué es esto", "que es esto", "qué tengo", "que tengo",
        "qué es eso", "que es eso", "qué es aquello", "que es aquello",
        "qué hay enfrente", "que hay enfrente", "qué hay delante", "que hay delante",
        "qué tienes enfrente", "que tienes enfrente", "qué tienes delante", "que tienes delante",
        "reconocé", "reconoce", "identificá", "identifica",
        "lee", "leé", "qué dice", "que dice",
        "foto", "imagen", "observa", "observas", "observá", "cámara", "camara",
        "dónde está", "donde esta", "busca", "buscá", "encuentra", "dónde dejé", "donde deje",
        // English
        "look", "looking", "looking at", "see", "seeing", "what do you see", "what are you seeing",
        "what are you looking at", "what is this", "what is that", "what am i holding",
        "what is in front of you", "what's in front of you", "can you see",
        "describe", "describe what you see", "read this", "what does it say", "recognize", "identify",
        "picture", "photo", "camera", "image", "find", "where is", "do you see"
    )

    /**
     * Keywords and phrases that require deep comprehension and reasoning from the server's multimodal LLM
     * (e.g. reading text, distinguishing brands, explaining relationships, recognizing facial identity, colors,
     * or providing general visual scene descriptions).
     */
    private val DEEP_REASONING_PATTERNS = listOf(
        // Visual scene queries / descriptions
        "qué estás viendo", "que estas viendo", "qué estas viendo",
        "qué estás mirando", "que estas mirando", "qué estas mirando",
        "qué ves", "que ves", "dime qué ves", "dime que ves", "cuéntame qué ves", "cuentame que ves",
        "qué hay enfrente", "que hay enfrente", "qué hay delante", "que hay delante",
        "qué tienes enfrente", "que tienes enfrente", "qué tienes delante", "que tienes delante",
        "describe lo que ves", "describí lo que ves", "describeme lo que ves", "descríbeme lo que ves",
        "what do you see", "what are you seeing", "what are you looking at",
        "what is in front of you", "what's in front of you", "describe what you see", "tell me what you see",
        // Reading text / OCR
        "lee", "leé", "qué dice", "que dice", "read", "what does it say", "what is written",
        // Specific attributes / brands / colors
        "qué marca", "que marca", "de qué marca", "de que marca", "what brand",
        "qué color", "que color", "de qué color", "de que color", "what color",
        // Detailed explanation / relationships / emotions
        "por qué", "por que", "why", "explicá", "explica", "explain", "describe detalladamente", "describe in detail",
        // Identity / facial recognition beyond generic "Person"
        "quién es", "quien es", "who is this", "who is that", "sabes quién soy", "sabes quien soy", "reconocé mi cara",
        // Counting multiple complex items or nuanced scene understanding
        "cuántos", "cuantos", "how many", "qué está haciendo", "que esta haciendo", "what are they doing"
    )

    /**
     * Determines whether a Base64 image must be sent to the server, or whether on-device ML Kit
     * detection suffices for the current interaction.
     *
     * @param userMessage User's query/command.
     * @param isCameraActive Whether the camera is currently running.
     * @param mlKitDetections Current detected objects from ML Kit on-device.
     * @param target Specific target if tracking/navigating (e.g. "cup", "person").
     * @param serverExplicitlyRequestedImage Whether server requested an image in previous turn.
     */
    fun shouldSendBase64Image(
        userMessage: String,
        isCameraActive: Boolean,
        mlKitDetections: List<DetectedObject>,
        target: String? = null,
        serverExplicitlyRequestedImage: Boolean = false
    ): Boolean {
        if (!isCameraActive) return false

        // 1. If server explicitly asked for an image in previous turn, fulfill it
        if (serverExplicitlyRequestedImage) return true

        val lowerMsg = userMessage.lowercase().trim()

        // 2. Check if deeper comprehension / reasoning is requested
        if (requiresDeepReasoning(lowerMsg)) {
            // Deeper multimodal reasoning is needed -> Send image to server
            return true
        }

        // 3. Check if the user expressed visual intent or has an active target
        val hasVisualIntent = VISUAL_INTENT_KEYWORDS.any { lowerMsg.contains(it) }
        val hasActiveTarget = !target.isNullOrBlank()

        if (!hasVisualIntent && !hasActiveTarget) {
            // General conversational turn (e.g. "Hola", "Cómo estás", "Cuéntame un chiste")
            // Efficient mode: DO NOT send images!
            return false
        }

        // 4. User is asking a visual question or looking for an object.
        // Check if on-device ML Kit can resolve it locally!
        val effectiveTarget = target ?: extractTargetCandidate(lowerMsg)
        if (!effectiveTarget.isNullOrBlank()) {
            val localMatch = mlKitDetections.any { detection ->
                detection.confidence >= 0.55f && isMatch(detection.label, effectiveTarget)
            }
            if (localMatch) {
                // Resolved locally by ML Kit! No need to send heavy Base64 image.
                return false
            }
        }

        // 5. If ML Kit could not confidently detect the target or the scene is generic,
        // send image to server to let the larger server vision model resolve it.
        return true
    }

    /**
     * Checks if the message requires deep reasoning beyond standard ML Kit object classification.
     */
    fun requiresDeepReasoning(message: String): Boolean {
        val lower = message.lowercase()
        return DEEP_REASONING_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Matches detected ML Kit label with target query, allowing common synonyms (e.g., cup/mug/taza, person/persona).
     */
    fun isMatch(detectedLabel: String, target: String): Boolean {
        val det = detectedLabel.lowercase().trim()
        val tgt = target.lowercase().trim()

        if (det.contains(tgt) || tgt.contains(det)) return true

        // Spanish <-> English common robotics mappings
        val synonymPairs = listOf(
            setOf("person", "persona", "humano", "human"),
            setOf("cup", "coffee cup", "taza", "mug"),
            setOf("bottle", "botella"),
            setOf("cell phone", "phone", "teléfono", "telefono", "celular", "mobile phone"),
            setOf("laptop", "computadora", "notebook", "computer"),
            setOf("chair", "silla", "asiento"),
            setOf("dog", "perro"),
            setOf("cat", "gato"),
            setOf("book", "libro"),
            setOf("table", "desk", "mesa", "escritorio")
        )

        for (synonyms in synonymPairs) {
            val matchesDet = synonyms.any { det.contains(it) }
            val matchesTgt = synonyms.any { tgt.contains(it) }
            if (matchesDet && matchesTgt) return true
        }

        return false
    }

    /**
     * Attempts to extract a target candidate from common questions like "¿ves una taza?", "where is the bottle?".
     */
    fun extractTargetCandidate(message: String): String? {
        val clean = message.lowercase()
            .replace("¿", "")
            .replace("?", "")
            .replace("¡", "")
            .replace("!", "")
            .trim()

        val regexes = listOf(
            Regex("""(?i)(?:ves|ve|miras|mira|busca|dónde está|donde esta|encuentra)\s+(?:(?:el|la|los|las|una|un|unos|unas|mi|tu|al|del)\s+)?([a-záéíóúñ]+)"""),
            Regex("""(?i)(?:do you see|find|where is|locate)\s+(?:(?:the|a|an|my|this)\s+)?([a-z]+)""")
        )

        for (regex in regexes) {
            val match = regex.find(clean)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length > 2 && candidate !in setOf("algo", "esto", "eso", "this", "that", "something")) {
                    return candidate
                }
            }
        }
        return null
    }
}
