package com.example.santiagoface.camera

import java.text.Normalizer
import java.util.Locale

/**
 * Intelligent multimodal decision helper that evaluates user communicative intent across
 * diverse Spanish and English dialects (e.g. Argentine/Rioplatense, Mexican, Colombian,
 * Chilean, Peninsular Spanish, US, UK, Australian English) to decide whether camera images
 * must be sent to the multimodal server LLM, or whether on-device ML Kit detection suffices.
 */
object VisionDecisionHelper {

    // --- 1. Perception & Visual Action Stems across Spanish & English Dialects ---

    // Spanish perception verbs across all tenses, moods, and dialect conjugations:
    // ver (ve, ves, vemos, ven, veis, viendo, viste, visto, ver...), mirar (mira, mirá, miren, miras, mirando...),
    // observar, fijar/fijarse (fijate, fíjate, te fijas, fijese...), checar (checa, checate, chequen...),
    // cachar (cacha, cachái, cachay, cachas...), pillar (pilla, pillás, pillate, pillan...),
    // wachar/guachar (wacha, wachate, guacha, wachas...), divisar, atisbar, avistar, distinguir,
    // asomar (asomate, asomese...), leer (lee, leé, leeme, leyendo, leas...),
    // inspeccionar, escanear, examinar, buscar, encontrar, hallar, mostrar (muestrame...), ensenar (ensename...)
    private val SPANISH_PERCEPTION_REGEX = Regex(
        """\b(?:mir[a-z]*|ve[orsn]?|veis|veas?|vean|vemos|viendo|viste[a-z]*|visto|ver|observ[a-z]*|fij[a-z]*|chec[a-z]*|chequ[a-z]*|cach[a-z]*|cachai|cachay|pill[a-z]*|wach[a-z]*|guach[a-z]*|divis[a-z]*|atisb[a-z]*|avist[a-z]*|disting[a-z]*|asom[a-z]*|le[eyioa][a-z]*|lectura|inspeccion[a-z]*|escan[a-z]*|examin[a-z]*|busc[a-z]*|encontr[a-z]*|hall[a-z]*|muestr[a-z]*|mostr[a-z]*|ensen[a-z]*)\b"""
    )

    // Spanish colloquial perception idioms: vistazo, ojo, ojear, ojito, mirada ("échale un ojo", "echa un vistazo", etc.)
    private val SPANISH_PERCEPTION_IDIOMS_REGEX = Regex(
        """\b(?:vistazo|ojo|ojear|ojito|mirada)\b"""
    )

    // English perception stems and colloquial idioms:
    // look, see, saw, seen, view, watch, check, inspect, examine, scan, observe, notice, spot,
    // glance, glimpse, peep, peek, stare, peer, read, gander, squint, looksee, find, found, locate, show, display
    private val ENGLISH_PERCEPTION_REGEX = Regex(
        """\b(?:look[a-z]*|see[a-z]*|saw|seen|view[a-z]*|watch[a-z]*|check[a-z]*|inspect[a-z]*|examin[a-z]*|scan[a-z]*|observ[a-z]*|notic[a-z]*|spot[a-z]*|glanc[a-z]*|glimps[a-z]*|peep[a-z]*|peek[a-z]*|star[a-z]*|peer[a-z]*|read[a-z]*|gander|squint|looksee|find[a-z]*|found|locat[a-z]*|show[a-z]*|display[a-z]*)\b"""
    )

    // Search and localization inquiries across dialects (e.g. "dónde está la laptop", "busca las llaves", "where is the bottle")
    private val SPANISH_SEARCH_REGEX = Regex(
        """\b(?:busc[a-z]*|encontr[a-z]*|hall[a-z]*|ubic[a-z]*|donde (?:esta|estan|deje|dejaste|quedo|pusiste|puso))\b"""
    )
    private val ENGLISH_SEARCH_REGEX = Regex(
        """\b(?:find[a-z]*|locat[a-z]*|search[a-z]*|where did (?:i|you) (?:leave|put)|(?:where is|wheres|where are)\s+(?!you\b|we\b|they\b)[a-z]+)\b"""
    )

    // Media & Camera explicitly referenced
    private val MEDIA_CAMERA_REGEX = Regex(
        """\b(?:camara|camera|foto|photo|picture|imagen|image|snapshot|pantalla|screen|video)\b"""
    )

    // Deictic / Demonstrative inquiries referencing physical context ("¿Qué es esto?", "What is this?", "What am I holding?")
    private val DEMONSTRATIVE_INQUIRY_REGEX = Regex(
        """\b(?:que|cual|cuales|what|whats|which)\b.*\b(?:esto|esta|este|estos|estas|eso|esa|ese|esos|esas|aquello|this|that|these|those)\b"""
    )

    // Spatial presence inquiries ("¿Qué hay enfrente?", "¿Qué tienes delante?", "What's in front?")
    private val SPATIAL_INQUIRY_REGEX = Regex(
        """\b(?:que|what|whats)\b.*\b(?:enfrente|delante|frente|adelante|ahead|in front|in sight)\b"""
    )

    // Description & recognition intent verbs
    private val DESCRIPTION_RECOGNITION_REGEX = Regex(
        """\b(?:describ[a-z]*|describe[a-z]*|reconoc[a-z]*|recogniz[a-z]*|identific[a-z]*|identify[a-z]*)\b"""
    )

    // --- 2. Deep Multimodal Reasoning Intent Patterns ---

    // Scene inquiry: Interrogative/request + visual perception verb
    // e.g. "¿Qué estás viendo?", "¿Qué ves?", "What do you see?", "What are you looking at?", "¿Qué cachái?", "¿Qué pillas?", "Dime qué ves"
    private val DEEP_SCENE_INQUIRY_REGEX = Regex(
        """\b(?:que|cual|what|whats|dime|cuentame|contame|tell|can you tell)\b.*\b(?:ves|ve|veo|viendo|mirando|miras|cacha|cachas|cachai|cachay|pilla|pillas|wacha|wachas|guacha|fijas|fijate|divisas|see|seeing|looking|look at|looking at|spot|spotted)\b"""
    )

    // Tell/describe what you see
    private val DEEP_TELL_SCENE_REGEX = Regex(
        """\b(?:dime|cuentame|contame|tell me)\b.*\b(?:lo que ves|what you see)\b"""
    )

    // Spatial surroundings scene inquiry
    // e.g. "¿Qué hay enfrente?", "¿Qué tienes delante?", "¿Qué hay acá?", "What is in front of you?", "What's in front?", "What is ahead?"
    private val DEEP_SPATIAL_SCENE_REGEX = Regex(
        """\b(?:que|what|whats|tell me|dime|cuentame)\b.*\b(?:hay|tienes|have|is|there)\b.*\b(?:enfrente|delante|frente|adelante|in front|ahead|around|alrededor|aqui|aca|here)\b"""
    )

    // Scene activity / dynamic events
    // e.g. "¿Qué está pasando?", "¿Qué está haciendo?", "What are they doing?", "What's happening?"
    private val DEEP_SCENE_ACTIVITY_REGEX = Regex(
        """\b(?:que|what|whats)\b.*\b(?:haciendo|pasando|pasa|doing|happening|going on)\b"""
    )

    // Text / Reading / OCR intent
    // e.g. "Leé este papel", "¿Qué dice aquí?", "¿Qué pone en la botella?", "Read this sign", "What is written on the box?"
    private val DEEP_OCR_VERBS_REGEX = Regex(
        """\b(?:le[eyioa][a-z]*|read[a-z]*)\b"""
    )
    private val DEEP_OCR_PHRASES_REGEX = Regex(
        """\b(?:que dice|que pone|what does it say|what is written|whats written|what it says)\b"""
    )
    private val DEEP_OCR_NOUNS_REGEX = Regex(
        """\b(?:texto|escrito|letras|cartel|etiqueta|letrero|text|words|writing|sign|label)\b"""
    )

    // Attribute & Property inquiries (color, brand, make, material, visual condition)
    // e.g. "¿De qué color es mi remera?", "¿De qué marca es la botella?", "What brand is this?", "What color?"
    private val DEEP_COLOR_REGEX = Regex("""\b(?:color|colour)\b""")
    private val DEEP_BRAND_REGEX = Regex("""\b(?:marca|brand|logo|fabricante|make|model)\b""")
    private val DEEP_MATERIAL_REGEX = Regex("""\b(?:de que esta hech[oa]|what is it made of|material|como se ve|how does it look)\b""")

    // Identity & Facial recognition
    // e.g. "¿Quién es esta persona?", "¿Quién soy?", "Who is this?", "Who is that?", "Sabes quién soy?", "Reconocé mi cara"
    private val DEEP_IDENTITY_REGEX = Regex(
        """\b(?:quien es|quien soy|quienes son|who is|who are|who am i|whos that|whos this)\b"""
    )
    private val DEEP_FACE_REGEX = Regex("""\b(?:cara|rostro|face)\b""")

    // Causal explanations & reasoning
    // e.g. "¿Por qué?", "Why?", "Explicá detalladamente lo que ves", "Explain what's happening"
    private val DEEP_EXPLANATION_REGEX = Regex(
        """\b(?:por que|porque|why|explic[a-z]*|explain[a-z]*)\b"""
    )

    // Counting & Quantitative analysis
    // e.g. "¿Cuántas personas hay?", "How many bottles?", "Count the cups"
    private val DEEP_COUNTING_REGEX = Regex(
        """\b(?:cuant[oa]s|how many|count)\b"""
    )

    // Demonstrative object inquiries (asking to identify / explain an object presented or pointed to)
    // e.g. "¿Qué es esto?", "¿Qué tengo acá?", "What is this?", "What am I holding?", "What's that thing?"
    private val DEEP_DEMONSTRATIVE_OBJECT_REGEX = Regex(
        """\b(?:que es esto|que es eso|que es aquello|que tengo|que objeto|what is this|whats this|what is that|whats that|what am i holding|what do i have|what is in my hand)\b"""
    )

    // Non-target pronouns and stop-words to exclude when extracting target candidates
    private val NON_TARGET_WORDS = setOf(
        "algo", "esto", "eso", "aquello", "nada", "nadie", "alguien",
        "this", "that", "these", "those", "something", "anything", "nothing", "someone", "there", "here"
    )

    // Synonym clusters for dialectal robotic object matching
    private val SYNONYM_CLUSTERS = listOf(
        // Person
        setOf("person", "persona", "humano", "human", "man", "woman", "hombre", "mujer", "chico", "chica", "pibe", "chaval", "people", "gente"),
        // Cup / Mug
        setOf("cup", "coffee cup", "taza", "mug", "pocillo", "jarro", "tazon"),
        // Bottle
        setOf("bottle", "botella", "termo", "caramanola", "pachon"),
        // Phone
        setOf("cell phone", "phone", "mobile", "mobile phone", "telefono", "celular", "movil"),
        // Computer
        setOf("laptop", "notebook", "computer", "computadora", "ordenador", "compu"),
        // Chair
        setOf("chair", "seat", "silla", "asiento", "sillon", "butaca"),
        // Glasses
        setOf("glasses", "sunglasses", "eyeglasses", "anteojos", "lentes", "gafas"),
        // Dog
        setOf("dog", "puppy", "perro", "perrito", "can", "chucho"),
        // Cat
        setOf("cat", "kitten", "gato", "gatito", "michi"),
        // Book
        setOf("book", "libro", "cuaderno", "notebook"),
        // Table
        setOf("table", "desk", "mesa", "escritorio"),
        // Bag / Backpack
        setOf("backpack", "bag", "handbag", "mochila", "bolso", "cartera", "morral"),
        // Vehicle / Car
        setOf("car", "automobile", "vehicle", "auto", "coche", "carro"),
        // Beer
        setOf("beer", "cerveza", "chela", "birra"),
        // Pen / Pencil
        setOf("pen", "pencil", "lapicera", "birome", "boligrafo", "pluma", "lapiz")
    )

    /**
     * Normalizes text by removing diacritical marks (accents), normalizing apostrophes,
     * removing non-alphanumeric punctuation, and collapsing whitespace.
     */
    fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noDiacritics
            .replace("'", "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Identifies whether the user expresses visual communicative intent (asking the robot to look,
     * perceive, inspect, find, search, or describe the physical scene).
     */
    fun hasVisualIntent(message: String): Boolean {
        val clean = normalizeText(message)
        if (clean.isBlank()) return false

        return SPANISH_PERCEPTION_REGEX.containsMatchIn(clean) ||
                SPANISH_PERCEPTION_IDIOMS_REGEX.containsMatchIn(clean) ||
                SPANISH_SEARCH_REGEX.containsMatchIn(clean) ||
                ENGLISH_PERCEPTION_REGEX.containsMatchIn(clean) ||
                ENGLISH_SEARCH_REGEX.containsMatchIn(clean) ||
                MEDIA_CAMERA_REGEX.containsMatchIn(clean) ||
                DEMONSTRATIVE_INQUIRY_REGEX.containsMatchIn(clean) ||
                SPATIAL_INQUIRY_REGEX.containsMatchIn(clean) ||
                DESCRIPTION_RECOGNITION_REGEX.containsMatchIn(clean)
    }

    /**
     * Checks if the message requires deep multimodal comprehension and reasoning from the
     * server's LLM (e.g. reading text, analyzing colors/brands, describing open-ended scenes,
     * explaining relationships, recognizing facial identity, or identifying pointed-at items).
     */
    fun requiresDeepReasoning(message: String): Boolean {
        val clean = normalizeText(message)
        if (clean.isBlank()) return false

        // 1. Scene description and open visual inquiries
        if (DEEP_SCENE_INQUIRY_REGEX.containsMatchIn(clean) ||
            DEEP_TELL_SCENE_REGEX.containsMatchIn(clean) ||
            DEEP_SPATIAL_SCENE_REGEX.containsMatchIn(clean) ||
            DESCRIPTION_RECOGNITION_REGEX.containsMatchIn(clean) ||
            DEEP_SCENE_ACTIVITY_REGEX.containsMatchIn(clean)
        ) {
            return true
        }

        // 2. Reading text / OCR
        if (DEEP_OCR_VERBS_REGEX.containsMatchIn(clean) ||
            DEEP_OCR_PHRASES_REGEX.containsMatchIn(clean) ||
            DEEP_OCR_NOUNS_REGEX.containsMatchIn(clean)
        ) {
            return true
        }

        // 3. Specific attributes (color, brand, make, material)
        if (DEEP_COLOR_REGEX.containsMatchIn(clean) ||
            DEEP_BRAND_REGEX.containsMatchIn(clean) ||
            DEEP_MATERIAL_REGEX.containsMatchIn(clean)
        ) {
            return true
        }

        // 4. Facial identity and recognition
        if (DEEP_IDENTITY_REGEX.containsMatchIn(clean) ||
            (DEEP_FACE_REGEX.containsMatchIn(clean) && hasVisualIntent(clean))
        ) {
            return true
        }

        // 5. Causal explanations & reasoning
        if (DEEP_EXPLANATION_REGEX.containsMatchIn(clean)) {
            return true
        }

        // 6. Quantitative counting
        if (DEEP_COUNTING_REGEX.containsMatchIn(clean)) {
            return true
        }

        // 7. Demonstrative object identification ("What is this / that / what am I holding")
        if (DEEP_DEMONSTRATIVE_OBJECT_REGEX.containsMatchIn(clean)) {
            return true
        }

        return false
    }

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

        // 2. Check if deeper comprehension / reasoning is requested
        if (requiresDeepReasoning(userMessage)) {
            // Deeper multimodal reasoning is needed -> Send image to server
            return true
        }

        // 3. Check if the user expressed visual intent or has an active target
        val hasVisual = hasVisualIntent(userMessage)
        val hasActiveTarget = !target.isNullOrBlank()

        if (!hasVisual && !hasActiveTarget) {
            // General conversational turn (e.g. "Hola", "Cómo estás", "Cuéntame un chiste")
            // Efficient mode: DO NOT send images!
            return false
        }

        // 4. User is asking a visual question or looking for an object.
        // Check if on-device ML Kit can resolve it locally!
        val effectiveTarget = target ?: extractTargetCandidate(userMessage)
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
     * Matches detected ML Kit label with target query, allowing common dialect synonyms
     * across Spanish and English (e.g., cup/mug/taza, phone/celular/móvil, glasses/lentes/gafas).
     */
    fun isMatch(detectedLabel: String, target: String): Boolean {
        val det = normalizeText(detectedLabel)
        val tgt = normalizeText(target)

        if (det.isEmpty() || tgt.isEmpty()) return false
        if (det.contains(tgt) || tgt.contains(det)) return true

        for (synonyms in SYNONYM_CLUSTERS) {
            val matchesDet = synonyms.any { det.contains(it) }
            val matchesTgt = synonyms.any { tgt.contains(it) }
            if (matchesDet && matchesTgt) return true
        }

        return false
    }

    /**
     * Attempts to extract a target candidate from visual search questions across dialects
     * (e.g. "¿Ves una taza?", "Do you see the bottle?", "¿Wachas la chela?", "Can you spot the mug?").
     */
    fun extractTargetCandidate(message: String): String? {
        val clean = normalizeText(message)
        if (clean.isBlank()) return null

        val regexes = listOf(
            Regex("""(?i)(?:ve[orsn]?|veis|mira[a-z]*|fij[a-z]*|checa[a-z]*|chequ[a-z]*|cacha[a-z]*|pilla[a-z]*|wacha[a-z]*|guacha[a-z]*|divis[a-z]*|busc[a-z]*|encuentr[a-z]*|hall[a-z]*|ubic[a-z]*|donde esta|donde estan|alcanzas a ver)\s+(?:(?:el|la|los|las|un|una|unos|unas|mi|mis|tu|tus|su|sus|al|del)\s+)?([a-z]+)"""),
            Regex("""(?i)(?:do you see|can you see|can you spot|spot|find|where is|where are|locate|check for|look for|search for)\s+(?:(?:the|a|an|my|this|any)\s+)?([a-z]+)""")
        )

        for (regex in regexes) {
            val match = regex.find(clean)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length > 2 && candidate !in NON_TARGET_WORDS) {
                    return candidate
                }
            }
        }
        return null
    }
}
