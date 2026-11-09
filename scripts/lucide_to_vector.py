#!/usr/bin/env python3
"""Converts Lucide stroke SVGs into Android vector drawables."""
import sys, pathlib, xml.etree.ElementTree as ET

STROKE = "1.75"

def to_path(el):
    tag = el.tag.split('}')[-1]
    a = el.attrib
    if tag == 'path':
        return a['d']
    if tag == 'line':
        return f"M{a['x1']} {a['y1']}L{a['x2']} {a['y2']}"
    if tag == 'circle':
        cx, cy, r = float(a['cx']), float(a['cy']), float(a['r'])
        return f"M{cx - r} {cy}a{r} {r} 0 1 0 {2*r} 0a{r} {r} 0 1 0 {-2*r} 0"
    if tag == 'rect':
        x, y, w, h = (float(a[k]) for k in ('x', 'y', 'width', 'height'))
        r = float(a.get('rx', a.get('ry', 0)))
        if r == 0:
            return f"M{x} {y}h{w}v{h}h{-w}z"
        return (f"M{x+r} {y}h{w-2*r}a{r} {r} 0 0 1 {r} {r}v{h-2*r}a{r} {r} 0 0 1 {-r} {r}"
                f"h{-(w-2*r)}a{r} {r} 0 0 1 {-r} {-r}v{-(h-2*r)}a{r} {r} 0 0 1 {r} {-r}z")
    if tag in ('polyline', 'polygon'):
        nums = a['points'].replace(',', ' ').split()
        pts = [f'{nums[i]} {nums[i + 1]}' for i in range(0, len(nums), 2)]
        d = 'M' + 'L'.join(pts)
        return d + ('z' if tag == 'polygon' else '')
    return None

src, dst = map(pathlib.Path, sys.argv[1:3])
dst.mkdir(parents=True, exist_ok=True)
for svg in sorted(src.glob('*.svg')):
    root = ET.fromstring(svg.read_text().split('-->', 1)[-1])
    paths = [p for p in (to_path(c) for c in root) if p]
    body = ''.join(
        f'\n    <path android:pathData="{p}" android:strokeColor="#FF000000" android:strokeWidth="{STROKE}" '
        'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>'
        for p in paths)
    name = 'ic_' + svg.stem.replace('-', '_')
    (dst / f'{name}.xml').write_text(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" '
        f'android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">{body}\n</vector>\n')
print('converted', len(list(src.glob('*.svg'))))
