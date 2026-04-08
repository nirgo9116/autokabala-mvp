import cv2
import numpy as np
import sys
from PIL import Image

# Crop percentages — mirrors OcrPreprocessor.kt
AMOUNT_X1, AMOUNT_Y1, AMOUNT_X2, AMOUNT_Y2 = 0.20, 0.30, 0.80, 0.65
NAME_X1,   NAME_Y1,   NAME_X2,   NAME_Y2   = 0.05, 0.18, 0.95, 0.32


def crop_region(image, x1, y1, x2, y2):
    h, w = image.shape[:2]
    return image[int(h * y1):int(h * y2), int(w * x1):int(w * x2)]


def deskew(image):
    coords = np.column_stack(np.where(image > 0))
    angle = cv2.minAreaRect(coords)[-1]

    if angle < -45:
        angle = -(90 + angle)
    else:
        angle = -angle

    (h, w) = image.shape[:2]
    center = (w // 2, h // 2)

    M = cv2.getRotationMatrix2D(center, angle, 1.0)
    rotated = cv2.warpAffine(image, M, (w, h),
                             flags=cv2.INTER_CUBIC,
                             borderMode=cv2.BORDER_REPLICATE)
    return rotated


def preprocess(image, scale=1.5):
    """Resize → grayscale → denoise → CLAHE → adaptive threshold → deskew."""
    resized  = cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    gray     = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, h=30)
    clahe    = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    contrast = clahe.apply(denoised)
    thresh   = cv2.adaptiveThreshold(
        contrast, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        blockSize=15,
        C=10
    )
    return deskew(thresh)


def preprocess_image(input_path, output_path):
    image = cv2.imread(input_path)
    if image is None:
        print(f"Error: could not load '{input_path}'")
        sys.exit(1)

    # Full image
    final_full = preprocess(image, scale=1.5)

    # Name crop (y 18–32%)
    name_region  = crop_region(image, NAME_X1, NAME_Y1, NAME_X2, NAME_Y2)
    final_name   = preprocess(name_region, scale=2.5)

    # Amount crop (y 30–65%)
    amount_region  = crop_region(image, AMOUNT_X1, AMOUNT_Y1, AMOUNT_X2, AMOUNT_Y2)
    final_amount   = preprocess(amount_region, scale=2.0)

    cv2.imwrite("full_"   + output_path, final_full)
    cv2.imwrite("name_"   + output_path, final_name)
    cv2.imwrite("amount_" + output_path, final_amount)

    h, w = image.shape[:2]
    print(f"full   → full_{output_path}   ({final_full.shape[1]}×{final_full.shape[0]})")
    print(f"name   → name_{output_path}   ({final_name.shape[1]}×{final_name.shape[0]})  [y {NAME_Y1}–{NAME_Y2} of {h}px]")
    print(f"amount → amount_{output_path} ({final_amount.shape[1]}×{final_amount.shape[0]})  [y {AMOUNT_Y1}–{AMOUNT_Y2} of {h}px]")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python preprocess_image.py <input_path> <output_path>")
        sys.exit(1)
    preprocess_image(sys.argv[1], sys.argv[2])
