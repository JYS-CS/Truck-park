package com.truckpark.management.feature.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

enum class OcrScanMode {
    PLATE,
    TRANSPORTER,
}

/** Rectangle expressed as fractions (0..1) of the camera preview. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** Box of [width] x [height] centred on ([cx], [cy]), shifted back inside 0..1 if needed. */
        fun centered(cx: Float, cy: Float, width: Float, height: Float): NormalizedRect {
            val w = width.coerceIn(0.1f, 1f)
            val h = height.coerceIn(0.05f, 1f)
            val left = (cx - w / 2f).coerceIn(0f, 1f - w)
            val top = (cy - h / 2f).coerceIn(0f, 1f - h)
            return NormalizedRect(left, top, left + w, top + h)
        }
    }
}

data class OcrScanResult(
    val plateNumber: String,
    val matchedCarrier: String? = null,
    val rawText: String = "",
)

object PlateOcrNormalizer {

    // Comprehensive SADC Regional License Plate Patterns:
    // - Zambia (ZM): BAA 1234, BCA 1234 B, ALX 123, CD 1234
    // - South Africa (ZA): AB 12 CD GP, BCD 123 MP, CA 123-456, ND 123456, AB 12 CD L
    // - Zimbabwe (ZW): AEB 1234
    // - Botswana (BW): B 123 ABC
    // - Namibia (NA): N 12345 W, N 12345 WB, N 123456 SH
    // - Tanzania (TZ): T 123 ABC
    // - Mozambique (MZ): AAA 123 MC, AAA 1234 MP
    // - Malawi (MW): BL 1234, LL 1234, BLK 1234
    // - DR Congo (CD): 1234 AB 01
    // - Angola (AO): LD 12-34-AB
    // - Lesotho (LS) / Eswatini (SZ): A 123 ABC, SD 123 AB, ASD 123 AM
    private val PLATE_PATTERNS = listOf(
        // Zambia / Zimbabwe / Malawi / SA numeric (e.g. BAA1234, BCA1234B, ALX123, BL1234, CA123456)
        Regex("""^[A-Z]{2,4}\d{3,6}[A-Z]?$"""),
        // South Africa modern provincial (e.g. AB12CDGP, CK88LLGP, AB12CDL, BB12CDZN)
        Regex("""^[A-Z]{2}\d{2}[A-Z]{2}[A-Z]{1,3}$"""),
        // South Africa older provincial / Mozambique / Eswatini (e.g. BCD123GP, AAA123MC, AAA1234MP, ASD123AM, SD123AB)
        Regex("""^[A-Z]{2,3}\d{3,4}[A-Z]{1,3}$"""),
        // Botswana / Tanzania / Lesotho (e.g. B123ABC, T123ABC, A123ABC)
        Regex("""^[A-Z]\d{3}[A-Z]{3}$"""),
        // Namibia (e.g. N12345W, N12345WB, N123456W)
        Regex("""^N\d{3,6}[A-Z]{1,2}$"""),
        // DR Congo (e.g. 1234AB01)
        Regex("""^\d{4}[A-Z]{2}\d{2}$"""),
        // Angola (e.g. LD1234AB)
        Regex("""^[A-Z]{2}\d{4}[A-Z]{2}$"""),
        // General SADC commercial / cross-border trailer fallback
        Regex("""^[A-Z]{1,4}\d{2,6}[A-Z]{0,3}$"""),
        Regex("""^\d{2,5}[A-Z]{2,4}\d{1,4}$""")
    )

    // Common non-transporter utility words on heavy commercial vehicles to discard
    private val IGNORED_WORDS = setOf(
        "STOP", "CAUTION", "DIESEL", "HAZMAT", "DANGER", "FLAMMABLE", "LONG", "VEHICLE",
        "LONGVEHICLE", "SPEED", "MAX", "LIMIT", "OVERTAKING", "WARNING", "ABS", "AIR",
        "BRAKE", "SUSPENSION", "VOLVO", "SCANIA", "MERCEDES", "BENZ", "MAN", "DAF",
        "SHACMAN", "HOWO", "FAW", "ISUZU", "HINO", "FUSO", "SUPERLINK", "TRAILER",
        "GROSS", "TARE", "PAYLOAD", "KG", "TONS", "TONNES", "EMPTY", "FULL"
    )

    fun isIgnoredTransporterWord(word: String): Boolean {
        val upper = word.trim().uppercase()
        if (IGNORED_WORDS.contains(upper)) return true
        val tokens = upper.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.all { IGNORED_WORDS.contains(it) }
    }

    /**
     * Normalizes character misreads (e.g., '0' <-> 'O', '1' <-> 'I', '8' <-> 'B')
     * across all SADC regional plate formats.
     */
    fun normalizeCandidate(raw: String): String {
        val upperRaw = raw.uppercase().trim()

        // 1. Token-aware pass before stripping spaces/hyphens across SADC formats
        val rawTokens = upperRaw.split(Regex("""[^A-Z0-9]+""")).filter { it.isNotEmpty() }
        val tokenRepaired = if (rawTokens.size >= 2) {
            repairSadcTokens(rawTokens)
        } else {
            upperRaw.filter { it.isLetterOrDigit() }
        }

        var cleaned = tokenRepaired.filter { it.isLetterOrDigit() }
        if (cleaned.length < 5 || cleaned.length > 10) return cleaned

        // 2. Structural repair for '8'/'0'/'1' misread in letter positions across all SADC formats
        cleaned = repairStructuralBAndDigits(cleaned)

        // If it already matches a known SADC plate pattern cleanly, return as-is
        if (isValidPlate(cleaned)) {
            return cleaned
        }

        // 3. Middle-digit repair for prefix+digits+(optional suffix letters) formats
        //    (e.g. "BCA12O4B" -> "BCA1204B", "AB1OCDGP" -> "AB10CDGP", "B12OABC" -> "B120ABC")
        val chars = cleaned.toCharArray()
        val prefixLen = chars.takeWhile { it.isLetter() }.count()
        val suffixLen = chars.reversed().takeWhile { it.isLetter() }.count()

        if (prefixLen in 1..4 && prefixLen + suffixLen < chars.size) {
            val digitEndIndex = chars.size - suffixLen
            val sb = StringBuilder()
            for (i in 0 until prefixLen) {
                sb.append(digitToLetter(chars[i]))
            }
            for (i in prefixLen until digitEndIndex) {
                sb.append(letterToDigit(chars[i]))
            }
            for (i in digitEndIndex until chars.size) {
                sb.append(digitToLetter(chars[i]))
            }
            val fixed = sb.toString()
            if (isValidPlate(fixed)) {
                return fixed
            }
        }

        return cleaned
    }

    private fun repairSadcTokens(tokens: List<String>): String {
        val t = tokens.toMutableList()

        // DR Congo: "1234 AB 01" -> [4 digits, 2 letters, 2 digits]
        if (t.size == 3 && t[0].length == 4 && t[0].count { it.isDigit() } >= 3 &&
            t[1].length == 2 && t[2].length == 2 && t[2].count { it.isDigit() } >= 1
        ) {
            t[0] = t[0].map { letterToDigit(it) }.joinToString("")
            t[1] = t[1].map { digitToLetter(it) }.joinToString("")
            t[2] = t[2].map { letterToDigit(it) }.joinToString("")
            return t.joinToString("")
        }

        // Botswana / Tanzania / Lesotho: "B 123 ABC" or "T 123 ABC" -> [1 letter, 3 digits, 3 letters]
        if (t.size == 3 && t[0].length == 1 && t[1].length == 3 && t[2].length == 3 &&
            t[1].count { it.isDigit() } >= 2 && t[2].any { it.isLetter() || it == '8' }
        ) {
            t[0] = digitToLetter(t[0][0]).toString()
            t[1] = t[1].map { letterToDigit(it) }.joinToString("")
            t[2] = t[2].map { digitToLetter(it) }.joinToString("")
            return t.joinToString("")
        }

        // South Africa modern: "AB 12 CD GP" -> [2 letters, 2 digits, 2 letters, 1-3 letters]
        if (t.size == 4 && t[0].length == 2 && t[1].length == 2 && t[2].length == 2 && t[3].length in 1..3) {
            val secondIsDigits = t[1].count { it.isDigit() } >= 1
            val thirdHasLetters = t[2].any { it.isLetter() }
            if (secondIsDigits && thirdHasLetters) {
                t[0] = t[0].map { digitToLetter(it) }.joinToString("")
                t[1] = t[1].map { letterToDigit(it) }.joinToString("")
                t[2] = t[2].map { digitToLetter(it) }.joinToString("")
                t[3] = t[3].map { digitToLetter(it) }.joinToString("")
                return t.joinToString("")
            }
            // Angola: "LD 12 34 AB" -> [2 letters, 2 digits, 2 digits, 2 letters]
            if (secondIsDigits && t[2].count { it.isDigit() } >= 1) {
                t[0] = t[0].map { digitToLetter(it) }.joinToString("")
                t[1] = t[1].map { letterToDigit(it) }.joinToString("")
                t[2] = t[2].map { letterToDigit(it) }.joinToString("")
                t[3] = t[3].map { digitToLetter(it) }.joinToString("")
                return t.joinToString("")
            }
        }

        // General SADC [Prefix letters] + [Digits] + [Optional suffix letters]
        // (Covers Zambia "BA8 1234", "BCA 1234 8", Mozambique "AAA 123 M8", Namibia "N 12345 W8", SA "BCD 123 GP", Angola "LD 1234 A8")
        val first = t[0]
        val second = t[1]
        if (first.length in 1..4 && second.count { it.isDigit() } >= 2 &&
            first.any { it.isLetter() || it == '8' }
        ) {
            t[0] = first.map { digitToLetter(it) }.joinToString("")
        }
        if (t.size >= 3) {
            val lastIdx = t.lastIndex
            val lastTok = t[lastIdx]
            val prevTok = t[lastIdx - 1]
            if (lastTok.length in 1..3 && prevTok.count { it.isDigit() } >= 2 &&
                lastTok.any { it.isLetter() || it in "018" }
            ) {
                t[lastIdx] = lastTok.map { digitToLetter(it) }.joinToString("")
            }
        }
        return t.joinToString("")
    }

    /**
     * Repairs common '8' <-> 'B' (and '0' <-> 'O', '1' <-> 'I') confusions in prefix, middle, and suffix
     * letter positions across SADC plate formats even when ML Kit returns the plate without spaces.
     */
    private fun repairStructuralBAndDigits(s: String): String {
        // 1. DR Congo (8 chars: 4 digits + 2 letters + 2 digits, e.g. "1234A801", "12348B01")
        if (s.length == 8 &&
            s.substring(0, 4).all { it.isDigit() } &&
            s.substring(6, 8).all { it.isDigit() } &&
            s.substring(4, 6).any { it.isLetter() } &&
            s.substring(4, 6).all { it.isLetter() || it in "018" }
        ) {
            val c = s.toCharArray()
            c[4] = digitToLetter(c[4])
            c[5] = digitToLetter(c[5])
            return String(c)
        }

        // 2. Botswana / Tanzania / Lesotho (7 chars: 1 letter + 3 digits + 3 letters, e.g. "8123ABC", "B123A8C", "T123AB8")
        if (s.length == 7 &&
            (s[0].isLetter() || s[0] == '8') &&
            s.substring(1, 4).all { it.isDigit() } &&
            s.substring(4, 7).count { it.isLetter() } >= 2 &&
            s.substring(4, 7).all { it.isLetter() || it in "018" }
        ) {
            val c = s.toCharArray()
            c[0] = digitToLetter(c[0])
            for (i in 4..6) c[i] = digitToLetter(c[i])
            return String(c)
        }

        // 3. South Africa modern (7..9 chars: 2 letters + 2 digits + 3..5 letters, e.g. "AB12C8GP", "8B12CDGP")
        if (s.length in 7..9 &&
            s.substring(0, 2).all { it.isLetter() || it in "018" } &&
            s.substring(2, 4).all { it.isDigit() } &&
            s.substring(4).count { it.isLetter() } >= 2 &&
            s.substring(4).all { it.isLetter() || it in "018" }
        ) {
            val c = s.toCharArray()
            c[0] = digitToLetter(c[0])
            c[1] = digitToLetter(c[1])
            for (i in 4 until c.size) c[i] = digitToLetter(c[i])
            return String(c)
        }

        // 4. Namibia (starts with 'N' + 3..6 digits + 1..2 suffix letters where trailing '8' is 'B', e.g. "N12345W8")
        if (Regex("""^N\d{3,6}[A-Z]8$""").matches(s)) {
            val c = s.toCharArray()
            c[c.lastIndex] = 'B'
            return String(c)
        }

        val chars = s.toCharArray()

        // 5. Any '8', '0', or '1' in indices 0..2 that is followed by a letter in the prefix
        //    (e.g., "8AA1234" -> "BAA1234", "B8A1234" -> "BBA1234", "8L1234" -> "BL1234", "8AA123MC" -> "BAA123MC")
        val lastPrefixLetterIdx = (0 until minOf(4, chars.size))
            .lastOrNull { chars[it].isLetter() && (it == 0 || chars.take(it).all { c -> c.isLetter() || c in "018" }) }
            ?: -1
        if (lastPrefixLetterIdx >= 1) {
            for (i in 0 until lastPrefixLetterIdx) {
                chars[i] = digitToLetter(chars[i])
            }
        }

        var current = String(chars)

        // 6. 2 letters + '8' + 3..4 digits (+ optional 1..2 suffix letters) -> 3rd char '8' is 'B'
        //    (e.g. "BA81234" -> "BAB1234", "BA8123MC" -> "BAB123MC")
        if (Regex("""^[A-Z]{2}8\d{3,4}[A-Z018]{0,2}$""").matches(current)) {
            val c = current.toCharArray()
            c[2] = 'B'
            current = String(c)
        }

        // 7. 1 letter + '8' + 3..4 digits (+ optional 1..2 suffix letters) -> 2nd char '8' is 'B'
        //    (e.g. "B81234" -> "BB1234", "L81234AB" -> "LB1234AB")
        if (Regex("""^[A-Z]8\d{3,4}[A-Z018]{0,2}$""").matches(current)) {
            val c = current.toCharArray()
            c[1] = 'B'
            current = String(c)
        }

        // 8. Trailing '8' in 2-letter suffix (Mozambique / SA / Angola / Eswatini, e.g. "AAA123M8" -> "AAA123MB", "LD1234A8" -> "LD1234AB")
        if (Regex("""^[A-Z]{2,3}\d{3,4}[A-Z]8$""").matches(current) ||
            Regex("""^[A-Z]{2,3}\d{3,4}8[A-Z]$""").matches(current)
        ) {
            val c = current.toCharArray()
            c[c.size - 2] = digitToLetter(c[c.size - 2])
            c[c.size - 1] = digitToLetter(c[c.size - 1])
            current = String(c)
        }

        // 9. 3 letters + 4 digits + trailing '8' -> trailing '8' is suffix letter 'B' (e.g. "BCA12348" -> "BCA1234B")
        if (Regex("""^[A-Z]{3}\d{4}8$""").matches(current)) {
            val c = current.toCharArray()
            c[c.lastIndex] = 'B'
            current = String(c)
        }

        return current
    }

    private fun digitToLetter(c: Char): Char = when (c) {
        '0' -> 'O'
        '1' -> 'I'
        '8' -> 'B'
        '5' -> 'S'
        '2' -> 'Z'
        else -> c
    }

    private fun letterToDigit(c: Char): Char = when (c) {
        'O', 'D', 'Q' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'B' -> '8'
        else -> c
    }

    fun isValidPlate(plate: String): Boolean {
        if (plate.length !in 5..10) return false
        // Must contain at least 2 letters and at least 2 digits to be a realistic plate
        val letterCount = plate.count { it.isLetter() }
        val digitCount = plate.count { it.isDigit() }
        if (letterCount < 2 || digitCount < 2) return false

        return PLATE_PATTERNS.any { it.matches(plate) }
    }
}

class PlateTextAnalyzer(
    private val scanMode: OcrScanMode = OcrScanMode.PLATE,
    private val knownTransporters: Set<String>,
    private val requiredConsecutiveFrames: Int = 3,
    private val onPlateConfirmed: (OcrScanResult) -> Unit,
    private val onFrameProcessed: (detectedText: String) -> Unit = {},
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var candidatePlate: String? = null
    private var consecutiveCount: Int = 0
    private var detectedCarrier: String? = null
    private var isConfirmed = false

    fun reset() {
        candidatePlate = null
        consecutiveCount = 0
        detectedCarrier = null
        isConfirmed = false
    }

    /**
     * Region the user placed on screen, as fractions (0..1) of the camera preview.
     * Only pixels inside this box are ever sent to the text recogniser.
     */
    @Volatile
    var cropRegion: NormalizedRect = NormalizedRect.centered(0.5f, 0.5f, 0.84f, 0.22f)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isConfirmed) {
            imageProxy.close()
            return
        }

        val cropped = try {
            buildCroppedBitmap(imageProxy)
        } catch (e: Exception) {
            null
        }
        if (cropped == null) {
            imageProxy.close()
            return
        }

        recognizer.process(InputImage.fromBitmap(cropped, 0))
            .addOnSuccessListener { visionText ->
                processVisionText(visionText)
            }
            .addOnCompleteListener {
                cropped.recycle()
                imageProxy.close()
            }
    }

    /**
     * Applies the camera viewport crop, rotates the frame upright, then cuts out exactly
     * the user's box so text outside it can never be recognised.
     */
    private fun buildCroppedBitmap(imageProxy: ImageProxy): Bitmap? {
        val full = imageProxy.toBitmap()
        var bitmap = full

        val crop = imageProxy.cropRect
        val isFullBuffer = full.width == imageProxy.width && full.height == imageProxy.height
        if (isFullBuffer && (crop.width() < full.width || crop.height() < full.height) &&
            crop.width() > 0 && crop.height() > 0
        ) {
            bitmap = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height())
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val region = cropRegion
        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (region.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /**
     * Collects plate candidates from lines, single words, adjacent word pairs and adjacent
     * line pairs (plates are often split as "BAH" / "1234"), then returns the one whose text
     * is largest in the frame, since the plate the user framed dominates the box.
     */
    private fun bestPlateIn(visionText: Text): String? {
        var best: String? = null
        var bestHeight = -1

        fun consider(raw: String, height: Int) {
            val normalized = PlateOcrNormalizer.normalizeCandidate(raw)
            if (!PlateOcrNormalizer.isValidPlate(normalized)) return
            // Prefer significantly taller text, or longer/more complete plate when heights are comparable
            val isMuchTaller = height > bestHeight + 6
            val isComparableAndLonger = height >= bestHeight - 6 && normalized.length > (best?.length ?: 0)
            if (best == null || isMuchTaller || isComparableAndLonger) {
                best = normalized
                bestHeight = maxOf(bestHeight, height)
            }
        }

        val lines = visionText.textBlocks.flatMap { it.lines }
        for ((index, line) in lines.withIndex()) {
            val lineHeight = line.boundingBox?.height() ?: 0
            consider(line.text, lineHeight)

            val elements = line.elements
            for ((i, element) in elements.withIndex()) {
                val h = element.boundingBox?.height() ?: lineHeight
                consider(element.text, h)
                if (i + 1 < elements.size) {
                    consider(element.text + " " + elements[i + 1].text, h)
                }
                if (i + 2 < elements.size) {
                    consider(
                        element.text + " " + elements[i + 1].text + " " + elements[i + 2].text,
                        h
                    )
                }
            }

            if (index + 1 < lines.size) {
                val next = lines[index + 1]
                val nextHeight = next.boundingBox?.height() ?: 0
                consider(line.text + " " + next.text, minOf(lineHeight, nextHeight))
            }
        }
        return best
    }

    /**
     * Collects all lines/words inside the user's crop box in top-to-bottom, left-to-right
     * reading order so multi-word and multi-line transporter information is captured in full.
     */
    private fun extractAllTransporterText(visionText: Text): String? {
        val orderedLines = visionText.textBlocks
            .flatMap { it.lines }
            .sortedWith(
                compareBy<Text.Line>(
                    { (it.boundingBox?.top ?: 0) / 24 },
                    { it.boundingBox?.left ?: 0 }
                )
            )
            .map { it.text.trim() }
            .filter { it.isNotEmpty() && !PlateOcrNormalizer.isIgnoredTransporterWord(it) }

        val combined = orderedLines
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return if (combined.count { it.isLetter() } >= 2) combined else null
    }

    private fun isSameCandidate(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (scanMode == OcrScanMode.PLATE) return a == b
        val normA = a.filter { it.isLetterOrDigit() }.uppercase()
        val normB = b.filter { it.isLetterOrDigit() }.uppercase()
        return normA.isNotEmpty() && normA == normB
    }

    private fun processVisionText(visionText: Text) {
        if (isConfirmed) return

        val allBlocks = visionText.textBlocks
        var bestPlateCandidate: String? = null
        var foundCarrier: String? = null

        val fullTextBuilder = StringBuilder()

        for (block in allBlocks) {
            val blockText = block.text.trim()
            fullTextBuilder.append(blockText).append(" ")

            // Check if any block matches or contains a known transporter name
            if (foundCarrier == null) {
                for (carrier in knownTransporters) {
                    if (blockText.contains(carrier, ignoreCase = true)) {
                        foundCarrier = carrier
                        break
                    }
                }
            }
        }

        val allDetected = fullTextBuilder.toString().replace(Regex("""\s+"""), " ").trim()
        if (foundCarrier == null && allDetected.isNotEmpty()) {
            for (carrier in knownTransporters) {
                if (allDetected.contains(carrier, ignoreCase = true)) {
                    foundCarrier = carrier
                    break
                }
            }
        }

        bestPlateCandidate = when (scanMode) {
            OcrScanMode.TRANSPORTER -> extractAllTransporterText(visionText)
            OcrScanMode.PLATE -> bestPlateIn(visionText)
        }

        onFrameProcessed(allDetected)

        // Consistency gate: only accept if candidate is consistently seen across consecutive frames
        if (bestPlateCandidate != null) {
            if (isSameCandidate(bestPlateCandidate, candidatePlate)) {
                consecutiveCount++
                // Keep the version with the most complete spacing/formatting
                if (bestPlateCandidate.length >= (candidatePlate?.length ?: 0)) {
                    candidatePlate = bestPlateCandidate
                }
                if (foundCarrier != null) {
                    detectedCarrier = foundCarrier
                }
                if (consecutiveCount >= requiredConsecutiveFrames) {
                    isConfirmed = true
                    onPlateConfirmed(
                        OcrScanResult(
                            plateNumber = candidatePlate ?: bestPlateCandidate,
                            matchedCarrier = detectedCarrier,
                            rawText = allDetected,
                        )
                    )
                }
            } else {
                candidatePlate = bestPlateCandidate
                consecutiveCount = 1
                detectedCarrier = foundCarrier
            }
        } else {
            // Reset streak if nothing detected
            consecutiveCount = (consecutiveCount - 1).coerceAtLeast(0)
        }
    }
}
