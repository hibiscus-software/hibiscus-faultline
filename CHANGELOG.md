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

[Unreleased]: https://github.com/hibiscus-software/hibiscus-faultline/compare/v1.0.0+26.3...HEAD
[1.0.0]: https://github.com/hibiscus-software/hibiscus-faultline/commits/v1.0.0+26.3
