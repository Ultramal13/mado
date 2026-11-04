#!/usr/bin/env python3
"""Convierte un manual de docs/manual/*.md a HTML y a PDF (con Google Chrome sin interfaz).

Uso: python3 tools/make_manual.py es en ja      (genera docs/manual/manual-<idioma>.html y .pdf)

Markdown mínimo: títulos #, ##, ###; párrafos; listas con «-» y «1.»; citas «>»; ---; **negrita**, *cursiva*, `código`;
imágenes ![texto|ancho%](ruta) (el texto es el pie; varias en la misma línea quedan una al lado de otra; con «r» tras el ancho, p. ej. |30r, flota a la derecha y el texto la rodea).
"""
import html, re, subprocess, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "docs" / "manual"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
@page { size: A4; margin: 16mm 16mm 18mm 16mm; }
* { box-sizing: border-box; }
body { font-family: -apple-system, "Helvetica Neue", "Hiragino Sans", "Noto Sans CJK JP", Arial, sans-serif; color: #151515; font-size: 10.6pt; line-height: 1.5; }
h1 { font-size: 30pt; margin: 40mm 0 2mm; letter-spacing: -0.5px; }
h1 + h2 { font-size: 17pt; color: #ff5a1f; margin: 0 0 6mm; border: 0; }
h2 { font-size: 15pt; margin: 9mm 0 3mm; padding-bottom: 1.5mm; border-bottom: 2px solid #ff5a1f; break-after: avoid; }
h3 { font-size: 11.6pt; margin: 5mm 0 1.5mm; break-after: avoid; color: #222; }
p { margin: 0 0 2.6mm; orphans: 3; widows: 3; }
ul, ol { margin: 0 0 3mm; padding-left: 6mm; } li { margin-bottom: 1.2mm; }
blockquote { margin: 3mm 0; padding: 2.5mm 4mm; background: #fff3ec; border-left: 3px solid #ff5a1f; break-inside: avoid; }
blockquote p { margin: 0; }
code { font-family: "SF Mono", Menlo, monospace; background: #f1f1ee; padding: 0 3px; border-radius: 3px; font-size: 9.4pt; }
hr { border: 0; border-top: 1px solid #ddd; margin: 5mm 0; }
figure { display: inline-block; margin: 2mm 1.5% 3mm 0; vertical-align: top; break-inside: avoid; }
figure img { width: 100%; border: 1px solid #ddd; border-radius: 4px; display: block; }
figcaption { font-size: 8.4pt; color: #666; margin-top: 1mm; }
.imgs { break-inside: avoid; }
figure.right { float: right; margin: 1mm 0 3mm 5mm; }
h2, h3 { clear: none; }
"""

def inline(t: str) -> str:
    t = html.escape(t, quote=False)
    t = re.sub(r"`([^`]+)`", r"<code>\1</code>", t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"(?<!\*)\*(?!\s)(.+?)(?<!\s)\*(?!\*)", r"<em>\1</em>", t)
    return t

IMG = re.compile(r"!\[([^\]|]*)(?:\|(\d+)(r?))?\]\(([^)]+)\)")

def render(md: str) -> str:
    out, i, lines = [], 0, md.split("\n")
    while i < len(lines):
        ln = lines[i]
        if not ln.strip(): i += 1; continue
        if ln.startswith("#"):
            n = len(ln) - len(ln.lstrip("#")); out.append(f"<h{n}>{inline(ln[n:].strip())}</h{n}>"); i += 1; continue
        if ln.strip() == "---":
            out.append('<div style="break-after: page"></div>' if not any(o.startswith("<div style") for o in out) else "<hr>"); i += 1; continue
        if IMG.search(ln) and not ln.strip().startswith(("-", ">")):
            figs = "".join(
                f'<figure class="{"right" if m.group(3) else ""}" style="width:{m.group(2) or 100}%"><img src="{m.group(4)}"><figcaption>{html.escape(m.group(1))}</figcaption></figure>'
                for m in IMG.finditer(ln))
            out.append(figs if "right" in figs else f'<div class="imgs">{figs}</div>'); i += 1; continue
        if ln.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].startswith(">"): buf.append(lines[i][1:].strip()); i += 1
            out.append(f"<blockquote><p>{inline(' '.join(buf))}</p></blockquote>"); continue
        m = re.match(r"^(\d+)\. ", ln)
        if ln.startswith("- ") or m:
            tag = "ol" if m else "ul"; items = []
            while i < len(lines) and (lines[i].startswith("- ") if tag == "ul" else re.match(r"^\d+\. ", lines[i])):
                body = lines[i][2:] if tag == "ul" else re.sub(r"^\d+\. ", "", lines[i]); i += 1
                while i < len(lines) and lines[i].startswith("  ") and lines[i].strip(): body += " " + lines[i].strip(); i += 1
                items.append(f"<li>{inline(body)}</li>")
            out.append(f"<{tag}>{''.join(items)}</{tag}>"); continue
        buf = []
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "- ", ">", "---")) and not re.match(r"^\d+\. ", lines[i]) and not IMG.search(lines[i]):
            buf.append(lines[i].strip()); i += 1
        out.append(f"<p>{inline(' '.join(buf))}</p>")
    return "\n".join(out)

def build(lang: str) -> None:
    src = ROOT / f"manual-{lang}.md"
    body = render(src.read_text(encoding="utf-8"))
    title = re.search(r"^# (.+)$", src.read_text(encoding="utf-8"), re.M).group(1)
    page = f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><title>{html.escape(title)}</title><style>{CSS}</style></head><body>{body}</body></html>'
    out_html = ROOT / f"manual-{lang}.html"; out_html.write_text(page, encoding="utf-8")
    pdf = ROOT / f"manual-{lang}.pdf"
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", f"--print-to-pdf={pdf}", out_html.as_uri()], check=True, capture_output=True, timeout=120)
    out_html.unlink()                                  # el HTML es intermedio: el entregable es el PDF
    print("ok", pdf)

if __name__ == "__main__":
    for l in (sys.argv[1:] or ["es"]): build(l)
