# ChatMenu

CHATMENU

Clickable chat menus from config for Paper/Spigot.Build fast, permission-gated chat UIs that run commands on click — no GUIs, no per-command plugin.yml edits.

FEATURES
--------

• Menus from config — every child under “commands:” becomes a dynamic command (/), registered at runtime.• Clickable segments — write buttons inline using: \[ Display | cmd1; cmd2 | Hover | flags \].• Run as console or player

*   Default: console
    
*   Per-button: add “|as=player”
    
*   Per-command: prefix with “player:/...” or “console:/...” (mix & match)• Self vs Target menus
    
*   type: self → acts on the viewer (e.g., /prefix)
    
*   type: target → requires “/ ” (e.g., /punish Scorn) with tab-complete for online players• Placeholder context control
    
*   Per-line directive: “{{ctx=viewer}}” or “{{ctx=target}}”
    
*   Per-button flag (4th part): “ctx=viewer” or “ctx=target”
    
*   %player% = clicking player (viewer), %target% = selected player
    
*   PlaceholderAPI supported; target context resolves case-insensitively and falls back to OfflinePlayer where possible• Literal brackets in labels — show “\[like this\]” via “”and“” and “”and“”• Colors & formatting — lines with “&” use legacy color codes; otherwise parsed as MiniMessage• Multi-command clicks — chain actions with “;” (small 2-tick spacing between them)• Minimal plugin.yml — only “chatmenu” (admin) and “cmrun” (internal) are declared; everything else is dynamic• Reloadable — “/chatmenu reload” re-parses config and safely re-registers commands
    

REQUIREMENTS
------------

• Server: Paper/Spigot 1.20+ (tested on 1.21)• Java: 17+ (21 recommended)• Optional: PlaceholderAPI for %placeholders%

INSTALLATION & RELOAD
---------------------

1.  Drop the jar into /plugins.
    
2.  Start the server to generate “plugins/ChatMenu/config.yml”.
    
3.  Edit config.yml (prefer single quotes to preserve “\[” and “&”).
    
4.  Run “/chatmenu reload” to apply changes.
    

CONFIGURATION SHAPE
-------------------

Each child under “commands:” defines a dynamic command.

commands::permission: \# required to open the menutype: self | target # / or / message:- ""

CLICKABLE SEGMENT SYNTAX
------------------------

\[ Display | command1 ; command2 | Hover | flags \]

• Display — label shown in chat. Use “”and“” and “”and“” inside Display to render visible square brackets.

• Commands — one or more commands separated by “;”. Default executor is console. You can mix:

*   player:/coords
    
*   console:lp user %target% meta addprefix 3 “&6★ &r”
    

• Hover — text shown on mouseover.

• Flags (optional) — space/comma not required; simple key=value:

*   as=player → run all commands in this button as the viewer (unless a command already has “player:/” or “console:/”)
    
*   ctx=viewer or ctx=target → render this button’s Display/Hover using viewer or target PlaceholderAPI context
    

PLACEHOLDER CONTEXT (TEXT RENDERING)
------------------------------------

• Per-line directive (place at the very start of the line):

*   {{ctx=viewer}} ...
    
*   {{ctx=target}} ...
    

• PlaceholderAPI rendering for text respects the chosen context.

*   Target resolution is case-insensitive.
    
*   OfflinePlayer fallback is used for ctx=target when the target is offline (subject to expansion support).
    

PLACEHOLDER SUBSTITUTION (COMMAND EXECUTION)
--------------------------------------------

%player% and %target% inside commands are replaced when the click executes:

• type: self → both map to the viewer’s name• type: target → %player% = viewer, %target% = selected target

COLORS & FORMATTING
-------------------

• If a string contains “&”, it’s parsed with legacy color codes.• Otherwise it’s parsed as MiniMessage (supports text, hover:show\_text:"…"…, click:open\_url:"https://…"…, etc.).

LITERAL BRACKETS
----------------

Use “”and“” and “”and“” anywhere you need visible “\[” and “\]” in normal text or button labels.

EXAMPLES
--------

1.  Prefix / Color Picker (self)
    

prefix:permission: chatmenu.prefixtype: selfmessage:- '&fPick your Name Color and Prefix:'- ''- '\[&2DarkGreenDark GreenDarkGreen|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &2; msg %player% &aSetting updated|Select Dark Green\]\[&aGreenGreenGreen|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &a; msg %player% &aSetting updated|Select Green\]\[&eYellowYellowYellow|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &e; msg %player% &aSetting updated|Select Yellow\]\[&6OrangeOrangeOrange|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &6; msg %player% &aSetting updated|Select Orange\]\[&cRedRedRed|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &c; msg %player% &aSetting updated|Select Red\]\[&9BlueBlueBlue|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &9; msg %player% &aSetting updated|Select Blue\]\[&5PurplePurplePurple|lp user %player% meta removeprefix 2; lp user %player% meta addprefix 2 &5; msg %player% &aSetting updated|Select Purple\]'- ''- '\[&d✿✿✿|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&d✿ &r"; msg %player% &aSetting updated|Bloom\]\[&c❤❤❤|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&c❤ &r"; msg %player% &aSetting updated|Heart\]\[&f♬♬♬|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&f♬ &r"; msg %player% &aSetting updated|Music\]\[&e✦✦✦|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&e✦ &r"; msg %player% &aSetting updated|Diamond\]\[&6✯✯✯|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&6✯ &r"; msg %player% &aSetting updated|Star\]\[&9☄☄☄|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&9☄ &r"; msg %player% &aSetting updated|Cosmic\]\[&a☀☀☀|lp user %player% meta removeprefix 3; lp user %player% meta addprefix 3 "&a☀ &r"; msg %player% &aSetting updated|Sun\]'- ''- '\[&cResetColorandPrefixReset Color and PrefixResetColorandPrefix|lp user %player% meta removeprefix 2; lp user %player% meta removeprefix 3; msg %player% &aSetting updated|Remove all selections\]'

1.  Punish (target)
    

punish:permission: chatmenu.punishtype: targetmessage:- '&cPunish Menu'- '&7Targeting: &f%target%'- ''- '\[&eWarnWarnWarn|warn %target%|Send a warning\]\[&6MuteMuteMute|mute %target%|Mute\]\[&cBanBanBan|ban %target%|Ban\] (1 Hour)'- '\[&eWarnWarnWarn|warn %target%|Send a warning\]\[&6MuteMuteMute|mute %target%|Mute\]\[&cBanBanBan|ban %target%|Ban\] (1 Day)'- '\[&eWarnWarnWarn|warn %target%|Send a warning\]\[&6MuteMuteMute|mute %target%|Mute\]\[&cBanBanBan|ban %target%|Ban\] (1 Week)'

1.  Rank Manager (target context in text & hovers)
    

rank:permission: chatmenu.ranktype: targetmessage:- '&4Rank Menu'- ''- '{{ctx=target}} &eTargeting: &c%target%&e in group(s): &e%luckperms\_inherited\_groups%'- ''- '\[&aPromoteUserPromote UserPromoteUser|console:lp user %target% promote; player:/rank %target%; console:msg %target% &aYou have been promoted; player:/tellraw @s {"text":"Operation completed","color":"white"}|Promote user to %luckperms\_next\_group\_on\_track\_main%|ctx=target\]'- '\[&cDemoteUserDemote UserDemoteUser|console:lp user %target% demote; player:/rank %target%; console:msg %target% &cYou have been demoted; player:/tellraw @s {"text":"Operation completed","color":"white"}|Demote user to %luckperms\_previous\_group\_on\_track\_main%|ctx=target\]'

1.  Settings (self, run as player)
    

settings:permission: chatmenu.settingstype: selfmessage:- '&aSettings'- '\[CoordsCoordsCoords|/coords|Toggle coords|as=player\]'- '\[PrefixPrefixPrefix|player:/prefix|Open the prefix menu\]'

1.  Teleport & Vanish (target) — mixed executors + confirmation
    

*   '\[tpandvanishtp and vanishtpandvanish|console:tp %player% %target%; player:/vanish; player:/tellraw @s {"text":"Operation completed","color":"white"}|Teleport to target, then vanish\]'
    

1.  Clickable URL (MiniMessage)
    

map:permission: chatmenu.maptype: selfmessage:- 'click:open\_url:"https://map.00001110.xyz/"[https://map.00001110.xyz/](https://map.00001110.xyz/)'- ''- '\[&aShowonMapShow on MapShowonMap|player:/dynmap show|Show on Map\]\[&cHideonMapHide on MapHideonMap|player:/dynmap hide|Hide on Map\]'

1.  Another clickable link example (postimages.org)
    

*   'Use a service like click:open\_url:"https://postimages.org/"[https://postimages.org/](https://postimages.org/) for getting a link.'
    

COMMANDS
--------

• /chatmenu reload — reloads config and re-registers dynamic menu commands• / — one per child under “commands:”• /cmrun — internal; clicks call this behind the scenes

PERMISSIONS
-----------

• chatmenu.admin — use /chatmenu reload (default: op)• Per-menu nodes are defined in config.yml under each command, for example:

*   chatmenu.prefix
    
*   chatmenu.punish
    
*   chatmenu.settings
    
*   chatmenu.rankGrant defaults in your permissions plugin (e.g., LuckPerms).
    

TIPS & GOTCHAS
--------------

• Use single-quoted YAML strings so “” and “&” are preserved.• If a label shows a backslash, you double-escaped; use “\\\[ ... ” (not “\\\[”).• If a command needs player context, run it as player (“|as=player” or “player:/cmd”).• If target placeholders render as the viewer, ensure you’re on a build that supports “{{ctx=target}}” / “ctx=target”. Target names are resolved case-insensitively and OfflinePlayer fallback is used (expansion-dependent).• For success feedback without code changes, append another command, e.g.:player:/tellraw @s {"text":"Operation completed","color":"white"}.

TROUBLESHOOTING
---------------

• Raw “|” or commands printed in chat:Ensure segments have at least three parts: \[Display | Commands | Hover\].Use “”/“” / “”/“” only inside Display text for visible brackets.

• Placeholders show viewer values:Set context with “{{ctx=target}}” (line) or “ctx=target” (button). Confirm PlaceholderAPI is installed and the relevant expansions are loaded.

• “Only players can use this.”:You tried to open a self menu via console or ran a player-only command as console; use “as=player” or “player:/...”.

• “A player is required to run this command here.”:The command requires a player executor; run it as the player.

DEVELOPMENT
-----------

• Build with Maven/Gradle against the Paper API for your server version.• No shading of Adventure needed on modern Paper.• PlaceholderAPI is optional; ChatMenu gracefully skips PAPI if not present.

![alt text](https://github.com/e1ixyz/ChatMenu/blob/main/img/punish.png)

![alt text](https://github.com/e1ixyz/ChatMenu/blob/main/img/settings.png)