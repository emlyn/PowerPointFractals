import sys
import numpy as np
from PIL import Image


def main(fname, target=None):
    image = Image.open(fname)
    arr = np.array(image)

    top = left = 0
    bot = right = -1
    while arr[top,:,:-1].max() == 0:
        top += 1
    while arr[:,left,:-1].max() == 0:
        left += 1
    while arr[bot,:,:-1].max() == 0:
        bot -= 1
    while arr[:,right,:-1].max() == 0:
        right -= 1

    crop = image.crop((left, top, image.width + right + 1, image.height + bot + 1))
    crop.save(target or fname)


if __name__ == "__main__":
    main(*sys.argv[1:])
