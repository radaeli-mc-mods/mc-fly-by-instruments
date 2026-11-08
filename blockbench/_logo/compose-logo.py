"""Logo do mod: horizonte artificial com o Avionics Computer em alerta.

Uso (na raiz do repo; precisa de Node e Pillow):
    python blockbench/_logo/compose-logo.py

O fundo é pixel art numa grade de 128 unidades, ampliada 8x (nearest). O computador
vem de render-logo-computer.mjs a 32 px por texel, alinhado com essa grade, e sai pela
borda de baixo do aro. Grava docs/gallery/logo.png (512) e docs/gallery/logo-large.png (1200).
"""
import math
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GALLERY = ROOT / "docs" / "gallery"

G = 128                    # grade da pixel art
S = 8                      # ampliação
C = G / 2 - 0.5            # centro
BANK = math.radians(-12)   # inclinação do horizonte (asa direita baixa)
PITCH = 6                  # horizonte abaixo do centro (nariz acima), em unidades
R_OUT, R_BEZ = 62, 52      # raio externo do aro e raio do mostrador
CANVAS = 1200              # quadrado final, com folga embaixo para o computador

SKY = [(40, 92, 150), (48, 106, 162), (58, 122, 176), (70, 140, 192),
       (86, 162, 210), (112, 186, 226), (150, 212, 238)]
EARTH = [(97, 75, 46), (85, 58, 31), (79, 49, 24), (68, 43, 21),
         (58, 36, 17), (48, 30, 14)]
BRASS = [(89, 36, 36), (114, 71, 49), (158, 105, 71), (191, 142, 85),
         (206, 160, 90), (228, 183, 99), (247, 203, 108), (255, 235, 140)]
MARK = (246, 237, 216)
HORIZON = (249, 242, 222)
ORANGE, ORANGE_D = (255, 166, 40), (178, 98, 30)
OUTLINE = (40, 18, 18, 255)
STROKE = (249, 242, 222, 255)


def dial():
    img = Image.new("RGBA", (G, G), (0, 0, 0, 0))
    px = img.load()
    ux, uy = math.cos(BANK), math.sin(BANK)   # ao longo do horizonte
    nx, ny = -uy, ux                           # normal (aponta para a terra)

    for y in range(G):
        for x in range(G):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r > R_OUT:
                continue
            if r > R_BEZ:
                # aro de latão: luz de cima à esquerda, chanfro na borda interna
                light = -math.cos(math.atan2(dy, dx) + math.radians(135))
                band = r - R_BEZ
                if band > R_OUT - R_BEZ - 1 or band < 1:
                    px[x, y] = BRASS[0]
                    continue
                bevel = (1 if light > 0 else -1) if band < 3 else 0
                px[x, y] = BRASS[max(1, min(7, 4 + round(light * 2.4) + bevel))]
                continue
            d = dx * nx + dy * ny - PITCH
            if abs(d) < 0.6:
                px[x, y] = HORIZON
            elif d < 0:
                px[x, y] = SKY[len(SKY) - 1 - min(len(SKY) - 1, int(-d / 7))]
            else:
                px[x, y] = EARTH[min(len(EARTH) - 1, int(d / 7))]

    # escada de pitch, paralela ao horizonte
    for off, half in [(-9, 7), (-18, 12), (-27, 7), (9, 7), (18, 12)]:
        bx, by = C + nx * (PITCH + off), C + ny * (PITCH + off)
        for t in range(-half, half + 1):
            x, y = round(bx + ux * t), round(by + uy * t)
            if math.hypot(x - C, y - C) < R_BEZ - 1:
                px[x, y] = MARK

    # escala de inclinação, fixa na caixa
    for deg, ln in [(10, 2), (20, 2), (30, 4), (45, 2), (60, 4)]:
        for s in (-1, 1):
            a = math.radians(-90 + s * deg)
            for k in range(ln):
                rr = R_BEZ - 1 - k
                px[round(C + math.cos(a) * rr), round(C + math.sin(a) * rr)] = MARK
    # índice laranja no topo
    for row in range(4):
        for col in range(-3 + row, 4 - row):
            px[round(C) + col, round(C - R_BEZ + 1 + row)] = ORANGE if row < 3 else ORANGE_D
    # parafusos nas diagonais
    for a in (45, 135, 225, 315):
        sx = round(C + math.cos(math.radians(a)) * (R_BEZ + 4))
        sy = round(C + math.sin(math.radians(a)) * (R_BEZ + 4))
        for ddx, ddy, col in [(0, 0, BRASS[2]), (1, 0, BRASS[1]), (0, 1, BRASS[1]),
                              (1, 1, BRASS[0]), (-1, 0, BRASS[6]), (0, -1, BRASS[6])]:
            px[sx + ddx, sy + ddy] = col
    return img.resize((G * S, G * S), Image.NEAREST)


def computer():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "computer.png"
        subprocess.run(["node", str(HERE / "render-logo-computer.mjs"), str(path)], check=True)
        obj = Image.open(path).convert("RGBA")
        obj.load()
    l, t, r, b = obj.getbbox()
    return obj.crop((l - 8, t - 8, r + 8, b + 8))  # folga para o contorno


def compose():
    bg = dial()
    # contorno externo claro e escuro, para ler em página clara e escura
    disc = Image.new("RGBA", (bg.width + 48, bg.height + 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(disc)
    d.ellipse((0, 0, disc.width - 1, disc.height - 1), fill=STROKE)
    d.ellipse((10, 10, disc.width - 11, disc.height - 11), fill=OUTLINE)
    disc.alpha_composite(bg, (24, 24))

    out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    dx0, dy0 = (CANVAS - disc.width) // 2, 16
    out.alpha_composite(disc, (dx0, dy0))

    obj = computer()
    ox, oy = (CANVAS - obj.width) // 2, CANVAS - obj.height

    # sombra sobre o mostrador, recortada pelo disco
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((ox - 10, oy + 30, ox + obj.width + 10, oy + obj.height),
                                     fill=(10, 5, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    mask = Image.new("L", out.size, 0)
    mask.paste(disc.getchannel("A"), (dx0, dy0))
    shadow.putalpha(ImageChops.multiply(shadow.getchannel("A"), mask))
    out = Image.alpha_composite(out, shadow)

    # contorno escuro em volta do computador
    alpha = obj.getchannel("A").point(lambda v: 255 if v else 0)
    outline = Image.new("RGBA", obj.size, OUTLINE)
    outline.putalpha(alpha.filter(ImageFilter.MaxFilter(13)))
    out.alpha_composite(outline, (ox, oy))
    out.alpha_composite(obj, (ox, oy))
    return out


if __name__ == "__main__":
    logo = compose()
    logo.save(GALLERY / "logo-large.png")
    logo.resize((512, 512), Image.LANCZOS).save(GALLERY / "logo.png")
    print(f"Wrote {GALLERY / 'logo-large.png'} and {GALLERY / 'logo.png'}.")
