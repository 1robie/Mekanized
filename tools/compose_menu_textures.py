"""Composes the textured menu assets (docs/MENU_TEXTURES_PLAN.md) from Mekanism's GUI sprites.

Not part of the build: run it by hand after changing a sprite or a bar shape, and commit what it writes.
    python mekanized/tools/compose_menu_textures.py

Writes, under the Mekanized resource pack:
- textures/gui/panel/panel_<rows>.png: Mekanism's base panel at the size of the vanilla chest screen,
  176 x (114 + 18 rows), with the player inventory and hotbar slots, for 3 to 6 rows;
- textures/gui/frame/*.png: the static frames of the bars, drawn in menu titles;
- textures/gui/fill/*.png: bar fills that are not a Mekanism texture as they are;
- textures/gui/side/<mode>.png: the side configuration buttons;
- models/gui/bar/<shape>/<cell>_<level>.json: one model per bar cell and fill level;
- models/gui/icon_{18,14}.json: the parents of the menu icons.

A bar spans 3 slots along its axis (54 px with the frame, 52 px of fill). Cell k (0 at the start: the
bottom of a vertical bar, the left of a horizontal one) covers fill pixels [max(0, 18k - 1), min(52, 18k + 17))
and draws them from its slot's item area, reaching 1 px out on each side across the gap to its neighbours.
Level L of cell k fills the first L of its pixels; level 0 is an empty model. MachineEngine picks cell levels the same way (stage 3).
Based on Mekanism (MIT, see LICENSE-MEKANISM.txt).
"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/mekanized/pack/resourcepack/assets/mekanized"
GUI = ASSETS / "textures/gui"
CELLS = 3
LENGTH = 18 * CELLS - 2


def nine_slice(source, border, width, height):
    sprite = Image.open(source).convert("RGBA")
    sw, sh = sprite.size
    out = Image.new("RGBA", (width, height))
    xs = [(0, border, 0, border), (border, sw - border, border, width - border), (sw - border, sw, width - border, width)]
    ys = [(0, border, 0, border), (border, sh - border, border, height - border), (sh - border, sh, height - border, height)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            if dx1 > dx0 and dy1 > dy0:
                out.paste(sprite.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


def save(image, relative):
    path = GUI / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def panels():
    slot = Image.open(GUI / "slot/normal.png").convert("RGBA")
    for rows in range(3, 7):
        panel = nine_slice(GUI / "sprites/base.png", 4, 176, 114 + rows * 18)
        # Vanilla ChestMenu: inventory slots at y 103 + 18 (rows - 4), hotbar 58 lower; frames 1 px up and left.
        top = 103 + (rows - 4) * 18 - 1
        for row in range(3):
            for column in range(9):
                panel.alpha_composite(slot, (7 + column * 18, top + row * 18))
        for column in range(9):
            panel.alpha_composite(slot, (7 + column * 18, top + 58))
        save(panel, f"panel/panel_{rows}.png")


def arrow(strip):
    """Mekanism's progress arrow (progress/right.png, strip 0 empty, 1 full) stretched to LENGTH px: its shaft repeats."""
    source = Image.open(GUI / "progress/right.png").convert("RGBA").crop((0, strip * 8, 32, strip * 8 + 8))
    out = Image.new("RGBA", (LENGTH, 8))
    extra = LENGTH - 32
    for x in range(LENGTH):
        sx = x if x < 12 else (12 if x < 12 + extra else x - extra)
        out.paste(source.crop((sx, 0, sx + 1, 8)), (x, 0))
    return out


def ticks():
    """Mekanism's gauge ticks (gauge/standard.png, 16 x 58) cut to the 52 px of a bar."""
    return Image.open(GUI / "gauge/standard.png").convert("RGBA").crop((0, 3, 16, 3 + LENGTH))


def frames():
    tall = nine_slice(GUI / "sprites/gauge/normal.png", 2, 18, 54)
    tall.alpha_composite(ticks(), (1, 1))
    save(tall, "frame/tall_gauge.png")
    vertical = Image.new("RGBA", (18, 54))
    vertical.alpha_composite(nine_slice(GUI / "sprites/bar/base.png", 1, 6, 54), (6, 0))
    save(vertical, "frame/power_vertical.png")
    horizontal = Image.new("RGBA", (54, 18))
    horizontal.alpha_composite(nine_slice(GUI / "sprites/bar/base.png", 1, 54, 6), (0, 6))
    save(horizontal, "frame/power_horizontal.png")
    progress = Image.new("RGBA", (54, 18))
    progress.alpha_composite(arrow(0), (1, 5))
    save(progress, "frame/progress.png")


def fills():
    save(arrow(1), "fill/progress.png")
    save(ticks(), "fill/ticks.png")
    # Heat and other tinted horizontal bars: white with Mekanism's bar shading, tinted per resource.
    heat = Image.new("RGBA", (LENGTH, 4), (255, 255, 255, 255))
    for x in range(LENGTH):
        heat.putpixel((x, 0), (235, 235, 235, 255))
        heat.putpixel((x, 3), (200, 200, 200, 255))
    save(heat, "fill/horizontal.png")


SIDE_MODES = {"none": (139, 139, 139), "input": (170, 0, 0), "output": (0, 0, 170), "both": (170, 0, 170)}


def side_buttons():
    """Mekanism's side configuration squares: 14 x 14, colored by mode (EnumColor dark red, dark blue, purple)."""
    for mode, (r, g, b) in SIDE_MODES.items():
        square = Image.new("RGBA", (14, 14), (r, g, b, 255))
        light = tuple(min(255, c + 70) for c in (r, g, b)) + (255,)
        dark = tuple(c // 2 for c in (r, g, b)) + (255,)
        for i in range(14):
            square.putpixel((i, 0), light)
            square.putpixel((0, i), light)
            square.putpixel((i, 13), dark)
            square.putpixel((13, i), dark)
        save(square, f"side/{mode}.png")


# shape: axis, cross-axis item range [from, to), texture, texture size along the axis (None: tiles of 16), tinted, overlay.
SHAPES = {
    "chemical": ("vertical", (0, 16), "mekanized:mek_chemical/infuse_type/base", None, True, "mekanized:gui/fill/ticks"),
    "fluid": ("vertical", (0, 16), "minecraft:block/water_still", None, True, "mekanized:gui/fill/ticks"),
    "energy": ("vertical", (6, 10), "mekanized:gui/bar/vertical_power", LENGTH, False, None),
    "energy_horizontal": ("horizontal", (6, 10), "mekanized:gui/bar/horizontal_power", LENGTH, False, None),
    "heat": ("horizontal", (6, 10), "mekanized:gui/fill/horizontal", LENGTH, True, None),
    "progress": ("horizontal", (4, 12), "mekanized:gui/fill/progress", LENGTH, False, None),
}


def cell_range(cell):
    return max(0, 18 * cell - 1), min(LENGTH, 18 * cell + 17)


def chunks(start, end, tile):
    """Splits fill pixels [start, end) so no piece crosses a multiple of tile."""
    while start < end:
        stop = min(end, (start // tile + 1) * tile) if tile else end
        yield start, stop
        start = stop


def element(axis, cross, cell, start, end, texture_length, texture, tinted, z):
    """The element drawing fill pixels [start, end) of a bar in cell's item, with its texture's UV."""
    low, high = start - 18 * cell, end - 18 * cell  # item coordinates along the axis, from the cell's start
    if texture_length:
        a, b = start * 16 / texture_length, end * 16 / texture_length
    else:
        a, b = (start % 16), (start % 16) + (end - start)
    if axis == "horizontal":
        frm, to = [low, 16 - cross[1], z], [high, 16 - cross[0], z]
        uv = [a, 0, b, 16]
    else:
        frm, to = [cross[0], low, z], [cross[1], high, z]
        uv = [0, 16 - b, 16, 16 - a]  # v grows downwards, fill pixel 0 is the bottom of the texture
    face = {"uv": [round(v, 4) for v in uv], "texture": texture}
    if tinted:
        face["tintindex"] = 0
    return {"from": frm, "to": to, "faces": {"south": face, "north": dict(face)}}


def bar_models():
    root = ASSETS / "models/gui/bar"
    for shape, (axis, cross, texture, texture_length, tinted, overlay) in SHAPES.items():
        folder = root / shape
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.json"):
            old.unlink()
        for cell in range(CELLS):
            start, end = cell_range(cell)
            # Level 0 draws nothing: menus name <shape>/<cell>/<level> straight from a placeholder.
            empty = {"textures": {"particle": texture}, "elements": []}
            (folder / f"{cell}_0.json").write_text(json.dumps(empty, separators=(",", ":")) + "\n", encoding="utf-8")
            for level in range(1, end - start + 1):
                elements = [element(axis, cross, cell, a, b, texture_length, "#fill", tinted, 8)
                            for a, b in chunks(start, start + level, None if texture_length else 16)]
                if overlay:
                    elements.append(element(axis, cross, cell, start, start + level, LENGTH, "#overlay", False, 8.01))
                for e in elements:
                    e["from"][2], e["to"][2] = e["from"][2] - 0.005, e["to"][2]
                textures = {"fill": texture, "particle": texture}
                if overlay:
                    textures["overlay"] = overlay
                model = {"gui_light": "front", "textures": textures, "elements": elements}
                (folder / f"{cell}_{level}.json").write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")


def icon_parents():
    """Parents of the menu icons (template mekanized:item/gui_icon): 18 px over the slot frame, or 14 px centered."""
    for size, (low, high) in {18: (-1, 17), 14: (1, 15)}.items():
        face = {"uv": [0, 0, 16, 16], "texture": "#icon"}
        model = {"gui_light": "front", "textures": {"particle": "#icon"},
                 "elements": [{"from": [low, low, 7.995], "to": [high, high, 8], "faces": {"south": face, "north": dict(face)}}]}
        (ASSETS / f"models/gui/icon_{size}.json").write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")


def main():
    panels()
    icon_parents()
    frames()
    fills()
    side_buttons()
    bar_models()
    print("written under", ASSETS)


if __name__ == "__main__":
    main()
