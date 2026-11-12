#!/usr/bin/env python3
"""Compares the screenshots of one scripts/vr-ab.sh run (frame-logs/ab/<timestamp>/).

Crops the same region around the left eye's centre from every screenshot, prints one row of picture measures per
variant and writes contact-sheet.png: the crops' middles magnified 3x (nearest neighbour) with their names.

    scripts/vr-ab-analyze.py frame-logs/ab/20261112-180000 [--size 512] [--center X,Y] [--sheet-size 256]

Measures (on gamma-encoded 0..255 values, inside the crop):
  grid    mean |difference| between horizontally and vertically adjacent pixels' luma, summed: the pixel grid and
          aliasing show up here first
  gridR/B the same for the red and blue channels alone: colour detail a PenTile panel turns into sparkle
  lapvar  variance of the 4-neighbour Laplacian of luma: sharpness
  over%   edge overshoot: at strong luma edges between two flat stretches, how far the pixels next to the edge go
          past the flat levels, in percent of the step (mean over the edges; "edges" is how many were found)
  hf%     share of luma's spectral energy (Hann window, mean removed) above 0.375 cycles/pixel: the top quarter
          of the frequencies a pixel grid can hold
  diff    mean |luma difference| to the first variant: 0 means the variant changed nothing (or the picture
          moved: use freeze_pose=1 and a still picture)
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

def luma_of(rgb):
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def adjacent_difference(a):
    return float(np.abs(np.diff(a, axis=1)).mean() + np.abs(np.diff(a, axis=0)).mean())


def laplacian_variance(a):
    lap = a[:-2, 1:-1] + a[2:, 1:-1] + a[1:-1, :-2] + a[1:-1, 2:] - 4 * a[1:-1, 1:-1]
    return float(lap.var())


def high_frequency_share(a, cutoff=0.375):
    h, w = a.shape
    window = np.outer(np.hanning(h), np.hanning(w))
    power = np.abs(np.fft.fft2((a - a.mean()) * window)) ** 2
    radius = np.hypot(np.fft.fftfreq(h)[:, None], np.fft.fftfreq(w)[None, :])
    total = power.sum()
    return float(100 * power[radius >= cutoff].sum() / total) if total > 0 else 0.0


def overshoot_rows(a, threshold=40.0):
    """Overshoot at edges along each row: (sum of overshoot / step, number of edges)."""
    if a.shape[1] < 19:
        return 0.0, 0
    # Windows of pixels i-9..i+9 around every possible edge position i
    win = np.lib.stride_tricks.sliding_window_view(a, 19, axis=1)
    left, right = win[..., 0:6], win[..., 13:19]
    near = win[..., 6:13]
    low_side, high_side = np.median(left, axis=-1), np.median(right, axis=-1)
    step = np.abs(high_side - low_side)
    # Both sides flat (apart from ringing, which the median ignores) and one edge in between
    flat = (np.ptp(left, axis=-1) < 0.35 * step) & (np.ptp(right, axis=-1) < 0.35 * step)
    gradient = np.abs(win[..., 10] - win[..., 8])
    is_edge = (step > threshold) & flat & (gradient > 0.5 * step)
    # One count per edge: where the gradient peaks along the row
    peak = np.ones_like(is_edge)
    peak[:, 1:] &= gradient[:, 1:] >= gradient[:, :-1]
    peak[:, :-1] &= gradient[:, :-1] > gradient[:, 1:]
    is_edge &= peak
    hi, lo = np.maximum(low_side, high_side), np.minimum(low_side, high_side)
    over = np.clip(near.max(axis=-1) - hi, 0, None) + np.clip(lo - near.min(axis=-1), 0, None)
    return float((over[is_edge] / step[is_edge]).sum()), int(is_edge.sum())


def overshoot(a):
    s1, n1 = overshoot_rows(a)
    s2, n2 = overshoot_rows(np.ascontiguousarray(a.T))
    n = n1 + n2
    return (100 * (s1 + s2) / n if n else float('nan')), n


def measure(rgb):
    luma = luma_of(rgb)
    over, edges = overshoot(luma)
    return {
        'grid': adjacent_difference(luma),
        'gridR': adjacent_difference(rgb[..., 0]),
        'gridB': adjacent_difference(rgb[..., 2]),
        'lapvar': laplacian_variance(luma),
        'over%': over,
        'edges': edges,
        'hf%': high_frequency_share(luma),
    }


def load_font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def contact_sheet(crops, names, path, sheet_size, scale=3, columns=None):
    tile = sheet_size * scale
    label = 28
    columns = columns or min(len(crops), 4)
    rows = -(-len(crops) // columns)
    sheet = Image.new('RGB', (columns * tile + (columns + 1) * 4, rows * (tile + label) + 4), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    font = load_font(20)
    for i, (crop, name) in enumerate(zip(crops, names)):
        h, w = crop.shape[:2]
        y0, x0 = (h - sheet_size) // 2, (w - sheet_size) // 2
        middle = Image.fromarray(crop[y0:y0 + sheet_size, x0:x0 + sheet_size])
        middle = middle.resize((middle.width * scale, middle.height * scale), Image.NEAREST)
        x = 4 + (i % columns) * (tile + 4)
        y = (i // columns) * (tile + label)
        draw.text((x + 2, y + 2), name, fill=(255, 255, 255), font=font)
        sheet.paste(middle, (x, y + label))
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('run', help='folder with the screenshots of one vr-ab.sh run')
    parser.add_argument('--size', type=int, default=512, help='side of the measured square, in pixels (512)')
    parser.add_argument('--center', help='its centre X,Y in the screenshot (default: the middle of the left half)')
    parser.add_argument('--sheet-size', type=int, default=256,
                        help="side of the crop's middle shown on the contact sheet, before the 3x magnification (256)")
    parser.add_argument('--columns', type=int, help='contact sheet columns (default: up to 4)')
    args = parser.parse_args()

    # In the order they were taken, if the run's list is there
    names = []
    props = {}
    listing = os.path.join(args.run, 'variants.txt')
    if os.path.exists(listing):
        for line in open(listing, encoding='utf-8'):
            parts = line.rstrip('\n').split('\t')
            if parts[0] and os.path.exists(os.path.join(args.run, parts[0] + '.png')):
                names.append(parts[0])
                props[parts[0]] = parts[1] if len(parts) > 1 else ''
    for f in sorted(os.listdir(args.run)):
        name = f[:-4]
        if f.endswith('.png') and f != 'contact-sheet.png' and name not in names:
            names.append(name)
    if not names:
        sys.exit('No screenshots in ' + args.run)

    crops = []
    box = None
    for name in names:
        image = Image.open(os.path.join(args.run, name + '.png')).convert('RGB')
        if box is None:
            w, h = image.size
            if h > w:
                print('Note: the screenshots are in portrait (%dx%d); pass --center X,Y for the left eye.' % (w, h))
            if args.center:
                cx, cy = (int(v) for v in args.center.split(','))
            else:
                cx, cy = w // 4, h // 2
            size = min(args.size, w, h)
            x0 = max(0, min(w - size, cx - size // 2))
            y0 = max(0, min(h - size, cy - size // 2))
            box = (x0, y0, x0 + size, y0 + size)
            shot_size = image.size
        elif image.size != shot_size:
            sys.exit('%s.png is %dx%d, the others %dx%d' % ((name,) + image.size + shot_size))
        crops.append(np.asarray(image.crop(box)))

    print('%d screenshots %dx%d, measured region x %d..%d, y %d..%d' %
          (len(names), shot_size[0], shot_size[1], box[0], box[2], box[1], box[3]))
    width = max(len(n) for n in names)
    columns = ['grid', 'gridR', 'gridB', 'lapvar', 'over%', 'edges', 'hf%', 'diff']
    print('%-*s ' % (width, 'variant') + ' '.join('%8s' % c for c in columns) + '  properties')
    first_luma = luma_of(crops[0].astype(np.float64))
    for name, crop in zip(names, crops):
        rgb = crop.astype(np.float64)
        m = measure(rgb)
        m['diff'] = float(np.abs(luma_of(rgb) - first_luma).mean())
        cells = []
        for c in columns:
            v = m[c]
            cells.append('%8d' % v if c == 'edges' else '%8s' % '-' if v != v else '%8.1f' % v if c == 'lapvar' else '%8.2f' % v)
        print('%-*s ' % (width, name) + ' '.join(cells) + '  ' + props.get(name, ''))

    sheet = os.path.join(args.run, 'contact-sheet.png')
    contact_sheet(crops, names, sheet, min(args.sheet_size, crops[0].shape[0], crops[0].shape[1]), columns=args.columns)
    print('Contact sheet: ' + sheet)


if __name__ == '__main__':
    main()
