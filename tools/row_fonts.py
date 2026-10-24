"""Builds the stacked text rows of the dialogue HUD from Minecraft's own font.

The actionbar is a single line, and a font can only lift its glyphs by as much as a
glyph is high (Minecraft rejects an ascent above the height). To stack lines upwards,
every row gets its own copy of the vanilla bitmap fonts whose cells are padded with
transparent pixels below the glyph: a taller cell may have a taller ascent, so the
glyph is drawn higher, at exactly the same size and width.

    python -I row_fonts.py <client.jar> <output: .../nexo/pack/assets/terranova>

Row r (1..ROWS) is drawn r * PITCH pixels above the actionbar line, as font
terranova:dialogue_row<r>. Row 0 is the actionbar line itself (the default font).
Run it again with the client of a new Minecraft version if Mojang changes the font.
"""
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROWS = 5
PITCH = 10  # px between lines: 8 px glyph cell plus 2 px gap

DEFAULT = "assets/minecraft/font/include/default.json"


def padded(texture: Image.Image, columns: int, rows: int, extra: int) -> Image.Image:
    """Copies the glyph grid with `extra` transparent pixels below every cell row."""
    cell_w, cell_h = texture.width // columns, texture.height // rows
    out = Image.new("RGBA", (texture.width, rows * (cell_h + extra)), (0, 0, 0, 0))
    for row in range(rows):
        band = texture.crop((0, row * cell_h, columns * cell_w, (row + 1) * cell_h))
        out.paste(band, (0, row * (cell_h + extra)))
    return out


def main(jar_path: str, out_dir: str) -> None:
    out = Path(out_dir)
    (out / "font").mkdir(parents=True, exist_ok=True)
    (out / "textures" / "font").mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar_path) as jar:
        providers = json.loads(jar.read(DEFAULT))["providers"]
        textures = {}
        for provider in providers:
            path = "assets/minecraft/textures/" + provider["file"].split(":", 1)[1]
            textures[provider["file"]] = Image.open(io.BytesIO(jar.read(path))).convert("RGBA")

    for r in range(1, ROWS + 1):
        lift = r * PITCH
        font = [{"type": "reference", "id": "minecraft:include/space"}]
        for provider in providers:
            name = Path(provider["file"].split(":", 1)[1]).stem
            texture = textures[provider["file"]]
            chars = provider["chars"]
            cell_h = texture.height // len(chars)
            height = provider.get("height", 8)
            if height != cell_h:
                raise SystemExit(f"{name}: height {height} != cell height {cell_h}; scaled fonts are not supported")
            file = f"dialogue_row{r}_{name}.png"
            padded(texture, len(chars[0]), len(chars), lift).save(out / "textures" / "font" / file)
            font.append({
                "type": "bitmap",
                "file": f"terranova:font/{file}",
                "height": height + lift,
                "ascent": provider["ascent"] + lift,
                "chars": chars,
            })
        with open(out / "font" / f"dialogue_row{r}.json", "w", encoding="utf-8", newline="\n") as f:
            json.dump({"providers": font}, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"dialogue_row{r}: +{lift} px, {len(providers)} bitmap providers")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
