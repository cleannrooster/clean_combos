# Exiled Combat — scope: "basic, but prestige"

Decided 2026-10-17 after playing 0.1.0 in Guild Adventures.

## The pitch

Drag and drop. No datapacks, no per-weapon setup. Every weapon in the game — vanilla or modded — gets one of
three reliable, polished attacks, and behaves the same every time. Flexibility and reliability are the selling
points.

## Rules

### Movesets

| Moveset | Weapons | Detection (automatic) |
|---|---|---|
| **Chop** | axes, maces | `AxeItem`, `MaceItem`, `#minecraft:axes`, `c:tools/axes`-style tags |
| **Thrust** | spears, tridents | `TridentItem`, spear-style tags, id containing `spear` / `lance` / `pike` / `javelin` |
| **Swing** | every other melee item | default |
| **Fists** | empty hand | unchanged: alternating hands |

- A moveset never changes with grip — only stats and the support-arm pose do.
- A short config list (`id or #tag → moveset`) fixes the rare misdetected modded weapon. It replaces the
  weapon-profile datapack system, which is removed.

### Grip

| Hands | Grip | Effect |
|---|---|---|
| one weapon, empty off hand | **two-handed** | +25% damage, −20% attack speed |
| one weapon, non-weapon in off hand (shield, torch…) | **one-handed** | baseline |
| two weapons | **dual wield** | −20% damage, +25% attack speed; swings alternate hands |

- All four numbers are config values.
- No keybind: holding something in the off hand is how you choose one-handed.

### Timing

- A full combo cycle takes vanilla's attack time, `20 / attackSpeed` ticks (sword 1.6 → 12.5 ticks,
  axe 0.8–1.0 → 20–25), split across the two swings by the one-two beat (`comboRhythm`).
- So any attack-speed change — weapon, gear, effects, grip — changes swing speed with no per-weapon data.

### Attributes and enchantments

- An off-hand swing resolves with the off-hand item as the main hand for its duration (stacks and attribute
  modifiers swapped) — already how 0.1.0 works.

### Jump attacks

- An attack started while airborne is always a **downward chop**, whatever the weapon — except **thrust**
  weapons, which keep their thrust.

## Prestige features (in scope)

1. **Hitstop** — a brief freeze on attacker and victim when a swing connects.
2. **Honest tooltips** — moveset, swing time and the current grip's modifiers, computed from the same numbers
   the game uses.
3. **Combo cue** — a subtle pulse on the target outline while a chained second swing is available.
4. **Jump attacks** — as above.

## Assets

- Body clips: swing (`swing_left_to_right`), chop (`slam_left_to_right`), lunge (`lunge_left_to_right`) exist.
- **Thrust clip** — user will author `thrust_left_to_right`.

## Kept from 0.1.0

Click-to-attack with screen-space target pick, PoE2 snap (distance-scaled), one-two combo + chain window,
breakable tracking, pitched arcs, narrow strikes that ignore i-frames, shared swing geometry driving pose /
weapon / trail, authored body clips time-warped onto phases, first-person model, attack movement speed,
`config/exiled_combat.json` synced from the server.

## Settled

- **Chop and auto two-hand:** chop stays one-handed with an empty off hand (decided 2026-10-17).
- **Reach:** the wielder's entity-interaction-range attribute, plus a per-moveset bonus (thrust +0.75, fists
  −0.5); snaps reach 7 blocks further.
- **Non-weapons:** anything that adds no attack damage (blocks, food, an empty hand) punches as fists.

## Build order (proposed)

1. ~~Moveset detection + removal of the datapack profiles; timing from attack speed; grip modifiers.~~ Done.
2. ~~Jump attacks (downward chop / thrust).~~ Done: `Moveset.AERIAL` shape, slam clip, `airborne` in both packets.
3. ~~Hitstop.~~ Done: session clock pauses on a hit (server + own-client prediction, `HitstopS2C` for others), chain window extended, victim shake; `hitstopTicks` 3 (+1 chop/jump).
4. ~~Tooltips.~~ Done: moveset, per-grip first→second swing times and damage change (current grip marked), reach — from the planner's own functions; translatable.
5. ~~Combo cue.~~ Done: hovered target's outline pulses white↔gold while the chain window is open (`Entity.getTeamColorValue` client mixin).
6. Thrust clip hook-up once authored.
