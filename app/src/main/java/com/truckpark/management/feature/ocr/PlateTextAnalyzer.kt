package com.truckpark.management.feature.ocr

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

data class OcrScanResult(
    val plateNumber: String,
    val matchedCarrier: String? = null,
    val rawText: String = "",
)

object PlateOcrNormalizer {

    private val ZAMBIA_PLATE_REGEX = Regex("""^[A-Z]{2,4}\d{3,5}[A-Z]?$""")

    fun normalizeCandidate(raw: String): String {
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.length < 5 || cleaned.length > 10) return cleaned

        val chars = cleaned.toCharArray()
        val letterPrefixLength = chars.takeWhile { it.isLetter() || it == '0' || it == '1' || it == '8' }.count()

        if (letterPrefixLength in 2..4) {
            val sb = StringBuilder()
            for (i in chars.indices) {
                val c = chars[i]
                if (i < letterPrefixLength) {
                    val fixed = when (c) {
                        '0' -> 'O'
                        '1' -> 'I'
                        '8' -> 'B'
                        else -> c
                    }
                    sb.append(fixed)
                } else {
                    val fixed = when (c) {
                        'O', 'D', 'Q' -> '0'
                        'I', 'L' -> '1'
                        'Z' -> '2'
                        'S' -> '5'
                        'B' -> '8'
                        else -> c
                    }
                    sb.append(fixed)
                }
            }
            val candidate = sb.toString()
            if (isValidPlate(candidate)) {
                return candidate
            }
        }

        return cleaned
    }

    fun isValidPlate(plate: String): Boolean {
        if (plate.length !in 5..10) return false
        return ZAMBIA_PLATE_REGEX.matches(plate)
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

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isConfirmed) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                processVisionText(visionText)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
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

            if (scanMode == OcrScanMode.TRANSPORTER) {
                if (foundCarrier != null) {
                    bestPlateCandidate = foundCarrier
                    break
                }
                for (line in block.lines) {
                    val t = line.text.trim()
                    if (t.length in 3..35 && t.any { it.isLetter() }) {
                        bestPlateCandidate = t
                        break
                    }
                }
                if (bestPlateCandidate != null) break
            } else {
                // Inspect individual lines for plates
                for (line in block.lines) {
                    val normalizedLine = PlateOcrNormalizer.normalizeCandidate(line.text)
                    if (PlateOcrNormalizer.isValidPlate(normalizedLine)) {
                        bestPlateCandidate = normalizedLine
                        break
                    }

                    // Check individual words in line
                    for (element in line.elements) {
                        val normalizedElement = PlateOcrNormalizer.normalizeCandidate(element.text)
                        if (PlateOcrNormalizer.isValidPlate(normalizedElement)) {
                            bestPlateCandidate = normalizedElement
                            break
                        }
                    }
                    if (bestPlateCandidate != null) break
                }
                // Stop on first matching plate so a lower/secondary plate in the frame doesn't overwrite it
                if (bestPlateCandidate != null) break
            }
        }

        val allDetected = fullTextBuilder.toString().trim()
        onFrameProcessed(allDetected)

        // Consistency gate: only accept if candidate is consistently seen across consecutive frames
        if (bestPlateCandidate != null) {
            if (bestPlateCandidate == candidatePlate) {
                consecutiveCount++
                if (foundCarrier != null) {
                    detectedCarrier = foundCarrier
                }
                if (consecutiveCount >= requiredConsecutiveFrames) {
                    isConfirmed = true
                    onPlateConfirmed(
                        OcrScanResult(
                            plateNumber = bestPlateCandidate,
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
