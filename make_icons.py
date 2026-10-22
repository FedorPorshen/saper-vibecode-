"""Генератор legacy-иконок сапёра: PNG без внешних библиотек (чистый zlib/struct).

Рисует мину на сером поле с фаской: ic_launcher.png и ic_launcher_round.png
во всех плотностях mipmap. Запуск: python make_icons.py
"""
import math
import os
import struct
import zlib

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # суперсэмплинг: рисуем в 4 раза крупнее и усредняем

GRAY = (192, 192, 192)
LIGHT = (232, 232, 232)
DARK = (122, 122, 122)
MINE = (26, 26, 26)
WHITE = (255, 255, 255)


def png_chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: str, size: int, rgba: bytes) -> None:
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    raw = b"".join(b"\x00" + rgba[y * size * 4:(y + 1) * size * 4] for y in range(size))
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b""))


def render_big(big: int, rounded: bool):
    """Рисует иконку размером big x big (RGBA с прозрачностью)."""
    px = [0.0] * (big * big * 4)
    bevel = big * 0.07
    corner = big * 0.14

    def put(x: int, y: int, col, alpha: float) -> None:
        i = (y * big + x) * 4
        a = alpha
        if a <= 0:
            return
        r = col[0] * a + px[i] * (1 - a)
        g = col[1] * a + px[i + 1] * (1 - a)
        b = col[2] * a + px[i + 2] * (1 - a)
        na = a + px[i + 3] / 255.0 * (1 - a)
        px[i], px[i + 1], px[i + 2], px[i + 3] = r, g, b, na * 255

    for y in range(big):
        for x in range(big):
            fx, fy = x + 0.5, y + 0.5
            # прозрачность вне формы: скруглённый квадрат или круг
            if rounded:
                if math.hypot(fx - big / 2, fy - big / 2) > big / 2:
                    continue
            else:
                cx = min(fx, big - fx)
                cy = min(fy, big - fy)
                if cx < corner and cy < corner and math.hypot(corner - cx, corner - cy) > corner:
                    continue
            # фон с фаской (светлее сверху-слева, темнее снизу-справа)
            col = GRAY
            if fx < bevel or fy < bevel:
                col = LIGHT
            elif fx > big - bevel or fy > big - bevel:
                col = DARK
            dx, dy = fx - big / 2, fy - big / 2
            dist = math.hypot(dx, dy)
            spike = big * 0.44
            sw = big * 0.055
            # шипы: горизонталь, вертикаль, две диагонали
            if abs(dy) <= sw and abs(dx) <= spike:
                col = MINE
            if abs(dx) <= sw and abs(dy) <= spike:
                col = MINE
            if abs(dx - dy) <= sw and dist <= spike:
                col = MINE
            if abs(dx + dy) <= sw and dist <= spike:
                col = MINE
            # блик (проверяем раньше корпуса, чтобы он не был перехвачен)
            if math.hypot(dx + big * 0.09, dy + big * 0.09) <= big * 0.06:
                put(x, y, WHITE, 1.0)
                continue
            # корпус мины с мягким краем
            r = big * 0.30
            if dist <= r + 1.5:
                a = max(0.0, min(1.0, (r + 1.5 - dist) / 3.0))
                put(x, y, MINE, a)
                continue
            put(x, y, col, 1.0)
    return px


def downscale(px, big: int, size: int) -> bytes:
    out = bytearray(size * size * 4)
    step = big // size
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0.0
            for sy in range(step):
                for sx in range(step):
                    i = ((y * step + sy) * big + (x * step + sx)) * 4
                    alpha = px[i + 3] / 255.0
                    r += px[i] * alpha
                    g += px[i + 1] * alpha
                    b += px[i + 2] * alpha
                    a += alpha
            n = step * step
            i = (y * size + x) * 4
            if a > 0:
                out[i] = int(r / a)
                out[i + 1] = int(g / a)
                out[i + 2] = int(b / a)
            out[i + 3] = int(a / n * 255)
    return bytes(out)


def main() -> None:
    for dpi, size in SIZES.items():
        folder = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(folder, exist_ok=True)
        for name, rounded in (("ic_launcher.png", False), ("ic_launcher_round.png", True)):
            big = render_big(size * SS, rounded)
            write_png(os.path.join(folder, name), size, downscale(big, size * SS, size))
            print("ok:", os.path.join(folder, name))


if __name__ == "__main__":
    main()
