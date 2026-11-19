#!/usr/bin/env python3
"""Baking Buddy launcher icon = logo concept 2 "Bun buddies" (Trevor's pick, 2026-11-17).
Writes ic_launcher.svg (full square icon, for docs) and ic_launcher_foreground.svg (adaptive icon foreground:
transparent, art shrunk into the 72dp-of-108dp visible circle), then renders the app's mipmaps:
  mipmap-*/ic_launcher_foreground.png (108dp)  +  mipmap-*/ic_launcher.png (48dp, legacy full icon).
Background layer = @color/ic_launcher_background (terracotta #C07A5E), see res/mipmap-anydpi-v26/ic_launcher.xml.
Needs rsvg-convert."""
import os, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "concepts"))
import importlib; mk = importlib.import_module("mk-concepts")
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
SCALE = 0.64  # art (512 box) -> keeps the buns inside the circle Android shows

open(os.path.join(HERE, "ic_launcher.svg"), "w").write(mk.c2())
fg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512"><defs>{mk.BUN}</defs>'
      f'<g transform="translate(256 262) scale({SCALE}) translate(-256 -272)">{mk.bun_buddies()}</g></svg>')
open(os.path.join(HERE, "ic_launcher_foreground.svg"), "w").write(fg)
for d, k in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    out = os.path.join(RES, f"mipmap-{d}")
    os.makedirs(out, exist_ok=True)
    for svg, name, dp in (("ic_launcher_foreground.svg", "ic_launcher_foreground.png", 108), ("ic_launcher.svg", "ic_launcher.png", 48)):
        with open(os.path.join(out, name), "wb") as f:
            subprocess.run(["rsvg-convert", "-w", str(int(dp * k)), os.path.join(HERE, svg)], stdout=f, check=True)
print("icons written")
