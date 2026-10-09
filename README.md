fabric-loader
===========

The loader for mods under Fabric. It provides mod loading facilities and useful abstractions for other mods to use.

## Mods menu

The client title screen has a Mods button beside Realms. The menu has a searchable, scrolling list with mod icons on the left and the selected mod's metadata on the right. The + button shows nested libraries, and the bottom buttons open the mods folder or return to the title screen. The menu uses Minecraft's own screens and widgets, with adapters built from the running client's classes.

The button is hidden when Mod Menu is installed. Set `-Dfabric.modsMenu=false` to disable it. Dedicated servers do not load the menu.

See [the compatibility check](tools/README.md) for the test commands and their limits.

## License

Licensed under the Apache License 2.0.
