# Utility+ Minecraft MCP: Complete Integration & Usage Guide

Welcome to the **Utility+ Model Context Protocol (MCP)** guide. This document explains how to connect external AI assistants—such as **Claude Desktop**, **Google Antigravity**, **Cursor IDE**, and custom scripts—directly to your running Minecraft client (**Utility+** on NeoForge 1.21.1) to observe the world and control the player in real-time.

---

## 1. Overview & Architecture

When Minecraft runs with the Utility+ client, it spins up an embedded HTTP/JSON-RPC server on `http://127.0.0.1:25590`. This server provides full access to 30 high-level gameplay automation tools built on top of **Baritone**, **AutoCraft**, and Utility+ Client systems.

To make connection effortless for desktop AI tools that use standard `stdio` communication, we provide `tools/mcp_bridge.py`, a zero-dependency Python bridge that routes JSON-RPC messages between your AI assistant and Minecraft.

```
+-------------------------------------------------------------+
|                     External AI Clients                     |
|  [Claude Desktop]   [Antigravity]   [Cursor]   [Claude Code]|
+-------------------------------------------------------------+
                              | (stdio JSON-RPC)
                              v
                 +--------------------------+
                 |   tools/mcp_bridge.py    |
                 +--------------------------+
                              | (HTTP POST /mcp)
                              v
+-------------------------------------------------------------+
|         Utility+ NeoForge Client (http://127.0.0.1:25590)   |
|                                                             |
|  [Embedded Server]  -->  [MinecraftToolRegistry (30 Tools)] |
|                                |                            |
|             +------------------+------------------+         |
|             |                  |                  |         |
|             v                  v                  v         |
|        [Baritone]         [AutoCraft]      [Player Engine]  |
|       (Path/Mine)        (Auto-Craft)     (Inventory/Action)|
+-------------------------------------------------------------+
```

---

## 2. 60-Second Quick Start

1. **Launch Minecraft** with the Utility+ client installed (`utility-neoforge-1.21.1-0.5.8.jar`).
2. Join any singleplayer world or multiplayer server.
3. Open a terminal in the project directory and test the bridge:
   ```bash
   python tools/mcp_bridge.py --check
   ```
4. If Minecraft is running, you will see:
   ```text
   [SUCCESS] Connected to Utility+ Minecraft MCP Server!
   Status: running | Tools available: 30
   ```
5. You are ready to connect Claude, Antigravity, or Cursor!

---

## 3. Client Setup Walkthroughs

### 3.1 Claude Desktop

Claude Desktop uses an MCP configuration file to launch tools.

#### Configuration File Location:
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`  
  *(Typically `C:\Users\<Username>\AppData\Roaming\Claude\claude_desktop_config.json`)*
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`

#### Configuration:
Open (or create) `claude_desktop_config.json` and add the `utility-plus` server:

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

> [!TIP]
> On Windows, make sure backslashes in paths are escaped (`\\`) or use forward slashes (`/`).

#### Verifying in Claude Desktop:
1. Restart Claude Desktop.
2. Look for the hammer (tool) icon in the bottom right of the prompt box.
3. You should see 30 tools available (e.g. `move_to`, `mine_block`, `craft_item`, `get_player_status`).
4. Type:
   > *"Check my status in Minecraft and tell me what is in my inventory."*
5. Claude will invoke `get_player_status` and `get_inventory` and report your exact coordinates and equipment!

---

### 3.2 Google Antigravity (CLI & IDE)

Antigravity natively discovers MCP servers from `mcp_config.json`.

#### Option A: Workspace-Specific Configuration (Recommended)
Place the configuration in your project under `.agents/mcp_config.json`:

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

#### Option B: Global Configuration
Edit `~/.gemini/config/mcp_config.json`:

```json
{
  "mcpServers": {
    "utility-plus": {
      "command": "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python314\\python.exe",
      "args": [
        "C:\\Users\\Administrator\\Documents\\Meteor-Neoforge-build\\tools\\mcp_bridge.py"
      ]
    }
  }
}
```

#### Testing in Antigravity:
Ask Antigravity in chat:
> *"What blocks and entities are nearby my player in Minecraft?"*

---

### 3.3 Cursor IDE / VS Code

In Cursor:
1. Open **Cursor Settings** (`Ctrl+,` or `Cmd+,`).
2. Go to **Features** > **MCP**.
3. Click **Add New MCP Server**:
   - **Name**: `utility-plus`
   - **Type**: `command`
   - **Command**: `python C:/Users/Administrator/Documents/Meteor-Neoforge-build/tools/mcp_bridge.py`
4. Or configure `.cursor/mcp.json` in your workspace:
   ```json
   {
     "mcpServers": {
       "utility-plus": {
         "command": "python",
         "args": [
           "C:/Users/Administrator/Documents/Meteor-Neoforge-build/tools/mcp_bridge.py"
         ]
       }
     }
   }
   ```
5. In Cursor Composer / Agent mode, ask:
   > *"Mine 10 oak_log blocks and craft them into planks."*

---

### 3.4 Claude Code CLI

Add the server with a single command:
```bash
claude mcp add utility-plus python C:/Users/Administrator/Documents/Meteor-Neoforge-build/tools/mcp_bridge.py
```

---

### 3.5 Direct Python / REST / cURL API

If you want to automate Minecraft from your own scripts, web applications, or bots, call the HTTP endpoints directly:

#### Inspect Available Tools:
```bash
curl http://127.0.0.1:25590/tools
```

#### Call Any Tool Directly:
```bash
curl -X POST http://127.0.0.1:25590/call \
  -H "Content-Type: application/json" \
  -d '{"tool": "move_to", "arguments": {"x": 100, "y": 64, "z": -200}}'
```

#### Python Example (`requests`):
```python
import requests

def call_tool(name, args=None):
    payload = {"tool": name, "arguments": args or {}}
    res = requests.post("http://127.0.0.1:25590/call", json=payload)
    return res.json()

# Get player status
status = call_tool("get_player_status")
print(status["content"][0]["text"])

# Move to coordinates
call_tool("move_to", {"x": 0, "y": 64, "z": 0})

# Queue crafting
call_tool("craft_item", {"item_name": "iron_sword", "count": 1})
```

---

## 4. In-Game AI Features & Commands

You can also control the AI directly from the Minecraft chat!

### Chat Commands:
- **`.ai <prompt>`**: Queries the configured LLM (Gemini or Ollama) with a simple question or instruction.
- **`.ai-mcp <prompt>`**: Instructs the LLM to execute tools directly in real-time.
- **`.ai-task <goal...>`**: Activates the autonomous multi-step agent to pursue a complex goal (e.g. `.ai-task gather 10 wood and craft a chest`).
- **`.ai-task status`**: Displays current agent state and goal.
- **`.ai-task cancel`**: Halts the agent and cancels all pathfinding.
- **`.minecraft:<tool> <json-args>`**: Executes any of the 30 tools manually as a command.

### In-Game GUI & Configuration:
- Press **Right Shift** (or your Utility+ GUI key).
- Navigate to the **MCP Tab** → **Configure AI**.
- **Gemini Auth Modes**:
  - **Antigravity CLI (`agy`)** *(Default)*: Runs via your local Google Antigravity CLI session without requiring any API keys.
  - **API Key**: Optional direct Google AI Studio API key configuration.
- Set Ollama endpoints (`http://localhost:11434`) for local models.
- Toggle the **AI Agent** module under the `Misc` category.

### StarScript Integration:
Use live game information in Utility+ HUD elements:
- `{mcp.minecraft.get_player_status()}`
- `{mcp.minecraft.get_inventory()}`

---

## 5. Complete 30-Tool Reference Dictionary

### 5.1 Movement & Navigation
| Tool Name | Parameters | Description |
|---|---|---|
| `move_to` | `x` (num), `y` (num), `z` (num) | Walks directly to block coordinates using Baritone pathfinding. |
| `pathfind_to` | `x` (num), `y` (num), `z` (num), `radius` (opt int, default 2) | Navigates to within `radius` blocks of target position. |
| `stop` | *(none)* | Halts all active movement, goals, and pathfinding immediately. |
| `follow` | `target` (str: entity name or type) | Follows a player, mob, or entity continuously. |
| `look_at` | `yaw` (opt num), `pitch` (opt num) OR `x`, `y`, `z` (opt num) | Rotates camera/head view to look at angles or 3D block. |
| `jump` | *(none)* | Makes player jump from the ground. |

### 5.2 Mining & Excavation
| Tool Name | Parameters | Description |
|---|---|---|
| `mine_block` | `block_name` (str, e.g. `'iron_ore'`), `count` (opt int, default 1) | Finds and mines specified block type using Baritone. |
| `mine_at` | `x` (num), `y` (num), `z` (num) | Pathfinds to and breaks the block at specific coordinates. |

### 5.3 Building & Area Clearing
| Tool Name | Parameters | Description |
|---|---|---|
| `place_block` | `block_name` (str), `x` (num), `y` (num), `z` (num) | Selects block from inventory and places it at position. |
| `build_schematic`| `schematic_name` (str) | Loads and builds a schematic file using Baritone. |
| `clear_area` | `x1`, `y1`, `z1`, `x2`, `y2`, `z2` (num) | Clears all blocks inside the specified 3D bounding box. |

### 5.4 Crafting (AutoCraft Integration)
| Tool Name | Parameters | Description |
|---|---|---|
| `craft_item` | `item_name` (str), `count` (opt int, default 1) | Queues crafting for any vanilla or modded item. |
| `craft_set` | `set_type` (`'armor'` or `'tools'`), `material` (str), `count` (opt int) | Crafts a complete 4-piece armor or 5-piece tool set. |
| `cancel_crafting`| *(none)* | Cancels active crafting and clears queue. |
| `get_craft_status`| *(none)* | Returns JSON of active task, remaining count, and queue size. |

### 5.5 Inventory & Equipment
| Tool Name | Parameters | Description |
|---|---|---|
| `get_inventory` | *(none)* | Returns full inventory breakdown (hotbar, main, armor, offhand, durability). |
| `equip_item` | `item_name` (str), `slot` (opt str: `'mainhand'`, `'offhand'`, `'head'`, etc.) | Equips specified item from inventory into slot. |
| `drop_item` | `item_name` (str), `all` (opt bool, default false) | Drops item or entire stack from inventory onto ground. |
| `use_item` | `hand` (opt str: `'mainhand'` or `'offhand'`) | Performs a right-click / use action with held item. |
| `eat_food` | *(none)* | Automatically finds edible food in inventory, swaps to it, and eats. |

### 5.6 Perception & Environment
| Tool Name | Parameters | Description |
|---|---|---|
| `get_player_status`| *(none)* | Returns health, food, saturation, position, yaw/pitch, dimension, effects. |
| `get_nearby_entities`| `radius` (opt num, default 16), `entity_type` (opt str) | Scans for entities in radius with health, distances, and types. |
| `get_nearby_blocks`| `radius` (opt int, default 8), `block_types` (opt str) | Scans for specific blocks (e.g. `'chest,diamond_ore'`). |

### 5.7 Combat & Interaction
| Tool Name | Parameters | Description |
|---|---|---|
| `attack_target`| `target` (str: entity name, type, or entity ID) | Attacks specified entity within reach and swings hand. |
| `interact_block`| `x` (num), `y` (num), `z` (num), `hand` (opt str) | Interacts (right clicks) with block at coordinates. |
| `interact_entity`| `target` (str: entity name or ID), `hand` (opt str) | Interacts with entity (villager trading, horse riding). |

### 5.8 Utility+ Client & Chat Automation
| Tool Name | Parameters | Description |
|---|---|---|
| `toggle_module` | `module_name` (str), `state` (opt bool) | Toggles or enables/disables any module (e.g. `'kill-aura'`). |
| `get_modules` | `category` (opt str), `only_active` (opt bool) | Lists all modules, active status, and descriptions. |
| `execute_command`| `command` (str) | Executes any client command (`.toggle`, `.goto`, etc.). |
| `send_chat` | `message` (str) | Sends a chat message or server command as the player. |

---

## 6. Prompting Recipes & Autonomous Scenarios

Try asking your AI assistant the following prompts:

### Scenario 1: Resource Gathering
> *"Inspect my current inventory. If I don't have a pickaxe, mine 3 oak_log blocks, craft them into planks and sticks, craft a wooden_pickaxe, and equip it."*

### Scenario 2: Autonomous Mining Expedition
> *"Check my health and food. If hunger is low, use eat_food. Then mine 10 iron_ore blocks and return to my starting position."*

### Scenario 3: Base Defense
> *"Scan for nearby hostile entities. If there are zombies or skeletons within 10 blocks, equip my best sword and enable kill-aura to neutralize them."*

### Scenario 4: Exploration & Scouting
> *"Scan the surrounding 16-block radius for chests or diamond_ore. If any are found, tell me their coordinates and pathfind to the closest one."*

---

## 7. Troubleshooting & FAQ

#### Q: Claude or Antigravity says "Minecraft client is unreachable on http://127.0.0.1:25590"
- **A**: Ensure Minecraft has finished loading and you have loaded into a world. The embedded HTTP server starts during game initialization.
- Test connection with `python tools/mcp_bridge.py --check`.

#### Q: The player does not move after calling `move_to`
- **A**: Check if Baritone is paused or if the target block is in unloaded chunks. You can call `stop` and retry with `pathfind_to`.

#### Q: How do I change the embedded server port?
- **A**: By default, port `25590` is used. If another application uses port `25590`, you can modify `DEFAULT_PORT` in `EmbeddedMCPServer.java` and recompile with `gradle jar`.

#### Q: Can I run this in multiplayer servers?
- **A**: Yes. All tools operate client-side through Utility+ and standard Minecraft packets. Be mindful of the server rules regarding automation and Baritone.
