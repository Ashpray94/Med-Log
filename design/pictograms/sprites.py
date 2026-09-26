"""
MedLog symptom icons: three sprite sheets (top, middle, lower body).
Simple flat icons that show only the body part involved. One palette, one gender-neutral character (30s).
Run:  python sprites.py   -> top.svg/.png, mid.svg/.png, low.svg/.png + sprites.json
The app slices each PNG by the grid in sprites.json.
"""
import json, math, os
import resvg_py

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sprites")

SKIN, SKIN_D, SKIN_L = "#F2C4A0", "#DDA27C", "#F8D9C0"
HAIR = "#3E2A21"
SWEATER, SWEATER_D = "#6F9FD8", "#5486C4"
PANTS, PANTS_D = "#56657A", "#465366"
RED, RED_L = "#EF4444", "#FCA5A5"
GREEN, GREEN_L = "#22A45D", "#86EFAC"
BLUE, BLUE_L = "#3B82F6", "#BFDBFE"
PURPLE = "#8B5CF6"
ORANGE = "#F97316"
YELLOW = "#F5B83D"
INK = "#2B2F36"
GREY, GREY_L = "#9AA3AE", "#E5E7EB"
WHITE = "#FFFFFF"
BRUISE = "#8E6BBF"

CELL, PAD = 200, 24     # icon box and gap around it


# ───────────── primitives ─────────────

def bolt(x, y, s=1.0, rot=0, color=RED):
    """Pain mark: a short jagged bolt."""
    return (f'<g transform="translate({x},{y}) rotate({rot}) scale({s})">'
            f'<path d="M0,-16 L7,-3 L-1,-1 L6,14" fill="none" stroke="{color}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></g>')

def bolts(x, y, spread=34, s=1.0):
    return bolt(x - spread, y, s, -20) + bolt(x + spread, y, s, 20)

def glow(x, y, r, color=RED, op=0.35):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="{color}" opacity="{op*0.45}"/>'
            f'<circle cx="{x}" cy="{y}" r="{r*0.62}" fill="{color}" opacity="{op*0.7}"/>')

def drop(x, y, s=1.0, color=BLUE):
    return (f'<path transform="translate({x},{y}) scale({s})" d="M0,-14 C6,-5 10,0 10,5 A10,10 0 0 1 -10,5 C-10,0 -6,-5 0,-14Z" fill="{color}"/>')

def star(x, y, r=7, color=YELLOW):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.45
        a = math.radians(-90 + i * 36)
        pts.append(f"{x + rr*math.cos(a):.1f},{y + rr*math.sin(a):.1f}")
    return f'<polygon points="{" ".join(pts)}" fill="{color}"/>'

def waves(x, y, color, n=3, w=26, gap=9, sw=4):
    out = ""
    for i in range(n):
        yy = y + i * gap
        out += f'<path d="M{x},{yy} q{w/4},-6 {w/2},0 t{w/2},0" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
    return out

def arcs(x, y, color, n=3, r0=12, step=9, a0=-45, a1=45, sw=4):
    out = ""
    for i in range(n):
        r = r0 + i * step
        x1, y1 = x + r*math.cos(math.radians(a0)), y + r*math.sin(math.radians(a0))
        x2, y2 = x + r*math.cos(math.radians(a1)), y + r*math.sin(math.radians(a1))
        out += f'<path d="M{x1:.1f},{y1:.1f} A{r},{r} 0 0 1 {x2:.1f},{y2:.1f}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
    return out

def cloud(x, y, w, color):
    return (f'<g fill="{color}"><circle cx="{x-w*0.28}" cy="{y}" r="{w*0.26}"/><circle cx="{x}" cy="{y-w*0.12}" r="{w*0.32}"/>'
            f'<circle cx="{x+w*0.3}" cy="{y}" r="{w*0.24}"/><rect x="{x-w*0.52}" y="{y}" width="{w*1.04}" height="{w*0.24}" rx="{w*0.12}"/></g>')

def qmark(x, y, s=1.0, color=PURPLE):
    return (f'<g transform="translate({x},{y}) scale({s})" fill="none" stroke="{color}" stroke-width="7" stroke-linecap="round">'
            f'<path d="M-10,-12 A11,11 0 1 1 3,-1 C-1,2 0,6 0,10"/></g><circle cx="{x}" cy="{y + 22*s}" r="{4.5*s}" fill="{color}"/>')

def moon(x, y, r=14, color=YELLOW):
    return f'<path d="M{x},{y-r} A{r},{r} 0 1 0 {x+r*0.9},{y+r*0.5} A{r*0.8},{r*0.8} 0 1 1 {x},{y-r}Z" fill="{color}"/>'

def flame(x, y, s=1.0):
    return (f'<g transform="translate({x},{y}) scale({s})"><path d="M0,16 C-12,12 -13,0 -6,-8 C-5,-2 -2,0 0,-2 C0,-10 4,-16 8,-18 C7,-8 14,-2 12,6 C11,12 6,16 0,16Z" fill="{ORANGE}"/>'
            f'<path d="M0,15 C-5,12 -5,5 -1,1 C0,5 3,5 3,1 C6,5 6,12 0,15Z" fill="{YELLOW}"/></g>')

def arrow(x, y, up=True, color=RED, s=1.0):
    d = -1 if up else 1
    return (f'<g transform="translate({x},{y}) scale({s})" stroke="{color}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" fill="none">'
            f'<path d="M0,{-16*d} L0,{16*d}"/><path d="M-11,{5*d} L0,{17*d} L11,{5*d}"/></g>')

def nosign(x, y, r=14):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="{WHITE}" stroke="{RED}" stroke-width="5"/>'
            f'<path d="M{x-r*0.62},{y+r*0.62} L{x+r*0.62},{y-r*0.62}" stroke="{RED}" stroke-width="5" stroke-linecap="round"/>')

def plaster(x, y, rot=-30, w=46, h=18):
    return (f'<g transform="translate({x},{y}) rotate({rot})"><rect x="{-w/2}" y="{-h/2}" width="{w}" height="{h}" rx="{h/2}" fill="#F1D1A6"/>'
            f'<rect x="{-w*0.16}" y="{-h/2}" width="{w*0.32}" height="{h}" fill="#E2B67D"/>'
            f'<circle cx="{-w*0.05}" cy="-2" r="1.6" fill="#C99A5C"/><circle cx="{w*0.07}" cy="2" r="1.6" fill="#C99A5C"/></g>')

def dots(x, y, pts, r=4.5, color=RED):
    return "".join(f'<circle cx="{x+dx}" cy="{y+dy}" r="{r}" fill="{color}"/>' for dx, dy in pts)

# ───────────── the character ─────────────

def head(expr="neutral", mouth="flat", tint=None, cx=100, cy=86, r=44, shoulders=True, cheeks=True, extra_face=""):
    s = ""
    if shoulders:
        s += (f'<path d="M34,200 C34,160 58,140 100,140 C142,140 166,160 166,200Z" fill="{SWEATER}"/>'
              f'<path d="M80,140 Q100,158 120,140" fill="none" stroke="{SWEATER_D}" stroke-width="5" stroke-linecap="round"/>')
        s += f'<rect x="{cx-13}" y="{cy+r-10}" width="26" height="22" rx="8" fill="{SKIN_D}"/>'
    # ears
    s += f'<ellipse cx="{cx-r+2}" cy="{cy+4}" rx="8" ry="11" fill="{SKIN_D}"/><ellipse cx="{cx+r-2}" cy="{cy+4}" rx="8" ry="11" fill="{SKIN_D}"/>'
    s += f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{SKIN}"/>'
    if tint == "green":
        s += f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{GREEN_L}" opacity="0.55"/>'
    elif tint == "red":
        s += f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{RED_L}" opacity="0.5"/>'
    elif tint == "yellow":
        s += f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{YELLOW}" opacity="0.35"/>'
    # short tousled hair
    s += (f'<path d="M{cx-r-1},{cy-2} C{cx-r-2},{cy-r-6} {cx-8},{cy-r-14} {cx+10},{cy-r-8} C{cx+r+8},{cy-r} {cx+r+4},{cy-10} {cx+r+1},{cy-4} '
          f'C{cx+r-8},{cy-20} {cx+14},{cy-26} {cx-4},{cy-22} C{cx-18},{cy-24} {cx-r+8},{cy-16} {cx-r-1},{cy-2}Z" fill="{HAIR}"/>')
    ey, ex = cy + 4, 16
    if expr == "neutral":
        s += f'<ellipse cx="{cx-ex}" cy="{ey}" rx="4.5" ry="5.5" fill="{INK}"/><ellipse cx="{cx+ex}" cy="{ey}" rx="4.5" ry="5.5" fill="{INK}"/>'
    elif expr == "squint":
        for sx, d in ((cx-ex, 1), (cx+ex, -1)):
            s += f'<path d="M{sx-7*d},{ey-6} L{sx+5*d},{ey} L{sx-7*d},{ey+6}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>'
    elif expr == "shut":
        for sx in (cx-ex, cx+ex):
            s += f'<path d="M{sx-8},{ey} Q{sx},{ey+6} {sx+8},{ey}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    elif expr == "half":
        for sx in (cx-ex, cx+ex):
            s += f'<path d="M{sx-8},{ey} L{sx+8},{ey}" stroke="{INK}" stroke-width="4" stroke-linecap="round"/><path d="M{sx-6},{ey} Q{sx},{ey+6} {sx+6},{ey}" fill="{INK}"/>'
    elif expr == "spiral":
        for sx in (cx-ex, cx+ex):
            pts = []
            for i in range(28):
                a = i * 0.45; rr = 0.35 * i
                pts.append(f"{sx + rr*math.cos(a):.1f},{ey + rr*math.sin(a):.1f}")
            s += f'<polyline points="{" ".join(pts)}" fill="none" stroke="{INK}" stroke-width="2.6" stroke-linecap="round"/>'
    elif expr == "sad":
        s += f'<ellipse cx="{cx-ex}" cy="{ey+1}" rx="4.5" ry="5" fill="{INK}"/><ellipse cx="{cx+ex}" cy="{ey+1}" rx="4.5" ry="5" fill="{INK}"/>'
        s += (f'<path d="M{cx-ex-9},{ey-10} L{cx-ex+5},{ey-15}" stroke="{INK}" stroke-width="3.5" stroke-linecap="round"/>'
              f'<path d="M{cx+ex+9},{ey-10} L{cx+ex-5},{ey-15}" stroke="{INK}" stroke-width="3.5" stroke-linecap="round"/>')
    elif expr == "wide":
        s += f'<circle cx="{cx-ex}" cy="{ey}" r="7" fill="{WHITE}" stroke="{INK}" stroke-width="2.5"/><circle cx="{cx+ex}" cy="{ey}" r="7" fill="{WHITE}" stroke="{INK}" stroke-width="2.5"/>'
        s += f'<circle cx="{cx-ex}" cy="{ey}" r="3.2" fill="{INK}"/><circle cx="{cx+ex}" cy="{ey}" r="3.2" fill="{INK}"/>'
    elif expr == "droop":
        s += f'<ellipse cx="{cx-ex}" cy="{ey}" rx="4.5" ry="5.5" fill="{INK}"/><path d="M{cx+ex-8},{ey+1} L{cx+ex+8},{ey+4}" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    if cheeks:
        s += f'<ellipse cx="{cx-26}" cy="{cy+18}" rx="7" ry="4.5" fill="#F29C8A" opacity="0.55"/><ellipse cx="{cx+26}" cy="{cy+18}" rx="7" ry="4.5" fill="#F29C8A" opacity="0.55"/>'
    my = cy + 24
    if mouth == "flat":
        s += f'<path d="M{cx-8},{my} L{cx+8},{my}" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    elif mouth == "smile":
        s += f'<path d="M{cx-11},{my-3} Q{cx},{my+7} {cx+11},{my-3}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    elif mouth == "frown":
        s += f'<path d="M{cx-10},{my+4} Q{cx},{my-5} {cx+10},{my+4}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    elif mouth == "open":
        s += f'<ellipse cx="{cx}" cy="{my+1}" rx="8" ry="9" fill="#7A2E2E"/>'
    elif mouth == "grimace":
        s += f'<rect x="{cx-12}" y="{my-5}" width="24" height="11" rx="5" fill="{WHITE}" stroke="{INK}" stroke-width="3"/><path d="M{cx-12},{my} L{cx+12},{my}" stroke="{INK}" stroke-width="2"/>'
    elif mouth == "wavy":
        s += f'<path d="M{cx-12},{my} q3,-5 6,0 t6,0 t6,0 t6,0" fill="none" stroke="{INK}" stroke-width="3.5" stroke-linecap="round"/>'
    elif mouth == "droop":
        s += f'<path d="M{cx-12},{my-2} Q{cx},{my+1} {cx+12},{my+8}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    return s + extra_face

def torso(expr="neutral", mouth_="flat", back=False, belly_hands=False, tint=None, round_belly=False):
    """Upper body with a small head, so it always reads as a person."""
    top = 76
    s = ""
    if belly_hands:
        s += f'<path d="M50,96 C34,112 36,146 74,150" fill="none" stroke="{SWEATER_D}" stroke-width="24" stroke-linecap="round"/>'
        s += f'<path d="M150,96 C166,112 164,146 126,150" fill="none" stroke="{SWEATER_D}" stroke-width="24" stroke-linecap="round"/>'
    else:
        s += f'<path d="M50,96 C36,110 32,146 36,180" fill="none" stroke="{SWEATER_D}" stroke-width="24" stroke-linecap="round"/>'
        s += f'<path d="M150,96 C164,110 168,146 164,180" fill="none" stroke="{SWEATER_D}" stroke-width="24" stroke-linecap="round"/>'
    w = 60 if round_belly else 50
    s += f'<path d="M{100-w},{top+26} C{100-w},{top+6} 72,{top} 100,{top} C128,{top} {100+w},{top+6} {100+w},{top+26} L{100+w-2},196 L{100-w+2},196Z" fill="{SWEATER}"/>'
    if not back:
        s += f'<path d="M86,{top} Q100,{top+12} 114,{top}" fill="none" stroke="{SWEATER_D}" stroke-width="5" stroke-linecap="round"/>'
    else:
        s += f'<path d="M100,{top+10} L100,192" stroke="{SWEATER_D}" stroke-width="4" stroke-dasharray="6 7" stroke-linecap="round"/>'
    if belly_hands:
        s += f'<ellipse cx="80" cy="150" rx="15" ry="12" fill="{SKIN}"/><ellipse cx="120" cy="150" rx="15" ry="12" fill="{SKIN}"/>'
    else:
        s += f'<circle cx="36" cy="184" r="11" fill="{SKIN_D}"/><circle cx="164" cy="184" r="11" fill="{SKIN_D}"/>'
    s += f'<rect x="91" y="{top-14}" width="18" height="18" rx="6" fill="{SKIN_D}"/>'
    if back:
        s += f'<ellipse cx="72" cy="42" rx="5" ry="8" fill="{SKIN_D}"/><ellipse cx="128" cy="42" rx="5" ry="8" fill="{SKIN_D}"/><circle cx="100" cy="38" r="28" fill="{HAIR}"/>'
    else:
        s += '<g transform="translate(100,38) scale(0.64) translate(-100,-86)">' + head(expr, mouth_, tint=tint, shoulders=False) + '</g>'
    return s


def forearm(rot=0):
    """A forearm and hand crossing the icon diagonally."""
    return (f'<g transform="rotate({rot} 100 100)">'
            f'<path d="M-10,150 L110,92" stroke="{SWEATER}" stroke-width="44" stroke-linecap="butt"/>'
            f'<path d="M40,126 L150,72" stroke="{SKIN}" stroke-width="38" stroke-linecap="round"/>'
            f'<ellipse cx="160" cy="66" rx="24" ry="20" fill="{SKIN}"/>'
            f'<path d="M168,52 Q186,40 190,52" stroke="{SKIN}" stroke-width="12" stroke-linecap="round" fill="none"/></g>')

def hand(cx=100, cy=110, s=1.0, fill=SKIN):
    """Open palm, fingers up."""
    return (f'<g transform="translate({cx},{cy}) scale({s})" fill="{fill}">'
            f'<rect x="-34" y="-8" width="68" height="62" rx="26"/>'
            f'<rect x="-34" y="-58" width="15" height="62" rx="7.5"/><rect x="-16" y="-70" width="15" height="72" rx="7.5"/>'
            f'<rect x="2" y="-66" width="15" height="68" rx="7.5"/><rect x="20" y="-52" width="14" height="56" rx="7"/>'
            f'<rect x="-66" y="-2" width="44" height="17" rx="8.5" transform="rotate(-38 -44 6)"/>'
            f'<rect x="-22" y="48" width="44" height="40" fill="{SWEATER}"/></g>')

def leg(bent=False):
    if not bent:
        return (f'<path d="M92,0 L92,120" stroke="{PANTS}" stroke-width="46"/>'
                f'<path d="M92,110 L94,168" stroke="{SKIN}" stroke-width="38" stroke-linecap="round"/>'
                f'<path d="M80,176 C80,196 138,196 142,184 C144,172 118,168 108,162 L84,162Z" fill="{SKIN}"/>')
    return (f'<path d="M20,70 L96,96" stroke="{PANTS}" stroke-width="46" stroke-linecap="round"/>'
            f'<path d="M100,98 L126,168" stroke="{SKIN}" stroke-width="38" stroke-linecap="round"/>'
            f'<circle cx="100" cy="98" r="24" fill="{SKIN}"/>'
            f'<path d="M112,178 C112,196 170,196 172,184 C174,172 148,166 138,160 L118,160Z" fill="{SKIN}"/>')

def foot(cx=100, cy=120, s=1.0, fill=SKIN, dotted=False, swollen=False):
    body = (f'M-40,-60 L-12,-60 L-8,-6 C10,6 52,14 60,30 C66,44 50,50 20,50 L-40,50 C-52,50 -54,36 -50,20Z')
    extra = f'<circle cx="46" cy="36" r="8" fill="{SKIN_D}"/>' if not dotted else ""
    if swollen:
        body = 'M-46,-60 L-6,-60 C-4,-40 0,-10 14,0 C40,10 66,18 66,34 C66,48 50,54 20,54 L-44,54 C-60,54 -64,36 -58,16 C-54,0 -52,-30 -46,-60Z'
    style = f'fill="{fill}"' if not dotted else f'fill="{SKIN_L}" stroke="{GREY}" stroke-width="4" stroke-dasharray="7 6"'
    return f'<g transform="translate({cx},{cy}) scale({s})"><rect x="-44" y="-100" width="36" height="44" fill="{PANTS}"/><path d="{body}" {style}/>{extra}</g>'

def figure(x=100, y=100, s=1.0, rot=0, pose="stand"):
    """Small full figure for falls and walking."""
    arm1, arm2, leg1, leg2 = "M-4,-30 L-26,-6", "M4,-30 L26,-6", "M-6,18 L-12,58", "M6,18 L12,58"
    if pose == "walk":
        arm1, arm2, leg1, leg2 = "M-4,-30 L-20,0", "M4,-30 L24,-10", "M-4,18 L-18,58", "M4,18 L16,56"
    if pose == "fall":
        arm1, arm2 = "M-4,-30 L-34,-40", "M4,-30 L30,-46"
    return (f'<g transform="translate({x},{y}) rotate({rot}) scale({s})" stroke-linecap="round">'
            f'<path d="{leg1}" stroke="{PANTS}" stroke-width="14"/><path d="{leg2}" stroke="{PANTS}" stroke-width="14"/>'
            f'<rect x="-18" y="-40" width="36" height="62" rx="16" fill="{SWEATER}"/>'
            f'<path d="{arm1}" stroke="{SWEATER_D}" stroke-width="12"/><path d="{arm2}" stroke="{SWEATER_D}" stroke-width="12"/>'
            f'<circle cx="0" cy="-58" r="17" fill="{SKIN}"/><path d="M-17,-60 C-16,-80 16,-80 17,-62 C10,-70 -8,-72 -17,-60Z" fill="{HAIR}"/></g>')

def toilet(cx=100, cy=112, s=1.0, water=None):
    w = water or BLUE_L
    return (f'<g transform="translate({cx},{cy}) scale({s})">'
            f'<rect x="-30" y="-72" width="60" height="40" rx="10" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/>'
            f'<path d="M-48,-26 L48,-26 C48,4 30,20 18,22 L20,56 L-20,56 L-18,22 C-30,20 -48,4 -48,-26Z" fill="{WHITE}" stroke="{GREY}" stroke-width="4" stroke-linejoin="round"/>'
            f'<ellipse cx="0" cy="-22" rx="40" ry="9" fill="{w}"/></g>')

def ear(cx=100, cy=100, s=1.0):
    return (f'<g transform="translate({cx},{cy}) scale({s})"><path d="M-10,-62 C30,-66 50,-36 44,-6 C40,16 20,20 16,38 C12,60 -14,66 -22,46 C-26,34 -14,30 -14,20 C-14,4 -38,-4 -38,-26 C-38,-48 -26,-60 -10,-62Z" fill="{SKIN}"/>'
            f'<path d="M-18,-26 C-18,-46 18,-50 22,-20 C24,-4 6,4 4,20" fill="none" stroke="{SKIN_D}" stroke-width="9" stroke-linecap="round"/></g>')

def eye(cx=100, cy=100, s=1.0, red=False, closed=False):
    white = "#FDE2E2" if red else WHITE
    out = (f'<g transform="translate({cx},{cy}) scale({s})"><path d="M-70,0 C-40,-44 40,-44 70,0 C40,44 -40,44 -70,0Z" fill="{white}" stroke="{SKIN_D}" stroke-width="10"/>')
    if closed:
        out += f'<path d="M-70,0 C-40,-44 40,-44 70,0 C40,10 -40,10 -70,0Z" fill="{SKIN}"/>'
    else:
        out += f'<circle cx="0" cy="0" r="26" fill="#6B4A2E"/><circle cx="0" cy="0" r="12" fill="{INK}"/><circle cx="8" cy="-9" r="6" fill="{WHITE}"/>'
    if red:
        out += (f'<path d="M-58,-4 L-38,-2 L-44,6 M58,4 L40,2 L46,-6 M-50,10 L-36,6" fill="none" stroke="{RED}" stroke-width="3" stroke-linecap="round"/>')
    return out + '</g>'

def nose(cx=100, cy=100, s=1.0):
    return (f'<g transform="translate({cx},{cy}) scale({s})"><path d="M-6,-70 C-6,-30 -30,0 -34,18 C-38,36 -16,44 0,36 C16,44 38,36 34,18 C30,0 6,-30 6,-70Z" fill="{SKIN}"/>'
            f'<ellipse cx="-14" cy="26" rx="8" ry="5" fill="{SKIN_D}"/><ellipse cx="14" cy="26" rx="8" ry="5" fill="{SKIN_D}"/></g>')

def mouth(cx=100, cy=100, s=1.0, teeth=True, open_=True):
    out = f'<g transform="translate({cx},{cy}) scale({s})"><path d="M-66,0 C-40,-34 40,-34 66,0 C40,46 -40,46 -66,0Z" fill="#E07A7A"/>'
    if open_:
        out += '<path d="M-50,0 C-30,-18 30,-18 50,0 C30,28 -30,28 -50,0Z" fill="#6B2424"/>'
        if teeth:
            out += f'<path d="M-40,-8 C-20,-16 20,-16 40,-8 L38,0 L-38,0Z" fill="{WHITE}"/>'
            out += '<path d="M-24,12 C-8,20 8,20 24,12 C10,26 -10,26 -24,12Z" fill="#E07A7A"/>'
    return out + '</g>'

def tooth(cx=100, cy=100, s=1.0):
    return (f'<g transform="translate({cx},{cy}) scale({s})"><path d="M-46,-50 C-30,-64 -12,-56 0,-50 C12,-56 30,-64 46,-50 C60,-36 54,-10 46,6 C40,20 38,56 26,60 C14,64 12,30 0,26 C-12,30 -14,64 -26,60 C-38,56 -40,20 -46,6 C-54,-10 -60,-36 -46,-50Z" fill="{WHITE}" stroke="{GREY}" stroke-width="5" stroke-linejoin="round"/></g>')

def plate(cx=100, cy=112, crossed=False):
    out = (f'<ellipse cx="{cx}" cy="{cy}" rx="60" ry="46" fill="{WHITE}" stroke="{GREY_L}" stroke-width="6"/><ellipse cx="{cx}" cy="{cy}" rx="38" ry="28" fill="none" stroke="{GREY_L}" stroke-width="4"/>'
           f'<path d="M{cx-78},{cy-40} L{cx-78},{cy+40} M{cx-86},{cy-40} L{cx-86},{cy-14} M{cx-70},{cy-40} L{cx-70},{cy-14}" stroke="{GREY}" stroke-width="5" stroke-linecap="round"/>'
           f'<path d="M{cx+78},{cy-42} C{cx+92},{cy-20} {cx+88},{cy} {cx+78},{cy-2} L{cx+78},{cy+40}" stroke="{GREY}" stroke-width="5" stroke-linecap="round" fill="{GREY}"/>')
    return out

def lungs(cx=100, cy=104):
    return (f'<g transform="translate({cx},{cy})"><path d="M-8,-52 L-8,-14 M8,-52 L8,-14" stroke="#E8A0A0" stroke-width="10" stroke-linecap="round"/>'
            f'<path d="M-12,-20 C-30,-40 -62,-30 -64,10 C-66,44 -46,60 -26,54 C-14,50 -12,30 -12,-20Z" fill="#F4A6A6"/>'
            f'<path d="M12,-20 C30,-40 62,-30 64,10 C66,44 46,60 26,54 C14,50 12,30 12,-20Z" fill="#F4A6A6"/></g>')

def heart(cx=100, cy=100, s=1.0, color=RED):
    return f'<path transform="translate({cx},{cy}) scale({s})" d="M0,40 C-60,4 -54,-44 -20,-44 C-8,-44 0,-34 0,-26 C0,-34 8,-44 20,-44 C54,-44 60,4 0,40Z" fill="{color}"/>'

def meter(cx=100, cy=100, color=BLUE):
    return (f'<rect x="{cx-38}" y="{cy-56}" width="76" height="112" rx="18" fill="#4B5563"/><rect x="{cx-26}" y="{cy-42}" width="52" height="36" rx="6" fill="#D1FAE5"/>'
            f'<circle cx="{cx}" cy="{cy+24}" r="10" fill="{GREY}"/>')

def belly(cx=100, cy=112):
    return (f'<path d="M36,20 C30,60 26,120 40,170 L160,170 C174,120 170,60 164,20Z" fill="{SWEATER}"/>'
            f'<path d="M44,120 C44,90 70,78 100,78 C130,78 156,90 156,120 C156,150 130,166 100,166 C70,166 44,150 44,120Z" fill="{SKIN}"/>'
            f'<circle cx="100" cy="126" r="4" fill="{SKIN_D}"/>'
            f'<path d="M36,170 L164,170 L164,200 L36,200Z" fill="{PANTS}"/>')


# ───────────── the 70 icons ─────────────

TOP = {
 "headache": head("squint", "grimace") + bolts(100, 62, 64, 1.1),
 "dizzy": head("spiral", "wavy") + f'<ellipse cx="100" cy="30" rx="54" ry="13" fill="none" stroke="{PURPLE}" stroke-width="5"/>' + star(56, 30) + star(144, 26) + star(100, 18, 6),
 "fever": head("half", "flat", tint="red") + f'<path d="M112,112 L150,128" stroke="{GREY}" stroke-width="8" stroke-linecap="round"/><circle cx="152" cy="129" r="7" fill="{RED}"/>' + waves(70, 18, RED, 2, 26, 9) + waves(110, 18, RED, 2, 26, 9),
 "tired": head("half", "open") + f'<g fill="none" stroke="{PURPLE}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"><path d="M140,30 h14 l-14,14 h14"/><path d="M160,6 h10 l-10,10 h10"/></g>',
 "fainted": f'<g transform="rotate(-22 100 110)">' + head("shut", "flat") + '</g>' + star(52, 28) + star(84, 14, 6) + star(118, 22),
 "confusion": head("sad", "wavy") + qmark(148, 34, 1.3),
 "memory": head("neutral", "flat") + cloud(150, 38, 46, GREY_L) + f'<circle cx="120" cy="66" r="6" fill="{GREY_L}"/><circle cx="110" cy="78" r="4" fill="{GREY_L}"/>' + f'<path d="M136,34 h28" stroke="{GREY}" stroke-width="4" stroke-dasharray="4 6" stroke-linecap="round"/>',
 "earache": ear(100, 104, 1.25) + bolt(48, 50, 1.2, -25) + bolt(160, 110, 1.2, 25) + glow(96, 100, 30, RED, 0.35),
 "hearing_loss": ear(86, 104, 1.15) + arcs(118, 100, GREY_L, 3, 22, 14, -40, 40, 5) + f'<path d="M122,56 L176,146" stroke="{RED}" stroke-width="7" stroke-linecap="round"/>',
 "eye_pain": eye(100, 110, 1.05) + bolts(100, 46, 50, 1.1),
 "blurred_vision": eye(100, 104, 1.05) + f'<g opacity="0.85">' + waves(50, 150, BLUE_L, 2, 100, 14, 6) + '</g>' + f'<rect x="28" y="60" width="144" height="90" rx="44" fill="{WHITE}" opacity="0.35"/>',
 "red_eye": eye(100, 104, 1.05, red=True),
 "runny_nose": nose(100, 96, 1.1) + drop(84, 156, 0.9) + drop(118, 172, 1.1),
 "blocked_nose": nose(100, 100, 1.1) + nosign(150, 148, 18),
 "sneeze": head("shut", "open", shoulders=True) + dots(150, 100, [(0, 0), (12, -10), (20, 8), (30, -4), (36, 14), (10, 16), (26, -20)], 3.5, BLUE),
 "nosebleed": nose(100, 92, 1.1) + drop(84, 150, 1.0, RED) + drop(112, 170, 1.2, RED),
 "toothache": tooth(100, 104, 1.1) + bolts(100, 40, 60, 1.1) + glow(100, 90, 26, RED, 0.3),
 "mouth_ulcer": mouth(100, 104, 1.25, teeth=False, open_=False) + f'<circle cx="80" cy="120" r="10" fill="{WHITE}" stroke="{RED}" stroke-width="4"/>',
 "dry_mouth": mouth(100, 110, 1.2, teeth=False, open_=False) + f'<path d="M58,104 l8,-6 l6,6 M120,100 l8,6 l8,-6" stroke="#B85454" stroke-width="3" fill="none" stroke-linecap="round"/>' + f'<path d="M100,32 C106,42 110,48 110,54 A10,10 0 0 1 90,54 C90,48 94,42 100,32Z" fill="none" stroke="{BLUE}" stroke-width="4" stroke-dasharray="5 5"/>',
 "sore_throat": head("squint", "frown") + glow(100, 150, 24, RED, 0.45) + bolt(66, 150, 1, -20) + bolt(134, 150, 1, 20),
 "cough": head("shut", "open") + cloud(160, 118, 34, GREY_L) + cloud(176, 88, 22, GREY_L),
 "swallowing": head("neutral", "frown") + f'<circle cx="100" cy="146" r="11" fill="#C08457"/>' + arrow(100, 184, up=False, color=GREY, s=0.6) + nosign(150, 150, 12),
 "low_mood": head("sad", "frown") + cloud(100, 20, 56, "#9CA3AF") + f'<g stroke="{BLUE}" stroke-width="4" stroke-linecap="round"><path d="M80,44 l-4,10"/><path d="M100,44 l-4,10"/><path d="M120,44 l-4,10"/></g>',
 "anxious": head("wide", "wavy") + drop(142, 58, 0.9) + f'<path d="M60,24 c8,-12 16,12 24,0 s16,12 24,0 s16,12 24,0" fill="none" stroke="{PURPLE}" stroke-width="5" stroke-linecap="round"/>',
 "cant_sleep": f'<rect x="10" y="118" width="180" height="70" rx="18" fill="{GREY_L}"/><rect x="18" y="104" width="88" height="40" rx="18" fill="{WHITE}"/>' + head("wide", "flat", cx=70, cy=96, r=36, shoulders=False, cheeks=False) + moon(152, 42, 20),
}

MID = {
 "chest_pain": torso("squint", "grimace") + glow(100, 116, 34, RED, 0.5) + f'<path d="M100,94 L107,110 L124,110 L111,120 L116,137 L100,127 L84,137 L89,120 L76,110 L93,110Z" fill="{RED}"/>',
 "breathless": head("wide", "open") + f'<g fill="none" stroke="{BLUE}" stroke-width="5" stroke-linecap="round"><path d="M114,112 C134,108 150,112 174,104"/><path d="M114,120 C136,122 156,128 178,124"/></g>' + drop(142, 60, 0.9),
 "palpitations": heart(100, 104, 1.4) + f'<g fill="none" stroke="{RED}" stroke-width="5" stroke-linecap="round"><path d="M20,100 L40,100 L50,82 L60,118 L68,100 L80,100"/><path d="M120,100 L132,100 L142,82 L152,118 L160,100 L180,100"/></g>',
 "acidity": torso("neutral", "frown") + flame(100, 116, 1.3),
 "nausea": head("half", "wavy", tint="green") + waves(76, 164, GREEN, 2, 48, 10, 5),
 "vomiting": head("shut", "open", tint="green", shoulders=False, cy=70) + f'<path d="M92,96 C84,120 96,132 90,150 L110,150 C104,132 116,120 108,96Z" fill="{GREEN}"/>' + f'<path d="M50,146 L150,146 C146,178 128,190 100,190 C72,190 54,178 50,146Z" fill="{GREY_L}" stroke="{GREY}" stroke-width="4"/>',
 "stomach_pain": torso("squint", "grimace", belly_hands=True) + glow(100, 146, 30, RED, 0.5) + bolts(100, 122, 58, 1.0),
 "back_pain": torso(back=True) + glow(100, 164, 30, RED, 0.5) + bolts(100, 160, 40, 1.0),
 "neck_pain": head("squint", "frown") + glow(100, 136, 22, RED, 0.5) + bolt(62, 140, 1, -25) + bolt(138, 140, 1, 25),
 "shoulder_pain": torso("squint", "frown") + glow(58, 96, 24, RED, 0.6) + bolt(30, 80, 1.1, -20) + bolt(70, 126, 1.1, 20),
 "arm_pain": forearm() + glow(96, 108, 26, RED, 0.55) + bolt(80, 70, 1.1, -20) + bolt(118, 64, 1.1, 20),
 "joint_swelling": hand() + dots(100, 104, [(-26, -40), (-8, -48), (10, -44), (27, -32)], 9, "#F28B82"),
 "tremor": hand() + f'<g fill="none" stroke="{PURPLE}" stroke-width="5" stroke-linecap="round"><path d="M40,60 q-10,20 0,40"/><path d="M26,54 q-12,26 0,52"/><path d="M160,60 q10,20 0,40"/><path d="M174,54 q12,26 0,52"/></g>',
 "numbness": hand(fill=SKIN_L) + f'<g transform="translate(100,110)" fill="none" stroke="{GREY}" stroke-width="4" stroke-dasharray="6 6"><rect x="-34" y="-58" width="15" height="62" rx="7.5"/><rect x="-16" y="-70" width="15" height="72" rx="7.5"/><rect x="2" y="-66" width="15" height="68" rx="7.5"/></g>',
 "rash": forearm() + dots(0, 0, [(80, 100), (96, 92), (110, 104), (124, 84), (100, 112), (138, 90), (88, 116), (116, 78)], 5, RED),
 "itching": forearm() + f'<g stroke="{RED}" stroke-width="4" stroke-linecap="round"><path d="M84,84 l10,26"/><path d="M100,78 l10,26"/><path d="M116,72 l10,26"/></g>',
 "bruise": forearm() + f'<ellipse cx="104" cy="96" rx="22" ry="15" fill="{BRUISE}" opacity="0.85" transform="rotate(-26 104 96)"/><ellipse cx="104" cy="96" rx="11" ry="7" fill="#6D4C9E" transform="rotate(-26 104 96)"/>',
 "cut": f'<rect x="72" y="20" width="56" height="170" rx="28" fill="{SKIN}"/><path d="M78,24 C82,10 118,10 122,24" fill="#F7E1D0"/>' + plaster(100, 100, -25, 76, 30) + drop(140, 140, 0.8, RED),
 "burn": hand() + glow(100, 118, 28, RED, 0.6) + flame(150, 56, 1.1),
 "chills": torso("shut", "grimace") + f'<g fill="none" stroke="{BLUE}" stroke-width="5" stroke-linecap="round"><path d="M16,90 l-8,8 l8,8"/><path d="M16,120 l-8,8 l8,8"/><path d="M184,90 l8,8 l-8,8"/><path d="M184,120 l8,8 l-8,8"/></g>' + f'<g stroke="{BLUE}" stroke-width="4" stroke-linecap="round"><path d="M166,10 v30 M153,17 l26,16 M153,33 l26,-16"/></g>',
 "weakness": head("half", "frown") + f'<rect x="136" y="28" width="46" height="24" rx="5" fill="{WHITE}" stroke="{INK}" stroke-width="4"/><rect x="182" y="35" width="6" height="10" rx="2" fill="{INK}"/><rect x="141" y="33" width="10" height="14" rx="2" fill="{RED}"/>',
 "no_appetite": plate() + f'<path d="M60,70 L140,154" stroke="{RED}" stroke-width="8" stroke-linecap="round"/>',
 "high_bp": f'<circle cx="92" cy="104" r="62" fill="{WHITE}" stroke="{GREY}" stroke-width="8"/><path d="M92,104 L128,70" stroke="{INK}" stroke-width="6" stroke-linecap="round"/><circle cx="92" cy="104" r="8" fill="{INK}"/>' + f'<path d="M50,78 A50,50 0 0 1 134,62" fill="none" stroke="{RED_L}" stroke-width="10"/>' + arrow(166, 70, True, RED, 1.2),
 "low_sugar": meter(90, 104) + drop(90, 72, 0.9, RED) + arrow(160, 120, False, BLUE, 1.2),
 "side_effect": f'<g transform="rotate(-35 90 110)"><rect x="40" y="88" width="100" height="44" rx="22" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/><path d="M62,88 L90,88 L90,132 L62,132 A22,22 0 0 1 62,88Z" fill="{RED}"/></g>' + f'<path d="M150,24 L186,86 L114,86Z" fill="{YELLOW}" stroke="#B7791F" stroke-width="4" stroke-linejoin="round"/><path d="M150,44 L150,66" stroke="{INK}" stroke-width="6" stroke-linecap="round"/><circle cx="150" cy="76" r="3.5" fill="{INK}"/>',
}

LOW = {
 "loose_motions": toilet() + drop(150, 38, 1.0) + drop(172, 64, 0.8) + f'<g stroke="{GREY}" stroke-width="5" stroke-linecap="round"><path d="M22,50 h30"/><path d="M14,70 h30"/></g>',
 "constipation": toilet() + nosign(156, 46, 20),
 "bloating": torso("neutral", "frown", round_belly=True, belly_hands=True) + f'<g fill="none" stroke="{GREY}" stroke-width="5" stroke-linecap="round"><path d="M20,60 a12,12 0 1 1 12,12"/><path d="M168,50 a10,10 0 1 1 10,10"/></g>',
 "burning_urine": toilet(water="#FDE68A") + flame(160, 50, 1.3),
 "frequent_urine": toilet(water="#FDE68A") + f'<g fill="none" stroke="{BLUE}" stroke-width="6" stroke-linecap="round"><path d="M142,30 A26,26 0 1 1 176,64"/><path d="M142,16 L142,32 L158,32"/></g>',
 "leaking_urine": f'<path d="M40,50 L160,50 L150,110 C130,120 116,140 100,150 C84,140 70,120 50,110Z" fill="{WHITE}" stroke="{GREY}" stroke-width="5" stroke-linejoin="round"/>' + drop(84, 168, 1.0) + drop(112, 182, 0.8) + drop(132, 160, 0.7),
 "blood_urine": f'<path d="M62,40 L138,40 L128,176 C126,186 74,186 72,176Z" fill="{WHITE}" stroke="{GREY}" stroke-width="5"/><path d="M68,100 L132,100 L128,176 C126,186 74,186 72,176Z" fill="#F4B183"/>' + drop(150, 60, 1.3, RED),
 "blood_stool": toilet() + drop(158, 44, 1.3, RED),
 "knee_pain": leg(bent=True) + glow(100, 98, 30, RED, 0.6) + bolt(70, 62, 1.1, -20) + bolt(134, 60, 1.1, 25),
 "hip_pain": figure(100, 104, 1.45) + glow(78, 128, 22, RED, 0.6) + bolt(52, 116, 1.0, -20) + bolt(54, 150, 1.0, 20),
 "leg_pain": leg() + glow(94, 136, 26, RED, 0.6) + bolt(56, 126, 1.1, -20) + bolt(134, 126, 1.1, 20),
 "cramps": leg() + f'<path d="M84,128 c10,-8 20,8 10,14 c-10,6 -16,-8 -4,-12 c10,-4 14,10 6,14" fill="none" stroke="{RED}" stroke-width="5" stroke-linecap="round"/>' + bolt(140, 124, 1, 20),
 "swollen_ankles": foot(96, 124, 1.05, swollen=True) + f'<path d="M44,70 C30,90 30,140 40,168" fill="none" stroke="{RED_L}" stroke-width="6" stroke-linecap="round"/><path d="M168,140 C182,150 182,170 168,180" fill="none" stroke="{RED_L}" stroke-width="6" stroke-linecap="round"/>',
 "foot_pain": foot(96, 118, 1.05) + glow(110, 150, 26, RED, 0.55) + bolt(90, 190, 1, -20) + bolt(146, 186, 1, 20),
 "foot_numb": foot(96, 118, 1.05, dotted=True),
 "fall": figure(104, 110, 1.05, 62, "fall") + f'<path d="M14,186 L186,186" stroke="{GREY}" stroke-width="6" stroke-linecap="round"/>' + f'<g fill="none" stroke="{GREY}" stroke-width="4" stroke-linecap="round"><path d="M40,60 q-12,-6 -18,-18"/><path d="M58,44 q-10,-10 -12,-22"/></g>',
 "near_fall": figure(92, 112, 1.0, 16, "walk") + f'<path d="M136,94 L150,188" stroke="#8D6E4F" stroke-width="7" stroke-linecap="round"/><path d="M136,94 L124,90" stroke="#8D6E4F" stroke-width="7" stroke-linecap="round"/>' + f'<path d="M20,188 L180,188" stroke="{GREY}" stroke-width="5" stroke-linecap="round"/>' + f'<path d="M70,176 l-16,-4" stroke="{RED}" stroke-width="5" stroke-linecap="round"/>',
 "trouble_walking": figure(84, 106, 1.0, 0, "walk") + f'<g fill="none" stroke="{GREY}" stroke-width="7" stroke-linecap="round"><path d="M112,86 L150,86"/><path d="M118,86 L112,186"/><path d="M146,86 L152,186"/><path d="M114,130 L150,130"/></g>',
 "sprain": foot(96, 120, 1.05) + f'<g transform="translate(96,120) scale(1.05)"><rect x="-48" y="-60" width="44" height="54" rx="8" fill="{WHITE}" stroke="{GREY_L}" stroke-width="3"/><path d="M-48,-44 L-4,-36 M-48,-28 L-4,-20" stroke="{GREY_L}" stroke-width="3"/></g>',
 "period_pain": torso("squint", "frown") + f'<rect x="66" y="136" width="68" height="50" rx="18" fill="#F87171"/><rect x="88" y="126" width="24" height="14" rx="4" fill="#DC2626"/>',
}


# ───────────── answer icons: depth, feel, how bad ─────────────

def layers(target):
    """Cross-section of the body wall; the layer where it hurts is bright, the rest faded, with a pin."""
    rows = [("skin", 40, 16, SKIN_D), ("under", 56, 26, "#FBE3B5"), ("muscle", 82, 44, "#E88A8A"), ("deep", 126, 60, "#F7C4C4")]
    out = '<clipPath id="lc"><rect x="20" y="40" width="160" height="146" rx="18"/></clipPath><g clip-path="url(#lc)">'
    for name, y, h, col in rows:
        op = 1 if name == target else 0.28
        out += f'<rect x="20" y="{y}" width="160" height="{h}" fill="{col}" opacity="{op}"/>'
        if name == "muscle":
            out += "".join(f'<path d="M{24+i*20},{y+6} q10,{h/2-6} 0,{h-12}" stroke="#C85D5D" stroke-width="3" fill="none" opacity="{op}"/>' for i in range(8))
        if name == "deep":
            bone_op = 1 if target == "bone" else 0.28
            org_op = 1 if target == "deep" else 0.28
            out += f'<ellipse cx="72" cy="{y+32}" rx="34" ry="20" fill="#E57373" opacity="{org_op}"/>'
            out += f'<g opacity="{bone_op}"><rect x="112" y="{y+24}" width="56" height="16" rx="8" fill="{WHITE}" stroke="{GREY}" stroke-width="3"/>' \
                   f'<circle cx="114" cy="{y+24}" r="9" fill="{WHITE}" stroke="{GREY}" stroke-width="3"/><circle cx="114" cy="{y+40}" r="9" fill="{WHITE}" stroke="{GREY}" stroke-width="3"/>' \
                   f'<circle cx="166" cy="{y+24}" r="9" fill="{WHITE}" stroke="{GREY}" stroke-width="3"/><circle cx="166" cy="{y+40}" r="9" fill="{WHITE}" stroke="{GREY}" stroke-width="3"/></g>'
    out += '</g><rect x="20" y="40" width="160" height="146" rx="18" fill="none" stroke="#D9D4CC" stroke-width="3"/>'
    pin_y = {"skin": 48, "under": 69, "muscle": 104, "deep": 158, "bone": 158}[target]
    pin_x = 140 if target == "bone" else (72 if target == "deep" else 100)
    out += f'<path d="M{pin_x},{pin_y} C{pin_x-16},{pin_y-20} {pin_x-16},{pin_y-40} {pin_x},{pin_y-40} C{pin_x+16},{pin_y-40} {pin_x+16},{pin_y-20} {pin_x},{pin_y}Z" fill="{RED}" transform="translate(0,-4)"/><circle cx="{pin_x}" cy="{pin_y-30}" r="6" fill="{WHITE}"/>'
    return out

def face(level):
    cols = ["#22B573", "#34C77B", "#A3CF3A", "#F5B83D", "#F97316", "#EF4444"]
    c = cols[level]
    out = f'<circle cx="100" cy="104" r="80" fill="{c}"/>'
    ey = 88
    if level == 0:
        out += f'<path d="M62,{ey+4} Q72,{ey-10} 82,{ey+4} M118,{ey+4} Q128,{ey-10} 138,{ey+4}" stroke="{INK}" stroke-width="7" fill="none" stroke-linecap="round"/>'
    elif level <= 2:
        out += f'<circle cx="72" cy="{ey}" r="9" fill="{INK}"/><circle cx="128" cy="{ey}" r="9" fill="{INK}"/>'
    elif level == 3:
        out += f'<circle cx="72" cy="{ey}" r="9" fill="{INK}"/><circle cx="128" cy="{ey}" r="9" fill="{INK}"/><path d="M58,68 L84,74 M142,68 L116,74" stroke="{INK}" stroke-width="6" stroke-linecap="round"/>'
    else:
        out += f'<path d="M58,{ey-10} L82,{ey} L58,{ey+10} M142,{ey-10} L118,{ey} L142,{ey+10}" stroke="{INK}" stroke-width="7" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
    mouth = {0: "M60,120 Q100,172 140,120", 1: "M66,126 Q100,160 134,126", 2: "M72,134 Q100,146 128,134", 3: "M72,140 L128,140", 4: "M70,146 Q100,120 130,146", 5: "M66,152 Q100,112 134,152"}[level]
    out += f'<path d="{mouth}" stroke="{INK}" stroke-width="8" fill="none" stroke-linecap="round"/>'
    if level == 5:
        out += drop(62, 124, 1.1, BLUE) + drop(140, 124, 1.1, BLUE)
    return out

ANSWERS_MID = {
    "depth_skin": layers("skin"),
    "depth_under": layers("under"),
    "depth_muscle": layers("muscle"),
    "depth_deep": layers("deep"),
    "depth_bone": layers("bone"),
    "feel_sharp": f'<circle cx="100" cy="100" r="80" fill="{RED_L}" opacity="0.35"/>' + f'<path d="M86,26 L122,86 L94,92 L120,172" fill="none" stroke="{RED}" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/>',
    "feel_dull": f'<circle cx="100" cy="100" r="78" fill="{RED}" opacity="0.12"/><circle cx="100" cy="100" r="56" fill="{RED}" opacity="0.2"/><circle cx="100" cy="100" r="32" fill="{RED}" opacity="0.35"/>',
    "feel_burning": f'<circle cx="100" cy="100" r="80" fill="#FFEDD5"/>' + flame(100, 108, 4.2),
    "feel_throbbing": heart(100, 108, 1.1) + arcs(100, 100, RED, 3, 62, 14, -40, 40, 6) + arcs(100, 100, RED, 3, 62, 14, 140, 220, 6),
    "feel_cramping": f'<circle cx="100" cy="100" r="80" fill="{RED_L}" opacity="0.3"/><path d="M100,100 m-8,0 a8,8 0 1 1 16,0 a16,16 0 1 1 -32,0 a24,24 0 1 1 48,0 a32,32 0 1 1 -64,0 a40,40 0 1 1 80,0" fill="none" stroke="{RED}" stroke-width="9" stroke-linecap="round"/>',
    "feel_pressing": f'<rect x="30" y="130" width="140" height="40" rx="12" fill="#E88A8A"/><rect x="58" y="46" width="84" height="60" rx="12" fill="#6B7280"/>' + arrow(100, 116, up=False, color=RED, s=1.1) + arrow(40, 80, up=False, color=RED, s=0.8) + arrow(160, 80, up=False, color=RED, s=0.8),
    "feel_stabbing": f'<rect x="30" y="130" width="140" height="40" rx="12" fill="#E88A8A"/>' + f'<g transform="rotate(35 100 90)"><rect x="92" y="10" width="16" height="60" rx="5" fill="#8D6E4F"/><path d="M90,70 L110,70 L100,130Z" fill="#CBD5E1" stroke="{GREY}" stroke-width="3"/></g>' + glow(120, 140, 22, RED, 0.8),
    "feel_tingling": "".join(star(100 + 64 * math.cos(math.radians(a)), 100 + 64 * math.sin(math.radians(a)), 10, PURPLE) for a in range(0, 360, 45)) + dots(100, 100, [(0, 0), (-20, 14), (22, -12), (14, 22), (-18, -20)], 6, PURPLE),
}
ANSWERS_TOP = {f"face_{i}": face(i) for i in range(0, 6)}
def burn(kind):
    base = f'<path d="M20,70 C40,50 160,50 180,70 L180,160 C160,176 40,176 20,160Z" fill="{SKIN}"/>'
    if kind == 1:
        return base + f'<ellipse cx="100" cy="112" rx="56" ry="32" fill="#F28B82" opacity="0.85"/>'
    if kind == 2:
        return base + f'<ellipse cx="100" cy="112" rx="58" ry="34" fill="#F28B82" opacity="0.85"/>' +             "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFF7E6" stroke="#E0A060" stroke-width="3"/>' for x, y, r in [(80, 104, 14), (112, 118, 18), (126, 96, 10)])
    return base + f'<path d="M44,112 C40,84 70,78 92,86 C110,72 150,80 156,104 C164,126 140,146 112,140 C90,152 50,142 44,112Z" fill="#F28B82" opacity="0.7"/>' +         f'<path d="M62,112 C60,94 80,92 96,98 C110,90 136,96 138,110 C142,126 122,134 104,130 C86,138 64,130 62,112Z" fill="#EFE8DE"/>' +         f'<path d="M84,110 C88,102 100,104 102,110 C110,106 120,112 116,120 C110,126 98,122 94,124 C86,124 80,118 84,110Z" fill="#4A3A30"/>' +         dots(0, 0, [(76, 104), (126, 104), (112, 128)], 3.5, "#6B5A4E")

def palm(size):
    hand_ = hand(100, 112, 0.95)
    spot = {1: (100, 116, 8), 2: (100, 112, 30), 3: (100, 110, 52)}[size]
    extra = '' if size < 3 else f'<circle cx="100" cy="110" r="70" fill="#F28B82" opacity="0.35"/>'
    return extra + hand_ + f'<circle cx="{spot[0]}" cy="{spot[1]}" r="{spot[2]}" fill="#EF4444" opacity="0.55"/>'

ANSWERS_MID.update({"burn_1": burn(1), "burn_2": burn(2), "burn_3": burn(3), "size_1": palm(1), "size_2": palm(2), "size_3": palm(3)})
MID.update(ANSWERS_MID)
TOP.update(ANSWERS_TOP)

def _extra():
    # imported late: extra_sprites uses the helpers above
    from extra_sprites import EXTRA
    return EXTRA

SHEETS = [("top", TOP), ("mid", MID), ("low", LOW)]
COLS = 5


def build():
    os.makedirs(OUT, exist_ok=True)
    meta = {}
    for name, icons in SHEETS:
        ids = list(icons)
        rows = math.ceil(len(ids) / COLS)
        step = CELL + 2 * PAD
        w, h = COLS * step, rows * step
        body = ""
        for i, k in enumerate(ids):
            x, y = (i % COLS) * step + PAD, (i // COLS) * step + PAD
            body += f'<g transform="translate({x},{y})"><g clip-path="url(#c)">{icons[k]}</g></g>'
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
               f'<defs><clipPath id="c"><rect x="0" y="0" width="{CELL}" height="{CELL}"/></clipPath></defs>{body}</svg>')
        with open(os.path.join(HERE, f"{name}.svg"), "w", encoding="utf-8") as f:
            f.write(svg)
        png = resvg_py.svg_to_bytes(svg_string=svg, width=w * 2, height=h * 2)
        with open(os.path.join(OUT, f"{name}.png"), "wb") as f:
            f.write(bytes(png))
        meta[name] = {"cols": COLS, "cell": step * 2, "pad": PAD * 2, "ids": ids}
        print(name, len(ids), f"{w*2}x{h*2}")
    with open(os.path.join(OUT, "sprites.json"), "w") as f:
        json.dump(meta, f, indent=1)


if __name__ == "__main__":
    SHEETS.append(("extra", _extra()))
    build()
