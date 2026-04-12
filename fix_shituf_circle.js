/**
 * fix_shituf_circle.js
 *
 * Replaces the freehand red circle on tutorial_shituf.png with a clean,
 * precise circle tightly around the AutoKabalaListener icon.
 *
 * Image: 731 × 1598, dark background rgb(37,37,39)
 * AutoKabalaListener icon: 3rd of 4 icons in bottom share-sheet row
 */

const { Jimp, rgbaToInt } = require('jimp');
const path = require('path');

const INPUT  = path.join(__dirname, 'app/src/main/res/drawable/tutorial_shituf.png');
const OUTPUT = INPUT;

// ── Tweakable parameters ──────────────────────────────────────────────────
// Icon center (x, y) and circle radius — adjust if needed after preview
const ICON_CX      = 460;   // 3rd icon of 4 in 731px-wide image
const ICON_CY      = 1375;  // bottom row of share sheet
const RADIUS       = 115;   // tight around icon + label
const STROKE_WIDTH = 10;    // px
// Background color of the dark-mode share sheet
const BG = { r: 37, g: 37, b: 39, a: 255 };
// Circle color
const RED = rgbaToInt(255, 59, 48, 255);
// ─────────────────────────────────────────────────────────────────────────

function isRed(r, g, b) {
    return r > 160 && g < 100 && b < 100;
}

(async () => {
    const img = await Jimp.read(INPUT);
    const { width, height } = img.bitmap;
    console.log(`Image: ${width} × ${height}`);

    // Step 1: erase all red pixels (freehand circle) with background color
    let erased = 0;
    img.scan(0, 0, width, height, function (x, y, idx) {
        const r = this.bitmap.data[idx];
        const g = this.bitmap.data[idx + 1];
        const b = this.bitmap.data[idx + 2];
        if (isRed(r, g, b)) {
            this.bitmap.data[idx]     = BG.r;
            this.bitmap.data[idx + 1] = BG.g;
            this.bitmap.data[idx + 2] = BG.b;
            this.bitmap.data[idx + 3] = BG.a;
            erased++;
        }
    });
    console.log(`Erased ${erased} red pixels`);

    // Step 2: draw clean circle
    const steps = Math.max(2000, RADIUS * 10);
    for (let i = 0; i < steps; i++) {
        const angle = (2 * Math.PI * i) / steps;
        for (let t = -Math.floor(STROKE_WIDTH / 2); t <= Math.floor(STROKE_WIDTH / 2); t++) {
            const px = Math.round(ICON_CX + (RADIUS + t) * Math.cos(angle));
            const py = Math.round(ICON_CY + (RADIUS + t) * Math.sin(angle));
            if (px >= 0 && px < width && py >= 0 && py < height) {
                img.setPixelColor(RED, px, py);
            }
        }
    }
    console.log(`Drew circle at (${ICON_CX}, ${ICON_CY}) r=${RADIUS}`);

    await img.write(OUTPUT);
    console.log(`Saved → ${OUTPUT}`);
})();
