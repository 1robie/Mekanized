# Mekanized

Mekanism's machines on Paper and Folia servers, with no client mod. Built on
[MachineEngine](https://github.com/1robie/MachineEngine) and
[CraftEngine](https://github.com/Xiao-MoMi/craft-engine): CraftEngine sends the blocks and items in a
resource pack, and [zMenu](https://modrinth.com/plugin/zmenu) draws the machine menus.

Inspired by [Mekanism](https://github.com/mekanism/Mekanism); textures, models, recipes and names come
from Mekanism by Aidan C. Brady (MIT, see
[LICENSE-MEKANISM.txt](src/main/resources/mekanized/LICENSE-MEKANISM.txt), which ships in the jar).
Mekanized is not affiliated with Mekanism.

**Documentation:** [1robie.github.io/plugins/mekanized](https://1robie.github.io/plugins/mekanized/)

## Requirements

| | |
|---|---|
| Server | Paper or Folia, Minecraft 1.20.6 – 26.3 |
| Java | 21 (1.20.6) or 25 (26.x) |
| Plugins | MachineEngine, CraftEngine; zMenu for the machine menus |

## Installing

Put CraftEngine, MachineEngine, Mekanized and zMenu in `plugins/`, start the server once, then run
`/ce reload all`. Players need CraftEngine's resource pack, which also draws the menus. Download
from [Hangar](https://hangar.papermc.io/1robie/Mekanized) or the
[GitHub releases](https://github.com/1robie/Mekanized/releases); the full guide is on the
[documentation site](https://1robie.github.io/plugins/mekanized/installation/installing).

**Dev builds** of the next version (unstable, not for production servers) are on the Hangar
Snapshot channel and the GitHub pre-release
[`dev`](https://github.com/1robie/Mekanized/releases/tag/dev), rebuilt on every change to `develop`.

## Building

```bash
./gradlew build
```

The plugin jar is written to `build/libs/Mekanized-<version>.jar`. JDK 25 builds it; the bytecode
stays Java 21, and `check` fails if it uses Paper API newer than 1.20.6 (`verifyPaperBaseline`).

**MachineEngine:** Mekanized compiles against `machineengine-api` and tests with
`machineengine-test`, from JitPack at the version in `gradle/libs.versions.toml`. With a MachineEngine
checkout next to this one (`../MachineEngine`, or `-Pmachineengine_dir=<path>`), the build uses that
checkout instead, so engine changes are picked up without a release; `-PlocalEngine=false` turns this
off. CI and releases clone MachineEngine at that version next to the checkout, so they do not
depend on JitPack.

## Dev servers

```bash
./gradlew runPaper-26.3
```

Also `runPaper-1.20.6`, `runFolia-1.20.6` and `runFolia-26.2`. They need the MachineEngine checkout:
the server gets MachineEngine's plugin jar built from it. CraftEngine is built from its `dev` branch
into `.craftengine/` on the first run (`-PrefreshCraftEngine` rebuilds it;
`-Pcraftengine_runtime=modrinth` downloads the release instead), and zMenu is downloaded
(`-PwithZMenu=false` leaves it out). Servers run in `run/<paper|folia>-<version>/`.

Server checks (admins, results in the console log): `/mekanized test`, `test-cable`,
`test-chemistry`, `check-block-options`, `check-upgrades`, `check-station`, `check-modules`,
`check-item-data`. `./gradlew ceSnapshot` checks that the CraftEngine blocks and items still expand as
in `ce-snapshot.json`.

## Content

The content is kept by hand in `src/main/resources/mekanized/`: textures and models under
`pack/resourcepack/`, CraftEngine items and crafting recipes under `pack/configuration/`, and machine
recipes and tags under `definitions/`. A `.yml` file added to `pack/configuration/` or `definitions/`
is installed automatically: the build lists them in `manifest.yml`.

## Branches and releases

The same model as MachineEngine (its D48 and CONTRIBUTING "Branches"):

- Work happens on `develop`, the default branch; pull requests target it. Every push (not
  docs-only) makes a dev build `<next>-dev.<run>`, on the rolling `dev` pre-release and Hangar's
  Snapshot channel (`dev-build.yml`). A newer push cancels a dev build still building or testing,
  never one already publishing. `[skip dev]` in the (last) commit message of a push skips the dev
  build, not the Build workflow; `[skip ci]` skips every workflow.
- `main` holds releases. Only the owner opens pull requests into it, from `develop` or `hotfix/*`
  (the `Release source` check); they need `Build (CraftEngine pinned)` and are merged with a merge
  commit. No force pushes or deletion on either branch.
- A release: release MachineEngine first if Mekanized needs it, and set its version in
  `gradle/libs.versions.toml`; then pull request `develop` → `main`, merge, tag the merge commit
  on `main` with the version and push the tag (`release.yml` refuses a tag that is not on `main`);
  then raise `version` in `gradle.properties` on `develop` to the next `-SNAPSHOT`.

## License

GPL-3.0, see [LICENSE](LICENSE). Mekanism's assets: MIT, see
[LICENSE-MEKANISM.txt](src/main/resources/mekanized/LICENSE-MEKANISM.txt).
