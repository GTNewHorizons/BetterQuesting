BetterQuesting
============

A new and improved questing mod for Minecraft pack creators

When joining a server with quest caching support, the client stores quest and chapter definitions in
`.minecraft/betterquesting/cache`. It keeps the four most recently used snapshots and downloads definitions again
when the matching snapshot is missing. Player progress is sent on every login. You can disable caching with the
`Cache server quest database` client option.

Standard Expansion
============

Contains all the basic tasks, rewards, importers and themes for the Better Questing mod

Quest Book
============

Contains a basic quest book item to give the player something to interact with if there are keybinding conflicts.

# CB-for-BQ
Command Blocks for Better Questing

 - Adds 3 Blocks:
 - Default Load Block to Load Quests after an update.
 - Hardcore Switch Block to activate/deactivate Hardcore Mode.
 - Reset Block to Reset Quests of the Player thats activating it.
 - Blocks can be used in MP but cannot be crafted.
 - works with BQ1 and SHOULD work with BQ2 aswell (untested)
