package com.autokabala.listener

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object OcrUtils {

    data class MlKitResult(val text: String, val amount: Double?, val nameAboveAmount: String?)

    sealed class GeminiResult {
        object Rejected : GeminiResult()
        data class Success(val data: PaymentData) : GeminiResult()
    }

    /**
     * Detect payment app source by sampling background color — no OCR needed.
     * Bit uses a dark background (purple/dark-blue), Paybox uses a light/white background.
     * Samples a grid of pixels in the center-top area (y 5%–20%) to get the dominant brightness.
     */
    fun detectSourceByColor(bitmap: Bitmap): String {
        val sampleCount = 12
        var totalBrightness = 0L
        val yRange = (bitmap.height * 0.05).toInt()..(bitmap.height * 0.20).toInt()
        val xStep = bitmap.width / (sampleCount + 1)
        val yStep = (yRange.last - yRange.first) / 3
        var samples = 0
        for (xi in 1..sampleCount) {
            for (yi in 0..2) {
                val x = xi * xStep
                val y = yRange.first + yi * yStep
                if (x < bitmap.width && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    totalBrightness += (android.graphics.Color.red(pixel) +
                        android.graphics.Color.green(pixel) +
                        android.graphics.Color.blue(pixel)) / 3
                    samples++
                }
            }
        }
        val avgBrightness = if (samples > 0) totalBrightness / samples else 128
        val source = if (avgBrightness < 100) "bit" else "paybox"
        Log.d("OcrUtils", "detectSourceByColor: avgBrightness=$avgBrightness → $source")
        return source
    }

    /**
     * Crop the bitmap to the relevant payment area before sending to OpenAI.
     * Uses background-color detection (fast, no OCR) to distinguish Bit from Paybox,
     * then applies per-source crop window to exclude irrelevant UI chrome.
     *
     * Bit (dark bg):    y 15%–80% — sender line + amount are in the middle section
     * Paybox (light bg): y 10%–75% — content starts slightly higher
     */
    fun cropForOpenAi(bitmap: Bitmap): Bitmap {
        val source = detectSourceByColor(bitmap)
        // Paybox: no crop — full image gives the model enough context to distinguish
        // sender ("העברה מ X") from recipient ("הועבר אל Y"). Cropping was proven to
        // cause hallucinations by showing "יריב" without the "הועבר אל" label context.
        if (source != "bit") {
            Log.d("OcrUtils", "cropForOpenAi: source=paybox — no crop (full image ${bitmap.width}×${bitmap.height})")
            return bitmap
        }
        // Bit: skip top empty dark area (0-25%) and capture through date/time (88%)
        val topFrac    = 0.25f
        val bottomFrac = 0.92f   // captures date row (~74-83%) and time row (~83-90%)
        val cropTop = (bitmap.height * topFrac).toInt().coerceAtLeast(0)
        val cropBottom = (bitmap.height * bottomFrac).toInt().coerceAtMost(bitmap.height)
        val cropH = (cropBottom - cropTop).coerceAtLeast(1)
        Log.d("OcrUtils", "cropForOpenAi: source=bit crop y=$cropTop...$cropBottom (${cropH}px / ${bitmap.height}px total)")
        return Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, cropH)
    }

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

    // ── Tesseract availability (lazy — checked once, cached) ──────────────────

    private val tesseractAvailable: Boolean by lazy {
        try { TessBaseAPI(); true }
        catch (e: Throwable) {
            Log.w("OcrUtils", "Tesseract unavailable (likely 16KB device): ${e.message}")
            false
        }
    }

    fun isTesseractAvailable(): Boolean = tesseractAvailable

    // Parses date/time strings from Gemini into epoch ms.
    // Supports: "DD.MM.YY HH:MM" (Bit) and "DD/MM/YYYY HH:MM" (Paybox)
    private fun parseDateTimeToMs(s: String): Long? {
        // Full date + time: "DD.MM.YY HH:MM" or "DD/MM/YYYY HH:MM"
        Regex("""(\d{1,2})[./](\d{1,2})[./](\d{2,4})\s+(\d{1,2}):(\d{2})""").find(s)?.let {
            val (d, m, y, h, min) = it.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            return java.util.Calendar.getInstance().apply {
                set(year, m.toInt() - 1, d.toInt(), h.toInt(), min.toInt(), 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        // Date only: "DD.MM.YY" or "DD/MM/YYYY" — default to noon so the date is correct
        Regex("""(\d{1,2})[./](\d{1,2})[./](\d{2,4})""").find(s)?.let {
            val (d, m, y) = it.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            Log.d("OcrUtils", "parseDateTimeToMs: date-only '$s' → $d/$m/$year 12:00")
            return java.util.Calendar.getInstance().apply {
                set(year, m.toInt() - 1, d.toInt(), 12, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return null
    }

    // ── Gemini Vision fallback ────────────────────────────────────────────────

    private val geminiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun runGeminiOcr(bitmap: Bitmap): GeminiResult? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                Log.w("OcrUtils", "Gemini API key not configured")
                return@withContext null
            }

            // Scale down to max 720px on the longest side to reduce upload size
            val maxDim = 720
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            else bitmap
            val baos = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            if (small !== bitmap) small.recycle()
            val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val nowMs = System.currentTimeMillis()
            val prompt = """
                You are a precise OCR data extractor for an Israeli payment automation app.
                You are analyzing a screenshot of an Israeli payment confirmation screen (Bit or PayBox).
                The interface is in Hebrew (RTL - right-to-left). Text is Hebrew, numbers are LTR.

                Return ONLY valid JSON in this exact schema:
                {
                  "source": "bit" | "paybox" | "",
                  "senderLine": "",
                  "amount": 0,
                  "dateTime": ""
                }

                Rules:
                1. source: "bit" if you see "נשלחו לך", "paybox" if you see "העברה מ", otherwise "".
                2. senderLine: Copy the FULL line containing "נשלחו לך" or "העברה מ" EXACTLY as it appears.
                   - STRICT CHARACTER MODE: copy each Hebrew character one by one, exactly as it appears in the image.
                   - Do NOT autocomplete names. Do NOT infer or add missing letters. Do NOT shorten names.
                   - Do NOT translate Hebrew. Do NOT fix or correct Hebrew spelling. Do NOT reorder words.
                   - If the name and phrase appear on separate lines, combine them with a space.
                   - If not found → "".
                3. amount: Number next to ₪. Ignore "+" signs. Most prominent (large font) if multiple. 0 if not found.
                4. dateTime: Date and time exactly as shown. Combine with space if separate. "" if not found.
                5. If unsure about any field → return "" or 0. Do NOT guess.
                6. CRITICAL — sender vs recipient: This screen shows money RECEIVED by the phone owner.
                   - The SENDER is the person who sent the money. Their name follows "העברה מ" or "נשלחו לך מ".
                   - The phone owner's name (recipient/account holder) may appear elsewhere in the UI — do NOT use it.
                   - Copy the name that immediately follows the payment phrase, character by character.
                7. VERIFICATION: Before returning, re-read the senderLine in the image character by character and confirm every letter matches what you wrote.

                Return JSON only. No explanations. No markdown. No backticks.
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingBudget", 5000)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = geminiClient.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            Log.d("OcrUtils", "Gemini HTTP ${response.code} body: $rawBody")
            if (!response.isSuccessful) {
                return@withContext null
            }

            val responseText = try {
                val parts = JSONObject(rawBody)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                // With thinkingBudget=0 there is only one part; if thinking is present skip it
                (0 until parts.length())
                    .map { parts.getJSONObject(it) }
                    .firstOrNull { !it.optBoolean("thought", false) }
                    ?.getString("text")
                    ?: return@withContext null
            } catch (e: Exception) {
                Log.e("OcrUtils", "Gemini response parse failed", e)
                return@withContext null
            }.trim().let { raw ->
                // Extract the first {...} block — handles both pure-JSON and prose+markdown responses
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
            }

            Log.d("OcrUtils", "Gemini text: $responseText")
            val result = try { JSONObject(responseText) } catch (e: Exception) {
                Log.e("OcrUtils", "Gemini JSON parse failed: $responseText", e)
                return@withContext null
            }

            val source      = result.optString("source", "")
            val senderLine  = result.optString("senderLine", "").trim()
            val amount      = result.optDouble("amount", 0.0)
            val dateTimeStr = result.optString("dateTime", "")
            Log.d("OcrUtils", "Gemini parsed: source='$source' senderLine='$senderLine' amount=$amount dateTime='$dateTimeStr'")
            val effectiveSource = when {
                source == "paybox" || senderLine.contains("העברה מ") -> "paybox"
                source == "bit"    || senderLine.contains("נשלחו לך") -> "bit"
                else -> ""
            }
            if (effectiveSource != source) Log.d("OcrUtils", "Gemini: source derived from senderLine → '$effectiveSource'")
            val senderName = when (effectiveSource) {
                "bit"    -> Regex("נשלחו לך מ(.+)").find(senderLine)?.groupValues?.get(1)?.trim() ?: ""
                "paybox" -> Regex("העברה מ(.+)").find(senderLine)?.groupValues?.get(1)?.trim() ?: ""
                else     -> ""
            }
            Log.d("OcrUtils", "Gemini senderName='$senderName'")
            if (senderName.isBlank()) {
                Log.d("OcrUtils", "Gemini: name empty — Tesseract fallback")
                return@withContext null
            }
            // Hallucination guard: the name we extracted must literally appear in the line the model returned.
            // If it doesn't, the model fabricated a name that wasn't in the image.
            if (!senderLine.contains(senderName)) {
                Log.w("OcrUtils", "Gemini: senderName '$senderName' not found in senderLine '$senderLine' — hallucination guard, falling back")
                return@withContext null
            }
            if (amount <= 0) {
                Log.d("OcrUtils", "Gemini: amount=$amount — Tesseract fallback")
                return@withContext null
            }

            val timestamp   = parseDateTimeToMs(dateTimeStr) ?: nowMs
            Log.d("OcrUtils", "Gemini timestamp: $timestamp from '$dateTimeStr'")

            GeminiResult.Success(PaymentData(
                source      = effectiveSource,
                senderName  = senderName,
                amount      = amount,
                isConfirmed = true,
                timestamp   = timestamp
            ))
        } catch (e: Exception) {
            Log.e("OcrUtils", "Gemini OCR failed", e)
            null
        }
    }

    // ── OpenAI Vision ─────────────────────────────────────────────────────────

    suspend fun runOpenAiOcr(bitmap: Bitmap): GeminiResult? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                Log.w("OcrUtils", "OpenAI API key not configured")
                return@withContext null
            }

            val maxDim = 720
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            else bitmap
            val baos = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            if (small !== bitmap) small.recycle()
            val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val nowMs = System.currentTimeMillis()
            val prompt = """
                You are a precise OCR data extractor for an Israeli payment automation app.
                You are analyzing a screenshot of an Israeli payment confirmation screen (Bit or PayBox).
                The interface is in Hebrew (RTL - right-to-left). Text is Hebrew, numbers are LTR.

                Return ONLY valid JSON in this exact schema:
                {
                  "source": "bit" | "paybox" | "",
                  "senderLine": "",
                  "amount": 0,
                  "dateTime": ""
                }

                Rules:
                1. source: "bit" if you see "נשלחו לך", "paybox" if you see "העברה מ", otherwise "".
                2. senderLine: Copy the FULL line containing "נשלחו לך" or "העברה מ" EXACTLY as it appears.
                   - STRICT CHARACTER MODE: copy each Hebrew character one by one, exactly as it appears in the image.
                   - Do NOT autocomplete names. Do NOT infer or add missing letters. Do NOT shorten names.
                   - Do NOT translate Hebrew. Do NOT fix or correct Hebrew spelling. Do NOT reorder words.
                   - If the name and phrase appear on separate lines, combine them with a space.
                   - If not found → "".
                3. amount: Number next to ₪. Ignore "+" signs. Most prominent (large font) if multiple. 0 if not found.
                4. dateTime: Date and time exactly as shown. Combine with space if separate. "" if not found.
                5. If unsure about any field → return "" or 0. Do NOT guess.
                6. CRITICAL — sender vs recipient: This screen shows money RECEIVED by the phone owner.
                   - The SENDER is the person who sent the money. Their name follows "העברה מ" or "נשלחו לך מ".
                   - The phone owner's name (recipient/account holder) may appear elsewhere in the UI — do NOT use it.
                   - Copy the name that immediately follows the payment phrase, character by character.
                7. VERIFICATION: Before returning, re-read the senderLine in the image character by character and confirm every letter matches what you wrote.

                Return JSON only. No explanations. No markdown. No backticks.
            """.trimIndent()

            val body = JSONObject().apply {
                put("model", "gpt-4o")
                put("max_tokens", 200)
                put("temperature", 0.2)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = geminiClient.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            Log.d("OcrUtils", "OpenAI HTTP ${response.code} body: $rawBody")
            if (!response.isSuccessful) return@withContext null

            val responseText = try {
                JSONObject(rawBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                Log.e("OcrUtils", "OpenAI response parse failed", e)
                return@withContext null
            }.let { raw ->
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
            }

            Log.d("OcrUtils", "OpenAI text: $responseText")
            val result = try { JSONObject(responseText) } catch (e: Exception) {
                Log.e("OcrUtils", "OpenAI JSON parse failed: $responseText", e)
                return@withContext null
            }

            val source      = result.optString("source", "")
            val senderLine  = result.optString("senderLine", "").trim()
            val amount      = result.optDouble("amount", 0.0)
            val dateTimeStr = result.optString("dateTime", "")
            Log.d("OcrUtils", "OpenAI parsed: source='$source' senderLine='$senderLine' amount=$amount dateTime='$dateTimeStr'")
            val effectiveSource = when {
                source == "paybox" || senderLine.contains("העברה מ") -> "paybox"
                source == "bit"    || senderLine.contains("נשלחו לך") -> "bit"
                else -> ""
            }
            if (effectiveSource != source) Log.d("OcrUtils", "OpenAI: source derived from senderLine → '$effectiveSource'")
            val senderName = when (effectiveSource) {
                "bit"    -> Regex("נשלחו לך מ(.+)").find(senderLine)?.groupValues?.get(1)?.trim() ?: ""
                "paybox" -> Regex("העברה מ(.+)").find(senderLine)?.groupValues?.get(1)?.trim() ?: ""
                else     -> ""
            }
            Log.d("OcrUtils", "OpenAI senderName='$senderName'")
            if (senderName.isBlank()) {
                Log.d("OcrUtils", "OpenAI: name empty — Tesseract fallback")
                return@withContext null
            }
            // Hallucination guard: the name we extracted must literally appear in the line the model returned.
            if (!senderLine.contains(senderName)) {
                Log.w("OcrUtils", "OpenAI: senderName '$senderName' not found in senderLine '$senderLine' — hallucination guard, falling back")
                return@withContext null
            }
            if (amount <= 0) {
                Log.d("OcrUtils", "OpenAI: amount=$amount — Tesseract fallback")
                return@withContext null
            }

            val timestamp = parseDateTimeToMs(dateTimeStr) ?: nowMs
            Log.d("OcrUtils", "OpenAI timestamp: $timestamp from '$dateTimeStr'")

            GeminiResult.Success(PaymentData(
                source      = effectiveSource,
                senderName  = senderName,
                amount      = amount,
                isConfirmed = true,
                timestamp   = timestamp
            ))
        } catch (e: Exception) {
            Log.e("OcrUtils", "OpenAI OCR failed", e)
            null
        }
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
