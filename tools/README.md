# Mods menu compatibility check

Download the screen, widget and text classes from each client listed by Fabric Meta. This also includes the experimental and unobfuscated clients in Fabric's extended manifest.

```sh
python3 tools/menu-clients.py /tmp/menu-clients
./gradlew :minecraft:menuClientCheck -PmenuClients=/tmp/menu-clients -PmenuAll=true
```

For a smaller run, pass `--versions 18w43b 1.14 1.16.5 1.20.2 26.1` to the downloader and omit `-PmenuAll=true` from Gradle. A full run fails if any version in the catalog is missing.

The check remaps the downloaded classes to intermediary where needed, applies the menu patch and checks the methods and fields used by the adapter. It then loads the generated screen against copies of the client class signatures with stubbed method bodies. This catches linkage errors, constructor changes and widget API changes without downloading game assets. It binds the native search field as well as the buttons. When the font class is available in the cache, it also binds and calls the drawing adapter against the stubbed methods.

These are API and bytecode checks. They do not launch Minecraft or check the appearance of the menu, graphics, narration or compatibility with other screen mixins.

Client classes are kept outside the repository and are not included in the loader jar.
