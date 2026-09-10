# Rider MCP Extension

A JetBrains Rider plugin that extends the stock [MCP Server Plugin](https://github.com/JetBrains/mcp-server-plugin) with full IDE observability and control tools.

## Why This Exists

The stock JetBrains MCP Server plugin provides ~30 tools, but most of them either duplicate what MCP clients already do better natively (file reading/editing, git operations) or target specific ecosystems (Unreal Engine, Godot, xdebug/PHP).

**The core problem: your AI assistant is blind to what happens inside the IDE.**

A programmer staring at Rider sees dozens of information surfaces simultaneously — Build Output streaming in real time, Problems panel lighting up with errors, test runner trees expanding, progress bars showing indexing status, notifications popping up about package restores. An MCP client sees none of this.

When a build hangs, you see the output frozen at "Building Project7..." — your assistant sees nothing. When publish fails silently, you see a red notification balloon — your assistant sees nothing. When zombie MSBuild nodes eat CPU, you see Activity Monitor — your assistant sees nothing.

This plugin bridges that gap. It gives any MCP-compatible client (Claude Code, Cursor, Windsurf, Continue, custom agents) the same situational awareness a programmer has — the ability to see what the IDE is doing, what it's showing, and what's happening in the background.

### What the stock MCP plugin provides vs. what this adds

| Capability | Stock MCP Plugin | This Extension |
|---|---|---|
| Read/edit files | ✅ (most clients do it natively) | — |
| Build solution | ✅ start + final status | ✅ streaming output, cancel, progress |
| See build errors | ✅ after build completes | ✅ real-time via Problems panel |
| Process management | ❌ | ✅ list & kill IDE-managed processes |
| IDE state/progress | ❌ | ✅ indexing, building, publishing status |
| Tool windows | ❌ | ✅ read any tool window content |
| Notifications | ❌ | ✅ balloon messages, event log |
| Programmer context | ❌ | ✅ open editors, cursor, selection |
| Test runner | ❌ | 🔜 planned |
| .NET debugger | Partial (xdebug only) | 🔜 planned |
| NuGet management | ❌ | 🔜 planned |
| IDE settings | ❌ | 🔜 planned |

## Architecture

This is **not a fork** of the JetBrains MCP Server plugin. It's a separate plugin that extends it through the official `mcpTool` extension point:

```
MCP Client ←MCP→ JS proxy (mcp-jetbrains) ←HTTP→ Rider JVM
                                                    ├── MCP Server Plugin (JetBrains)
                                                    │   └── stock tools (~30)
                                                    └── Rider MCP Extension (this plugin)
                                                        └── additional tools (~12)
```

All tools from both plugins appear as a unified set in any MCP client. Our tools are prefixed with `rider_` to avoid naming conflicts.

### Polling Pattern for Async Operations

MCP tools are synchronous (request → response). For long-running operations like builds and test runs, we use a polling pattern:

1. `rider_start_build` → returns `{"sessionId": "build_1"}`
2. `rider_get_build_output("build_1")` → returns new lines since last call
3. Repeat until `status` is no longer `"running"`

## Available Tools (12)

### Build (3 tools)
| Tool | Description |
|---|---|
| `rider_start_build` | Start solution build, returns session ID for polling |
| `rider_get_build_output` | Poll build output lines and status until completion |
| `rider_cancel_build` | Cancel running build |

### Process Management (2 tools)
| Tool | Description |
|---|---|
| `rider_list_processes` | List running processes (returns display names) |
| `rider_kill_process` | Kill a process by display name |

### IDE State (4 tools)
| Tool | Args | Description |
|---|---|---|
| `rider_get_ide_state` | — | Progress indicators, active file, busy status |
| `rider_get_notifications` | `limit` (default 5) | Recent IDE notifications |
| `rider_list_tool_windows` | `all` (default false) | Tool windows (visible only by default) |
| `rider_get_tool_window_content` | `windowId` | Tab names of a tool window |

### Programmer Context (2 tools)
| Tool | Description |
|---|---|
| `rider_get_context` | Active file + cursor + surrounding code + selection + open editors + bookmarks — all in one call |
| `rider_get_recent_files` | 20 most recently opened files |

### Token Efficiency

Responses are optimized to minimize token consumption by the MCP client:
- **Compact JSON** — false/empty fields omitted, only non-default values included
- **Filtered defaults** — `rider_list_tool_windows` returns only visible windows, `rider_list_processes` only running ones
- **Combined context** — `rider_get_context` replaces 5 separate tools (editors + cursor + selection + bookmarks) in a single round-trip
- **Capped payloads** — notifications default to 5, tool windows to visible-only

## Installation

### Prerequisites
- JetBrains Rider 2024.3+
- [MCP Server Plugin](https://plugins.jetbrains.com/plugin/26071-mcp-server) installed and enabled

### From Source
```bash
# Clone and build
git clone https://github.com/tropin/rider-mcp.git
cd rider-mcp
./gradlew buildPlugin

# Install the built plugin
# Rider → Settings → Plugins → ⚙️ → Install Plugin from Disk
# Select build/distributions/rider-mcp-*.zip
```

### Development
```bash
# Run Rider with the plugin loaded for debugging
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin
```

## Roadmap

See [TODO.md](TODO.md) for the full prioritized roadmap.

**Next up:**
- P1: Test Runner integration (run/poll/rerun tests)
- P1: Run/Debug Configuration CRUD
- P2: .NET Debugger (breakpoints, evaluate, step)
- P2: NuGet management
- P3: IDE Settings control

## License

MIT
