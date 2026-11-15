# SchemReplace

A small client-side Fabric mod for **Minecraft 26.3** that swaps blocks inside your loaded **Litematica** schematic.

Don't have 200 stone bricks? Swap them for a block you like. The matching stairs, slabs, walls, fences and doors get swapped with them, and Litematica's preview and material list update right away.

## Download

Get the jar from the [Releases](../../releases) page and put it in your `mods` folder.

Requires:
- Minecraft 26.3 + Fabric Loader 0.20.1+
- Fabric API
- Litematica + MaLiLib

Client-side only: the server doesn't need it.

## How to use

1. Select a placement in Litematica (`M` + `P`).
2. Open the **Swap Blocks** screen:
   - the **Swap Blocks** button in the bottom-right corner of Litematica's main menu or placement list,
   - or press `.` (period, can be changed in Controls),
   - or type `/sr gui`.
3. Left: click the block to replace (blocks in the schematic, with counts). Right: click the block to use instead, or press **Held block**.
4. Click **Swap**. **Undo** reverts it.

The schematic file is saved after each swap. The first time, the original is kept next to it as `<name>.litematic.bak`.

### Commands

| Command | What it does |
|---|---|
| `/sr list` | Blocks in the schematic with counts (click to swap) |
| `/sr <from> <to>` | Swap a block plus its stairs/slabs/walls/etc. |
| `/sr <from> hand` | Swap for the block in your hand |
| `/sr exact <from> <to>` | Swap only that exact block |
| `/sr undo` | Undo the last swap (up to 10) |
| `/sr gui` | Open the Swap Blocks screen |

`/schemreplace` works as a long alias.

## Building

Put `litematica-fabric-26.3-0.29.1.jar` and `malilib-fabric-26.3-0.30.2.jar` into `libs/`, then with JDK 25:

```
./gradlew build
```

The jar ends up in `build/libs/`.

## License

CC0 1.0, public domain.
