const { Jimp } = require('jimp');
const path = require('path');

const IMG = path.join(__dirname, 'app/src/main/res/drawable/tutorial_icount_create_token.png');

function setPixel(data, iw, x, y, r, g, b, a = 255) {
  if (x < 0 || x >= iw || y < 0) return;
  const idx = (y * iw + x) * 4;
  data[idx] = r; data[idx + 1] = g; data[idx + 2] = b; data[idx + 3] = a;
}

async function main() {
  const img = await Jimp.read(IMG);
  const { width, height } = img.bitmap;
  const data = img.bitmap.data;

  console.log(`Image: ${width}x${height}`);

  // ── Step 1: erase the freehand blue curved arrow ──────────────────────────
  // The arrow occupies roughly x=210–500, y=315–415
  // Paint all non-white pixels in that zone to white (catches main arrow + ghost outlines)
  for (let y = 315; y < 415; y++) {
    for (let x = 210; x < 510; x++) {
      const idx = (y * width + x) * 4;
      const r = data[idx], g = data[idx + 1], b = data[idx + 2];
      // Keep only near-white pixels (background); paint everything else white
      if (r < 240 || g < 240 || b < 240) {
        data[idx] = 255; data[idx + 1] = 255; data[idx + 2] = 255; data[idx + 3] = 255;
      }
    }
  }

  // ── Step 2: draw a clean straight arrow pointing left to the button ───────
  // Button "יצירת טוקן API" is at approx x=44–220, y=334–388 → right edge ~220
  // Arrow: from x=480 → x=240, at y=361, with arrowhead pointing left at x=240
  const AR = 52, AG = 130, AB = 200;   // arrow colour (medium blue matching other slides)
  const lineThick = 7;
  const startX = 480, endX = 240, arrowY = 361;
  const headLen = 20;   // arrowhead length in pixels

  // Horizontal shaft
  for (let lx = endX + headLen; lx <= startX; lx++) {
    for (let t = -Math.floor(lineThick / 2); t <= Math.floor(lineThick / 2); t++) {
      setPixel(data, width, lx, arrowY + t, AR, AG, AB);
    }
  }

  // Arrowhead — filled triangle pointing left
  for (let i = 0; i <= headLen; i++) {
    for (let t = -i; t <= i; t++) {
      setPixel(data, width, endX + i, arrowY + t, AR, AG, AB);
    }
  }

  await img.write(IMG);
  console.log('Done: arrow replaced.');
}

main().catch(console.error);
