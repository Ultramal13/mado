#!/usr/bin/env python3
"""Genera el ícono de Mado (opción J: anillo de cinco rayas + recuadro naranja 3:2).

Salidas: vectores Android (primer plano y monocromo), fondo, XML adaptativo, y los maestros
SVG/PNG en docs/branding (oscuro, claro y noche roja). Ejecutar desde la raíz del proyecto.
"""
import math, pathlib
from PIL import Image, ImageDraw

ORANGE = "#FF5A1F"
THEMES = {  # fondo, rayas, recuadro
    "oscuro": ("#0B0B0B", "#F0F0F0", ORANGE),
    "claro": ("#E3E3DF", "#1A1A1A", ORANGE),
    "noche-roja": ("#0B0000", "#B3261E", "#B3261E"),
}
ROOT = pathlib.Path(".")

# Geometría del maestro (caja de 100): anillo r=33, trazo 5, recuadro 30 x 20 (3:2).
# Primer plano adaptativo (caja de 108, zona segura ~66): mismo dibujo escalado a 0.74 para que el anillo quepa.
GAP_DEG = 11.5          # hueco visible entre rayas
N = 5                   # cinco rayas (el cero en Morse, como LUX-0)


def geometry(cx, cy, scale):
    r, sw = 33 * scale, 5 * scale
    cap = math.degrees(sw / r)                       # lo que los remates redondos agregan en total
    span = 360 / N - GAP_DEG - cap                   # arco de la línea central
    arcs = []
    for k in range(N):
        c = -90 + k * 360 / N                        # una raya centrada arriba: simétrico izquierda-derecha
        a0, a1 = math.radians(c - span / 2), math.radians(c + span / 2)
        arcs.append(((cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + r * math.cos(a1), cy + r * math.sin(a1))))
    rw, rh = 30 * scale, 20 * scale
    rect = (cx - rw / 2, cy - rh / 2, rw, rh, 3 * scale)
    return r, sw, arcs, rect


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def rect_path(x, y, w, h, rx):
    return (f"M{f(x + rx)},{f(y)}H{f(x + w - rx)}A{f(rx)},{f(rx)} 0 0 1 {f(x + w)},{f(y + rx)}V{f(y + h - rx)}"
            f"A{f(rx)},{f(rx)} 0 0 1 {f(x + w - rx)},{f(y + h)}H{f(x + rx)}A{f(rx)},{f(rx)} 0 0 1 {f(x)},{f(y + h - rx)}"
            f"V{f(y + rx)}A{f(rx)},{f(rx)} 0 0 1 {f(x + rx)},{f(y)}Z")


def arc_paths(r, arcs):
    return [f"M{f(a[0])},{f(a[1])}A{f(r)},{f(r)} 0 0 1 {f(b[0])},{f(b[1])}" for a, b in arcs]


# ---------- Android (vector drawables) ----------
def android():
    r, sw, arcs, rect = geometry(54, 54, 0.74)
    def vector(ring, box, name):
        lines = ['<?xml version="1.0" encoding="utf-8"?>',
                 '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                 '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">']
        for d in arc_paths(r, arcs):
            lines.append(f'    <path android:pathData="{d}" android:strokeColor="{ring}" android:strokeWidth="{f(sw)}" android:strokeLineCap="round"/>')
        lines.append(f'    <path android:pathData="{rect_path(*rect)}" android:fillColor="{box}"/>')
        lines.append('</vector>')
        (ROOT / f"app/src/main/res/drawable/{name}.xml").write_text("\n".join(lines) + "\n")
    vector("#F0F0F0", ORANGE, "ic_launcher_foreground")
    vector("#FFFFFFFF", "#FFFFFFFF", "ic_launcher_monochrome")
    (ROOT / "app/src/main/res/values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#0B0B0B</color>\n</resources>\n')
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
    for n in ("ic_launcher", "ic_launcher_round"):
        (ROOT / f"app/src/main/res/mipmap-anydpi/{n}.xml").write_text(adaptive)


# ---------- Maestros SVG / PNG ----------
def svg(bg, ring, box):
    r, sw, arcs, rect = geometry(50, 50, 1.0)
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="1024" height="1024">',
           f'  <rect width="100" height="100" rx="22" fill="{bg}"/>']
    for d in arc_paths(r, arcs):
        out.append(f'  <path d="{d}" fill="none" stroke="{ring}" stroke-width="{f(sw)}" stroke-linecap="round"/>')
    out.append(f'  <path d="{rect_path(*rect)}" fill="{box}"/>')
    out.append('</svg>')
    return "\n".join(out) + "\n"


def png(bg, ring, box, size, path):
    k = 4 * size / 100                                # supermuestreo x4
    S = int(size * 4)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, S - 1, S - 1), radius=22 * k, fill=bg)
    r, sw, arcs, (rx0, ry0, rw, rh, rr) = geometry(50, 50, 1.0)
    cap = sw / 2 * k
    for k_i in range(N):
        c = -90 + k_i * 360 / N
        span = 360 / N - GAP_DEG - math.degrees(sw / r)
        steps = int(span * 12)
        for i in range(steps + 1):
            a = math.radians(c - span / 2 + span * i / steps)
            x, y = (50 + r * math.cos(a)) * k, (50 + r * math.sin(a)) * k
            d.ellipse((x - cap, y - cap, x + cap, y + cap), fill=ring)
    d.rounded_rectangle((rx0 * k, ry0 * k, (rx0 + rw) * k, (ry0 + rh) * k), radius=rr * k, fill=box)
    img.resize((size, size), Image.LANCZOS).save(path)


def masters():
    out = ROOT / "docs/branding"
    for name, (bg, ring, box) in THEMES.items():
        (out / f"mado-icon-{name}.svg").write_text(svg(bg, ring, box))
        png(bg, ring, box, 1024, out / f"mado-icon-{name}.png")
    png(*THEMES["oscuro"], 512, out / "mado-icon-play-store-512.png")


if __name__ == "__main__":
    android()
    masters()
    print("ok")
