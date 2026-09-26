"""
Body chart for pinning where a problem is: front and back, in the same coordinate space the app uses
(100 wide x 170 tall). Soft, anatomical, neutral: a medical body chart, not a cartoon.
Run: python body.py -> app/src/main/assets/sprites/body_front.png, body_back.png
"""
import os
import resvg_py

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sprites")
SCALE = 20          # px per body unit -> 2000 x 3400 px

SKIN, SHADE, EDGE, LINE = "#F3DDCB", "#E8C9B2", "#C99A7A", "#D8B49A"
HAIR = "#4A3428"

# Half outline (the figure's right side = left of the image), top of head to crotch, in body units.
# Each entry: ("M"|"L"|"C", points...)
HALF = [
    ("M", (50, 6.5)),
    ("C", (43.5, 6.5), (39.2, 11.5), (39.4, 19)),          # skull
    ("C", (39.5, 24.5), (41.5, 29), (45.2, 31.4)),          # cheek to jaw
    ("C", (45.6, 33.5), (45.6, 35.5), (45.2, 37.2)),        # neck
    ("C", (41, 38.6), (36, 39.6), (32.5, 41.2)),            # trapezius
    ("C", (28.6, 43), (26.8, 46.5), (26.2, 51)),            # shoulder
    ("C", (25.4, 57), (24.6, 62), (23.8, 68)),              # upper arm, outer
    ("C", (23.2, 72.5), (22.2, 78), (21.2, 84.5)),          # forearm, outer
    ("C", (20.8, 87.5), (19.2, 90), (18.8, 93.5)),          # wrist to hand
    ("C", (18.4, 97), (19.6, 100.5), (21.6, 101)),          # fingers
    ("C", (23.6, 101.3), (25.2, 98.8), (25.4, 95.5)),       # fingertips, inner
    ("C", (25.6, 92), (25.6, 89), (25.9, 86.6)),            # hand, inner
    ("C", (26.8, 80.5), (27.8, 75), (28.8, 69.6)),          # forearm, inner
    ("C", (29.6, 64), (30.4, 58.5), (31.2, 54)),            # upper arm, inner
    ("C", (31.6, 52.6), (32.4, 52), (33.2, 52.4)),          # armpit
    ("C", (33.6, 60), (34.6, 68), (35.4, 75.5)),            # ribs to waist
    ("C", (35.6, 80), (34.2, 85), (33.8, 90)),              # waist to hip
    ("C", (33.6, 97), (34.8, 106), (36.2, 114)),            # outer thigh
    ("C", (37, 119), (37.6, 123), (37.6, 127)),             # outer knee
    ("C", (37.6, 134), (39.2, 142), (40.4, 151)),           # calf
    ("C", (40.6, 154), (39.4, 157.5), (38.6, 159.5)),       # ankle to heel
    ("C", (38, 162), (40.5, 163.6), (44.2, 163.4)),         # sole
    ("C", (47.2, 163.2), (48.6, 161.8), (48, 159.4)),       # toes
    ("C", (47.4, 156.5), (47.2, 153.5), (47.4, 150.5)),     # inner ankle
    ("C", (47.6, 142), (47.4, 134), (47.8, 127)),           # inner calf
    ("C", (48, 122), (47.8, 118), (47.9, 113)),             # inner knee
    ("C", (48.1, 106), (48.6, 101), (50, 97.5)),            # inner thigh to crotch
]

def mirror(pt):
    return (100 - pt[0], pt[1])

def outline_path():
    d = []
    for cmd, *pts in HALF:
        d.append(cmd + " " + " ".join(f"{x:.2f},{y:.2f}" for x, y in pts))
    # the other half, reversed and mirrored
    rev = []
    segs = HALF[1:]
    start = HALF[-1][-1] if len(HALF[-1]) > 1 else HALF[-1][1]
    prev_end = [HALF[0][1]] + [s[-1] for s in segs]
    for i in range(len(segs) - 1, -1, -1):
        cmd, *pts = segs[i]
        c1, c2, end = pts
        target = prev_end[i]
        rev.append("C " + " ".join(f"{x:.2f},{y:.2f}" for x, y in (mirror(c2), mirror(c1), mirror(target))))
    return " ".join(d) + " " + " ".join(rev) + " Z"

def svg(back: bool) -> str:
    s = SCALE
    body = outline_path()
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{100*s}" height="{170*s}" viewBox="0 0 100 170">']
    parts.append('<defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="0">'
                 f'<stop offset="0" stop-color="{SHADE}"/><stop offset="0.28" stop-color="{SKIN}"/><stop offset="0.72" stop-color="{SKIN}"/><stop offset="1" stop-color="{SHADE}"/></linearGradient></defs>')
    parts.append(f'<path d="{body}" fill="url(#g)" stroke="{EDGE}" stroke-width="0.45" stroke-linejoin="round"/>')
    ln = f'fill="none" stroke="{LINE}" stroke-width="0.35" stroke-linecap="round"'
    if not back:
        # face, gently: eyes, nose, mouth, ears
        parts.append(f'<ellipse cx="45.6" cy="19.5" rx="1.1" ry="0.7" fill="#9C7560"/><ellipse cx="54.4" cy="19.5" rx="1.1" ry="0.7" fill="#9C7560"/>')
        parts.append(f'<path d="M50,20.5 L49.3,24.2 L50.7,24.4" {ln}/><path d="M47.6,27 Q50,28.2 52.4,27" fill="none" stroke="#C58D78" stroke-width="0.45" stroke-linecap="round"/>')
        parts.append(f'<path d="M39.6,17 q-1.8,2.6 0,5.6 M60.4,17 q1.8,2.6 0,5.6" {ln}/>')
        parts.append(f'<path d="M39.3,15.5 C40,8.5 45,5.8 50,5.8 C55,5.8 60,8.5 60.7,15.5 C57,11.5 53,10.6 50,10.8 C47,10.6 43,11.5 39.3,15.5Z" fill="{HAIR}"/>')
        # collarbones, chest, navel, groin line, knees
        parts.append(f'<path d="M36,42.6 Q42,42 48.6,43.6 M64,42.6 Q58,42 51.4,43.6" {ln}/>')
        parts.append(f'<path d="M38.5,56 Q43,60 48.5,57.5 M61.5,56 Q57,60 51.5,57.5" {ln}/>')
        parts.append(f'<ellipse cx="50" cy="79" rx="0.8" ry="1.1" fill="none" stroke="{EDGE}" stroke-width="0.35"/>')
        parts.append(f'<path d="M36.5,89 Q43,92 50,97.5 Q57,92 63.5,89" {ln}/>')
        parts.append(f'<path d="M40.8,125.5 q2.3,-2.8 4.6,0 M54.6,125.5 q2.3,-2.8 4.6,0" {ln}/>')
    else:
        parts.append(f'<path d="M39.4,19 C39.2,11 44,6.5 50,6.5 C56,6.5 60.8,11 60.6,19 C60.4,24 58.8,28.5 55,31 L45,31 C41.2,28.5 39.6,24 39.4,19Z" fill="{HAIR}"/>')
        parts.append(f'<path d="M50,38 L50,90" {ln}/>')
        parts.append(f'<path d="M38.5,48 Q42,55 46.5,52.5 M61.5,48 Q58,55 53.5,52.5" {ln}/>')
        parts.append(f'<path d="M36.5,80 Q43,78 50,80 Q57,78 63.5,80" {ln}/>')
        parts.append(f'<path d="M35,96 Q42,101 49.5,97 M65,96 Q58,101 50.5,97" {ln}/>')
        parts.append(f'<path d="M41,124 q2.6,1.8 5,0 M54,124 q2.6,1.8 5,0" {ln}/>')
    parts.append("</svg>")
    return "".join(parts)

def build():
    os.makedirs(OUT, exist_ok=True)
    for name, back in (("body_front", False), ("body_back", True)):
        s = svg(back)
        open(os.path.join(HERE, f"{name}.svg"), "w", encoding="utf-8").write(s)
        png = resvg_py.svg_to_bytes(svg_string=s, width=100 * SCALE, height=170 * SCALE)
        open(os.path.join(OUT, f"{name}.png"), "wb").write(bytes(png))
        print(name)

if __name__ == "__main__":
    build()
