# Clean Combos API

Everything in `com.cleannrooster.clean_combos.api` — plus the enums it hands out (`WeaponType`, `Moveset`,
`Grip`, `SwingHand`, `ComboSlot`, `AttackPhase`) — is stable across 1.x. Everything else is internal.

The entry point is `CleanCombosApi`. All of it is read-only or opt-in: Clean Combos itself builds nothing on the
damage kinds or the events, so they cost nothing until an add-on uses them.

## Classifying items

```java
WeaponType type = CleanCombosApi.weaponType(stack);   // SWORD, RAPIER, CLAW, MACE, FIST...
Moveset moveset = CleanCombosApi.moveset(stack);       // SWING, CHOP, THRUST, FIST; null = vanilla attack
DamageKind kind = CleanCombosApi.damageKind(stack);    // SLASHING, PIERCING, BLUDGEONING; null = vanilla
```

| Weapon types | |
|---|---|
| Edged | `sword`, `greatsword`, `katana`, `rapier`, `dagger`, `axe`, `scythe`, `halberd`, `spear`, `trident`, `claw` |
| Blunt | `mace`, `hammer`, `staff`, `fist`, `tool`, `other` |

`WeaponType.id()` gives the stable lower-case name; `translationKey()` a display name.

### Tags: classifying your own items

Detection is automatic (class, tags, then the item id's name), but weapon mods and modpacks can state it:

| Tag | Effect |
|---|---|
| `#clean_combos:weapon_type/<type>` | the item's weapon type, e.g. `weapon_type/rapier` |
| `#clean_combos:moveset/<moveset>` | how it attacks: `swing`, `chop`, `thrust` or `fist` |
| `#clean_combos:vanilla_attack` | left to vanilla's own attack entirely |

`data/clean_combos/tags/item/weapon_type/rapier.json`:

```json
{ "replace": false, "values": ["mymod:silver_rapier", "#mymod:rapiers"] }
```

The server config's `movesetOverrides` still wins over the moveset tags. Constants: `CleanCombosTags`.

## Damage kinds

`DamageKind` is decided per attack: the attack decides first, the weapon decides edged or blunt.

| Attack | Edged weapon | Blunt weapon |
|---|---|---|
| Swing, chop, jump chop | `SLASHING` | `BLUDGEONING` |
| Thrust (spears, running attacks) | `PIERCING` | `BLUDGEONING` |
| Punch | `SLASHING` with claws | `BLUDGEONING` |

Grip doesn't change the kind; it travels with the hit in `AttackInfo.grip()`.

## Attack state

```java
CleanCombosApi.isAttacking(entity);
AttackInfo attack = CleanCombosApi.activeAttack(entity);   // null between attacks
AttackPhase phase = CleanCombosApi.attackPhase(entity);    // WINDUP, STRIKE, RECOVERY
float progress = CleanCombosApi.attackProgress(entity);    // 0..1
float hitAt = CleanCombosApi.strikeProgress(entity);       // the progress at which the hit lands
ComboSlot next = CleanCombosApi.nextComboSlot(player);     // FIRST or SECOND
boolean chain = CleanCombosApi.isChainOpen(player);
```

On the server these work for every player. On a client they work for every player in view (other players'
attacks are mirrored), and combo state for the local player only.

`AttackInfo` carries: `attacker`, `weapon`, `weaponType`, `moveset` (situational: `AERIAL` for a jump attack,
`THRUST` for a running sword attack), `kind`, `grip`, `hand`, `slot`, `jumpAttack`, `running`.

## Events

```java
// Refuse an attack (stamina, silence...). Fires on the attacking client and the server: register on both,
// with the same rule, or the client will animate an attack the server refuses.
CleanCombosApi.onAttackStart(attack -> stamina(attack.attacker()) > 0);

// Each target, just before it's hit. Server only.
CleanCombosApi.onStrike((strike, modifier) -> {
    if (strike.attack().kind() == DamageKind.BLUDGEONING && isSkeleton(strike.target())) {
        modifier.multiplyDamage(1.5f);
    }
    // modifier.cancel() skips this target.
});

// An attack that hit nothing (or whose every target was cancelled). Server only.
CleanCombosApi.onMiss(attack -> resetComboMeter(attack.attacker()));
```

`StrikeInfo` is the attack plus the `target` and `critical` (vanilla's crit conditions hold).

### Inside the hit: `currentStrike()`

Clean Combos hits through vanilla's own player attack, so damage keeps its normal `player_attack` source and
every enchantment and mod that hooks player attacks keeps working. While each target's hit resolves,
`CleanCombosApi.currentStrike()` returns its `StrikeInfo` — so an existing damage hook can tell a Clean Combos hit,
and its kind, from any other:

```java
StrikeInfo strike = CleanCombosApi.currentStrike();
if (strike != null && strike.attack().kind() == DamageKind.PIERCING) { ... }
```

It's null outside a hit, and set on the server thread only.
