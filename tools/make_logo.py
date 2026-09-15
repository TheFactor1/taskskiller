# Regenerates the logo drawables (launcher icon, TV banners, in-app mark,
# notification icon) for logo B ("Reconnect").
# Needs python3-fonttools and the Lexend variable font (SIL OFL):
#   curl -L -o Lexend.ttf "https://github.com/google/fonts/raw/main/ofl/lexend/Lexend%5Bwght%5D.ttf"
#   python3 tools/make_logo.py app/src/main/res Lexend.ttf

import math, sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

RES, FONT = sys.argv[1], sys.argv[2]
num = lambda v: (f"{v:.2f}".rstrip("0").rstrip(".")) or "0"
pt = lambda r, a: (54 + r * math.cos(math.radians(a)), 54 + r * math.sin(math.radians(a)))

(x1, y1), (x2, y2) = pt(22, 25), pt(22, 315)
RING = f"M{num(x1)},{num(y1)}A22,22 0 1,1 {num(x2)},{num(y2)}"
dx, dy = pt(22, 350)
circle = lambda cx, cy, r: f"M{num(cx - r)},{num(cy)}a{num(r)},{num(r)} 0 1,0 {num(2 * r)},0a{num(r)},{num(r)} 0 1,0 {num(-2 * r)},0z"
HALO, DOT = circle(dx, dy, 11), circle(dx, dy, 6.5)

BLUE, GREEN, HALO_GREEN, WHITE, TEXT = "#FF7FB2FF", "#FF6FD48A", "#386FD48A", "#FFFFFFFF", "#FFF2F5FA"
TOP, BOTTOM = "#FF1F2C46", "#FF0B1020"
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n')
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
NS_AAPT = NS + '\n    xmlns:aapt="http://schemas.android.com/aapt"'

def mark(ring=BLUE, dot=GREEN, halo=True, indent="    "):
    out = [f'{indent}<path\n{indent}    android:pathData="{RING}"\n{indent}    android:strokeColor="{ring}"\n{indent}    android:strokeLineCap="round"\n{indent}    android:strokeWidth="8" />']
    if halo:
        out.append(f'{indent}<path\n{indent}    android:fillColor="{HALO_GREEN}"\n{indent}    android:pathData="{HALO}" />')
    out.append(f'{indent}<path\n{indent}    android:fillColor="{dot}"\n{indent}    android:pathData="{DOT}" />')
    return "\n".join(out)

def ground(w, h):
    return f'''    <path android:pathData="M0,0h{w}v{h}h-{w}z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endColor="{BOTTOM}"
                android:endX="0"
                android:endY="{h}"
                android:startColor="{TOP}"
                android:startX="0"
                android:startY="0"
                android:type="linear" />
        </aapt:attr>
    </path>'''

# Wordmark: Lexend at weight 600, drawn as outlines so every TV shows the same letters.
font = instantiateVariableFont(TTFont(FONT), {"wght": 600})
glyphs, cmap, upm = font.getGlyphSet(), font.getBestCmap(), font["head"].unitsPerEm
def word(text, x0, baseline, size, tracking=-0.3):
    s, x, parts = size / upm, x0, []
    for ch in text:
        g = glyphs[cmap[ord(ch)]]
        pen = SVGPathPen(glyphs, ntos=num)
        g.draw(TransformPen(pen, (s, 0, 0, -s, x, baseline)))
        if pen.getCommands():
            parts.append(pen.getCommands())
        x += g.width * s + tracking
    return " ".join(parts), x - tracking - x0

# The mark's visible extent on the 108 grid: the ring's outer edge on the
# left (54 - 22 - 4) to the dot's halo on the right, centred on y = 54.
MARK_LEFT, MARK_RIGHT, MARK_CY = 28.0, dx + 11, 54.0
CAP = font["OS/2"].sCapHeight / upm

def banner(name, text, comment, max_size=40.0, scale=1.0, gap=14.0, margin=24.0):
    # Ring and wordmark are centred as one group, as large as fits between
    # 24 dp margins. Launchers such as Projectivy show the banner inside a
    # rounded pill, where a small mark set left of centre looked lost.
    mark_w = (MARK_RIGHT - MARK_LEFT) * scale
    size = max_size
    while True:
        _, width = word(text, 0, 0, size)
        total = mark_w + gap + width
        if total <= 320 - 2 * margin or size <= 20:
            break
        size -= 0.5
    x0 = (320 - total) / 2
    tx, ty = x0 - MARK_LEFT * scale, 90 - MARK_CY * scale
    path, width = word(text, x0 + mark_w + gap, 90 + CAP * size / 2, size)
    xml = f'''{HEAD}<!-- {comment} Generated from logo B ("Reconnect"): the ring
     and dot beside the wordmark, centred as one group; the wordmark in
     Lexend SemiBold (SIL Open Font License) as outlines. -->
<vector {NS_AAPT}
    android:width="320dp"
    android:height="180dp"
    android:viewportWidth="320"
    android:viewportHeight="180">
{ground(320, 180)}
    <group
        android:scaleX="{num(scale)}"
        android:scaleY="{num(scale)}"
        android:translateX="{num(tx)}"
        android:translateY="{num(ty)}">
{mark(indent="        ")}
    </group>
    <path
        android:fillColor="{TEXT}"
        android:pathData="{path}" />
</vector>
'''
    open(f"{RES}/drawable/{name}.xml", "w").write(xml)
    print(f"{name}: '{text}' at {size}px, group {total:.1f} wide from x={x0:.1f} of 320")

banner("app_banner", "Refresher", "TV home-screen banner.")
banner("refresh_now_banner", "Refresh now", 'Banner for the "Refresh now" tile.')

open(f"{RES}/drawable/ic_launcher.xml", "w").write(f'''{HEAD}<!-- Launcher icon, logo B ("Reconnect"): a ring broken at the right, with a
     green dot in the gap for the connection coming back. 108 dp grid. -->
<vector {NS_AAPT}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{ground(108, 108)}
{mark()}
</vector>
''')

open(f"{RES}/drawable/ic_mark.xml", "w").write(f'''{HEAD}<!-- Logo B's ring and dot alone, on the 108 grid, for layering over a tile. -->
<vector {NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{mark()}
</vector>
''')

# Notification icon: Android draws it from the alpha channel only, so one colour, no halo.
open(f"{RES}/drawable/ic_restart.xml", "w").write(f'''{HEAD}<!-- Notification icon: logo B as a one-colour silhouette (Android tints it). -->
<vector {NS}
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="76"
    android:viewportHeight="76">
    <group
        android:translateX="-16"
        android:translateY="-16">
{mark(ring=WHITE, dot=WHITE, halo=False, indent="        ")}
    </group>
</vector>
''')

open(f"{RES}/drawable/ic_logo_tile.xml", "w").write(f'''{HEAD}<!-- In-app logo: logo B on the launcher icon's night-blue tile, rounded to
     match the cards. (ic_launcher keeps its square for the TV home screen.) -->
<layer-list {NS}>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="12dp" />
            <gradient
                android:angle="270"
                android:endColor="{BOTTOM}"
                android:startColor="{TOP}" />
            <stroke
                android:width="1dp"
                android:color="@color/hairline_strong" />
        </shape>
    </item>
    <item android:drawable="@drawable/ic_mark" />
</layer-list>
''')
print("ring", RING, "| dot", num(dx), num(dy))
