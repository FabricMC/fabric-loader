# Mods menu

Adds a Mods button to the title screen. The list shows loaded mods and their versions. It can be searched by name or ID. Selecting a mod opens its authors and description.

The menu is bundled in the loader. It does not need Fabric API. If Mod Menu is installed, its menu is used instead. To turn off the built-in menu, add `-Dfabric.modsMenu=false`.

## Compatibility

The current implementation only supports Minecraft 1.20.2. Support for the other versions is still missing. The version check must stay until those clients have a working menu; removing it would let incompatible classes load.

The screen uses Minecraft classes whose rendering methods and widget constructors change between releases. The mod list and search can be shared, but the game-facing code needs adapters for those changes.

## Tests

```sh
./gradlew :check :minecraft:check :minecraft:mods-menu:check fatJar
./gradlew finalJar --warning-mode fail
```

37 tests passed on JDK 21, with no failures or skipped tests. Checkstyle, Spotless and final JAR packaging also passed. For local tests, Loom's Unix-socket probe was disabled and Mockito's agent was loaded at startup because this environment blocks Unix sockets.

The menu still needs to be checked in-game, including window resizing, keyboard navigation, narration and running alongside Mod Menu.
