# Utility+ (NeoForge 1.21.1)

[![Build Status](https://github.com/Towartz/M-Neoforge/actions/workflows/build.yml/badge.svg)](https://github.com/Towartz/M-Neoforge/actions/workflows/build.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://www.minecraft.net/)
[![NeoForge Version](https://img.shields.io/badge/NeoForge-21.1.249-orange.svg)](https://neoforged.net/)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Latest Release](https://img.shields.io/github/v/release/Towartz/M-Neoforge?include_prereleases&color=red)](https://github.com/Towartz/M-Neoforge/releases/latest)

Utility+ is a high-performance, modular utility client engineered for **Minecraft 1.21.1 on NeoForge**. Adapted and overhauled from Meteor Client, it incorporates direct source integration of **Baritone 1.11.3**, an embedded **Model Context Protocol (MCP)** server providing 30 autonomous tools for external AI agents, a multi-stage **AutoCraft** engine, and a custom **Sodium 0.6 chunk rendering pipeline** that enables full X-Ray and WallHack block translucency.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Key Subsystems](#key-subsystems)
   - [Model Context Protocol (MCP) Server & 30 Tools](#1-model-context-protocol-mcp-server--30-tools)
   - [Autonomous AutoCraft Engine](#2-autonomous-autocraft-engine)
   - [Sodium 0.6 Translucency Pipeline](#3-sodium-06-translucency-pipeline)
   - [Integrated Baritone 1.11.3 & Pathing](#4-integrated-baritone-1113--pathing)
3. [Module Directory](#module-directory)
4. [Command Reference](#command-reference)
5. [In-Game Starscript Engine](#in-game-starscript-engine)
6. [External AI Assistant Setup](#external-ai-assistant-setup)
7. [Installation & Requirements](#installation--requirements)
8. [Building from Source](#building-from-source)
9. [Troubleshooting](#troubleshooting)
10. [Upstream Credits & License](#upstream-credits--license)

---

## Architecture Overview

Utility+ operates as a standalone NeoForge mod (`modid: utility`) with embedded background services and native mixin hooks into the Minecraft game engine:

```text
+-------------------------------------------------------------------------------+
|                             External AI Assistants                            |
|        [Claude Desktop]       [Google Antigravity]       [Cursor IDE]         |
+-------------------------------------------------------------------------------+
                                      | (stdio JSON-RPC)
                                      v
                         +--------------------------+
                         |   tools/mcp_bridge.py    |
                         +--------------------------+
                                      | (HTTP POST /mcp)
                                      v
+-------------------------------------------------------------------------------+
|                       Utility+ Client Runtime (Port 25590)                    |
|                                                                               |
|  +-------------------------------------------------------------------------+  |
|  |              Embedded HTTP / JSON-RPC 2.0 MCP Server                    |  |
|  |    Endpoints: /tools (GET), /call (POST), /mcp (POST), /status (GET)    |  |
|  +-------------------------------------------------------------------------+  |
|                                      |                                        |
|  +-------------------------------------------------------------------------+  |
|  |                 MinecraftToolRegistry (30 Native Tools)                 |  |
|  |   Perception | Movement | Mining | Building | Crafting | Inventory | Combat |  |
|  +-------------------------------------------------------------------------+  |
|            |                        |                         |               |
|            v                        v                         v               |
|  +--------------------+   +--------------------+   +--------------------+     |
|  |  Integrated        |   |  AutoCraft Engine  |   |  Sodium Pipeline   |     |
|  |  Baritone 1.11.3   |   |  (Recursive Solve  |   |  (Translucent Quad |     |
|  |  (C++ Pathfinder)  |   |  & Container Loot) |   |  Material Mapping) |     |
|  +--------------------+   +--------------------+   +--------------------+     |
|            |                        |                         |               |
|            +------------------------+-------------------------+               |
|                                     |                                         |
|                                     v                                         |
|                    Minecraft 1.21.1 Engine (NeoForge)                         |
+-------------------------------------------------------------------------------+
```

---

## Key Subsystems

### 1. Model Context Protocol (MCP) Server & 30 Tools

Utility+ bundles `com.cope.meteormcp` directly into its core lifecycle via `AddonManager`. An embedded HTTP/JSON-RPC server starts automatically on `127.0.0.1:25590`, allowing local and remote agents to inspect and manipulate player state with millisecond latency.

#### Endpoints
- `GET  /tools`  – Enumerates all registered tool definitions with full JSON Schema specifications.
- `POST /call`   – Executes a specific tool by name (`{"name": "...", "arguments": {...}}`).
- `POST /mcp`    – Standard JSON-RPC 2.0 endpoint (`initialize`, `tools/list`, `tools/call`).
- `GET  /status` – Returns server operational health, latency, active dimension, and coordinates.

#### Complete 30-Tool Taxonomy

| Category | Tool Identifier | Parameters | Description |
|---|---|---|---|
| **Perception** | `get_player_status` | *None* | Position, health, food, saturation, dimension, active effects, main/offhand items. |
| | `get_nearby_entities` | `radius` (num), `entity_type` (str) | Scans for living entities, dropped items, or vehicles within bounding radius. |
| | `get_nearby_blocks` | `radius` (num), `block_name` (str) | Identifies matching block coordinate positions within spherical reach. |
| **Movement** | `move_to` | `x` (num), `y` (num), `z` (num) | Sets Baritone coordinate pathing target and begins immediate navigation. |
| | `pathfind_to` | `x` (num), `y` (num), `z` (num) | Alias for pathfinding to target coordinate. |
| | `stop` | *None* | Cancels all active Baritone pathfinding, mining, or follow behaviors immediately. |
| | `follow` | `entity_name` (str) | Commands Baritone to track and navigate to a named player or entity. |
| | `look_at` | `yaw` (num), `pitch` (num) | Adjusts client player look angles directly. |
| | `jump` | *None* | Triggers a single jump impulse on the client player physics engine. |
| **Mining** | `mine_block` | `block_name` (str) | Sets Baritone target block type and mines all instances in range. |
| | `mine_at` | `x` (int), `y` (int), `z` (int) | Directly navigates to and mines the block at exact target coordinates. |
| **Building** | `place_block` | `x` (int), `y` (int), `z` (int), `block_item` (str) | Places designated block item against target face from player inventory. |
| | `build_schematic` | `name` (str) | Loads and begins building an active Baritone schematic file. |
| | `clear_area` | `x1`, `y1`, `z1`, `x2`, `y2`, `z2` (int) | Excavates all solid blocks within bounding coordinate prism. |
| **Crafting** | `craft_item` | `item_name` (str), `count` (int) | Queues item for autonomous crafting via the AutoCraft solver. |
| | `craft_set` | `set_type` (armor/tools), `material` (str), `count` (int) | Queues complete 4-piece armor or 5-piece tool sets of requested material. |
| | `cancel_crafting`| *None* | Clears active crafting queue and returns state machine to `IDLE`. |
| | `get_craft_status`| *None* | Queries active AutoCraft state, remaining items, and pending queue size. |
| **Inventory** | `get_inventory` | *None* | Returns complete 36-slot player inventory listing item IDs, counts, and damage. |
| | `equip_item` | `item_name` (str), `slot` (str) | Moves specified item from inventory into hotbar, main hand, or armor slots. |
| | `drop_item` | `item_name` (str), `count` (int) | Drops designated items from player inventory onto the ground. |
| | `use_item` | `hand` (main/off) | Sends interact packet to use active item (e.g. splash potion, ender pearl). |
| | `eat_food` | *None* | Selects best available food item from inventory and consumes it. |
| **Combat** | `attack_target` | `entity_name` (str) | Targets and attacks entity within reach distance using best available weapon. |
| | `interact_block`| `x`, `y`, `z` (int), `hand` (str) | Performs right-click interaction on specified block position. |
| | `interact_entity`| `entity_name` (str) | Performs right-click interaction on specified entity (e.g. trading, riding). |
| **System** | `toggle_module` | `module_name` (str), `state` (bool) | Toggles any client module ON or OFF dynamically. |
| | `get_modules` | `category` (str, optional) | Lists all client modules with active state and categorization. |
| | `execute_command`| `command` (str) | Executes an in-game Utility+ chat command directly (e.g. `.damage 5`). |
| | `send_chat` | `message` (str) | Sends an in-game chat packet or server command (`/msg`, `/spawn`). |

---

### 2. Autonomous AutoCraft Engine

Located in `meteordevelopment.meteorclient.systems.modules.player.AutoCraft`, AutoCraft is a fully autonomous crafting engine capable of resolving missing recipes recursively, pulling ingredients from nearby chests or portable backpacks, placing crafting tables, and assembling items without manual intervention.

#### State Machine Pipeline
```text
[IDLE] ──> [RESOLVING] ──> [OPENING_BACKPACK] ──> [WITHDRAWING_FROM_BACKPACK]
                 │                                           │
                 v                                           v
       [NAVIGATING_TO_CHEST] ───────────────> [LOOTING_CHEST]
                 │                                           │
                 v                                           v
       [CRAFTING_TABLE_PLACING] ────────────> [NAVIGATING_TO_TABLE]
                 │                                           │
                 v                                           v
       [OPENING_TABLE] ─────────────────────> [CRAFTING] ──> [CLEANUP] ──> [IDLE]
```

- **`CraftPlanner`**: Analyzes target recipe requirements against current inventory. Resolves multi-tier dependencies (e.g. Raw Wood -> Planks -> Sticks -> Pickaxe) and detects whether crafting can occur in 2x2 player inventory or requires a 3x3 table.
- **`ContainerSearcher`**: Identifies nearby chests, shulker boxes, or barrels, paths via Baritone, opens containers, and extracts required ingredient quantities.
- **`BackpackAdapter`**: Detects equipped or held Sophisticated Backpacks portable crafting units to eliminate the need for placing physical crafting tables in the world.
- **`CraftCommand` (`.craft`)**: Command interface for queuing single items or complete sets directly from chat.
- **GUI Integration**: Top-level `AutoCraftTab` and `AutoCraftScreen` with interactive queue monitoring and confirmation dialog widgets (`WConfirmedButton`).

---

### 3. Sodium 0.6 Translucency Pipeline

Vanilla Meteor Client cannot render translucent blocks when Sodium is installed because Sodium completely bypasses the vanilla block rendering pipeline, compiling chunks directly into custom GPU vertex buffers.

Utility+ resolves this through `SodiumBlockRendererMixin`:

```text
[Sodium Chunk Meshing Pipeline]
              │
              v
       processQuad()  ──>  Intercepted by SodiumBlockRendererMixin
              │
              ├── If block is in Xray / WallHack active set:
              │     1. Remap quad material to DefaultMaterials.TRANSLUCENT
              │     2. Set custom alpha mask: (alpha & 0xFF) << 24 | (rgb & 0x00FFFFFF)
              │
              v
       bufferQuad()   ──>  Buffers translucent vertices with true alpha into GPU VAO
```

- **Zero Performance Penalty**: Block lookups are performed against fast `ReferenceOpenHashSet<Block>` instances instead of array list iterations.
- **Compatibility**: Supports Sodium 0.6+ and Iris shader pipeline. Translucency warnings previously present in settings menus have been eliminated.

---

### 4. Integrated Baritone 1.11.3 & Pathing

Rather than shading a third-party Baritone JAR, Baritone 1.11.3 is compiled directly from source within `src/main/java/baritone` and `dev/babbaj/pathfinder`.

- **C++ Native NetherPathfinder**: Embedded multi-platform natives (`natives.zip.xz`) extracted dynamically on launch for accelerated A* calculation across Nether dimensions.
- **Dynamic Surface Goal (`GoalDynamicSurface`)**:
  - Automatically identifies cavern openings, valley floors, and sky-exposed air shafts.
  - Applies a sky-light gradient heuristic to navigate players toward sunlight.
  - Detects nearby water elevators and vertical air shafts for rapid ascending.
  - Nether-aware: Routes players safely between Y=65 and Y=95, preventing entrapment under the bedrock ceiling or routes intersecting lava ocean levels.
- **Movement Module Integration**: Dynamically synchronizes jump and step inputs with active client modules (`Step`, `HighJump`, `Speed`, `SafeWalk`), preventing off-path aborts and camera flicker.

---

## Module Directory

Utility+ features 150+ modules organized across 6 primary categories:

### Combat
- **`MaceDamage`**: Spoofs fall distance and injects ground-reset packets upon attacking to trigger maximum smash damage with 1.21 Maces without requiring physical elevation drops.
- **`Criticals`**: Packet and mini-jump spoofing ensuring 100% critical hit ratios on all standard attacks.
- **`KillAura`**: Automated target tracking, weapon switching, and attack scheduling supporting custom FOV, delay modes, and priority filters.
- **`AutoTotem`**: Automatically swaps Totems of Undying to the offhand slot before lethal damage occurs.
- **`CrystalAura` / `BedAura` / `AnchorAura`**: High-speed end crystal, bed, and respawn anchor placement and detonation engine.

### Player
- **`AutoCraft`**: Fully autonomous recursive crafting engine with container search and backpack support.
- **`NoStatusEffects`**: Suppresses negative potion and status effects client-side (Levitation, Slowness, Mining Fatigue, Darkness, Blindness) using `ReferenceOpenHashSet` filtering inside `LivingEntityMixin`.
- **`AutoEat` / `AutoGap`**: Automatically consumes food or enchanted golden apples when hunger or health thresholds are breached.
- **`AutoTool`**: Dynamically switches to the fastest effective tool before breaking targeted blocks.
- **`ChestSwap`**: Instantaneous hotkey-based chestplate-to-elytra equipment swaps.

### Movement
- **`SafeWalk`**: Prevents players from stepping off edges. Features configurable edge distance offsets, minimum fall distance gating, sprint awareness, and 3D bounding box debug rendering.
- **`ReverseStep`**: Pulls player instantly downward off ledges with slab, stair, and bed-trap detection to prevent fall damage or trap entry.
- **`ElytraFly`**: Multi-mode Elytra flight controller (Bounce, 2b2t, Packet, Pitch40) with anti-fall safety.
- **`Speed` / `Step` / `AirJump` / `Jesus`**: High-speed ground locomotion, obstacle step-up, mid-air jumping, and water surface walking.

### World
- **`PacketMine`**: Packet-based block mining engine featuring **Predict Mode** (`invokeStartPrediction`), `obscureBreakingProgress` (sends `ABORT_DESTROY_BLOCK` to mask cracks from spectators), safe slot restoration, and auto-rebreak caching.
- **`GotoSurface`**: Automatically escapes underground mines and caves to the surface using `GoalDynamicSurface`.
- **`ChunkScanner`**: Multi-threaded chunk inspector and ore radar displaying exact ore counts, fortune estimations, and waypoint paths.
- **`BonemealAura` / `FeedAura` / `TillAura`**: Autonomous agricultural automation modules for crops, animal breeding, and land cultivation.
- **`HighwayBuilder`**: Multi-block automated nether highway excavation, obsidian floor laying, and paving engine.

### Render
- **`WallHack` / `Xray`**: Translucent wall and ore visualizer fully integrated with the Sodium chunk rendering pipeline.
- **`WeatherChanger`**: Client-side weather override allowing independent rain level and thunder intensity control.
- **`ESP` / `Tracers` / `StorageESP`**: Highlights players, hostile mobs, containers, and waypoints with customizable shaders, health bars, and corner brackets.
- **`PopChams`**: Spawns rendered ghost figures whenever an enemy entity pops a Totem of Undying.

### Misc
- **`AIAgent`**: Embedded autonomous loop that queries local or cloud LLMs, plans multi-step actions, and executes tools in sequence.
- **`BetterChat`**: Chat history expansion, message timestamps, spam protection, and custom formatting.
- **`DiscordPresence`**: Rich Presence integration showing current server, coordinates, and active module status.

---

## Command Reference

Utility+ commands use the `.` prefix by default.

### Automation & Crafting Commands

| Command Syntax | Description | Example |
|---|---|---|
| `.craft <item> [count]` | Queues autonomous crafting for specified item | `.craft golden_apple 16` |
| `.craft armor <material> [count]` | Queues complete 4-piece armor set | `.craft armor diamond 1` |
| `.craft tools <material> [count]` | Queues complete 5-piece tool set | `.craft tools iron 1` |
| `.craft status` | Displays active task, progress, and queue length | `.craft status` |
| `.craft cancel` | Aborts current crafting task and clears queue | `.craft cancel` |
| `.craft gui` (or `.craft ui`) | Opens the AutoCraft management dashboard | `.craft gui` |

### Artificial Intelligence & MCP Commands

| Command Syntax | Description | Example |
|---|---|---|
| `.ai <prompt>` | Direct text query to configured LLM (no tool calls) | `.ai How do I breed villagers?` |
| `.ai-mcp <prompt>` | Query LLM with live Minecraft tool execution | `.ai-mcp Find iron ore and mine it` |
| `.ai-task <goal...>` | Starts the multi-step autonomous `AIAgent` loop | `.ai-task gather 64 wood and craft chests` |
| `.ai-task status` | Returns current goal step and agent status | `.ai-task status` |
| `.ai-task cancel` | Aborts active agent loop and stops pathfinding | `.ai-task cancel` |
| `.<server>:<tool> [args]` | Manually invokes any MCP tool from chat | `.minecraft:jump` |

### Macro & Utility Commands

| Command Syntax | Description | Example |
|---|---|---|
| `.macro <macro>` | Executes configured macro immediately | `.macro EatAndPot` |
| `.macro <macro> <delay>` | Schedules macro execution after tick delay | `.macro DropTrash 100` |
| `.macro clear [macro]` | Clears scheduled macro queue or specific macro | `.macro clear` |
| `.surface` | Begins autonomous navigation to surface/Nether safe Y | `.surface` |
| `.cs` | Toggles and opens Chunk Scanner interface | `.cs` |
| `.damage <hearts>` | Applies client-side fall damage spoof | `.damage 5` |
| `.help [page/command]` | Displays interactive help manual for all commands | `.help craft` |

---

## In-Game Starscript Engine

Utility+ includes a fast, native Starscript evaluator used throughout HUD text elements, window titles, and chat formatting.

### Available Property Namespaces

| Starscript Expression | Output Type | Description |
|---|---|---|
| `{utility.name}` | String | Mod display name (`Utility+`). |
| `{utility.version}` | String | Mod version string (`0.5.8`). |
| `{utility.modules}` | Number | Total registered modules count. |
| `{utility.active_modules}` | Number | Currently enabled modules count. |
| `{utility.is_module_active("id")}` | Boolean | Checks if named module is currently active. |
| `{utility.get_module_info("id")}` | String | Fetches dynamic module status string. |
| `{baritone.process_name}` | String | Active Baritone process name (e.g. `PathingCommand`). |
| `{baritone.target}` | String | Active Baritone coordinate target `(X, Y, Z)`. |
| `{mcp.minecraft.get_player_status()}` | Object | Live JSON payload from MCP player status tool. |

---

## External AI Assistant Setup

Connect external AI assistants (Claude Desktop, Google Antigravity, Cursor IDE) to your running Minecraft client using the zero-dependency bridge script in `tools/mcp_bridge.py`.

```bash
# 1. Start Minecraft with Utility+ installed
# 2. Test the connection from a terminal:
python tools/mcp_bridge.py --check
```

### Configuration Snippets

#### Claude Desktop (`%APPDATA%\Claude\claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "utility-plus": {
      "command": "python",
      "args": [
        "C:\\Users\\Administrator\\Documents\\Meteor-Neoforge-build\\tools\\mcp_bridge.py"
      ]
    }
  }
}
```

#### Google Antigravity (`.agents/mcp_config.json`)
```json
{
  "mcpServers": {
    "utility-plus": {
      "command": "python",
      "args": [
        "tools/mcp_bridge.py"
      ]
    }
  }
}
```

#### Cursor IDE (`.cursor/mcp.json`)
```json
{
  "mcpServers": {
    "utility-plus": {
      "command": "python",
      "args": [
        "${workspaceFolder}/tools/mcp_bridge.py"
      ]
    }
  }
}
```

For the complete 339-line guide and tool calling recipes, see **[docs/MCP_GUIDE.md](docs/MCP_GUIDE.md)**.

---

## Installation & Requirements

### System Requirements
- **Java Runtime**: JDK / JRE **21** (64-bit).
- **Minecraft**: **1.21.1**.
- **Mod Loader**: **NeoForge** `>= 21.1.249`.

### Installation Steps
1. Download `utility-neoforge-1.21.1-0.5.8.jar` from the [Latest Release](https://github.com/Towartz/M-Neoforge/releases/latest).
2. Place the JAR inside your Minecraft profile `.minecraft/mods` directory.
3. Launch Minecraft using the NeoForge profile.
4. Open the in-game GUI by pressing `Right Shift` (configurable in Options -> Controls -> Key Binds).

> [!NOTE]
> **Configuration Auto-Migration**: If upgrading from an older Meteor Client installation, Utility+ automatically copies your settings, waypoints, macros, and accounts from `.minecraft/meteor-client` to `.minecraft/utility` on initial launch.

---

## Building from Source

### Prerequisites
- JDK 21 installed and available on system path (`java -version`).
- Git for repository cloning.

### Build Steps

```bash
# Clone the repository
git clone https://github.com/Towartz/M-Neoforge.git
cd M-Neoforge

# Compile and package the mod JAR
./gradlew jar
```

Upon completion, the compiled, shaded mod artifact will be available at:
```text
build/libs/utility-neoforge-1.21.1-0.5.8.jar
```

---

## Troubleshooting

- **`Port 25590 Already in Use`**: Another instance of Minecraft or a stale MCP process is bound to the port. Check active listeners with `netstat -ano | findstr 25590` and terminate the orphaned process.
- **`Baritone Pathing Fails in the Nether`**: Ensure the player is between Y=32 and Y=100. If near the bedrock ceiling, activate `.surface` or clear an air opening so `GoalDynamicSurface` can compute a safe path.
- **`X-Ray Not Transparent with Shaders`**: When using Iris shaders with active shadow maps, block opacity is constrained by the active shaderpack. Toggle off shader packs or use standard Sodium rasterization for full translucency.

---

## Upstream Credits & License

Utility+ is developed and maintained by **Towartz** and contributors. Built upon foundational work by:
- **[Meteor Client](https://github.com/MeteorDevelopment/meteor-client)** by MineGame159, squidoodly, and seasnail (GPL-3.0).
- **[Baritone](https://github.com/cabaletta/baritone)** by cabaletta, Brady, and leijurv (LGPL-3.0).
- **[Meteor MCP Addon](https://github.com/GhostTypes/Meteor-MCP-Addon)** by GhostTypes and cope (GPL-3.0).

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See `LICENSE` for details.
