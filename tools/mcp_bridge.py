#!/usr/bin/env python3
"""
Utility+ Minecraft MCP Bridge (Stdio <-> HTTP)
---------------------------------------------
A lightweight, zero-dependency bridge that connects external MCP clients
(Claude Desktop, Google Antigravity, Cursor, Claude Code) over stdio
to the Utility+ Minecraft MCP server running locally on http://127.0.0.1:25590.

Usage:
  python mcp_bridge.py               # Runs standard stdio MCP bridge
  python mcp_bridge.py --check       # Tests connection to Minecraft and lists tools
"""

import sys
import json
import time
import argparse
import urllib.request
import urllib.error

DEFAULT_BASE_URL = "http://127.0.0.1:25590"

def check_connection(base_url):
    print(f"Checking connection to Utility+ Minecraft MCP Server at {base_url}...")
    try:
        req = urllib.request.Request(f"{base_url}/", headers={"User-Agent": "MCP-Bridge/1.0"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"[SUCCESS] Connected to {data.get('name', 'Utility+ MCP Server')}!")
            print(f"Status: {data.get('status')} | Tools available: {data.get('toolsCount')}")
    except Exception as e:
        print(f"[ERROR] Could not connect to Minecraft at {base_url}: {e}")
        print("Make sure Minecraft is running with Utility+ client and the MCP server is active.")
        return False

    try:
        req = urllib.request.Request(f"{base_url}/tools", headers={"User-Agent": "MCP-Bridge/1.0"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            tools = data.get("tools", [])
            print(f"\nDiscovered {len(tools)} tools:")
            for i, t in enumerate(tools, 1):
                name = t.get("name")
                desc = t.get("description", "")
                print(f"  {i:2d}. {name:<20} - {desc}")
        print("\nAll systems operational! The MCP bridge is ready to use.")
        return True
    except Exception as e:
        print(f"[ERROR] Failed to fetch tools: {e}")
        return False

def forward_mcp_request(base_url, payload_bytes):
    url = f"{base_url}/mcp"
    req = urllib.request.Request(
        url,
        data=payload_bytes,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": "MCP-Bridge/1.0"
        },
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            if resp.status == 204:
                return None
            return resp.read()
    except urllib.error.HTTPError as he:
        err_body = he.read()
        try:
            return err_body
        except Exception:
            return None
    except Exception as ex:
        sys.stderr.write(f"[mcp_bridge] HTTP request failed: {ex}\n")
        sys.stderr.flush()
        return None

def main():
    parser = argparse.ArgumentParser(description="Utility+ Minecraft MCP Bridge")
    parser.add_argument("--url", default=DEFAULT_BASE_URL, help="Base URL of Utility+ MCP server (default: http://127.0.0.1:25590)")
    parser.add_argument("--check", action="store_true", help="Check server health and list available tools")
    args = parser.parse_args()

    base_url = args.url.rstrip("/")

    if args.check:
        success = check_connection(base_url)
        sys.exit(0 if success else 1)

    sys.stderr.write(f"[mcp_bridge] Utility+ Minecraft MCP Stdio Bridge started -> {base_url}\n")
    sys.stderr.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            req_obj = json.loads(line)
        except Exception as e:
            sys.stderr.write(f"[mcp_bridge] Invalid JSON on stdin: {e}\n")
            sys.stderr.flush()
            continue

        req_id = req_obj.get("id")
        method = req_obj.get("method", "")

        # Notifications (no id field)
        if req_id is None:
            forward_mcp_request(base_url, line.encode("utf-8"))
            continue

        # Forward request
        raw_response = forward_mcp_request(base_url, line.encode("utf-8"))

        if raw_response:
            try:
                resp_text = raw_response.decode("utf-8").strip()
                if resp_text:
                    sys.stdout.write(resp_text + "\n")
                    sys.stdout.flush()
                    continue
            except Exception:
                pass

        # If failed or empty response, synthesize JSON-RPC error
        err_response = {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {
                "code": -32000,
                "message": f"Utility+ Minecraft client is unreachable on {base_url}. Ensure Minecraft is running with Utility+."
            }
        }
        sys.stdout.write(json.dumps(err_response) + "\n")
        sys.stdout.flush()

if __name__ == "__main__":
    main()
