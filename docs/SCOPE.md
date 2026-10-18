# Clean Combos — scope: "basic, but prestige"

Decided 2026-10-17 after playing 0.1.0 in Guild Adventures. Current as of **1.0.0-beta.1**.

## The pitch

Drag and drop. No datapacks, no per-weapon setup. Every weapon in the game — vanilla or modded — gets one of
a few reliable, polished attacks, and behaves the same every time. Flexibility and reliability are the selling
points. Few knobs, on purpose.

## Rules

### Movesets

| Moveset | Weapons | Detection (automatic) |
|---|---|---|
| **Chop** | axes, modded maces | `AxeItem`, `MaceItem` subclasses, `#minecraft:axes`, `c:tools/axes`-style tags, id containing `mace` |
| **Thrust** | spears, tridents | `TridentItem`, spear-style tags, id containing `spear` / `lance` / `pike` / `javelin` |
| **Swing** | every other melee item | default |
| **Fists** | empty hand, non-weapons, fist weapons | melee-damage items with id containing `claw` / `fist` / `gauntlet` / `bracer` / `knuckle` / `cestus` / `katar` / `glove` / `handwrap` / `talon` / `tekko` |

- **The vanilla mace is left to vanilla** (its own attack and smash) by default. Override with
  `"minecraft:mace": "chop"`; any item can be opted out with `"<id>": "vanilla"`.
- A short config list (`movesetOverrides`: `id or #tag → moveset`) fixes the rare misdetected modded weapon.
- **The one-two:** every moveset is a two-hit combo. Swings, chops and punches mirror on the second hit (the
  pair crosses in an X); spear thrusts repeat the same side.

### Situational attacks

- **Jump attacks:** an attack started while airborne is a **downward chop**, whatever the weapon — except
  thrust weapons, which keep their thrust. Jump attacks stand outside the one-two: fixed timing (the average
  cycle with the short windup), always the first hit's hand and side, and the combo is left as it was — so
  landing a falling crit is consistent.
- **Running thrust:** sprinting while moving forward turns a swing into a thrust. The weapon keeps its own
  reach (a sword doesn't borrow the spear bonus). A sprint-jump still chops.

### Grip

| Hands | Grip | Effect |
|---|---|---|
| one weapon, empty off hand | **two-handed** | +25% damage, −20% attack speed (swing and thrust; chop stays one-handed) |
| one weapon, non-weapon in off hand (shield, torch…) | **one-handed** | baseline |
| two weapons | **dual wield** | −20% damage, +25% attack speed; swings alternate hands |
| fist weapon, empty off hand | **dual wield** | weapon hand and bare fist alternate (fist weapons count as weapons, so two claws or claw + sword dual wield too) |

- All four numbers are config values. No keybind: holding something in the off hand is how you choose.

### Timing

- A combo's average swing takes vanilla's attack time scaled by `attackPace` (0.8): `20 / attackSpeed × 0.8`
  ticks, split across the two hits by the one-two beat (`comboRhythm`). Each moveset has a minimum cycle.
- **Speed bonus:** every attack is `attackSpeedBonusPercent` (25%) faster, so a two-handed sword
  (1.6 × 0.8) plays at the sword's own 1.6.
- **Anti-scaling:** attack speed above `attackSpeedSoftCap` (2.0) counts at `attackSpeedOverCap` (half), so
  stacked attack speed can't run away. Normal weapons sit below it.
- Control returns at the hit; the animation plays on, and the next attack waits for the cycle.

### Reach

- The wielder's entity-interaction-range attribute (fists −0.5); snaps reach 7 blocks further.
- Holding a spear (any thrust weapon) adds `spearReachBonus` (+1) to the attribute itself.

### Hits

- Server-authoritative cone check (swings ±11°, narrow by design), pitched with the aim, ignoring i-frames;
  vanilla's player attack supplies crits, enchantments, knockback and durability. Off-hand swings resolve
  with the off-hand item swapped in.

## Feel

1. **Hitstop** — a brief freeze on attacker and victim when a swing connects (`hitstopTicks` 3, +1 chop/jump).
2. **Animation** — the authored clips drive the whole player (`clipOnlyAnimation`, default on): body, legs,
   both arms; weapons stay in vanilla's hold. Two-handed swing and thrust have their own clips. A snapping
   attack layers the lunge clip. The swing turns with the player mid-swing.
3. **Trails** — flat sheets following the weapon the clip moves, pinned to the line the hit is checked along,
   tilted with the aim; swings show the middle of the motion. Thrusts and punches draw a straight line.
4. **Sounds** — sword / heavy sword / dagger whooshes and hits by weight, a blunt punch whoosh.
5. **Tooltips** — one line naming the weapon's attack.
6. **Combo cue** — the target's outline pulses while the second hit is available.

## Assets

- Clips (`player_animations/`): `swing_left_to_right` (+ `_2h`), `slam_left_to_right`, `thrust_left_to_right`
  (+ `_2h`), `unarmed_left_to_right`, `lunge_left_to_right`. Authored left-to-right with the right hand
  leading; mirrored in code.
- Sounds: Fantasy SFX Pack Vol 1 by JC Sounds, CC BY 4.0 — see `CREDITS.md`.

## Config

- `config/clean_combos.json` — gameplay; the server's copy is synced to clients on join.
- `config/clean_combos-client.json` — per player, never synced: `firstPersonArms` (off; unarmed attacks
  always show the arms).

## Compatibility

- Fabric + NeoForge, Minecraft 1.21.1. Requires Architectury API and PlayerAnimator.
- Incompatible with Better Combat (both replace the attack).
