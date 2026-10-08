# Built-in loaded-mod browser prototype

This module is bundled in Fabric Loader's nested jars. Players do not have to download a separate mod to get a basic Mods button on the title screen.

The current prototype supports **Minecraft 1.20.2 only**, matching the existing Minecraft integration-test project. This is not a claim of general Fabric Loader version coverage. Additional releases need separate adapters and client verification before this can become a default feature upstream.

## Behavior

- Adds a Mods button below the vanilla title-screen controls.
- Lists the mods actually loaded by Fabric Loader, including nested libraries and built-in entries.
- Searches names and IDs case-insensitively, with stable name/ID sorting and pagination.
- Shows each mod's ID, version, authors and description in a scrollable details view.
- Uses vanilla controls for mouse, keyboard navigation and narration.
- Includes English and Spanish labels.
- Leaves the existing Mod Menu implementation in control when `modmenu` is loaded.
- Supports a user opt-out with `-Dfabric.modsMenu=false`.

## Version isolation

The UI is compiled separately against Yarn 1.20.2. The Java 8-compatible mixin plugin lives in the existing Minecraft provider module and dynamically selects the title-screen mixin only for a supported client. Unsupported Minecraft releases and dedicated servers do not select the UI mixin. No Fabric API dependency or game-class dependency is added to the loader core.

## Validation

With JDK 21 or newer:

```sh
./gradlew :check :minecraft:check :minecraft:mods-menu:check fatJar
```

Automated checks passed with JDK 21: 37 tests (including six new tests), Checkstyle and Spotless. The bundled JAR was inspected for the menu, its refmap, English/Spanish resources and selection plugin. Local validation disabled Loom's Unix-socket capability probe and preloaded Mockito's agent because Unix sockets are unavailable in the execution environment; production sources and assertions were not changed for those workarounds. Manual client acceptance checks below remain pending.

The root `build` retains the existing additional Java 8 toolchain requirement for ProGuard.

Manual client acceptance checks before requesting upstream review:

1. Launch Minecraft 1.20.2 with the resulting loader and no external menu mod. Open Mods from the title screen.
2. Search by name and ID, check pagination with many mods, and open long descriptions.
3. Navigate using Tab, Enter and Escape; resize the window and change GUI scale.
4. Enable Spanish and narration and verify labels and focus.
5. Repeat with Mod Menu installed and with `-Dfabric.modsMenu=false`; only the external menu or no new button should appear.
6. Launch an unsupported client release and a dedicated server; neither should load UI classes.

## Deliberate scope

This is a read-only browser. It does not download, enable, disable or delete mods, and does not provide a configuration-screen API. Mod Menu remains the richer option. The outstanding design question for maintainers is whether a basic bundled browser belongs in Loader at all, and how version-specific adapters should be maintained.
