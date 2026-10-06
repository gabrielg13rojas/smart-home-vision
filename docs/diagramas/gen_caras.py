# Genera 4_reconocimiento_facial.svg
import math
import random
from xml.sax.saxutils import escape as e

o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080" width="1920" height="1080" font-family="Segoe UI, Arial, sans-serif">',
     '<defs><marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#0d6efd"/></marker></defs>',
     '<rect width="1920" height="1080" fill="#ffffff"/>',
     '<text x="60" y="80" font-size="46" font-weight="700" fill="#212529">¿Cómo reconoce una cara?</text>',
     '<text x="60" y="122" font-size="23" fill="#6c757d">De una foto a 128 números · OpenCV con los modelos YuNet y SFace (preentrenados)</text>']


def face(cx, cy, r, tilt=0, skin="#f1c27d"):
    return "".join([
        f'<g transform="rotate({tilt} {cx} {cy})">',
        f'<ellipse cx="{cx}" cy="{cy}" rx="{r*0.8}" ry="{r}" fill="{skin}" stroke="#a0754a" stroke-width="2"/>',
        f'<path d="M{cx-r*0.8} {cy-r*0.25} q{r*0.8} {-r*1.25} {r*1.6} 0" fill="#5c3d2e"/>',
        f'<circle cx="{cx-r*0.32}" cy="{cy-r*0.1}" r="{r*0.08}" fill="#212529"/>',
        f'<circle cx="{cx+r*0.32}" cy="{cy-r*0.1}" r="{r*0.08}" fill="#212529"/>',
        f'<path d="M{cx} {cy-r*0.02} l{-r*0.08} {r*0.25} h{r*0.16}" fill="none" stroke="#a0754a" stroke-width="2"/>',
        f'<path d="M{cx-r*0.3} {cy+r*0.45} q{r*0.3} {r*0.2} {r*0.6} 0" fill="none" stroke="#c0392b" stroke-width="3"/>',
        '</g>'])


steps = [
    ("1 · Foto", ["JPEG 640×480 → matriz de", "píxeles (Mat, azul-verde-rojo)", "≈ 920.000 números"]),
    ("2 · YuNet detecta", ["recuadro de la cara +", "5 puntos (ojos, nariz, boca)", "+ confianza (0,98)"]),
    ("3 · Alinear", ["gira y recorta a 112×112 px:", "los ojos siempre quedan", "en el mismo lugar"]),
    ("4 · SFace resume", ["una red neuronal convierte", "la cara en 128 números", "(el embedding)"]),
    ("5 · Comparar", ["similitud coseno con los", "usuarios de users.json", "≥ 0,363 → reconocido"]),
]
W, X0, Y0, IMG_H = 330, 50, 160, 220
for i, (title, lines) in enumerate(steps):
    x = X0 + i * (W + 47)
    o.append(f'<rect x="{x}" y="{Y0}" width="{W}" height="420" rx="18" fill="#f8f9fa" stroke="#dee2e6" stroke-width="2"/>')
    cx, cy = x + W / 2, Y0 + 20 + IMG_H / 2
    o.append(f'<rect x="{x+20}" y="{Y0+20}" width="{W-40}" height="{IMG_H}" rx="12" fill="#e9ecef"/>')
    if i == 0:
        o.append(face(cx, cy, 70))
        for gx in range(int(x + 20), int(x + W - 20), 18):
            o.append(f'<line x1="{gx}" y1="{Y0+20}" x2="{gx}" y2="{Y0+20+IMG_H}" stroke="#ffffff" stroke-opacity="0.5"/>')
        for gy in range(Y0 + 20, Y0 + 20 + IMG_H, 18):
            o.append(f'<line x1="{x+20}" y1="{gy}" x2="{x+W-20}" y2="{gy}" stroke="#ffffff" stroke-opacity="0.5"/>')
    elif i == 1:
        o.append(face(cx, cy, 70))
        o.append(f'<rect x="{cx-66}" y="{cy-82}" width="132" height="164" fill="none" stroke="#198754" stroke-width="5"/>')
        for px, py in [(-22, -7), (22, -7), (0, 12), (-18, 32), (18, 32)]:
            o.append(f'<circle cx="{cx+px}" cy="{cy+py}" r="6" fill="#0d6efd" stroke="#fff" stroke-width="2"/>')
        o.append(f'<rect x="{cx-66}" y="{cy-110}" width="70" height="26" rx="5" fill="#198754"/>'
                 f'<text x="{cx-31}" y="{cy-91}" font-size="16" font-weight="700" fill="#fff" text-anchor="middle">0,98</text>')
    elif i == 2:
        o.append(face(cx - 70, cy, 42, tilt=-22))
        o.append(f'<line x1="{cx-20}" y1="{cy}" x2="{cx+18}" y2="{cy}" stroke="#0d6efd" stroke-width="4" marker-end="url(#ar)"/>')
        o.append(f'<rect x="{cx+25}" y="{cy-55}" width="110" height="110" fill="#ffffff" stroke="#212529" stroke-width="2"/>')
        o.append(face(cx + 80, cy + 2, 46))
        o.append(f'<text x="{cx+80}" y="{cy+80}" font-size="16" fill="#495057" text-anchor="middle">112 × 112</text>')
    elif i == 3:
        for li, n in enumerate([5, 4, 3, 2]):
            lx = x + 50 + li * 50
            for k in range(n):
                ly = cy - (n - 1) * 22 + k * 44
                o.append(f'<circle cx="{lx}" cy="{ly}" r="12" fill="#6f42c1" fill-opacity="{0.45+li*0.15}"/>')
        random.seed(7)
        for k in range(14):
            h = random.randint(-55, 55)
            bx = x + 240 + (k % 7) * 9
            base = cy - 55 if k < 7 else cy + 65
            color = "#0d6efd" if h > 0 else "#dc3545"
            o.append(f'<rect x="{bx}" y="{base - abs(h)/2}" width="6" height="{abs(h)/2+2}" fill="{color}"/>')
        o.append(f'<text x="{x+268}" y="{cy+12}" font-size="22" font-weight="800" fill="#212529" text-anchor="middle">128</text>')
    else:
        o.append(f'<text x="{cx}" y="{cy-60}" font-size="17" font-family="Consolas, monospace" fill="#212529" text-anchor="middle">[0.04, -0.13, 0.08 …]</text>')
        o.append(f'<text x="{cx}" y="{cy-22}" font-size="18" fill="#495057" text-anchor="middle">vs. users.json</text>')
        o.append(f'<rect x="{x+40}" y="{cy}" width="{W-80}" height="44" rx="10" fill="#198754"/>')
        o.append(f'<text x="{cx}" y="{cy+30}" font-size="21" font-weight="800" fill="#fff" text-anchor="middle">✓ Gabriel · 0,61</text>')
        o.append(f'<rect x="{x+40}" y="{cy+56}" width="{W-80}" height="40" rx="10" fill="#dc3545"/>')
        o.append(f'<text x="{cx}" y="{cy+83}" font-size="19" font-weight="800" fill="#fff" text-anchor="middle">Desconocido · 0,12</text>')
    o.append(f'<text x="{x+22}" y="{Y0+290}" font-size="26" font-weight="800" fill="#212529">{e(title)}</text>')
    for j, line in enumerate(lines):
        o.append(f'<text x="{x+22}" y="{Y0+330+j*28}" font-size="20" fill="#495057">{e(line)}</text>')
    if i < 4:
        ax = x + W + 4
        o.append(f'<line x1="{ax}" y1="{Y0+130}" x2="{ax+38}" y2="{Y0+130}" stroke="#0d6efd" stroke-width="5" marker-end="url(#ar)"/>')

# ---- Mapa de caras
MX, MY, MW, MH = 50, 620, 900, 420
o.append(f'<rect x="{MX}" y="{MY}" width="{MW}" height="{MH}" rx="18" fill="#ffffff" stroke="#dee2e6" stroke-width="2"/>')
o.append(f'<text x="{MX+28}" y="{MY+48}" font-size="26" font-weight="800" fill="#212529">El "mapa de caras"</text>')
o.append(f'<text x="{MX+28}" y="{MY+80}" font-size="19" fill="#6c757d">(dibujado en 2D; en realidad tiene 128 dimensiones)</text>')
random.seed(3)
for name, color, ccx, ccy in [("Gabriel", "#198754", 300, 250), ("Ana", "#0d6efd", 640, 200), ("Luis", "#6f42c1", 580, 340)]:
    for _ in range(6):
        a, r = random.uniform(0, 6.28), random.uniform(5, 36)
        o.append(f'<circle cx="{MX+ccx+r*math.cos(a)}" cy="{MY+ccy+r*math.sin(a)}" r="9" fill="{color}" fill-opacity="0.8"/>')
    o.append(f'<text x="{MX+ccx}" y="{MY+ccy-50}" font-size="21" font-weight="700" fill="{color}" text-anchor="middle">{name}</text>')
nx, ny = MX + 345, MY + 292
star = [(0, -16), (5, -5), (16, -4), (8, 4), (10, 16), (0, 9), (-10, 16), (-8, 4), (-16, -4), (-5, -5)]
o.append('<polygon points="' + " ".join(f"{nx+a},{ny+b}" for a, b in star) + '" fill="#ffc107" stroke="#212529" stroke-width="1.5"/>')
o.append(f'<text x="{MX+60}" y="{MY+390}" font-size="19" fill="#212529">★ foto nueva: cae cerca de Gabriel → reconocido</text>')
ux, uy = MX + 820, MY + 300
o.append(f'<circle cx="{ux}" cy="{uy}" r="10" fill="#dc3545"/>'
         f'<text x="{ux}" y="{uy-20}" font-size="18" font-weight="700" fill="#dc3545" text-anchor="middle">desconocido</text>')

# ---- Datos clave
KX, KY, KW = 990, 620, 880
o.append(f'<rect x="{KX}" y="{KY}" width="{KW}" height="420" rx="18" fill="#e7f1ff" stroke="#0d6efd" stroke-width="2"/>')
o.append(f'<text x="{KX+30}" y="{KY+50}" font-size="26" font-weight="800" fill="#0a58ca">Para recordar</text>')
facts = [
    ("No entrenamos nada.", "YuNet y SFace ya vienen entrenados con muchísimas caras."),
    ("Registrarse = guardar números.", "5 vectores de 128 números en users.json; las fotos se descartan."),
    ("Ningún número es \"la nariz\".", "La red aprendió sola a poner cerca las fotos de la misma persona."),
    ("El % en pantalla es un indicador.", "Umbral 0,363 = 60 %; similitud 0,7 = 100 %. No es una métrica biométrica."),
    ("Sigue siendo un dato biométrico.", "No es una foto, pero te identifica: protégelo y permite borrarlo."),
]
for j, (b, t) in enumerate(facts):
    y = KY + 100 + j * 62
    o.append(f'<circle cx="{KX+42}" cy="{y-7}" r="7" fill="#0d6efd"/>')
    o.append(f'<text x="{KX+62}" y="{y}" font-size="21" font-weight="700" fill="#212529">{e(b)}</text>')
    o.append(f'<text x="{KX+62}" y="{y+27}" font-size="18" fill="#495057">{e(t)}</text>')
o.append('</svg>')
open("4_reconocimiento_facial.svg", "w", encoding="utf-8").write("\n".join(o))
print("ok")
