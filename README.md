BetterQuesting
============

A new and improved questing mod for Minecraft pack creators

Quest database cache: when joining a server, the client keeps a copy of the server's quest and chapter configs in
`.minecraft/betterquesting/cache` (the 4 most recently used, stored by hash). On the next login the server only sends
them again if they changed; player progress is always sent. It can be turned off on the client with the
`Cache server quest database` config option.

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
