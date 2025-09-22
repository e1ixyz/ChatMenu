# ChatMenu

ChatMenu

Clickable chat menus from config for Paper/Spigot.
Build fast, permission-gated chat UIs that run commands on click — no GUIs, no per-command plugin.yml edits.

Features

Menus from config — every child under commands: becomes a dynamic command (/<name>).

Clickable segments — [ Display | cmd1; cmd2 | Hover | flags ].

Run as console or player

Default: console

Per-button: add |as=player

Per-command: prefix with player:/... or console:/...

Self vs Target menus

type: self → acts on the viewer (/prefix)

type: target → requires /<cmd> <player> (/punish Scorn) with tab-complete of online players

Placeholder context control

Per-line directive: {{ctx=viewer}} or {{ctx=target}}

Per-button flag (4th part): ctx=viewer / ctx=target

%player% = the clicking player (viewer), %target% = the selected player

PlaceholderAPI supported; target context resolves case-insensitively and falls back to OfflinePlayer

Literal brackets in labels — show [like this] via \[ and \]

Colors — lines with & use legacy color codes; otherwise parsed as MiniMessage

Dynamic registration — only chatmenu (admin) and cmrun (internal) live in plugin.yml; all menu commands are registered at runtime

Multi-command clicks — chain actions with ; (small 2-tick spacing between them)

Requirements

Paper/Spigot 1.20+ (tested on 1.21)

Java 17+ (21 recommended)

(Optional) PlaceholderAPI for %placeholders%

Installation & Reload

Drop the jar into /plugins.

Start the server to generate plugins/ChatMenu/config.yml.

Edit config.yml (prefer single quotes to preserve \[ and &).

Run /chatmenu reload to apply changes.

Configuration

Each child under commands: defines a command:

commands:
  <menuname>:
    permission: <node>      # required to open the menu
    type: self | target     # /<menuname> or /<menuname> <player>
    message:
      - "<text and/or clickable segments>"

Clickable Segment
[ Display | command1 ; command2 | Hover | flags ]


Display: label shown in chat. Use \[ and \] for visible square brackets.

Commands: one or more commands; default executor is console. You can mix:

player:/coords

console:lp user %target% meta addprefix 3 "&6★ &r"

Hover: text shown on mouseover.

Flags (optional):

as=player — run all commands in this button as the viewer (unless a command has its own player:/ or console:/)

ctx=viewer / ctx=target — render this button’s Display/Hover with viewer or target PlaceholderAPI context

Placeholder Context

Per-line (put at the start of the line):

{{ctx=viewer}} ...

{{ctx=target}} ...

%player% and %target% inside commands are replaced when the click executes:

type: self → both map to the viewer’s name

type: target → %player% = viewer; %target% = selected target

PlaceholderAPI rendering for text respects the chosen context (viewer/target). Target resolution is case-insensitive and supports offline players where expansions allow it.

Colors & Formatting

If a string contains &, it’s parsed with legacy color codes.

Otherwise it’s parsed as MiniMessage (e.g., <red>text</red>, <click:open_url:"https://...">link</click>).

Literal Brackets

Use \[ and \] anywhere you need visible square brackets in normal text or button labels.

Examples
Prefix / Color Picker (self)
prefix:
  permission: chatmenu.prefix
  type: self
  message:
    - '&fPick your Name Color and Prefix:'
    - ''
    - '[&2\[Dark Green\]|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &2; msg %player% &aSetting updated|Select Dark Green][&a\[Green\]|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &a; msg %player% &aSetting updated|Select Green]'
    - ''
    - '[&d\[✿\]|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&d✿ &r"; msg %player% &aSetting updated|Bloom][&6\[✯\]|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&6✯ &r"; msg %player% &aSetting updated|Star]'
    - ''
    - '[&c\[Reset Color and Prefix\]|lp user %player% meta removeprefix 2; lp user %player% meta removeprefix 3; msg %player% &aSetting updated|Remove all selections]'

Punish (target)
punish:
  permission: chatmenu.punish
  type: target
  message:
    - '&cPunish Menu'
    - '&7Targeting: &f%target%'
    - ''
    - '[&e\[Warn\]|warn %target%|Send a warning][&6\[Mute\]|mute %target%|Mute][&c\[Ban\]|ban %target%|Ban] (1 Hour)'

Rank Manager (target context in text & hovers)
rank:
  permission: chatmenu.rank
  type: target
  message:
    - '&4Rank Menu'
    - ''
    - '{{ctx=target}} &eTargeting: &c%target%&e in group(s): &e%luckperms_inherited_groups%'
    - ''
    - '[&a\[Promote User\]|console:lp user %target% promote; player:/rank %target%; console:msg %target% &aYou have been promoted; player:/tellraw @s {"text":"Operation completed","color":"white"}|Promote user to %luckperms_next_group_on_track_main%|ctx=target]'
    - '[&c\[Demote User\]|console:lp user %target% demote; player:/rank %target%; console:msg %target% &cYou have been demoted; player:/tellraw @s {"text":"Operation completed","color":"white"}|Demote user to %luckperms_previous_group_on_track_main%|ctx=target]'

Settings (run as player)
settings:
  permission: chatmenu.settings
  type: self
  message:
    - '&aSettings'
    - '[\[Coords\]|/coords|Toggle coords|as=player]'
    - '[\[Prefix\]|player:/prefix|Open the prefix menu]'

Teleport & Vanish (target) — mixed executors
- '[\[tp and vanish\]|console:tp %player% %target%; player:/vanish; player:/tellraw @s {"text":"Operation completed","color":"white"}|Teleport to target, then vanish]'

Clickable URL (MiniMessage)
map:
  permission: chatmenu.map
  type: self
  message:
    - '<hover:show_text:"Open Dynmap"><click:open_url:"https://map.00001110.xyz/"><white><underlined>https://map.00001110.xyz/</underlined></white></click></hover>'
    - ''
    - '[&a\[Show on Map\]|player:/dynmap show|Show on Map][&c\[Hide on Map\]|player:/dynmap hide|Hide on Map]'

Commands

/chatmenu reload — reloads config and re-registers dynamic menu commands

/<your-menu> — one per child under commands:

/cmrun — internal; clicks call this behind the scenes

Permissions

chatmenu.admin — use /chatmenu reload (default: op)

Per-menu nodes are defined in config.yml under each command, e.g.:

chatmenu.prefix

chatmenu.punish

chatmenu.settings

chatmenu.rank

Grant defaults in your permission plugin (e.g., LuckPerms).

Tips & Gotchas

Prefer single-quoted YAML strings so \[ and & are preserved.

If a label shows a backslash, you double-escaped; use \[ ... \] (not \\[).

If a command needs player context, run it as player (button as=player or player:/cmd).

If target placeholders render as the viewer, ensure you’re on the build that supports {{ctx=target}} / ctx=target. Target names are resolved case-insensitively and support offline players where possible.

For “success” feedback without code changes, append another command, e.g.
player:/tellraw @s {"text":"Operation completed","color":"white"}.

Troubleshooting

Raw |/commands printed in chat → malformed segment. Ensure you have three parts ([Display|Commands|Hover]) and use \[ / \] only inside Display text.

Placeholders show viewer values → set context with {{ctx=target}} (line) or ctx=target (button).

“Only players can use this.” → you tried to open a self menu via console or ran a player-only command as console; use as=player or player:/....

“A player is required to run this command here.” → same as above; run command as the player.

Development

Build with your preferred tooling (Maven/Gradle).

Target Paper API matching your server version.

No shading of Adventure is required on modern Paper. PlaceholderAPI is optional (soft usage).

![alt text](https://github.com/e1ixyz/ChatMenu/blob/main/img/punish.png)

![alt text](https://github.com/e1ixyz/ChatMenu/blob/main/img/settings.png)