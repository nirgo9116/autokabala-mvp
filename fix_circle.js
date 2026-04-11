const { Jimp } = require('jimp');
const path = require('path');
const fs = require('fs');

const IMG = path.join(__dirname, 'app/src/main/res/drawable/tutorial_shituf.png');

async function main() {
  const image = await Jimp.read(IMG);
  const { width, height } = image.bitmap;
  console.log(`Image size: ${width}x${height}`);

  // ---- Step 1: find the AutoKabalaListener icon by its green robot background ----
  // The icon has a distinctive mid-green background (~#3ddc84 or similar)
  // Search in the bottom third of the image for a cluster of green pixels
  let greenPixels = [];
  image.scan(0, Math.floor(height * 0.65), width, Math.floor(height * 0.35), function (x, y, idx) {
    const r = this.bitmap.data[idx];
    const g = this.bitmap.data[idx + 1];
    const b = this.bitmap.data[idx + 2];
    // Green: G dominant, R moderate, B low-moderate
    if (g > 130 && g > r * 1.3 && g > b * 1.1 && r < 180 && b < 160) {
      greenPixels.push({ x, y });
    }
  });

  console.log(`Green pixels found: ${greenPixels.length}`);

  // Cluster the green pixels to find icon center
  if (greenPixels.length > 0) {
    const sumX = greenPixels.reduce((s, p) => s + p.x, 0);
    const sumY = greenPixels.reduce((s, p) => s + p.y, 0);
    const gcx = Math.round(sumX / greenPixels.length);
    const gcy = Math.round(sumY / greenPixels.length);
    console.log(`Green cluster center: (${gcx}, ${gcy})`);

    // Also find bounds
    const gminX = Math.min(...greenPixels.map(p => p.x));
    const gmaxX = Math.max(...greenPixels.map(p => p.x));
    const gminY = Math.min(...greenPixels.map(p => p.y));
    const gmaxY = Math.max(...greenPixels.map(p => p.y));
    console.log(`Green bounds: (${gminX},${gminY})→(${gmaxX},${gmaxY}), size: ${gmaxX-gminX}x${gmaxY-gminY}`);
  }

  // ---- Step 2: also find red pixel bounds ----
  let minX = width, maxX = 0, minY = height, maxY = 0;
  image.scan(0, 0, width, height, function (x, y, idx) {
    const r = this.bitmap.data[idx];
    const g = this.bitmap.data[idx + 1];
    const b = this.bitmap.data[idx + 2];
    if (r > 170 && g < 80 && b < 80) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    }
  });
  console.log(`Red pixel bounds: (${minX},${minY})→(${maxX},${maxY})`);
}

main().catch(console.error);
