"""
A picture of its own for every problem that used to borrow another's (so "Losing weight" is a weighing scale,
not the "No appetite" plate). Same style and helpers as sprites.py. Imported by sprites.py as the "extra" sheet.
"""
from sprites import (head, torso, forearm, hand, leg, foot, figure, toilet, ear, eye, nose, mouth, tooth, plate, lungs, heart,
                     meter, bolt, bolts, glow, drop, star, waves, arcs, cloud, qmark, moon, flame, arrow, nosign, plaster, dots,
                     SKIN, SKIN_D, SKIN_L, HAIR, SWEATER, PANTS, RED, RED_L, GREEN, GREEN_L, BLUE, BLUE_L, PURPLE, ORANGE, YELLOW,
                     INK, GREY, GREY_L, WHITE, BRUISE)

BROWN = "#8D6E4F"


# ───────────── a few new shapes ─────────────

def bed(y=130):
    return (f'<rect x="12" y="{y}" width="176" height="44" rx="14" fill="{GREY_L}"/><rect x="12" y="{y+38}" width="12" height="26" rx="4" fill="{GREY}"/>'
            f'<rect x="176" y="{y+38}" width="12" height="26" rx="4" fill="{GREY}"/><rect x="20" y="{y-16}" width="60" height="28" rx="12" fill="{WHITE}"/>')

def sleeper(y=130, expr="shut", mouth_="flat"):
    return bed(y) + head(expr, mouth_, cx=56, cy=y - 20, r=28, shoulders=False, cheeks=False) + \
        f'<rect x="80" y="{y-6}" width="104" height="30" rx="12" fill="{SWEATER}"/>'

def scale_(cx=100, cy=120, down=True):
    return (f'<rect x="{cx-70}" y="{cy-40}" width="140" height="84" rx="22" fill="{WHITE}" stroke="{GREY}" stroke-width="6"/>'
            f'<path d="M{cx-38},{cy-10} A40,40 0 0 1 {cx+38},{cy-10}" fill="none" stroke="{GREY_L}" stroke-width="10"/>'
            f'<path d="M{cx},{cy+2} L{cx-24 if down else cx+24},{cy-22}" stroke="{INK}" stroke-width="6" stroke-linecap="round"/>'
            f'<circle cx="{cx}" cy="{cy+2}" r="7" fill="{INK}"/>')

def glass(cx=100, cy=110, fill=0.6, color=BLUE_L):
    top, bot = cy - 56, cy + 56
    wl = bot - (bot - top) * fill
    return (f'<path d="M{cx-40},{top} L{cx+40},{top} L{cx+30},{bot} L{cx-30},{bot}Z" fill="{WHITE}" stroke="{GREY}" stroke-width="5" stroke-linejoin="round"/>'
            + (f'<path d="M{cx-40+(wl-top)*10/112},{wl} L{cx+40-(wl-top)*10/112},{wl} L{cx+30},{bot} L{cx-30},{bot}Z" fill="{color}"/>' if fill > 0 else ''))

def bubble(cx, cy, w=64, h=46, color=WHITE, stroke=GREY):
    return (f'<rect x="{cx-w/2}" y="{cy-h/2}" width="{w}" height="{h}" rx="16" fill="{color}" stroke="{stroke}" stroke-width="4"/>'
            f'<path d="M{cx-w/2+14},{cy+h/2-2} L{cx-w/2+6},{cy+h/2+14} L{cx-w/2+26},{cy+h/2-2}" fill="{color}" stroke="{stroke}" stroke-width="4" stroke-linejoin="round"/>')

def sun(cx, cy, r=16):
    rays = "".join(f'<path d="M{cx},{cy} m0,-{r+6} v-10" stroke="{YELLOW}" stroke-width="4" stroke-linecap="round" transform="rotate({a} {cx} {cy})"/>' for a in range(0, 360, 45))
    return rays + f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{YELLOW}"/>'

def clock(cx, cy, r=22):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{WHITE}" stroke="{INK}" stroke-width="4"/>'
            f'<path d="M{cx},{cy} v-{r*0.6} M{cx},{cy} h{r*0.45}" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>')

def zzz(x, y, color=PURPLE):
    return (f'<g fill="none" stroke="{color}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">'
            f'<path d="M{x},{y} h14 l-14,14 h14"/><path d="M{x+20},{y-24} h10 l-10,10 h10"/></g>')

def bone(cx=100, cy=100, rot=-30, broken=False):
    b = (f'<g transform="rotate({rot} {cx} {cy})">'
         f'<rect x="{cx-60}" y="{cy-10}" width="120" height="20" rx="10" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/>'
         f'<circle cx="{cx-64}" cy="{cy-10}" r="12" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/><circle cx="{cx-64}" cy="{cy+10}" r="12" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/>'
         f'<circle cx="{cx+64}" cy="{cy-10}" r="12" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/><circle cx="{cx+64}" cy="{cy+10}" r="12" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/>')
    if broken:
        b += f'<path d="M{cx-8},{cy-16} L{cx+4},{cy-2} L{cx-6},{cy+4} L{cx+8},{cy+18}" fill="none" stroke="{RED}" stroke-width="5" stroke-linejoin="round"/>'
    return b + '</g>'

def pad(cx=100, cy=110):
    return (f'<rect x="{cx-30}" y="{cy-66}" width="60" height="132" rx="30" fill="{WHITE}" stroke="{GREY}" stroke-width="5"/>'
            f'<rect x="{cx-16}" y="{cy-44}" width="32" height="88" rx="16" fill="{GREY_L}"/>')

def calendar(cx, cy, w=64):
    return (f'<rect x="{cx-w/2}" y="{cy-w/2}" width="{w}" height="{w}" rx="10" fill="{WHITE}" stroke="{GREY}" stroke-width="4"/>'
            f'<rect x="{cx-w/2}" y="{cy-w/2}" width="{w}" height="16" rx="8" fill="{RED}"/>'
            + "".join(f'<rect x="{cx-w/2+10+c*16}" y="{cy-w/2+24+r*14}" width="8" height="8" rx="2" fill="{GREY}"/>' for r in range(2) for c in range(3)))

def bee(cx, cy):
    return (f'<ellipse cx="{cx-8}" cy="{cy-14}" rx="12" ry="8" fill="{BLUE_L}" opacity="0.9"/><ellipse cx="{cx+8}" cy="{cy-14}" rx="12" ry="8" fill="{BLUE_L}" opacity="0.9"/>'
            f'<ellipse cx="{cx}" cy="{cy}" rx="20" ry="13" fill="{YELLOW}"/><path d="M{cx-6},{cy-12} v24 M{cx+6},{cy-12} v24" stroke="{INK}" stroke-width="5"/>'
            f'<path d="M{cx+20},{cy} l10,0" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>')

def oximeter(cx=100, cy=106, low=True):
    return (f'<rect x="{cx-50}" y="{cy-44}" width="100" height="88" rx="24" fill="#4B5563"/>'
            f'<rect x="{cx-36}" y="{cy-30}" width="72" height="40" rx="8" fill="#D1FAE5"/>'
            f'<text x="{cx}" y="{cy+2}" font-family="Arial" font-weight="bold" font-size="30" fill="{RED if low else INK}" text-anchor="middle">88</text>'
            f'<path d="M{cx-70},{cy+30} C{cx-40},{cy+10} {cx-30},{cy+44} {cx-50},{cy+56}" fill="{SKIN}" stroke="{SKIN_D}" stroke-width="3"/>')

def magnifier(cx, cy, r=22):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{WHITE}" fill-opacity="0.35" stroke="{INK}" stroke-width="6"/>'
            f'<path d="M{cx+r*0.7},{cy+r*0.7} l18,18" stroke="{INK}" stroke-width="8" stroke-linecap="round"/>')


EXTRA = {
 # ── general ──
 "unwell": head("half", "frown") + f'<path d="M140,40 L172,40" stroke="{GREY}" stroke-width="8" stroke-linecap="round"/>' + cloud(150, 30, 40, GREY_L) + drop(150, 62, 0.7, BLUE),
 "weight_loss": scale_(100, 128, down=True) + arrow(100, 40, up=False, color=BLUE, s=1.3),
 "night_sweats": sleeper(132) + moon(160, 40, 18) + drop(56, 70, 0.8) + drop(84, 84, 0.6),
 "thirsty": glass(88, 112, 0.15) + f'<path d="M150,50 C156,60 160,66 160,72 A10,10 0 0 1 140,72 C140,66 144,60 150,50Z" fill="none" stroke="{BLUE}" stroke-width="4" stroke-dasharray="5 5"/>' + f'<path d="M136,120 q12,-10 24,0" fill="none" stroke="#B85454" stroke-width="5" stroke-linecap="round"/>',
 "dehydrated": glass(92, 112, 0) + nosign(150, 60, 18) + drop(150, 60, 0.6),
 "hot_flush": head("half", "open", tint="red") + flame(52, 40, 0.9) + flame(150, 36, 0.9) + waves(84, 20, ORANGE, 2, 32, 9),
 "pale": head("half", "flat", tint=None) + f'<circle cx="100" cy="86" r="44" fill="{WHITE}" opacity="0.55"/>' + f'<rect x="136" y="28" width="46" height="24" rx="5" fill="{WHITE}" stroke="{INK}" stroke-width="4"/><rect x="141" y="33" width="10" height="14" rx="2" fill="{RED}"/>',
 "body_ache": figure(100, 104, 1.4) + bolt(60, 70, 1.0, -20) + bolt(146, 74, 1.0, 20) + bolt(70, 150, 1.0, -20) + bolt(136, 156, 1.0, 20),
 "sleep_too_much": sleeper(136) + zzz(120, 70) + clock(56, 50),
 "night_pain": sleeper(136, "squint", "grimace") + moon(160, 40, 18) + bolt(120, 90, 1.1, 20),
 "lonely": figure(100, 112, 1.2) + f'<g fill="{GREY_L}"><circle cx="30" cy="150" r="10"/><circle cx="170" cy="150" r="10"/></g>' + f'<path d="M20,190 L180,190" stroke="{GREY}" stroke-width="5" stroke-linecap="round"/>' + cloud(150, 30, 40, "#9CA3AF"),
 "hallucination": head("wide", "wavy") + cloud(152, 38, 46, "#E9D5FF") + eye(152, 38, 0.35) + f'<path d="M20,40 c10,-12 20,12 30,0" fill="none" stroke="{PURPLE}" stroke-width="5" stroke-linecap="round"/>',
 "self_harm": heart(100, 108, 1.3, "#F28B82") + plaster(100, 100, -25, 70, 26),
 # ── head, brain, nerves ──
 "migraine": head("shut", "grimace") + glow(62, 62, 28, RED, 0.55) + bolt(40, 40, 1.2, -25) + bolt(70, 22, 1.0, 10),
 "fits": figure(100, 130, 1.1, 90, "fall") + f'<g fill="none" stroke="{PURPLE}" stroke-width="5" stroke-linecap="round"><path d="M40,60 l10,-12 l10,12 l10,-12 l10,12 l10,-12 l10,12 l10,-12 l10,12 l10,-12 l10,12"/></g>',
 "face_droop": head("neutral", "flat", extra_face='') + f'<rect x="80" y="104" width="44" height="12" fill="{SKIN}"/>' + f'<path d="M80,106 Q96,106 104,110 Q114,118 122,124" fill="none" stroke="#B85454" stroke-width="5" stroke-linecap="round"/>' + arrow(160, 120, up=False, color=RED, s=1.0),
 "speech_trouble": head("neutral", "open") + bubble(154, 40, 70, 48) + f'<path d="M130,40 c6,-10 10,10 16,0 s10,10 16,0 s10,10 16,0" fill="none" stroke="{RED}" stroke-width="4" stroke-linecap="round"/>',
 "one_side_weak": figure(100, 104, 1.4) + f'<rect x="100" y="10" width="90" height="190" fill="{GREY_L}" opacity="0.75"/>' + f'<path d="M100,10 L100,190" stroke="{GREY}" stroke-width="4" stroke-dasharray="6 6"/>',
 "tingling": hand() + f'<g stroke="{YELLOW}" stroke-width="4" stroke-linecap="round">' + "".join(f'<path d="M{x},{y} l6,-6 M{x},{y-6} l6,6"/>' for x, y in ((64, 40), (96, 28), (128, 40), (150, 70), (48, 72))) + '</g>',
 "balance": figure(100, 112, 1.1, 18, "walk") + f'<path d="M20,188 L180,188" stroke="{GREY}" stroke-width="5" stroke-linecap="round"/>' + arcs(40, 60, PURPLE, 2, 14, 12, 200, 340, 5) + arcs(160, 60, PURPLE, 2, 14, 12, 20, 160, 5),
 "head_injury": head("half", "frown") + f'<path d="M58,62 C80,36 122,36 144,62" fill="none" stroke="{WHITE}" stroke-width="16" stroke-linecap="round"/><path d="M58,62 C80,36 122,36 144,62" fill="none" stroke="{GREY_L}" stroke-width="3"/>' + drop(126, 44, 0.6, RED),
 # ── ears, nose, eyes, mouth ──
 "ear_discharge": ear(96, 100, 1.2) + drop(118, 170, 1.0, YELLOW) + drop(140, 150, 0.7, YELLOW),
 "ringing_ears": ear(86, 104, 1.15) + f'<path d="M140,50 C150,30 176,30 180,56 L186,96 L134,96 L140,56Z" fill="{YELLOW}"/><circle cx="160" cy="102" r="7" fill="{ORANGE}"/>' + arcs(120, 76, ORANGE, 2, 10, 10, 200, 340, 4),
 "hoarse": head("neutral", "open") + glow(100, 150, 18, RED, 0.35) + bubble(156, 40, 64, 44) + f'<path d="M136,40 l8,-8 l8,12 l8,-10 l8,10" fill="none" stroke="{GREY}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>',
 "sinus": head("squint", "frown") + glow(78, 100, 16, RED, 0.6) + glow(122, 100, 16, RED, 0.6) + glow(100, 72, 14, RED, 0.5),
 "no_smell": nose(96, 104, 1.1) + f'<g transform="translate(160,56)"><circle r="8" fill="{YELLOW}"/>' + "".join(f'<circle cx="{c}" cy="{d}" r="9" fill="#F9A8D4"/>' for c, d in ((0, -14), (13, -4), (8, 12), (-8, 12), (-13, -4))) + '</g>' + nosign(160, 56, 26),
 "watery_eye": eye(100, 96, 1.05) + drop(70, 150, 0.9) + drop(92, 176, 0.7),
 "vision_loss": eye(100, 104, 1.05) + f'<path d="M100,40 A64,64 0 0 1 100,168Z" fill="{INK}" opacity="0.85"/>',
 "double_vision": f'<g opacity="0.6">' + eye(84, 104, 0.9) + '</g>' + eye(116, 104, 0.9),
 "dry_eyes": eye(100, 104, 1.05) + f'<path d="M40,170 l14,-8 l10,10 l12,-12 l12,10 l12,-10 l12,10 l12,-12 l10,10 l14,-8" fill="none" stroke="{ORANGE}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>',
 "floaters": eye(100, 110, 1.0) + star(150, 36, 9) + f'<g fill="{GREY}"><circle cx="54" cy="40" r="5"/><circle cx="70" cy="28" r="4"/><circle cx="84" cy="44" r="3"/></g>',
 "itchy_eyes": eye(100, 110, 1.0, red=True) + f'<g stroke="{RED}" stroke-width="4" stroke-linecap="round"><path d="M60,30 l10,22"/><path d="M78,24 l10,22"/><path d="M96,20 l10,22"/></g>',
 "jaundice": head("neutral", "flat", tint="yellow") + f'<circle cx="84" cy="90" r="7" fill="{YELLOW}"/><circle cx="116" cy="90" r="7" fill="{YELLOW}"/>',
 "bleeding_gums": mouth(100, 100, 1.25) + drop(150, 150, 1.0, RED) + dots(0, 0, [(76, 92), (100, 90), (124, 92)], 4, RED),
 "denture_pain": f'<path d="M40,90 C40,60 160,60 160,90 L160,110 C160,130 40,130 40,110Z" fill="#F9A8D4"/>' + "".join(f'<rect x="{50+i*20}" y="84" width="16" height="26" rx="5" fill="{WHITE}" stroke="{GREY}" stroke-width="2"/>' for i in range(5)) + bolts(100, 44, 60, 1.0),
 "lump": head("neutral", "flat") + f'<circle cx="124" cy="150" r="13" fill="{SKIN_D}" stroke="#B9835F" stroke-width="3"/>' + magnifier(160, 150, 18),
 # ── chest, breathing ──
 "chest_tight": torso("squint", "frown") + f'<rect x="40" y="104" width="120" height="22" rx="8" fill="{GREY}"/><rect x="92" y="100" width="16" height="30" rx="4" fill="{INK}"/>',
 "wheeze": lungs(92, 108) + f'<g fill="none" stroke="{BLUE}" stroke-width="5" stroke-linecap="round"><path d="M140,70 c10,-8 20,8 30,0"/><path d="M140,90 c10,-8 20,8 30,0"/></g>' + f'<path d="M150,40 l14,-8 l0,16Z" fill="{GREY}"/>',
 "cough_blood": head("shut", "open") + drop(150, 116, 1.0, RED) + drop(172, 96, 0.7, RED),
 "low_oxygen": oximeter(96, 100) + arrow(170, 120, up=False, color=RED, s=1.1),
 "snoring": sleeper(136) + f'<g fill="none" stroke="{RED}" stroke-width="6" stroke-linecap="round"><path d="M134,50 v30"/><path d="M152,50 v30"/></g>' + zzz(100, 60, GREY),
 "breast_lump": torso("neutral", "flat") + f'<circle cx="80" cy="112" r="10" fill="{SKIN_D}" stroke="#B9835F" stroke-width="3"/>' + magnifier(140, 70, 20),
 "breast_pain": torso("squint", "frown") + glow(78, 112, 24, RED, 0.55) + bolt(44, 96, 1.0, -20),
 # ── blood pressure, sugar ──
 "low_bp": f'<circle cx="92" cy="104" r="62" fill="{WHITE}" stroke="{GREY}" stroke-width="8"/><path d="M92,104 L58,82" stroke="{INK}" stroke-width="6" stroke-linecap="round"/><circle cx="92" cy="104" r="8" fill="{INK}"/>' + f'<path d="M50,78 A50,50 0 0 1 134,62" fill="none" stroke="{BLUE_L}" stroke-width="10"/>' + arrow(166, 130, False, BLUE, 1.2),
 "high_sugar": meter(90, 104) + drop(90, 72, 0.9, RED) + arrow(160, 70, True, RED, 1.2),
 # ── stomach, toilet ──
 "vomit_blood": head("shut", "open", shoulders=False, cy=70) + f'<path d="M92,96 C84,120 96,132 90,150 L110,150 C104,132 116,120 108,96Z" fill="{RED}"/>' + f'<path d="M50,146 L150,146 C146,178 128,190 100,190 C72,190 54,178 50,146Z" fill="{GREY_L}" stroke="{GREY}" stroke-width="4"/>',
 "black_stool": toilet() + drop(158, 44, 1.3, INK),
 "burp": head("neutral", "open") + f'<g fill="none" stroke="{GREY}" stroke-width="4"><circle cx="150" cy="104" r="12"/><circle cx="170" cy="80" r="8"/><circle cx="180" cy="60" r="5"/></g>',
 "hiccup": head("wide", "open") + f'<text x="150" y="52" font-family="Arial" font-weight="bold" font-size="30" fill="{PURPLE}" text-anchor="middle">hic!</text>',
 "trouble_eating": plate(100, 122) + f'<path d="M152,40 L152,120" stroke="{GREY}" stroke-width="8" stroke-linecap="round"/><ellipse cx="152" cy="40" rx="12" ry="18" fill="{GREY}"/>' + qmark(60, 40, 0.9),
 "no_urine": toilet(water="#FDE68A") + nosign(158, 46, 22),
 "toilet_accident": f'<path d="M40,60 L160,60 L154,120 L112,140 L100,120 L88,140 L46,120Z" fill="{SWEATER}"/>' + drop(80, 162, 0.9) + drop(110, 176, 0.7) + drop(132, 158, 0.6),
 "kidney_pain": torso(back=True) + f'<path d="M58,146 c-12,-2 -18,14 -8,22 c8,6 18,-2 14,-12" fill="#C0392B"/>' + glow(64, 156, 24, RED, 0.45) + bolt(34, 140, 1.0, -20),
 # ── joints, skin ──
 "morning_stiffness": figure(96, 116, 1.1) + sun(160, 40, 16) + f'<g stroke="{GREY}" stroke-width="5" stroke-linecap="round"><path d="M40,110 h20"/><path d="M40,130 h20"/><path d="M136,110 h20"/></g>',
 "gout": foot(96, 118, 1.05) + glow(142, 176, 20, RED, 0.8) + flame(160, 126, 0.8),
 "swelling": hand() + f'<g fill="none" stroke="{RED}" stroke-width="5" stroke-linecap="round"><path d="M30,110 l-18,0 m6,-6 l-6,6 l6,6"/><path d="M170,110 l18,0 m-6,-6 l6,6 l-6,6"/></g>' + glow(100, 120, 40, RED_L, 0.4),
 "face_swelling_morning": head("half", "flat") + f'<circle cx="68" cy="104" r="16" fill="{SKIN_D}" opacity="0.6"/><circle cx="132" cy="104" r="16" fill="{SKIN_D}" opacity="0.6"/>' + sun(162, 30, 14),
 "hives": forearm() + "".join(f'<ellipse cx="{x}" cy="{y}" rx="12" ry="8" fill="#F9A8D4" stroke="{RED}" stroke-width="2"/>' for x, y in ((84, 98), (112, 84), (104, 112), (132, 96))),
 "allergic_reaction": head("wide", "open") + f'<ellipse cx="100" cy="112" rx="22" ry="12" fill="#F28B82"/>' + f'<path d="M150,24 L186,86 L114,86Z" fill="{YELLOW}" stroke="#B7791F" stroke-width="4" stroke-linejoin="round"/><path d="M150,44 L150,66" stroke="{INK}" stroke-width="6" stroke-linecap="round"/><circle cx="150" cy="76" r="3.5" fill="{INK}"/>',
 "blisters": hand() + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{WHITE}" stroke="{RED_L}" stroke-width="3"/>' for x, y, r in ((88, 120, 10), (110, 112, 8), (104, 136, 7))),
 "sunburn": forearm() + f'<path d="M50,130 L150,60 L166,84 L66,154Z" fill="{RED}" opacity="0.35"/>' + sun(160, 34, 16),
 "wound_not_healing": forearm() + plaster(104, 92, -26, 60, 24) + clock(158, 150, 22),
 "bed_sore": bed(124) + f'<path d="M24,104 L180,104 L180,128 L24,128Z" fill="{SWEATER}"/>' + head("shut", "frown", cx=40, cy=96, r=22, shoulders=False, cheeks=False) + glow(120, 116, 16, RED, 0.8),
 "mole": forearm() + f'<path d="M96,90 c8,-10 22,-2 20,8 c6,8 -6,18 -14,12 c-10,4 -16,-10 -6,-20Z" fill="#6B4226"/>' + magnifier(150, 140, 22),
 "hair_loss": head("neutral", "frown") + f'<g fill="none" stroke="{HAIR}" stroke-width="3" stroke-linecap="round"><path d="M150,110 q6,10 0,20"/><path d="M166,130 q6,10 0,20"/><path d="M144,150 q6,10 0,20"/></g>' + f'<circle cx="100" cy="46" r="18" fill="{SKIN}"/>',
 "fungal": foot(96, 118, 1.05) + dots(0, 0, [(110, 150), (126, 160), (100, 170), (138, 176)], 6, "#84CC16"),
 "bruising_easily": forearm() + "".join(f'<ellipse cx="{x}" cy="{y}" rx="12" ry="8" fill="{BRUISE}" opacity="0.85" transform="rotate(-26 {x} {y})"/>' for x, y in ((80, 110), (106, 92), (132, 76))),
 # ── injuries ──
 "bleeding": drop(80, 90, 2.0, RED) + drop(136, 130, 1.4, RED),
 "bite": leg() + f'<g fill="{WHITE}" stroke="{RED}" stroke-width="2">' + "".join(f'<path d="M{x},{y} l5,10 l5,-10Z"/>' for x, y in ((74, 116), (86, 112), (98, 112), (110, 116))) + '</g>' + f'<g transform="translate(160,48)" fill="{BROWN}"><ellipse cx="0" cy="8" rx="12" ry="10"/><circle cx="-12" cy="-8" r="5"/><circle cx="0" cy="-12" r="5"/><circle cx="12" cy="-8" r="5"/></g>',
 "sting": forearm() + f'<circle cx="104" cy="96" r="8" fill="{RED}"/>' + glow(104, 96, 20, RED, 0.35) + bee(150, 44),
 "broken_bone": bone(100, 104, -30, broken=True),
 "choking": head("wide", "open") + f'<g fill="{SKIN}" stroke="{SKIN_D}" stroke-width="3"><rect x="70" y="138" width="24" height="40" rx="10"/><rect x="106" y="138" width="24" height="40" rx="10"/></g>' + f'<path d="M150,30 l24,24 M174,30 l-24,24" stroke="{RED}" stroke-width="7" stroke-linecap="round"/>',
 # ── women's health ──
 "heavy_bleeding": pad(90, 112) + drop(90, 110, 1.1, RED) + drop(150, 60, 1.0, RED) + drop(166, 96, 0.8, RED),
 "postmeno_bleeding": calendar(64, 72, 70) + drop(140, 124, 1.4, RED),
 "discharge": pad(96, 112) + drop(96, 112, 1.0, "#FEF3C7") + f'<path d="M96,90 C102,100 106,106 106,112 A10,10 0 0 1 86,112 C86,106 90,100 96,90Z" fill="none" stroke="{GREY}" stroke-width="2"/>',
}
