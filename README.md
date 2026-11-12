# Whimsy Automation

**奇思妙想自动化** — Automated item recovery and recipe-driven deconstruction for Minecraft.

## Target
- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- JEI 19.x (optional integration)

## Planned machines
- **Enchantment Extractor**: accepts enchanted equipment through item automation, extracts every enchantment into enchanted books, and outputs both the cleaned item and books.
- **Deconstruction Table**: discovers recipes from the recipe manager, lets players choose and lock a recipe, and reverses supported crafting and processing recipes when the required ingredients are available.

## Design rules
- Server-authoritative processing.
- No item loss or duplication when outputs are blocked.
- Independent input/output automation faces.
- Recipe selection is saved with the block entity.
- JEI is an optional client-side discovery/display integration; actual recipe execution uses the server recipe manager.

This repository is being built incrementally. See commits for implementation progress.
