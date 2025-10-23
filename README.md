# EgyptianCasino Plugin

EgyptianCasino is a Paper plugin that extends the AmethystControl economy with Egyptian Tokens, immersive NPC exchanges, and modular casino experiences. Players can convert minted crystals from AmethystControl into tokens, then wager them on minigames such as blackjack, dice, and the new Pharaoh Slots machine.

## Requirements

- Paper (or another Spigot-compatible server) 1.19+
- [AmethystControl](https://github.com/) plugin installed and configured with minted crystals
- Java 17+

## Installation

1. Build the plugin with Gradle:
   ```bash
   ./gradlew build
   ```
   The wrapper script downloads and caches the Gradle distribution on first run (requires `curl` or `wget`). The compiled plugin will be placed in `build/libs/EgyptianCasino-<version>.jar`.
2. Copy both `EgyptianCasino-<version>.jar` and your existing `AmethystControl` plugin into the server's `plugins/` folder.
3. Restart or reload the server.

Player balances are written to `tokens.yml` in the plugin data folder.

## Configuration

The default configuration is stored in `config.yml` and copied to the plugin's data folder on first run. You can edit the file and reload the plugin to change its behaviour:

```yaml
# Number of Egyptian Tokens granted per minted crystal redeemed with the Priest of Ra
tokens-per-crystal: 1

priest:
  # When true, spawned priests will be frozen in place and cannot walk away.
  disable-movement: true
  # Make the Priest of Ra invulnerable to damage.
  invulnerable: true
```

- `tokens-per-crystal`: sets how many Egyptian Tokens a single minted crystal is worth when exchanged.
- `priest.disable-movement`: toggles whether the Priest of Ra can wander away after being spawned.
- `priest.invulnerable`: protects the Priest of Ra from all damage.

After making changes, restart the server (or reload the plugin) to apply them.

## Commands

| Command | Description |
| --- | --- |
| `/egyptiancasino` | Shows plugin help, including registered minigames. |
| `/egyptiancasino balance` | Displays the player's current Egyptian Token balance. |
| `/egyptiancasino <game> <bet>` | Plays a registered casino game (slots, blackjack, dice). |
| `/egyptiancasino spawnpriest` | Spawns the Priest of Ra villager (admin only). |
| `/egyptiancasino givetokens [amount]` | Gives gold nugget token items with `CustomModelData:1010` for resource-pack testing (admin only). |
| `/giveslot [amount]` | Gives the Pharaoh Slot Machine placement item (admin only). |

## Spawning the Priest of Ra

Administrators can summon the exchange NPC directly in game:

1. Ensure your player has the `egyptiancasino.admin` permission.
2. Stand at the location where you want the NPC to appear.
3. Run `/egyptiancasino spawnpriest`.

This command spawns a desert villager named **Priest of Ra**. When players right-click the Priest, any minted crystals in their inventory are converted into Egyptian Tokens automatically. Each crystal is validated and marked as redeemed through AmethystControl, so they cannot be exchanged twice.

If you ever move or respawn the Priest manually, you can reapply the identification tag by running `/egyptiancasino spawnpriest` again.

## Pharaoh Slots Machine

Pharaoh Slots introduces a physical, two-block tall slot machine that uses BlockDisplay and ItemDisplay entities to render an ancient Egyptian cabinet with turquoise inlays and glowing crystals.

### Placement and Ownership

1. Use `/giveslot` to obtain the **Slot Machine** item (base material: Blaze Rod with `CustomModelData:2100`).
2. Right-click any solid surface with at least two blocks of vertical clearance to place the machine.
3. The placer becomes the owner. Only the owner can dismantle it by sneaking and left-clicking any part of the machine.

The item uses the plugin's `PersistentDataContainer` markers to track ownership. When a machine is broken by its owner, the placement item is dropped back at the base location.

### Gameplay Flow

- Right-click the machine (any player) to pull the lever.
- The machine withdraws **1 Egyptian Token** from the player's balance.
- A lever animation plays, the reels spin for ~3 seconds, and stop one-by-one.
- Outcomes are processed through `SlotMachineManager`/`EgyptSlots`:
  - **3 matching symbols:** rewards +5 tokens, plays a victory fanfare, and emits golden glow particles.
  - **2 matching symbols:** rewards +1 token with a small win sound and sparks.
  - **0 matches:** no reward, with a sandy puff effect.

Results, particles, and sounds are broadcast to nearby players, ensuring the experience feels shared on busy servers.

### Editing Reel Symbols or Rewards

The reel `CustomModelData` values and payout table are centralized in [`src/main/java/dev/lixqa/egyptiancasino/slotmachine/EgyptSlots.java`](src/main/java/dev/lixqa/egyptiancasino/slotmachine/EgyptSlots.java). Update the list in `createDefaultSymbolList()` to align with your resource-pack models, and adjust the `rewardForMatches()` switch statement to rebalance winnings.

## Resource Pack Setup

The `resource-pack/` directory contains JSON stubs for the Pharaoh Slots assets. Replace the placeholder textures, sounds, and animation references with your production files.

### 1. Custom Items

- `assets/minecraft/models/item/gold_nugget.json` – Adds an override for `CustomModelData:1010` → `item/egypt_token`. Supply `textures/item/egypt_token.png` (recommended 32×32 or 64×64) for the minted token artwork.
- `Slot Machine` placement item uses Blaze Rod with `CustomModelData:2100`. Create a matching model (for example `item/slot_machine.json`) that references your Blockbench export if you want a custom inventory icon.

### 2. Block Models & Animations

Export your Blockbench models as `.json` and drop them into:

```
resource-pack/
└── assets/minecraft/
    ├── models/
    │   ├── block/egyptslot_machine.json
    │   ├── block/slot_reel_1.json
    │   ├── block/slot_reel_2.json
    │   ├── block/slot_reel_3.json
    │   └── block/slot_lever.json
    └── animations/
        ├── slot_lever.animation.json
        └── slot_reel_spin.animation.json
```

The provided stubs include `_comment` fields marking where to hook up your final texture names (for example `block/egyptslot_machine.png`, `block/slot_lever.png`, and a glow layer). Recommended texture resolution is 64×64 or higher for crisp cabinet details.

### 3. Hooking Up Animations

- `animations/slot_lever.animation.json` – Contains forward/back keyframes for a 0.6 second lever pull (32° swing). Rename the `lever` bone and adjust keyframes to match your Blockbench rig.
- `animations/slot_reel_spin.animation.json` – Spins a reel bone twice over three seconds. Tie this to your reel bone or use texture cycling if you prefer frame-based symbols.

### 4. Registering CustomModelData

Ensure your item overrides point to the models above. The gold nugget override is preconfigured for the Egyptian Token. Update `resource-pack/assets/minecraft/models/item/gold_nugget.json` if you change model names or add additional predicates.

### 5. Testing In-Game

1. Load the resource pack on your test client or server.
2. Run `/egyptiancasino givetokens 16` to preview the Egyptian Token item.
3. Run `/giveslot` to obtain the Slot Machine item.
4. Place the machine, pull the lever, and confirm the lever/reel animations line up with your models.

> **Note:** The packaged textures and animation JSONs are placeholders. The server operator must supply the final `.png` files and fully animated `.animation.json` content before deployment.

## Token Storage

Player balances are tracked in memory and saved asynchronously to `tokens.yml`. The token manager guards against concurrent updates so balances remain consistent even when multiple games operate at once.

## Development Notes

- Game logic lives in `src/main/java/dev/lixqa/egyptiancasino/game/`.
- Pharaoh Slots runtime code resides in `src/main/java/dev/lixqa/egyptiancasino/slotmachine/`.
- Token persistence is handled through `TokenManager` and `YamlTokenStorage` in `src/main/java/dev/lixqa/egyptiancasino/tokens/`.
- The Priest of Ra functionality is encapsulated in `PriestOfRaManager` and the `CrystalExchangeListener` under `src/main/java/dev/lixqa/egyptiancasino/exchange/`.

Feel free to add new games by implementing the `CasinoGame` interface and registering the instance in `EgyptianCasinoPlugin#setupGames`.
