# Changelog

All notable changes to Hibiscus Faultline are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.0.0+26.3`), set by `mod_version` and `minecraft_version` in
`gradle.properties`, and each is released as the tag `vMAJOR.MINOR.PATCH+MINECRAFT`. Each Minecraft version has its own
branch and its own changelog; this one is Minecraft 26.3's (`main`). The same version number on two Minecraft versions
has the same changes.

- **PATCH** (1.0.0 → 1.0.1): bug fixes and small tweaks. Configs carry over untouched.
- **MINOR** (1.0.0 → 1.1.0): new modes, features or settings. Configs carry over.
- **MAJOR** (1.x → 2.0.0): anything that breaks configs, tags or keybinds.

Changes land under **Unreleased** as they're made; when a version is released, that section gets its number and
date. How to cut a release is in [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Changed

- Build tool selections: left-click picks the first corner and right-click the second. With a selection, left-click
  cancels it and right-click confirms. Selections clear when you switch tools and after every operation.
- The tool slot looks like a vanilla hotbar slot and sits a little further from the hotbar; while it's active the
  hotbar shows nothing selected. Holding Alt turns the slot into the tool column, so the current tool isn't shown
  twice, with the options panel to its right. The tool's name sits a little above its hints.
- The cursor box before the first corner outlines the whole block, not just the face.

### Fixed

- Ctrl + scroll into and out of the tool slot follows the way vanilla moves the hotbar, so it no longer sometimes
  fails with smooth or high-resolution scrolling.

## [1.1.0] - 2026-10-17

### Added

- **Build tools**: Move, Clone, Stack, Erase and Set, in a tool slot to the right of the hotbar. Ctrl + scroll past
  either end of the hotbar (or the unbound "Toggle Build Tool Slot" key) to enter it; while it's active your clicks
  drive the tool instead of the held item. Hold Alt for the tool palette and each tool's options, and Alt + scroll to
  change tool. Survival friendly: blocks come from and go to your inventory, nothing is free and nothing dupes.
- **Selections**: left-click two corners, then click a block outside the box to extend it, scroll over a face to push
  or pull it, or middle-click to extend to the looked-at block. A coloured Size readout, mouse hints under the
  crosshair, and Escape to step back.
- **Previews**: ghost blocks show where Move, Clone and Stack put things, positions that will be skipped show in red,
  and a bill of materials shows what an operation needs against what you have. The server checks every preview.
- **Move** lifts the selection and puts it down elsewhere: drag the gizmo, scroll to move along the axis you look
  along, middle-click for Move To, Ctrl+R to rotate and Ctrl+F to flip. Chests keep their items and machines their
  state, and nothing drops. Move Entities brings item frames, armour stands, paintings and mobs along.
- **Clone** copies block states for one matching item each; a chest arrives empty. **Stack** repeats the selection:
  scroll for the number of copies and Shift + scroll for the gap.
- **Erase** removes the selection as if mined: in survival each block takes its real mining time with the best tool
  you carry (halved by default), drops go to your inventory, and it costs the same hunger as vein mining. **Set**
  fills it with one block, picked from a searchable list or held in your offhand, with Air Only, Replace, Hollow and
  Walls. Anything the tools break in the way pays the same time and hunger.
- Right-click confirms; left-click pauses and a second left-click stops; a progress bar shows blocks done and time
  left. Shift + right-click links a container as an extra material source.
- Every break and place asks protection mods first, as vanilla does. Unbreakable blocks and the new
  `hibiscusfaultline:build_immovable` tag (spawners, trial spawners, vaults, reinforced deepslate) are never touched.
- **Creative**: no materials, no drops, no hunger and no break time; Set lists every block; bigger limits.
- **Undo and redo** with Ctrl+Z and Ctrl+Y. Survival undo refunds what was placed and puts back what was erased only
  if you can pay for it again, and only touches positions still as the operation left them.
- Operator commands `/faultline build cancel <player>`, `/faultline build undo <player>` and
  `/faultline build limits [player]`.
- Permission nodes `faultline.build.use`, `.move`, `.clone`, `.stack`, `.erase`, `.set`, `.max_volume` and
  `.bypass_protection`, and a `buildTools` section in the server and client configs for every limit and setting (see
  the README).

### Fixed

- Vein-mining only takes blocks the held tool is meant for, so a shovel in a tunnel mode no longer digs through
  cobblestone and a pickaxe no longer takes dirt. A bare hand or non-tool item only takes blocks that drop without a
  tool. Instant-break blocks (flowers, torches) are only taken when the vein started on one. Turn it off with the new
  `toolMustSuitBlock` server option.

## [1.0.0] - 2026-10-16

The first release of Hibiscus Faultline, for Minecraft 26.3 on NeoForge 26.3.0.23-beta or newer. Needs Hibiscus Lib
1.4.1 or newer.

### Added

- **Vein mining.** Hold the Faultline key (`` ` `` by default) and break a block to mine every matching block
  connected to it, with any tool or a bare hand. Each block costs durability and hunger as usual, and Fortune, Silk
  Touch and other enchantments apply.
- **Six modes**, picked by scrolling while the key is held: Shapeless, Small Tunnel, Mining Tunnel, Large Tunnel,
  Escape Tunnel and Small Square.
- **A preview** while the key is held: one merged outline around every block that would be mined, in a configurable
  colour.
- **A HUD panel** in the Hibiscus theme showing the mode, the scroll hint and the block count, or why vein mining is
  blocked (cooldown, wrong tool, no permission). Its corner and X/Y offsets are configurable, so it can clear other
  overlays such as an FPS counter.
- **Matching rules:** stone and deepslate ores count as the same block, and so do logs and wood from the same tree;
  leaves are only taken when you start on a leaf.
- **Server settings**, synced to clients so the preview matches what the server breaks: max blocks, max distance,
  hunger per block, cooldown, stopping before the tool breaks, gathering drops (optionally into the inventory),
  matching rules, requiring a tool and which modes are enabled. Client settings cover the HUD, the outline colour,
  inverted scrolling and remembering the last mode. Both are editable from the Mods screen.
- **Tags for pack makers:** `hibiscusfaultline:excluded`, `hibiscusfaultline:included_only` and
  `hibiscusfaultline:excluded_tools`.
- A mod list icon and banner.

[Unreleased]: https://github.com/hibiscus-software/hibiscus-faultline/compare/v1.1.0+26.3...HEAD
[1.1.0]: https://github.com/hibiscus-software/hibiscus-faultline/compare/v1.0.0+26.3...v1.1.0+26.3
[1.0.0]: https://github.com/hibiscus-software/hibiscus-faultline/commits/v1.0.0+26.3
