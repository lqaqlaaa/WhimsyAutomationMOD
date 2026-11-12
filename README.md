# Whimsy Automation

**奇思妙想自动化** — automated item recovery and recipe-driven deconstruction for Minecraft.

## Target platform

- Minecraft **1.21.1**
- **NeoForge 21.1.x**
- Java 21
- JEI 19.x (optional integration target)

## Machines

### Enchantment Extractor

- Accepts enchanted items through its input capability.
- Converts each enchantment level into an individual level-I enchanted book.
- Keeps the original item and removes its regular enchantments.
- Exposes input on the top/sides and output on the bottom.
- Stops when there is not enough output space; the input item is not consumed.

### Deconstruction Table

- Scans the server recipe manager instead of a hard-coded vanilla crafting list.
- Supports recipe types that expose a result and ingredients, including crafting, smelting, blasting, smoking and stonecutting recipes.
- Stores a locked recipe ID; an empty ID means automatic recipe selection.
- Exposes input on the top/sides and output on the bottom.
- Stops when output slots are full; the input item is not consumed.

## Current development status

This is an early implementation, not a release-ready build yet.

- [x] NeoForge 1.21.1 project setup
- [x] Machine block and block-entity registration
- [x] Hopper item-handler capabilities
- [x] First-pass enchantment extraction logic
- [x] First-pass reverse recipe processing
- [x] Persistent recipe lock field
- [x] Basic block/item models and English/Chinese names
- [ ] Player GUI and recipe selection controls
- [ ] JEI recipe-browser integration
- [ ] More robust recipe-specific reverse rules and edge-case handling
- [ ] In-game testing and automated tests

## Design guarantees

- Processing runs on the logical server.
- Outputs are checked before inputs are consumed.
- Recipe selection is persisted with the block entity.
- JEI is intended to be optional; core automation must not require the JEI mod to be installed.
