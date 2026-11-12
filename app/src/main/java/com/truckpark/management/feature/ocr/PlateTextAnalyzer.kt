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
            if (PlateOcrNormalizer.isValidPlate(normalized) && height > bestHeight) {
                best = normalized
                bestHeight = height
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
                    consider(element.text + elements[i + 1].text, h)
                }
            }

            if (index + 1 < lines.size) {
                val next = lines[index + 1]
                val nextHeight = next.boundingBox?.height() ?: 0
                consider(line.text + next.text, minOf(lineHeight, nextHeight))
            }
        }
        return best
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
            }
        }

        if (scanMode == OcrScanMode.PLATE) {
            bestPlateCandidate = bestPlateIn(visionText)
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
