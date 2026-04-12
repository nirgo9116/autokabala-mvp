/**
 * fix_kabala_circles.js
 *
 * Replaces two freehand red circles on tutorial_kabala.png with clean ones:
 *   1. Top-right: around the "ניר" name badge in the iCount green header
 *   2. Bottom-left: around the "₪40.00" total amount
 *
 * Erase strategy: for each red pixel, replace with the average of its
 * nearest non-red neighbors — cleanly restores the content underneath.
 */

const { Jimp, rgbaToInt } = require('jimp');
const path = require('path');

const INPUT  = path.join(__dirname, 'app/src/main/res/drawable/tutorial_kabala.png');
const OUTPUT = INPUT;

const STROKE_WIDTH = 10;
const RED_COLOR = rgbaToInt(255, 59, 48, 255);

// Manually specified targets (derived from pixel analysis)
const CIRCLES = [
    { cx: 650, cy: 612, r: 64  },  // "ניר" name badge (top-right)
    { cx: 136, cy: 1216, r: 85 },  // "₪40.00" amount (bottom-left)
];

function isRed(r, g, b) {
    return r > 150 && g < 110 && b < 110 && r > g + 60 && r > b + 60;
}

function neighborAvg(img, x, y, width, height) {
    const offsets = [[-3,0],[3,0],[0,-3],[0,3],[-5,0],[5,0],[0,-5],[0,5]];
    let sr=0, sg=0, sb=0, n=0;
    for (const [dx, dy] of offsets) {
        const nx = x+dx, ny = y+dy;
        if (nx<0||nx>=width||ny<0||ny>=height) continue;
        const idx = (ny*width+nx)*4;
        const r=img.bitmap.data[idx], g=img.bitmap.data[idx+1], b=img.bitmap.data[idx+2];
        if (!isRed(r,g,b)) { sr+=r; sg+=g; sb+=b; n++; }
    }
    if (n===0) return null;
    return { r: Math.round(sr/n), g: Math.round(sg/n), b: Math.round(sb/n), a: 255 };
}

function drawCircle(img, cx, cy, radius, width, height) {
    const steps = Math.max(2000, radius * 12);
    for (let i = 0; i < steps; i++) {
        const angle = (2 * Math.PI * i) / steps;
        for (let t = -Math.floor(STROKE_WIDTH/2); t <= Math.floor(STROKE_WIDTH/2); t++) {
            const px = Math.round(cx + (radius + t) * Math.cos(angle));
            const py = Math.round(cy + (radius + t) * Math.sin(angle));
            if (px >= 0 && px < width && py >= 0 && py < height) {
                img.setPixelColor(RED_COLOR, px, py);
            }
        }
    }
}

(async () => {
    const img = await Jimp.read(INPUT);
    const { width, height } = img.bitmap;
    console.log(`Image: ${width} × ${height}`);

    // Step 1: erase red pixels using neighbor-average color
    let erased = 0;
    img.scan(0, 0, width, height, function (x, y, idx) {
        const r = this.bitmap.data[idx];
        const g = this.bitmap.data[idx + 1];
        const b = this.bitmap.data[idx + 2];
        if (isRed(r, g, b)) {
            const avg = neighborAvg(img, x, y, width, height);
            if (avg) {
                this.bitmap.data[idx]     = avg.r;
                this.bitmap.data[idx + 1] = avg.g;
                this.bitmap.data[idx + 2] = avg.b;
                this.bitmap.data[idx + 3] = avg.a;
                erased++;
            }
        }
    });
    console.log(`Erased ${erased} red pixels`);

    // Step 2: draw clean circles
    for (const { cx, cy, r } of CIRCLES) {
        drawCircle(img, cx, cy, r, width, height);
        console.log(`Drew circle at (${cx}, ${cy}) r=${r}`);
    }

    await img.write(OUTPUT);
    console.log(`Saved → ${OUTPUT}`);
})();
