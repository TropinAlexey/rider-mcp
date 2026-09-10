# Rider MCP Extension

A JetBrains Rider plugin that extends the stock [MCP Server Plugin](https://github.com/JetBrains/mcp-server-plugin) with full IDE observability and control tools.

## Why This Exists

Claude Code connects to Rider through JetBrains' built-in MCP Server plugin, which provides ~30 tools. But most of them either duplicate what Claude Code already does better natively (file reading/editing, git operations) or target specific ecosystems (Unreal Engine, Godot, xdebug/PHP).

**The core problem: Claude is blind to what happens inside the IDE.**

A programmer staring at Rider sees dozens of information surfaces simultaneously — Build Output streaming in real time, Problems panel lighting up with errors, test runner trees expanding, progress bars showing indexing status, notifications popping up about package restores. Claude sees none of this.

When a build hangs, the programmer sees the build output frozen at "Building Project7..." — Claude sees nothing. When publish fails silently, the programmer sees a red notification balloon — Claude sees nothing. When zombie MSBuild nodes eat CPU, the programmer sees Activity Monitor — Claude sees nothing.

This plugin bridges that gap. It gives Claude the same situational awareness a programmer has — the ability to see what the IDE is doing, what it's showing, and what's happening in the background.

### What the stock MCP plugin provides vs. what this adds

| Capability | Stock MCP Plugin | This Extension |
|---|---|---|
| Read/edit files | ✅ (but Claude Code does it better) | — |
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
Claude Code ←MCP→ JS proxy (mcp-jetbrains) ←HTTP→ Rider JVM
                                                    ├── MCP Server Plugin (JetBrains)
                                                    │   └── stock tools (~30)
                                                    └── Rider MCP Extension (this plugin)
                                                        └── additional tools (~15+)
```

All tools from both plugins appear as a unified set in Claude Code. Our tools are prefixed with `rider_` to avoid naming conflicts.

### Polling Pattern for Async Operations

MCP tools are synchronous (request → response). For long-running operations like builds and test runs, we use a polling pattern:

1. `rider_start_build` → returns `{"sessionId": "build_1"}`
2. `rider_get_build_output("build_1")` → returns new lines since last call
3. Repeat until `status` is no longer `"running"`

## Available Tools

### Build Observability (P0)
- `rider_start_build` — Start solution build, get session ID
- `rider_get_build_output` — Poll build output (streaming via polling)
- `rider_cancel_build` — Cancel running build

### Process Management (P0)
- `rider_list_processes` — List Rider-managed processes (MSBuild, compilers, dev servers)
- `rider_kill_process` — Kill a process by name

### IDE State (P0)
- `rider_get_ide_state` — Current IDE activity (indexing, building, idle), active file
- `rider_get_notifications` — Recent balloon notifications and event log
- `rider_list_tool_windows` — All tool windows with visibility state
- `rider_get_tool_window_content` — Content of a specific tool window

### Programmer Context (P1)
- `rider_get_open_editors` — Open editor tabs (MRU order)
- `rider_get_cursor_context` — Current file, line, column, surrounding code
- `rider_get_selection` — Currently selected text
- `rider_get_recent_files` — Recently opened files
- `rider_get_bookmarks` — All bookmarks with locations

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
