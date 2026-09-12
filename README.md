# Utility+ (formerly Meteor) - Model Context Protocol (MCP) Addon (NeoForge 1.21.1)

An autonomous AI-powered utility client for Minecraft 1.21.1 (NeoForge). Integrates the **Model Context Protocol (MCP)** directly into Utility+, enabling local LLMs (Ollama, Gemini) and external AI agents (**Claude Desktop**, **Google Antigravity**, **Cursor**) to observe, plan, and automate gameplay in real-time.

---

## Highlights

- **30 High-Level Autonomous Minecraft Tools**:
  - **Movement & Pathfinding**: `move_to`, `pathfind_to`, `stop`, `follow`, `look_at`, `jump` (powered by Baritone).
  - **Mining & Excavation**: `mine_block`, `mine_at`.
  - **Building & Clearing**: `place_block`, `build_schematic`, `clear_area`.
  - **Crafting**: `craft_item`, `craft_set` (armor/tools), `cancel_crafting`, `get_craft_status` (powered by AutoCraft).
  - **Inventory Management**: `get_inventory`, `equip_item`, `drop_item`, `use_item`, `eat_food`.
  - **Perception & Sensors**: `get_player_status`, `get_nearby_entities`, `get_nearby_blocks`.
  - **Combat & Interaction**: `attack_target`, `interact_block`, `interact_entity`.
  - **Client & Chat Control**: `toggle_module`, `get_modules`, `execute_command`, `send_chat`.
- **Autonomous Multi-Step AI Agent**:
  - `AIAgent` module (`Category.Misc`) that autonomously loops perception, LLM planning, and action until complex goals are completed.
  - Chat commands: `.ai <prompt>`, `.ai-mcp <prompt>`, `.ai-task <goal...>`, `.ai-task status`, `.ai-task cancel`.
- **Embedded HTTP & JSON-RPC MCP Server**:
  - Listens on `http://127.0.0.1:25590/` with zero external dependencies.
  - Endpoints: `/tools`, `/call`, `/mcp`, `/status`.
- **External AI Assistant Integration**:
  - Seamless support for **Claude Desktop**, **Google Antigravity**, **Cursor IDE**, and **Claude Code** via `tools/mcp_bridge.py`.
- **In-Game StarScript**:
  - Use live tool outputs in HUD elements (`{mcp.minecraft.get_player_status()}`).

---

## Quick Start

### 1. Build the Mod
Build the mod JAR using system Gradle:
```bash
gradle jar
```
The output file will be generated at:
```text
build/libs/utility-neoforge-1.21.1-0.5.8.jar
```

### 2. Install and Run
1. Place the generated JAR into your Minecraft `.minecraft/mods` (or `game/mods`) folder.
2. Launch Minecraft 1.21.1 with NeoForge.
3. Join a world or server.

### 3. Connect External AI Assistants
Run the diagnostic test:
```bash
python tools/mcp_bridge.py --check
```

See the complete step-by-step setup guides for **Claude Desktop**, **Antigravity**, **Cursor**, and **REST/Python** in:
👉 **[docs/MCP_GUIDE.md](docs/MCP_GUIDE.md)**

---

## Configuration Files

Pre-configured templates are available in the [`tools/`](tools/) folder:
- **Claude Desktop**: [`tools/claude_desktop_config.example.json`](tools/claude_desktop_config.example.json)
- **Google Antigravity**: [`tools/antigravity_mcp_config.example.json`](tools/antigravity_mcp_config.example.json)
- **Cursor IDE**: [`tools/cursor_mcp.example.json`](tools/cursor_mcp.example.json)

---

## In-Game AI Commands

| Command | Description | Example |
|---|---|---|
| `.ai <prompt>` | Query AI assistant without tool calls | `.ai What is the best armor in Minecraft?` |
| `.ai-mcp <prompt>` | Query AI with real-time Minecraft tool execution | `.ai-mcp Find nearby diamond ores and mine them` |
| `.ai-task <goal>` | Start autonomous multi-step agent | `.ai-task gather 10 wood and craft a crafting table` |
| `.ai-task status` | Check current agent goal and step status | `.ai-task status` |
| `.ai-task cancel` | Stop active agent and cancel all pathfinding | `.ai-task cancel` |
| `.<server>:<tool>` | Manually execute any MCP tool | `.minecraft:jump` |

---

## Documentation

- **[Complete MCP Integration & Usage Guide](docs/MCP_GUIDE.md)**: Full 30-tool dictionary, scenario recipes, and client setup guides.
- **[MCP Architecture & Walkthrough](walkthrough.md)**: Technical overview of porting, Baritone bindings, and server internals.
