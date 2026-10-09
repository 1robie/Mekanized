"""Composes the textured menu panels (docs/MENU_TEXTURES_PLAN.md) from Mekanism's GUI sprites.

Not part of the build: run it by hand after changing a sprite, and commit the PNGs it writes.
    python mekanized/tools/compose_menu_panels.py

Writes textures/gui/panel/panel_<rows>.png for chests of 3 to 6 rows: Mekanism's base panel
(sprites/base.png, nine-slice, border 4) at the size of the vanilla chest screen, 176 x (114 + 18 rows),
with the player inventory and hotbar slots (slot/normal.png) where the client draws them.
Based on Mekanism (MIT, see LICENSE-MEKANISM.txt).
"""
from pathlib import Path

from PIL import Image

GUI = Path(__file__).resolve().parent.parent / "src/main/resources/mekanized/pack/resourcepack/assets/mekanized/textures/gui"


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


def main():
    slot = Image.open(GUI / "slot/normal.png").convert("RGBA")
    (GUI / "panel").mkdir(exist_ok=True)
    for rows in range(3, 7):
        panel = nine_slice(GUI / "sprites/base.png", 4, 176, 114 + rows * 18)
        # Vanilla ChestMenu: inventory slots at y 103 + 18 (rows - 4), hotbar 58 lower; frames 1 px up and left.
        top = 103 + (rows - 4) * 18 - 1
        for row in range(3):
            for column in range(9):
                panel.alpha_composite(slot, (7 + column * 18, top + row * 18))
        for column in range(9):
            panel.alpha_composite(slot, (7 + column * 18, top + 58))
        panel.save(GUI / f"panel/panel_{rows}.png")
        print("wrote", GUI / f"panel/panel_{rows}.png")


if __name__ == "__main__":
    main()
