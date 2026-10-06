# Genera 3_librerias.svg (tarjetas en una grilla 3x3)
from xml.sax.saxutils import escape as e

COLORS = {"nav": ("#198754", "Navegador · JavaScript"), "srv": ("#0d6efd", "Servidor · Java"),
          "mod": ("#6f42c1", "Modelo dentro de OpenCV"), "ard": ("#fd7e14", "Placa Arduino Uno · C++")}
cards = [
    ("nav", "MediaPipe Hands (Google)", ["Reconoce las manos: encuentra la palma", "y devuelve 21 puntos (x, y, z) por mano,", "hasta 2 manos, en tiempo real."], "Apache 2.0", "RECONOCE LAS MANOS"),
    ("srv", "GestureClassifier", ["21 puntos → qué dedos están extendidos", "→ gesto (☝️ ✌️ 🤟 🖖 ✋ ✊ 👍).", "Reglas geométricas, sin entrenamiento."], "código propio", None),
    ("srv", "OpenCV 4.9", ["Convierte la foto JPEG en una matriz de", "píxeles (Mat) y ejecuta los modelos de", "rostros. Paquete org.openpnp:opencv."], "Apache 2.0", None),
    ("mod", "YuNet", ["Detecta rostros: recuadro, 5 puntos", "(ojos, nariz, boca) y confianza.", "Red neuronal preentrenada · 230 KB."], "MIT", "DETECTA CARAS"),
    ("mod", "SFace", ["Convierte cada cara en 128 números", "(embedding) y los compara con los", "usuarios registrados en users.json."], "Apache 2.0", "RECONOCE QUIÉN ES"),
    ("srv", "Spring Boot 3.3", ["API REST, regla de autorización,", "estado de los 4 focos, historial", "y registro de usuarios."], "Apache 2.0", None),
    ("srv", "jSerialComm 2.11", ["Abre el puerto USB (COM3) a 9600 baudios,", "envía \"RED_ON\" y lee la respuesta", "\"OK:RED_ON\"."], "Apache 2.0 / LGPL v3", None),
    ("nav", "Bootstrap 5.3 + Icons", ["Estilos del dashboard: tarjetas,", "botones, alertas, interruptores", "e iconos."], "MIT", None),
    ("ard", "Sketch Arduino", ["Lee el texto que llega por Serial y", "enciende o apaga pines con digitalWrite.", "Sin librerías adicionales."], "LGPL (core)", None),
]

out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080" width="1920" height="1080" font-family="Segoe UI, Arial, sans-serif">',
       '<rect width="1920" height="1080" fill="#ffffff"/>',
       '<text x="60" y="80" font-size="46" font-weight="700" fill="#212529">¿Qué procesa cada librería?</text>',
       '<text x="60" y="122" font-size="23" fill="#6c757d">No se entrenó nada: los modelos vienen preentrenados y los gestos son reglas escritas a mano</text>']
W, H = 580, 255
for i, (kind, title, lines, lic, badge) in enumerate(cards):
    x = 60 + (i % 3) * (W + 30)
    y = 160 + (i // 3) * (H + 25)
    color, where = COLORS[kind]
    stroke = 4 if badge == "RECONOCE LAS MANOS" else 2
    out.append(f'<rect x="{x}" y="{y}" width="{W}" height="{H}" rx="16" fill="#ffffff" stroke="{color}" stroke-width="{stroke}"/>')
    out.append(f'<path d="M{x} {y+16} a16 16 0 0 1 16 -16 h{W-32} a16 16 0 0 1 16 16 v34 h-{W} z" fill="{color}"/>')
    out.append(f'<text x="{x+22}" y="{y+34}" font-size="19" font-weight="700" fill="#ffffff">{e(where)}</text>')
    if badge:
        out.append(f'<rect x="{x+W-232}" y="{y+12}" width="214" height="28" rx="14" fill="#ffffff"/>')
        out.append(f'<text x="{x+W-125}" y="{y+32}" font-size="15" font-weight="800" fill="{color}" text-anchor="middle">{e(badge)}</text>')
    out.append(f'<text x="{x+22}" y="{y+92}" font-size="30" font-weight="800" fill="#212529">{e(title)}</text>')
    for j, line in enumerate(lines):
        out.append(f'<text x="{x+22}" y="{y+132+j*30}" font-size="21" fill="#495057">{e(line)}</text>')
    tw = 14 + len(lic) * 9
    out.append(f'<rect x="{x+W-tw-18}" y="{y+H-44}" width="{tw}" height="28" rx="14" fill="#f1f3f5" stroke="#ced4da"/>')
    out.append(f'<text x="{x+W-18-tw/2}" y="{y+H-24}" font-size="15" font-weight="700" fill="#495057" text-anchor="middle">{e(lic)}</text>')

# leyenda
lx = 60
for kind in ("nav", "srv", "mod", "ard"):
    color, where = COLORS[kind]
    out.append(f'<rect x="{lx}" y="1015" width="24" height="24" rx="5" fill="{color}"/>')
    out.append(f'<text x="{lx+34}" y="1034" font-size="20" fill="#495057">{e(where)}</text>')
    lx += 34 + len(where) * 10 + 50
out.append('</svg>')
open("3_librerias.svg", "w", encoding="utf-8").write("\n".join(out))
print("ok")
