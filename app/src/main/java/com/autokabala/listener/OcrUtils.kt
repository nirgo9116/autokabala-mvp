package com.autokabala.listener

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object OcrUtils {

    data class MlKitResult(val text: String, val amount: Double?, val nameAboveAmount: String?)

    suspend fun runMlKitOcr(bitmap: Bitmap): MlKitResult? =
        suspendCancellableCoroutine { cont ->
            try {
                val image      = InputImage.fromBitmap(bitmap, 0)
                // Hebrew recognizer reads both Hebrew text and numbers from the same image.
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (result.text.isBlank()) {
                            if (cont.isActive) cont.resume(null)
                            return@addOnSuccessListener
                        }

                        // ── Amount: find the "amount block" without relying on ₪ ──────────
                        // ML Kit Latin recogniser does not reliably output the ₪ symbol —
                        // it is either absent or misread (e.g. „160 instead of ₪160).
                        // The amount is still readable as a block of digits.
                        // Strategy:
                        //  1. For every block, strip one optional leading non-alphanumeric
                        //     character (handles „, ₪, •, etc.).
                        //  2. Keep blocks whose remaining text is purely digits, 2–5 chars,
                        //     with no dots (dates) or colons (times).
                        //  3. Among candidates pick the one with the largest bounding box —
                        //     the amount is always displayed in the biggest font on screen.
                        //  4. Validate the result is in the range 10–99 999.
                        // O/o → 0 normalization handles common OCR digit misreads.
                        fun String.normDigits() = replace('O', '0').replace('o', '0')
                            .replace('l', '1').replace('I', '1')

                        Log.d("MlKitOcr", "Blocks: ${result.textBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val candidateBlocks = result.textBlocks.filter { block ->
                            val text = block.text.normDigits().trim()
                            // Strip one leading non-digit char (handles misread ₪ → „, B, etc.)
                            val stripped = if (text.isNotEmpty() && !text.first().isDigit()) text.drop(1) else text
                            // Strip one trailing non-digit char (Paybox shows "900-" with trailing minus)
                            val core = if (stripped.isNotEmpty() && !stripped.last().isDigit()) stripped.dropLast(1) else stripped
                            // Strip commas to handle thousand-separator format (e.g. "1,000")
                            val coreDigits = core.replace(",", "")
                            coreDigits.length in 2..5 &&
                                coreDigits.all { it.isDigit() } &&
                                !text.contains('.') &&
                                !text.contains(':')
                        }

                        Log.d("MlKitOcr", "Candidate blocks: ${candidateBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val amountBlock = candidateBlocks.maxByOrNull { block ->
                            val bb = block.boundingBox
                            if (bb != null) bb.height() * bb.width() else 0
                        }
                        val amount = amountBlock?.let { block ->
                            block.text.normDigits().filter { it.isDigit() }
                                .toDoubleOrNull()?.takeIf { it in 1.0..99_999.0 }
                        }

                        // Collect text from all blocks spatially ABOVE the amount block.
                        // For Paybox: this is the sender's contact name (e.g. "Hanita").
                        // For Bit: this is the name line (e.g. "נשלחו לך מ ...").
                        val nameAboveAmount = amountBlock?.boundingBox?.let { amountBb ->
                            result.textBlocks
                                .filter { block -> block.boundingBox?.bottom?.let { it < amountBb.top } == true }
                                .sortedBy { it.boundingBox?.top ?: 0 }
                                .joinToString(" ") { it.text.trim() }
                                .trim().ifBlank { null }
                        }

                        Log.d("MlKitOcr", "Final amount: $amount")
                        Log.d("MlKitOcr", "Name above amount: $nameAboveAmount")
                        if (cont.isActive) cont.resume(MlKitResult(result.text, amount, nameAboveAmount))
                    }
                    .addOnFailureListener { e ->
                        Log.e("MlKitOcr", "Recognition failed: ${e.javaClass.simpleName} — ${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                Log.w("MlKitOcr", "Error", e)
                if (cont.isActive) cont.resume(null)
            }
        }

    // ── Tesseract: Hebrew model — accurate sender names ───────────────────────

    suspend fun runTesseractOcr(context: Context, preprocessedBitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            try {
                val tessDataDir = ensureTessData(context)
                val tess = TessBaseAPI()
                if (!tess.init(tessDataDir, "heb")) {
                    Log.w("OcrUtils", "Tesseract init failed")
                    return@withContext null
                }
                tess.setImage(preprocessedBitmap)
                val result = tess.utF8Text
                tess.recycle()
                result.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w("OcrUtils", "Tesseract OCR failed", e)
                null
            }
        }

    /**
     * Crop retry for Paybox: when the full-image scan misses the amount digit,
     * crop y 15%–55% of the original (where the Paybox amount is always displayed).
     *
     * Strategy:
     *  1. ML Kit on the raw crop — without the confirmation-code / date / PayBox
     *     blocks in the lower portion, the ₪ amount block may now be detectable.
     *  2. If ML Kit fails, Tesseract on the crop at 3× zoom with contrast boost.
     */
    suspend fun retesseractPayboxAmount(context: Context, original: Bitmap): Double? {
        try {
            val cropTop = (original.height * 0.15).toInt()
            val cropH   = (original.height * 0.40).toInt()  // y 15%–55%
            if (cropH <= 0) return null

            val crop = withContext(Dispatchers.IO) {
                Bitmap.createBitmap(original, 0, cropTop, original.width, cropH)
            }

            // Step 1: ML Kit on the crop (no preprocessing — ML Kit prefers original pixels)
            val mlKitCropAmount = runMlKitOcr(crop)?.amount
            if (mlKitCropAmount != null) {
                withContext(Dispatchers.IO) { crop.recycle() }
                Log.d("AmountCrop", "ML Kit crop found: $mlKitCropAmount")
                return mlKitCropAmount
            }
            Log.d("AmountCrop", "ML Kit crop null — trying Tesseract")

            // Step 2: Tesseract on a tight sub-crop at 5× zoom.
            // Narrow crop: y=17%–40% of original — covers only the amount line, not the
            // recipient. Aggressive binarization (c=20) sharpens anti-aliased thin strokes.
            // PSM_SPARSE_TEXT finds text anywhere with no direction assumptions.
            return withContext(Dispatchers.IO) {
                crop.recycle()   // discard the wider crop — build a new tighter one
                val tightTop = (original.height * 0.17).toInt()
                val tightH   = (original.height * 0.23).toInt()   // y 17%–40%
                if (tightH <= 0) return@withContext null
                val tight    = Bitmap.createBitmap(original, 0, tightTop, original.width, tightH)

                val sharpened = sharpenBitmap(tight)
                tight.recycle()
                val scaled = Bitmap.createScaledBitmap(sharpened, original.width * 5, tightH * 5, true)
                sharpened.recycle()

                // Grayscale → hard binarization (c=20): anti-aliased thin strokes → solid black
                val processed = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                Canvas(processed).apply {
                    val paint = Paint()
                    val cm = ColorMatrix().apply {
                        setSaturation(0f)
                        val c = 20f; val t = 128f * (1f - c)
                        postConcat(ColorMatrix(floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    drawBitmap(scaled, 0f, 0f, paint)
                }
                scaled.recycle()

                val tessDataDir = ensureTessData(context)
                val tess = TessBaseAPI()
                if (!tess.init(tessDataDir, "heb")) {
                    processed.recycle()
                    return@withContext null
                }
                // PSM_SPARSE_TEXT (11): no direction/layout assumptions — finds any text fragment
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                tess.setImage(processed)
                val text = tess.utF8Text
                tess.recycle()
                processed.recycle()
                Log.d("AmountCrop", "Tesseract tight-crop raw: '$text'")

                // ₪N wins; fall back to first standalone number
                val shekelMatch = Regex("""₪\s*([\d,]+\.?\d*)""").find(text)
                val numMatch    = Regex("""([\d,]+\.?\d*)""").find(text)
                (shekelMatch?.groupValues?.get(1) ?: numMatch?.groupValues?.get(1))
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
                    ?.takeIf { it in 1.0..99_999.0 }
            }  // closes return withContext(Dispatchers.IO)
        } catch (e: Exception) {
            Log.w("AmountCrop", "Crop OCR exception", e)
            return null
        }
    }

    fun preprocessForOcr(src: Bitmap): Bitmap {
        // Scale up 2x — improves Tesseract accuracy on small text
        val scaled = Bitmap.createScaledBitmap(src, src.width * 2, src.height * 2, true)
        src.recycle()
        // Grayscale + invert: Bit uses dark background with white text.
        // Tesseract works best with dark text on light background.
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()
        return out
    }

    // Paybox: white background, dark text.
    // Pipeline: sharpen at 1x (fewer pixels) → scale 2x → grayscale + contrast boost.
    fun preprocessForPayboxOcr(src: Bitmap): Bitmap {
        val sharpened = sharpenBitmap(src)
        val scaled = Bitmap.createScaledBitmap(sharpened, sharpened.width * 2, sharpened.height * 2, true)
        sharpened.recycle()
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix().apply {
            setSaturation(0f) // grayscale
            val c = 1.6f; val t = 128f * (1f - c)
            postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()
        return out
    }

    // 3×3 unsharp-mask kernel (0 -1 0 / -1 5 -1 / 0 -1 0).
    // Sharpens at 1x before upscaling to keep computation manageable.
    fun sharpenBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                fun ch(shift: Int) = (
                    5 * ((pixels[i]     shr shift) and 0xFF) -
                        ((pixels[i - w] shr shift) and 0xFF) -
                        ((pixels[i + w] shr shift) and 0xFF) -
                        ((pixels[i - 1] shr shift) and 0xFF) -
                        ((pixels[i + 1] shr shift) and 0xFF)
                ).coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        src.recycle()
        return result
    }

    fun ensureTessData(context: Context): String {
        val tessDir = File(context.filesDir, "tessdata")
        tessDir.mkdirs()
        val destFile = File(tessDir, "heb.traineddata")
        val assetSize = try { context.assets.openFd("tessdata/heb.traineddata").length } catch (_: Exception) { -1L }
        if (!destFile.exists() || (assetSize > 0 && destFile.length() != assetSize)) {
            context.assets.open("tessdata/heb.traineddata").use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
        return context.filesDir.absolutePath
    }
}
