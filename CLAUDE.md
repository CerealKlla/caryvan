# Caryvan

Hand carts, horse-drawn carts, and wagons for a modular Minecraft project (Minecraft Java Edition).
Part of a suite of intercommunicating mods that expose public APIs so other mods — the user's own
and third parties' — can integrate.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/caryvan-context), not part
of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned
as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh
clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/caryvan-context.git context
```

- `context/design-document.md` — the authoritative design spec. Start here for anything about
  intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or
  override the design document, with rationale.
- `context/classes/` — one short markdown file per implemented class: public surface, key state,
  collaborators. Read the relevant file here before opening the actual source, and before editing a
  class update its file to match.

**Keep this system current as you work:**
- When a design decision is made that conflicts with or is absent from design-document.md, update
  design-document.md directly and add a dated entry to decisions.md explaining the change.
- When a class is added or its public surface changes, add or update its file in `context/classes/`.
- Don't let source and these docs drift — treat updating them as part of finishing the change, not
  optional cleanup.
- `context/` has its own git history, independent of this repo's commits. Commit and push changes
  there separately (`git -C context add . && git -C context commit -m "..." && git -C context
  push`) — editing the files alone doesn't back them up.

## Origin and licensing — read before touching movement/rendering code

This mod's design is based on researching three existing (non-suite) cart mods, none of which
target this environment's Minecraft/NeoForge version:

- **AstikorCarts** (github.com/MennoMax/astikor-carts) — archived, MIT licensed, Forge, last real
  work at MC 1.19.2.
- **NiftyCarts** (github.com/jmb05/nifty-carts) — actively maintained, MIT licensed, Fabric, up to
  MC 1.21.11. A fork/superset of AstikorCarts (adds Hand Cart, Wagon, Seed Drill, Reaper). The
  primary code reference for this mod — **MIT permits copying/adapting its code directly**, as long
  as the original copyright/license notice is preserved for whatever is adapted (add a
  `THIRD_PARTY_NOTICES.md` crediting it the first time any of its code is actually copied/ported,
  not just referenced for inspiration).
- **Trotting Wagons** (github.com/Favouriteless/Trotting-Wagons) — actively maintained, NeoForge
  1.21.1, but **`mod_license=All Rights Reserved` in its own build config — no open license at
  all**. Design/feature inspiration only (its wagon-variety idea). **Never copy its code.**

See `context/design-document.md` for the full research writeup and build plan.

## Status

Scaffolded 2026-10-25 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**, Minecraft **26.1.2**, Java **25** — same toolchain as every sibling mod.
- Group ID: `com.github.cerealklla.caryvan` / Mod ID: `caryvan`.
- Depends on **Nuclyus** (required, same as every mod in the suite).
- No sibling-mod dependencies yet — see design-document.md's "Integration points" section for the
  planned (not-yet-built) optional hooks into Settlemynts/Lyfe/Yconomics.
- `./gradlew compileJava` green. No real features implemented yet — bare `@Mod` class only.

See [context/design-document.md](context/design-document.md) for the real plan.
