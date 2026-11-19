#!/usr/bin/env python3
"""Five Bakery Buddy logo ideas (2026-11-17): happy bakers, buns, and the two B's. Writes NN-name.svg next to this file.
Palette = the app's Scandinavian colors. Render: rsvg-convert -w 512 NN-name.svg > NN-name.png"""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
DEEP, CHAR, LINEN, OAT = "#2A2724", "#3A3632", "#F2EEE8", "#FBF9F6"
TERRA, OCHRE, FJORD, ROSE = "#C07A5E", "#C9A15A", "#7C93A6", "#D9897C"
WHITE, PLEAT = "#FFFFFF", "#E4DDD3"

def svg(body, bg, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">'
            f'<defs>{BUN}{defs}</defs><rect width="512" height="512" rx="112" fill="{bg}"/>{body}</svg>')

BUN = ('<radialGradient id="bun" cx="0.36" cy="0.28" r="0.8"><stop offset="0" stop-color="#F3D196"/>'
       '<stop offset="0.55" stop-color="#DCA35B"/><stop offset="1" stop-color="#B9773D"/></radialGradient>')

def bowl(sx, y0, y1, flat, d=1):
    """B bowl: flat side on x=sx, round side bulging to +d."""
    k = 0.667 * (y1 - y0); ex = sx + d * flat
    return f"M{sx},{y0} L{ex},{y0} C{ex+d*k},{y0} {ex+d*k},{y1} {ex},{y1} L{sx},{y1} Z"

def hump(x0, x1, by, flat):
    """B bowl lying on its back: flat base at y=by, round side up."""
    k = 0.667 * (x1 - x0); ey = by - flat
    return f"M{x0},{by} L{x0},{ey} C{x0},{ey-k} {x1},{ey-k} {x1},{ey} L{x1},{by} Z"

def outlined(shapes, fill, stroke, sw):
    """Shapes (with PAINT placeholder) drawn as one outlined silhouette."""
    under = "".join(s.replace("PAINT", f'fill="{stroke}" stroke="{stroke}" stroke-width="{2*sw}" stroke-linejoin="round"') for s in shapes)
    over = "".join(s.replace("PAINT", f'fill="{fill}"') for s in shapes)
    return under + over

def toque_shapes(cx, by, w):
    bh = 0.30 * w
    return [f'<rect x="{cx-0.42*w}" y="{by-bh-0.32*w}" width="{0.84*w}" height="{0.4*w}" PAINT/>',
            f'<circle cx="{cx-0.30*w}" cy="{by-bh-0.30*w}" r="{0.29*w}" PAINT/>',
            f'<circle cx="{cx+0.30*w}" cy="{by-bh-0.30*w}" r="{0.29*w}" PAINT/>',
            f'<circle cx="{cx}" cy="{by-bh-0.46*w}" r="{0.35*w}" PAINT/>',
            f'<rect x="{cx-0.45*w}" y="{by-bh}" width="{0.9*w}" height="{bh}" rx="{0.07*w}" PAINT/>']

def toque(cx, by, w, stroke=None, sw=0):
    s = toque_shapes(cx, by, w)
    out = outlined(s, WHITE, stroke, sw) if stroke else "".join(x.replace("PAINT", f'fill="{WHITE}"') for x in s)
    bh = 0.30 * w
    for dx in (-0.2, 0.2):  # pleats rising out of the band
        out += (f'<path d="M{cx+dx*w},{by-bh-0.02*w} Q{cx+dx*w*1.15},{by-bh-0.25*w} {cx+dx*w*0.9},{by-bh-0.42*w}" '
                f'fill="none" stroke="{PLEAT}" stroke-width="{0.035*w}" stroke-linecap="round"/>')
    out += f'<line x1="{cx-0.45*w}" y1="{by-bh}" x2="{cx+0.45*w}" y2="{by-bh}" stroke="{PLEAT}" stroke-width="{0.035*w}"/>'
    return out

def glyph_b(x, y, h, color, hole):
    """A small filled letter B (for hat bands)."""
    st = 0.24 * h
    return (f'<rect x="{x}" y="{y}" width="{st}" height="{h}" fill="{color}"/>'
            f'<path d="{bowl(x+st*0.5, y, y+0.5*h, 0.12*h)}" fill="{color}"/>'
            f'<path d="{bowl(x+st*0.5, y+0.44*h, y+h, 0.16*h)}" fill="{color}"/>'
            f'<path d="{bowl(x+st, y+0.15*h, y+0.36*h, 0.03*h)}" fill="{hole}"/>'
            f'<path d="{bowl(x+st, y+0.6*h, y+0.85*h, 0.05*h)}" fill="{hole}"/>')

def eyes(cx, cy, s, col=DEEP, gap=0.30):
    sw, ew = max(3, 0.055 * s), 0.11 * s
    return "".join(f'<path d="M{cx+dx-ew},{cy} Q{cx+dx},{cy-ew*1.5} {cx+dx+ew},{cy}" fill="none" stroke="{col}" '
                   f'stroke-width="{sw}" stroke-linecap="round"/>' for dx in (-gap * s, gap * s))

def mouth(cx, my, s, col=DEEP):
    mw = 0.17 * s
    return (f'<path d="M{cx-mw},{my} Q{cx},{my+0.34*s} {cx+mw},{my} Z" fill="{col}" stroke="{col}" '
            f'stroke-width="{0.04*s}" stroke-linejoin="round"/>'
            f'<ellipse cx="{cx}" cy="{my+0.115*s}" rx="{mw*0.42}" ry="{0.045*s}" fill="#E8806F"/>')

def cheeks(cx, cy, s, dx=0.42):
    return "".join(f'<ellipse cx="{cx+d*s}" cy="{cy}" rx="{0.09*s}" ry="{0.055*s}" fill="{ROSE}" opacity="0.85"/>'
                   for d in (-dx, dx))

def face(cx, cy, s, col=DEEP):
    return eyes(cx, cy, s, col) + mouth(cx, cy + 0.15 * s, s, col) + cheeks(cx, cy + 0.12 * s, s)

def seeds(pts, col=OAT):
    return "".join(f'<ellipse cx="{x}" cy="{y}" rx="6" ry="3.4" transform="rotate({a} {x} {y})" fill="{col}"/>' for x, y, a in pts)

def shine(d, w=7):
    return f'<path d="{d}" fill="none" stroke="#FFFFFF" stroke-width="{w}" stroke-linecap="round" opacity="0.55"/>'

# 1 ---------------------------------------------------------------- rolling-pin B's
def c1():
    sw = 6
    def letter(sx):
        o = outlined([f'<path d="{bowl(sx+30, 240, 386, 18)}" PAINT/>'], "url(#bun)", DEEP, sw)
        o += outlined([f'<path d="{bowl(sx+30, 128, 248, 14)}" PAINT/>'], "url(#bun)", DEEP, sw)
        o += seeds([(sx+70, 162, -20), (sx+96, 176, 15), (sx+74, 196, 30), (sx+102, 206, -10)])
        o += shine(f"M{sx+108},{280} Q{sx+122},{292} {sx+124},{310}", 6)
        o += face(sx + 92, 315, 74)
        for y0, y1 in ((96, 134), (378, 416)):  # handles
            o += outlined([f'<rect x="{sx+8}" y="{y0}" width="20" height="{y1-y0}" rx="9" PAINT/>'], "#9C6A3F", DEEP, sw)
        o += outlined([f'<rect x="{sx}" y="122" width="36" height="262" rx="15" PAINT/>'], "#D29E68", DEEP, sw)
        o += f'<line x1="{sx+12}" y1="140" x2="{sx+12}" y2="366" stroke="#F0C994" stroke-width="5" stroke-linecap="round"/>'
        return o
    body = f'<g transform="translate(256 258) scale(1.12) translate(-256 -256)">{letter(110)}{letter(262)}</g>'
    return svg(body, LINEN)

# 2 ---------------------------------------------------------------- bun buddies
def bun_path(cx, cy, w, h):
    return (f"M{cx-w/2},{cy+h*0.35} C{cx-w/2},{cy-h*0.75} {cx+w/2},{cy-h*0.75} {cx+w/2},{cy+h*0.35} "
            f"Q{cx+w/2},{cy+h*0.5} {cx+w*0.42},{cy+h*0.5} L{cx-w*0.42},{cy+h*0.5} Q{cx-w/2},{cy+h*0.5} {cx-w/2},{cy+h*0.35} Z")

def c2():
    return svg(bun_buddies(), TERRA)

def bun_buddies():
    """The art of concept 2 without its background square (also the launcher icon's foreground, see ../mk-icon.py)."""
    b = '<circle cx="256" cy="268" r="200" fill="#CB8B70"/>'
    b += '<ellipse cx="262" cy="428" rx="190" ry="20" fill="#A9654A"/>'
    for cx, cy, w, h, tx, tw, rot, s in ((180, 336, 236, 196, 172, 132, -9, 118), (342, 352, 206, 170, 350, 116, 10, 104)):
        b += f'<path d="{bun_path(cx, cy, w, h)}" fill="url(#bun)"/>'
        b += shine(f"M{cx-w*0.36},{cy-h*0.05} Q{cx-w*0.34},{cy-h*0.3} {cx-w*0.16},{cy-h*0.4}", 8)
        b += face(cx + 4, cy + 4, s)
        by = cy - h * 0.475 + 16
        b += f'<g transform="rotate({rot} {tx} {by})">{toque(tx, by, tw)}'
        b += glyph_b(tx - 0.08 * tw, by - 0.27 * tw, 0.24 * tw, TERRA, WHITE) + '</g>'
    return b

# 3 ---------------------------------------------------------------- scored bun: "BB" and a smile cut into the crust
def score(d, w=16):
    """A bread score: dark crust edge, pale crumb showing through."""
    return (f'<path d="{d}" fill="none" stroke="#94531F" stroke-width="{w+14}" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{d}" fill="none" stroke="#F7E1B5" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')

def b_stroke(x, y0, y1, w1, w2):
    ym = (y0 + y1) / 2 - 4
    k1, k2 = 0.667 * (ym - y0), 0.667 * (y1 - ym)
    return (f"M{x},{y1} L{x},{y0} L{x+w1},{y0} C{x+w1+k1},{y0} {x+w1+k1},{ym} {x+w1},{ym} L{x},{ym} "
            f"M{x+w1},{ym} L{x+w2},{ym} C{x+w2+k2},{ym} {x+w2+k2},{y1} {x+w2},{y1} L{x},{y1}")

def c3():
    b = '<ellipse cx="262" cy="452" rx="170" ry="18" fill="#000000" opacity="0.35"/>'
    b += '<circle cx="256" cy="256" r="196" fill="url(#bun)"/>'
    b += '<circle cx="256" cy="256" r="193" fill="none" stroke="#9E5C27" stroke-width="6" opacity="0.45"/>'
    for x, y, r in ((150, 130, 4), (176, 118, 3), (128, 168, 3), (200, 104, 2.5), (118, 200, 2.5), (166, 152, 2)):
        b += f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFFFFF" opacity="0.7"/>'
    b += score(b_stroke(150, 150, 300, 22, 30)) + score(b_stroke(274, 150, 300, 22, 30))
    b += score("M196,352 Q256,404 316,352")
    return svg(b, DEEP)

# 4 ---------------------------------------------------------------- big B and little B, each a baker (hat on top, bun face below)
def baker_b(sx, y0, h, cid):
    st, f1, f2 = 0.15 * h, 0.05 * h, 0.07 * h
    bx = sx + 0.11 * h                      # bowls start just inside the stem
    top = bowl(bx, y0, y0 + 0.46 * h, f1)
    o = f'<clipPath id="{cid}"><path d="{top}"/></clipPath>'
    o += f'<path d="{bowl(bx, y0+0.42*h, y0+h, f2)}" fill="url(#bun)"/>'
    o += shine(f"M{bx+0.27*h},{y0+0.53*h} Q{bx+0.34*h},{y0+0.57*h} {bx+0.37*h},{y0+0.65*h}", 0.025 * h)
    o += face(bx + 0.20 * h, y0 + 0.71 * h, 0.30 * h)
    ex = bx + f1
    for cx, cy, r in ((ex + 0.02*h, y0 + 0.03*h, 0.11*h), (ex + 0.15*h, y0 + 0.01*h, 0.12*h), (ex + 0.25*h, y0 + 0.15*h, 0.10*h)):
        o += f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{WHITE}"/>'
    o += f'<path d="{top}" fill="{WHITE}"/>'
    o += (f'<g clip-path="url(#{cid})"><rect x="{sx}" y="{y0+0.33*h}" width="{0.6*h}" height="{0.13*h}" fill="#EFE9E0"/>'
          f'<rect x="{sx}" y="{y0+0.33*h}" width="{0.6*h}" height="{0.014*h}" fill="{PLEAT}"/>'
          f'<path d="M{ex+0.12*h},{y0+0.31*h} Q{ex+0.15*h},{y0+0.2*h} {ex+0.1*h},{y0+0.1*h}" fill="none" stroke="{PLEAT}" '
          f'stroke-width="{0.022*h}" stroke-linecap="round"/></g>')
    o += f'<rect x="{sx}" y="{y0-0.04*h}" width="{st}" height="{1.04*h}" rx="{0.05*h}" fill="{CHAR}"/>'
    return o

def c4():
    b = '<ellipse cx="256" cy="430" rx="190" ry="16" fill="#5E6B73" opacity="0.55"/>'
    b += baker_b(96, 134, 292, "hb")
    b += f'<g transform="rotate(-6 300 426)">{baker_b(300, 228, 198, "hs")}</g>'
    return svg(f'<g transform="translate(18 0)">{b}</g>', FJORD)

# 5 ---------------------------------------------------------------- happy baker
def c5():
    b = f'<path d="M96,512 C96,424 172,396 256,396 C340,396 416,424 416,512 Z" fill="{OAT}"/>'
    b += f'<path d="M204,398 L308,398 L256,452 Z" fill="{TERRA}"/>'
    b += '<circle cx="152" cy="306" r="20" fill="#E9B48C"/><circle cx="360" cy="306" r="20" fill="#E9B48C"/>'
    b += '<circle cx="256" cy="300" r="104" fill="#F2C7A0"/>'
    b += eyes(256, 286, 130, DEEP, gap=0.31)
    b += cheeks(256, 324, 130, dx=0.5)
    b += '<ellipse cx="256" cy="318" rx="13" ry="10" fill="#E3A57C"/>'
    b += f'<path d="{mouth_path()}" fill="{DEEP}"/>'
    b += '<ellipse cx="256" cy="379" rx="11" ry="5" fill="#E8806F"/>'
    # mustache = a B on its side (flat spine on top, two round bowls hanging down)
    b += f'<path d="M198,330 L314,330 L314,334 C314,370 260,370 258,338 L254,338 C252,370 198,370 198,334 Z" fill="#6B4A35"/>'
    b += '<circle cx="196" cy="328" r="9" fill="#6B4A35"/><circle cx="316" cy="328" r="9" fill="#6B4A35"/>'
    tq = toque(256, 224, 196)
    b += f'<g transform="translate(256 224) scale(1 0.8) translate(-256 -224)">{tq}</g>'
    b += glyph_b(226, 180, 32, TERRA, WHITE) + glyph_b(262, 180, 32, TERRA, WHITE)
    return svg(b, OCHRE)

def mouth_path():
    return "M232,366 Q256,398 280,366 Z"

def main():
  for i, (name, f) in enumerate((("rolling-pin-bs", c1), ("bun-buddies", c2), ("scored-bun", c3), ("baker-bs", c4), ("happy-baker", c5)), 1):
    open(os.path.join(HERE, f"{i:02d}-{name}.svg"), "w").write(f())
    print(f"{i:02d}-{name}.svg")

if __name__ == "__main__":
    main()
