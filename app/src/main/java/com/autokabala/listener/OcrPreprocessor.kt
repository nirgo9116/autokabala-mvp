package com.autokabala.listener

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV-based image preprocessing for OCR on Bit/Paybox payment screenshots.
 *
 * Public entry points:
 *   preprocessForAmount(bitmap) — crop amount region → upscale → preprocess
 *   preprocessForName(bitmap)   — crop name region   → upscale → preprocess
 *
 * Coordinate conventions (all percentages of image dimensions, 0.0–1.0):
 *   Amount region: x 20–80%, y 30–65%   (central band where ₪ amount appears)
 *   Name region:   x  5–95%, y 10–30%   (upper band where sender name appears)
 *
 * Dark mode: detected by sampling mean brightness; if mean < 128 the image is
 * inverted before thresholding so Tesseract sees dark text on a light background.
 */
object OcrPreprocessor {

    private const val TAG = "OcrPreprocessor"

    // ── Full pipelines ────────────────────────────────────────────────────────

    /**
     * Full pipeline optimised for the payment amount (₪ line).
     *
     * Smart crop (contour-aware) → upscale ×2 → grayscale → invert if dark → blur → adaptive threshold
     */
    fun preprocessForAmount(bitmap: Bitmap): Bitmap {
        val cropped = cropAmountRegionSmart(bitmap)
        val scaled  = upscale(cropped, scale = 2.0f)
        val dark    = isDarkMode(scaled)
        return preprocess(scaled, isDark = dark).also {
            Log.d(TAG, "preprocessForAmount: ${bitmap.width}×${bitmap.height} → ${it.width}×${it.height} (dark=$dark)")
        }
    }

    /**
     * Full pipeline optimised for the sender name.
     *
     * Crop → upscale ×2.5 → grayscale → invert if dark → blur → adaptive threshold
     */
    fun preprocessForName(bitmap: Bitmap): Bitmap {
        val cropped = cropNameRegion(bitmap)
        val scaled  = upscale(cropped, scale = 2.5f)
        val dark    = isDarkMode(scaled)
        return preprocess(scaled, isDark = dark).also {
            Log.d(TAG, "preprocessForName: ${bitmap.width}×${bitmap.height} → ${it.width}×${it.height} (dark=$dark)")
        }
    }

    // ── Region cropping ───────────────────────────────────────────────────────

    /**
     * Crops a region defined by percentage coordinates.
     *
     * @param xStart  left edge, 0.0–1.0
     * @param yStart  top edge,  0.0–1.0
     * @param xEnd    right edge, 0.0–1.0
     * @param yEnd    bottom edge, 0.0–1.0
     */
    fun cropRegion(
        bitmap: Bitmap,
        xStart: Float,
        yStart: Float,
        xEnd: Float,
        yEnd: Float
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val left   = (w * xStart).toInt().coerceIn(0, w - 1)
        val top    = (h * yStart).toInt().coerceIn(0, h - 1)
        val right  = (w * xEnd).toInt().coerceIn(left + 1, w)
        val bottom = (h * yEnd).toInt().coerceIn(top  + 1, h)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Crops the central band where the ₪ amount is shown (x 20–80%, y 30–65%). */
    fun cropAmountRegion(bitmap: Bitmap): Bitmap =
        cropRegion(bitmap, xStart = 0.20f, yStart = 0.30f, xEnd = 0.80f, yEnd = 0.65f)

    /**
     * Smart amount crop — starts with the fixed region (y 30–65%), runs contour
     * detection inside it, and returns a tight crop around the amount text.
     *
     * Handles both large (₪160) and small (₪1) amounts by scoring contours on
     * three independent signals rather than relying on "largest contour":
     *
     *   centerScore  (35%) — contour vertically centred inside the band
     *   heightScore  (40%) — contour height relative to band height (digits > noise)
     *   ratioScore   (25%) — aspect ratio in digit-like range (0.3–5)
     *
     * Area is intentionally NOT the primary signal: ₪1 has a tiny area but still
     * scores well if it's centred and tall relative to the band.
     *
     * If the ₪ symbol is detected (narrow contour in left ≤35%), only contours
     * within ±2× its height are kept — this anchors the selection to the amount
     * row regardless of what else appears in the band.
     *
     * Fallback: fixed crop is returned when detection yields nothing useful.
     */
    fun cropAmountRegionSmart(bitmap: Bitmap): Bitmap {
        val fixed = cropAmountRegion(bitmap)
        val w = fixed.width
        val h = fixed.height

        val src = fixed.toMat()
        val gray = src.toGray()
        src.release()

        // Normalise orientation for Tesseract: dark text on light background
        val mean = Core.mean(gray).`val`[0]
        if (mean < 128.0) Core.bitwise_not(gray, gray)   // dark mode → invert in-place

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        gray.release()

        // THRESH_BINARY_INV: text pixels → 255 (white foreground for findContours)
        // Using THRESH_BINARY here is a common mistake — it makes text=0 (black),
        // so findContours would detect background blobs instead of glyphs.
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            15, 10.0
        )
        blurred.release()

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()
        hierarchy.release()

        if (contours.isEmpty()) {
            Log.d(TAG, "cropAmountRegionSmart: no contours — fixed crop")
            return fixed
        }

        // ── Score every contour ───────────────────────────────────────────────
        val centerY   = h / 2.0
        val minHeight = h * 0.12   // reject anything shorter than 12% of band (noise)

        data class Scored(val rect: Rect, val score: Double)

        val candidates = contours.mapNotNull { c ->
            val r = Imgproc.boundingRect(c)
            if (r.height < minHeight) return@mapNotNull null

            val aspect = r.width.toDouble() / r.height
            if (aspect > 10.0 || aspect < 0.15) return@mapNotNull null   // hairlines / wide blobs

            val distFromCenter = Math.abs((r.y + r.height / 2.0) - centerY) / h
            val centerScore = 1.0 - distFromCenter

            val heightScore = (r.height.toDouble() / h).coerceAtMost(1.0)

            // Digit-like aspect ratios score highest; wide multi-char blobs score lower
            val ratioScore = when {
                aspect in 0.3..3.0 -> 1.0
                aspect in 3.0..5.0 -> 0.5
                else               -> 0.2
            }

            Scored(r, centerScore * 0.35 + heightScore * 0.40 + ratioScore * 0.25)
        }.sortedByDescending { it.score }

        if (candidates.isEmpty()) {
            Log.d(TAG, "cropAmountRegionSmart: no valid candidates — fixed crop")
            return fixed
        }

        // ── ₪ detection ───────────────────────────────────────────────────────
        // Shekel sign: narrow (aspect < 1.5), sits in the left ≤35% of the crop
        val shekel = candidates.firstOrNull { s ->
            s.rect.x < w * 0.35 && s.rect.width.toDouble() / s.rect.height < 1.5
        }

        val selected: List<Scored> = if (shekel != null) {
            val shekelMidY = shekel.rect.y + shekel.rect.height / 2.0
            val bandHalf   = shekel.rect.height * 2.0
            candidates
                .filter { Math.abs((it.rect.y + it.rect.height / 2.0) - shekelMidY) < bandHalf }
                .also { Log.d(TAG, "cropAmountRegionSmart: ₪ at x=${shekel.rect.x}, ${it.size} contours in row") }
        } else {
            candidates.take(5)
                .also { Log.d(TAG, "cropAmountRegionSmart: no ₪ found, top-${candidates.size.coerceAtMost(5)} contours") }
        }

        if (selected.isEmpty()) return fixed

        // ── Merge selected rects + padding → final crop ───────────────────────
        val minX = selected.minOf { it.rect.x }
        val minY = selected.minOf { it.rect.y }
        val maxX = selected.maxOf { it.rect.x + it.rect.width }
        val maxY = selected.maxOf { it.rect.y + it.rect.height }

        val padX = (w * 0.04).toInt()
        val padY = (h * 0.06).toInt()
        val left   = (minX - padX).coerceAtLeast(0)
        val top    = (minY - padY).coerceAtLeast(0)
        val right  = (maxX + padX).coerceAtMost(w)
        val bottom = (maxY + padY).coerceAtMost(h)

        Log.d(TAG, "cropAmountRegionSmart: ($left,$top)→($right,$bottom) of ${w}×$h")
        return Bitmap.createBitmap(fixed, left, top, right - left, bottom - top)
    }

    /** Crops the upper band where the sender name is shown (x 5–95%, y 10–30%). */
    fun cropNameRegion(bitmap: Bitmap): Bitmap =
        cropRegion(bitmap, xStart = 0.05f, yStart = 0.10f, xEnd = 0.95f, yEnd = 0.30f)

    // ── Upscaling ─────────────────────────────────────────────────────────────

    /**
     * Scales the bitmap uniformly by [scale] using bicubic interpolation.
     * Bicubic preserves sharp edges better than bilinear for text glyphs.
     */
    fun upscale(bitmap: Bitmap, scale: Float): Bitmap {
        val mat = bitmap.toMat()
        val out = Mat()
        Imgproc.resize(
            mat, out,
            Size(mat.cols() * scale.toDouble(), mat.rows() * scale.toDouble()),
            0.0, 0.0,
            Imgproc.INTER_CUBIC
        )
        mat.release()
        return out.toBitmap()
    }

    // ── Dark mode detection ───────────────────────────────────────────────────

    /**
     * Returns true when the image has a dark background (white text on dark bg).
     *
     * Samples every 10th pixel to compute a fast approximate mean brightness,
     * then compares to a threshold of 128 (mid-grey).
     *
     * Bit and Paybox both support dark mode; Tesseract requires dark text on
     * a light background, so dark-mode images must be inverted before OCR.
     */
    fun isDarkMode(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        var sum = 0L
        var count = 0
        val step = 10
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr  8) and 0xFF
                val b =  pixel         and 0xFF
                // Perceptual luminance (ITU-R BT.601)
                sum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                count++
            }
        }
        val mean = if (count > 0) sum.toDouble() / count else 128.0
        return mean < 128.0
    }

    // ── Core preprocessing (OpenCV) ───────────────────────────────────────────

    /**
     * Converts to grayscale, optionally inverts (dark mode), denoises, and
     * binarises using adaptive Gaussian threshold.
     *
     * Adaptive threshold is chosen over Otsu because payment screenshots have
     * gradients and shadows (especially from phone cameras) that cause a single
     * global threshold to clip either the bright or dark regions.  The adaptive
     * variant computes a local threshold per pixel neighbourhood, handling both
     * evenly lit and uneven screenshots correctly.
     *
     * Steps:
     *  1. Grayscale
     *  2. Invert (if dark mode)
     *  3. Gaussian blur 3×3 — removes sensor noise before thresholding
     *  4. Adaptive Gaussian threshold (blockSize 15, C 10)
     *  5. Unsharp-mask sharpening — sharpens edges blurred by step 3
     */
    fun preprocess(bitmap: Bitmap, isDark: Boolean): Bitmap {
        val mat = bitmap.toMat()
        try {
            val gray = mat.toGray()

            val normalised = if (isDark) {
                val inv = Mat()
                Core.bitwise_not(gray, inv)
                gray.release()
                inv
            } else {
                gray
            }

            val blurred = Mat()
            Imgproc.GaussianBlur(normalised, blurred, Size(3.0, 3.0), 0.0)
            normalised.release()

            val thresholded = Mat()
            Imgproc.adaptiveThreshold(
                blurred, thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                /* blockSize = */ 15,
                /* C = */ 10.0
            )
            blurred.release()

            val sharpened = thresholded.sharpen()
            thresholded.release()

            return sharpened.toBitmap()
        } finally {
            mat.release()
        }
    }

    // ── Mat helpers ───────────────────────────────────────────────────────────

    private fun Bitmap.toMat(): Mat {
        val mat = Mat()
        Utils.bitmapToMat(this, mat)
        return mat
    }

    private fun Mat.toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bmp)
        return bmp
    }

    private fun Mat.toGray(): Mat {
        val gray = Mat()
        return when (channels()) {
            1    -> this.clone()
            4    -> { Imgproc.cvtColor(this, gray, Imgproc.COLOR_BGRA2GRAY); gray }
            else -> { Imgproc.cvtColor(this, gray, Imgproc.COLOR_BGR2GRAY);  gray }
        }
    }

    /**
     * Unsharp-mask sharpening.
     * Subtracts a blurred copy from the original (weight 1.5 / -0.5).
     * Enhances character edges after the Gaussian blur in [preprocess].
     */
    private fun Mat.sharpen(): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(this, blurred, Size(0.0, 0.0), 3.0)
        val out = Mat()
        Core.addWeighted(this, 1.5, blurred, -0.5, 0.0, out)
        blurred.release()
        return out
    }
}
