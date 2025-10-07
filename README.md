# ChatMenu

Clickable chat menus from config for Paper/Spigot. Build fast, permission-gated chat UIs that run commands on click—no inventory GUIs or manual `plugin.yml` entries for every shortcut.

## Features
- **Dynamic commands** – every child under `commands:` becomes `/yourcommand` at runtime, with optional self/target modes and tab-complete for target menus.
- **Structured YAML menus** – compose lines from `text` and `button` segments so large inline menus stay readable. Legacy bracket syntax (`[display|commands|hover]`) still works for existing configs.
- **Console or player execution** – choose executors per button (`run-as: player`) or per command (`player:/cmd`). `%player%` and `%target%` placeholders are substituted at runtime.
- **PlaceholderAPI aware** – render text/hover with viewer or target context (`context: target`). Offline lookups are attempted when needed.
- **Reload safe** – `/chatmenu reload` re-parses config, registers new commands, and retires old ones without a server restart.

## Requirements
- Paper/Spigot 1.20+ (tested on 1.21)
- Java 17+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for `%placeholders%`

## Installation
1. Drop the jar in `plugins/`.
2. Start the server once to generate `plugins/ChatMenu/config.yml`.
3. Edit the config (single-quote strings to keep `&` and `[` intact).
4. Use `/chatmenu reload` in game or console to apply changes.

## Configuration Overview

Each command entry defines a dynamic command:

```yaml
commands:
  prefix:
    permission: chatmenu.prefix     # optional
    type: self                      # self | target
    message:
      - text: "&fPick your Name Color and Prefix:"
      - line:
          - text: "&fColor: "
          - button:
              text: "&2Dark Green"
              hover: "&aSelect Dark Green"
              commands:
                - "lp user %player% meta removeprefix 2"
                - "lp user %player% meta addprefix 2 &2"
                - "msg %player% &aSetting updated"
          - text: ", "
          - button:
              text: "&aGreen"
              hover: "&aSelect Green"
              commands:
                - "lp user %player% meta removeprefix 2"
                - "lp user %player% meta addprefix 2 &a"
```

### Building Lines
- `text` segments render plain text (MiniMessage if it lacks `&`, legacy color codes otherwise).
- `button` segments create a clickable component:
  - `text`: label shown in chat.
  - `hover`: tooltip; accepts a string or list of strings (joined with newlines).
  - `commands`: string or list. Strings can include `;` to sequence actions. You may also use objects such as `{player: "msg %player% hi"}` or `{command: "op %player%", run-as: console}`.
  - `run-as`: optional default executor for commands without a prefix (`player | console`).
  - `append-space`: add a trailing space after the button (default `false`; legacy lines keep the old behaviour).
  - `context`: choose PlaceholderAPI context per segment (`viewer | target`); defaults to the surrounding line.

Lines can be expressed in block or flow style, letting you keep dense inline menus readable:

```yaml
  - line: [
      {text: "&7["},
      {button: {text: "&aAccept", hover: "&7Confirm action", commands: ["confirm"], run-as: player}},
      {text: "&7] "}
    ]
```

### Context & Placeholders
- `%player%` always resolves to the viewer (clicking player).
- `%target%` resolves to the named target for `type: target`; for self menus it falls back to the viewer so commands stay consistent.
- Set `context: target` on a line or segment to render PlaceholderAPI text/hover with the target player.
- Opening text with `{{ctx=target}}` still works for legacy strings.

### Legacy Bracket Syntax
Existing configs using `[Display | command1; command2 | Hover | flags]` continue to load. Flags support `as=player` and `ctx=viewer|target`. You can mix legacy strings with the new structure per line during migration.

## Example Menus

The default `config.yml` ships with:
- `prefix` – color/prefix picker with inline buttons using the new structured layout.
- `punish` – target menu with `Warn/Mute/Ban` buttons.

Both illustrate inline lists without unreadable escape soup.

## Commands
- `/chatmenu reload` – reload config, rebuild dynamic commands.
- `/<name>` – one per `commands.<name>` entry.
- `/cmrun <token>` – internal command used by menu clicks; now locked to generated tokens (no permission required).

## Permissions
- `chatmenu.admin` – access to `/chatmenu reload` (default: op).
- Per-menu nodes defined under each command (`permission:`) – grant as needed in your permissions plugin.

## Troubleshooting
- **Nothing happens on click** – ensure your button has at least one command. Remember to include `run-as: player` or prefix `player:` if the command needs player context.
- **Viewer placeholders show for target buttons** – add `context: target` on the button or line, and verify the PlaceholderAPI expansion supports target players.
- **Spaces disappear between buttons** – add small `text` segments such as `{text: ", "}` or set `append-space: true` on the buttons you want spaced automatically.

Enjoy building menus!
