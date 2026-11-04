#!/usr/bin/env python3
"""Arma los gráficos de la ficha de Google Play: capturas con título (1080x1920) e imagen destacada (1024x500) por idioma.

Uso: python3 tools/make_store_assets.py <carpeta_con_capturas>   (carpeta/<es|en|ja>/{viewer,panel-1,panel-2,calibration,projects,shots}.png)
Salida: docs/play/<idioma>/ (capturas 01-08 y feature-graphic.png) y docs/play/icon-512.png.
Las fotos de ejemplo y la hoja PDF salen de docs/manual/img y de los PDF de ejemplo de la misma carpeta (sheet_<idioma>_list.pdf).
"""
import pathlib, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path("/tmp/store")
OUT = ROOT / "docs" / "play"
BG, ORANGE, WHITE, GRAY = (11, 11, 11), (255, 90, 31), (240, 240, 240), (165, 165, 160)
FONT_LATIN = "/System/Library/Fonts/Avenir Next.ttc"
FONT_JA = "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc"
TEXT = {
    "es": ["Encuadra cualquier cámara y lente sobre tu teléfono", "Cámaras, modos de grabación y lentes reales", "Ajustes para el operador: fps, obturación, ISO y perfil",
           "Cada foto lleva todos los datos", "Proyectos, locaciones y escenas", "Revisa y corrige tus planos", "Hoja de locaciones en PDF lista para enviar", "Calibra tu teléfono para un recuadro preciso"],
    "en": ["Frame any camera and lens on your phone", "Real cameras, recording modes and lenses", "Settings for the operator: fps, shutter, ISO and profile",
           "Every photo carries all the data", "Projects, locations and scenes", "Review and correct your shots", "Location sheet PDF ready to send", "Calibrate your phone for an accurate frame"],
    "ja": ["スマホでカメラとレンズの画角を確認", "実在のカメラ・記録モード・レンズ", "カメラマン向け設定：fps・シャッター・ISO・プロファイル",
           "写真にすべてのデータを記録", "プロジェクト・ロケ地・シーン", "ショットを確認・修正", "送れるロケ地シート PDF", "校正で正確な枠に"],
}
TAGLINE = {"es": "Visor de director para location scouting", "en": "Director's viewfinder for location scouting", "ja": "ロケハンのためのディレクターズビューファインダー"}

def font(lang, size, bold=True):
    if lang == "ja": return ImageFont.truetype(FONT_JA, size)
    return ImageFont.truetype(FONT_LATIN, size, index=5 if bold else 0)       # índice 5 = Demi Bold

def wrap(draw, text, f, width):
    words, lines, cur = (text.split(" ") if " " in text else list(text)), [], ""
    sep = " " if " " in text else ""
    for w in words:
        t = (cur + sep + w) if cur else w
        if draw.textlength(t, font=f) <= width: cur = t
        else: lines.append(cur); cur = w
    return lines + [cur]

def rounded(img, radius):
    m = Image.new("L", img.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, img.width, img.height), radius, fill=255)
    out = Image.new("RGBA", img.size); out.paste(img.convert("RGBA"), (0, 0), m); return out

def shot_card(lang, caption, image, name):
    canvas = Image.new("RGB", (1080, 1920), BG); d = ImageDraw.Draw(canvas)
    f = font(lang, 58); lines = wrap(d, caption, f, 940)[:3]
    y = 70
    for ln in lines: d.text((70, y), ln, font=f, fill=WHITE); y += 76
    d.rectangle((70, y + 14, 190, y + 20), fill=ORANGE)
    top = y + 60; box_w, box_h = 960, 1920 - top - 60
    im = ImageOps.contain(image.convert("RGB"), (box_w, box_h), Image.LANCZOS)
    card = rounded(im, 36); canvas.paste(card, ((1080 - card.width) // 2, top + (box_h - card.height) // 2), card)
    canvas.save(name, optimize=True)

def feature(lang, viewer, icon, name):
    c = Image.new("RGB", (1024, 500), BG); d = ImageDraw.Draw(c)
    c.paste(icon.resize((120, 120), Image.LANCZOS), (60, 56), icon.resize((120, 120), Image.LANCZOS))
    d.text((60, 190), "Mado", font=font(lang, 96), fill=WHITE)
    d.text((62, 305), "Director's Viewfinder", font=font(lang, 38), fill=ORANGE)
    tf = font(lang, 26, bold=False); y = 365
    for ln in wrap(d, TAGLINE[lang], tf, 470)[:3]: d.text((62, y), ln, font=tf, fill=GRAY); y += 36
    crop = viewer.crop((0, 900, viewer.width, 1900)); crop = ImageOps.fit(crop, (420, 336), Image.LANCZOS)
    card = rounded(crop, 22); c.paste(card, (560, 82), card)
    c.save(name, optimize=True)

def pdf_page(lang):
    pdf = SRC / f"sheet_{lang}_list.pdf"
    subprocess.run(["pdftoppm", "-r", "150", "-f", "2", "-l", "2", "-png", "-singlefile", str(pdf), str(SRC / f"pdf_{lang}")], check=True)
    return Image.open(SRC / f"pdf_{lang}.png")

icon = Image.open(ROOT / "docs/branding/mado-icon-play-store-512.png").convert("RGBA")
OUT.mkdir(parents=True, exist_ok=True); shutil.copy(ROOT / "docs/branding/mado-icon-play-store-512.png", OUT / "icon-512.png")
for lang in ("es", "en", "ja"):
    d = OUT / lang; d.mkdir(exist_ok=True)
    seq = [SRC / lang / "viewer.png", SRC / lang / "panel-1.png", SRC / lang / "panel-2.png", ROOT / f"docs/manual/img/{lang}/photo-marco.jpg",
           SRC / lang / "projects.png", SRC / lang / "shots.png", None, SRC / lang / "calibration.png"]
    for i, (p, cap) in enumerate(zip(seq, TEXT[lang]), 1):
        shot_card(lang, cap, pdf_page(lang) if p is None else Image.open(p), d / f"{i:02d}.png")
    feature(lang, Image.open(SRC / lang / "viewer.png"), icon, d / "feature-graphic.png")
    print("ok", lang)
