#!/usr/bin/env python3
"""Rasterize the existing vector for launcher/APK-extractor compatibility.
Requires ImageMagick (magick). The in-app and splash vectors remain unchanged.
"""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
a = '{http://schemas.android.com/apk/res/android}'
vector = ET.parse(res / 'drawable/feather.xml').getroot()
svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg',
                 viewBox=f"0 0 {vector.get(a + 'viewportWidth')} {vector.get(a + 'viewportHeight')}")
for path in vector.findall('path'):
    attrs = {'d': path.attrib[a + 'pathData'], 'fill': path.get(a + 'fillColor', 'none')}
    for source, target in [('strokeColor', 'stroke'), ('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap')]:
        if a + source in path.attrib:
            attrs[target] = path.attrib[a + source]
    ET.SubElement(svg, 'path', attrs)
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / 'launcher.svg'
    ET.ElementTree(svg).write(source)
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        destination = res / f'mipmap-{density}' / 'ic_launcher.png'
        destination.parent.mkdir(exist_ok=True)
        subprocess.run(['magick', '-background', 'none', '-density', '768', str(source),
                        '-resize', f'{size}x{size}', '-strip', 'PNG32:' + str(destination)], check=True)
