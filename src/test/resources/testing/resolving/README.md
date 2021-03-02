# Mod resolving test files

## These are collections of `fabric.mod.json` files used for testing the mod resolver.

There are two categories of folders:
- Valid -> `valid`
    - Folders in this category contain a valid set of mods
- Error-checking -> `error`
    - Folders in this category contain an invalid set of mods, which should fail to load.

Instead of proper jar files, we instead use folders suffixed with ".jar". All "jars" at the root of the test folder are considered 
