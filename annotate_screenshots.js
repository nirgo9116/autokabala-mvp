const { Jimp } = require('jimp');
const path = require('path');

const DRAWABLE = 'app/src/main/res/drawable';

function drawBorder(img, x, y, w, h, r, g, b, thickness = 10) {
  const data = img.bitmap.data;
  const iw = img.width, ih = img.height;
  x = Math.max(0, x); y = Math.max(0, y);
  w = Math.min(w, iw - x); h = Math.min(h, ih - y);
  for (let t = 0; t < thickness; t++) {
    for (let px = x; px < x + w; px++) {
      setPixel(data, iw, px, y + t, r, g, b);
      setPixel(data, iw, px, y + h - t - 1, r, g, b);
    }
    for (let py = y; py < y + h; py++) {
      setPixel(data, iw, x + t, py, r, g, b);
      setPixel(data, iw, x + w - t - 1, py, r, g, b);
    }
  }
}

function fillRect(img, x, y, w, h, r, g, b, alpha) {
  const data = img.bitmap.data;
  const iw = img.width, ih = img.height;
  const a = alpha / 255;
  x = Math.max(0, x); y = Math.max(0, y);
  w = Math.min(w, iw - x); h = Math.min(h, ih - y);
  for (let py = y; py < y + h; py++) {
    for (let px = x; px < x + w; px++) {
      const idx = (py * iw + px) * 4;
      data[idx]   = Math.round(data[idx]   * (1 - a) + r * a);
      data[idx+1] = Math.round(data[idx+1] * (1 - a) + g * a);
      data[idx+2] = Math.round(data[idx+2] * (1 - a) + b * a);
    }
  }
}

function setPixel(data, iw, x, y, r, g, b) {
  if (x < 0 || y < 0) return;
  const idx = (y * iw + x) * 4;
  data[idx] = r; data[idx+1] = g; data[idx+2] = b; data[idx+3] = 255;
}

function highlight(img, x, y, w, h) {
  fillRect(img, x, y, w, h, 255, 230, 0, 55);
  drawBorder(img, x, y, w, h, 210, 25, 25, 11);
}

async function annotate(filename, highlights, crop) {
  const src = path.join(DRAWABLE, filename + '.png');
  const img = await Jimp.read(src);
  highlights.forEach(([x, y, w, h]) => highlight(img, x, y, w, h));
  if (crop) {
    let [cx, cy, cw, ch] = crop;
    cw = Math.min(cw, img.width - cx);
    ch = Math.min(ch, img.height - cy);
    img.crop({ x: cx, y: cy, w: cw, h: ch });
  }
  await img.write(src);
  console.log('Done:', filename);
}

(async () => {
  // ── 1. Login ─────────────────────────────────────────────────────────────
  // All 3 input fields + login button (confirmed via probe)
  await annotate('tutorial_icount_login', [
    [505, 415, 890, 70],   // email field
    [505, 508, 890, 72],   // company-id (מזהה החברה)
    [505, 592, 890, 68],   // password
    [505, 762, 635, 68],   // login button
  ], [400, 195, 1120, 660]);

  // ── 2. Dashboard ──────────────────────────────────────────────────────────
  // "מערכת ⚙️" row with ˅ expand arrow in right sidebar
  await annotate('tutorial_icount_dashboard', [
    [1588, 948, 328, 55],
  ], [1498, 870, 419, 260]);

  // ── 3. Settings nav – "הגדרות" item in expanded מערכת section ────────────
  await annotate('tutorial_icount_settings_nav', [
    [1590, 928, 320, 50],
  ], [1498, 820, 419, 310]);

  // ── 4. Settings page – "אוטומציה" card (right column, row 3) ─────────────
  // Confirmed via probe: card at absolute y=748–963, x=640–1345
  await annotate('tutorial_icount_settings', [
    [645, 748, 700, 215],
  ], [600, 710, 770, 280]);

  // ── 5. API Tokens – "API Tokens" tab + "יצירת טוקן API" button ──────────
  await annotate('tutorial_icount_api_tokens', [
    [685, 283, 174, 44],   // "API Tokens" tab (selected, underlined)
    [10,  393, 195, 44],   // "יצירת טוקן API" button (shifted +40px vs v1)
  ], [0, 265, 960, 185]);

  // ── 6. Create token dialog – "יצירת טוקן" purple submit button ───────────
  // Confirmed via probe: button at absolute y=555–610, x=550–710
  await annotate('tutorial_icount_create_token', [
    [548, 555, 165, 58],
  ], [375, 330, 680, 350]);

  console.log('All done!');
})().catch(console.error);
