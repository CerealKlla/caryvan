# Third-party notices

This project's core towing-physics/hitching design (`entity/AbstractDrawnEntity`,
`entity/AbstractDrawnInventoryEntity`, `entity/HandCartEntity`, `item/HandCartItem`,
`util/CartWheel`, `util/CaryvanWorld`, `network/ToggleSlowPayload`), its real 3D model geometry
(`client/renderer/entity/model/HandCartModel`, `EasyMeshBuilder`), and its item/entity icon art
(`textures/item/hand_cart.png`, derived from NiftyCarts' own icon; `textures/entity/hand_cart.png`
reuses only its UV layout, not its actual pixel art -- see that file's own generation notes in
decisions.md) is adapted from **NiftyCarts**, a Fabric mod by jmb05:

- Repository: https://github.com/jmb05/nifty-carts
- License: MIT

```
MIT License

Copyright (c) jmb05 (NiftyCarts contributors)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

NiftyCarts is itself a Fabric port of **AstikorCarts** by MennoMax
(https://github.com/MennoMax/astikor-carts, also MIT licensed), which the above code is
originally descended from.

Separately, Animal Cart's real 3D model geometry (`client/renderer/entity/model/AnimalCartModel`)
and its item icon (`textures/item/animal_cart.png`) are adapted directly from **AstikorCarts'**
own `AnimalCartModel`/`CartModel` and `textures/items/animal_cart.png` -- NiftyCarts itself dropped
the original "Animal Cart" concept in favor of its own different cart types (Hand Cart, Wagon, Seed
Drill, Reaper), so this is the first content this project has copied directly from AstikorCarts
itself rather than through NiftyCarts, same MIT license as above (same repository link). Box
dimensions/UV offsets are copied as-is (real physical geometry, not anything API-version-specific);
`textures/entity/animal_cart.png` reuses only the UV layout, not AstikorCarts' own runtime-
assembled-texture pixel art (that system, `AssembledTexture`/`AssembledTextureFactory`/`Fill`/
`Material`, was deliberately not ported -- a static baked PNG ships instead, same simplification
Hand Cart's own texture already made).

Separately, Wagon's item icon (`textures/item/wagon.png`) is adopted directly from **NiftyCarts'**
own `oak_wagon.png` (NiftyCarts has a real "Wagon" cart type of its own, with per-wood icons) --
same MIT license/repository as above. Wagon's 3D model geometry (`client/renderer/entity/model/
WagonModel`) started as an original, deliberately simple placeholder (box bed + flat square wheels,
no spokes), explicit user scoping 2026-10-25; its real-geometry pass, same day, is loosely informed
by **NiftyCarts'** own real `WagonModel`/`CartModel` (same MIT license/repository as above, pulled
directly from the repo before writing any of this) for the open-bed/wall/axle shape specifically
(corner posts, rail-built side walls, real axles) -- NiftyCarts' own Wagon is a full covered wagon
with a furl-able canvas roof and three separately-lidded internal chests, neither of which this
project's `WagonEntity` has any mechanic for, so only the structural bed/wall/axle shape was
adapted, not ported wholesale; the wheel geometry itself reuses this project's own existing
Animal-Cart wheel pattern (already attributed above, ultimately from AstikorCarts) scaled down,
rather than NiftyCarts' own wheel code. The driving bench is this project's own original addition,
with no equivalent in either reference mod (NiftyCarts' Wagon isn't drivable at all).

None of this project's code is copied verbatim -- every adapted class was reworked against this
environment's current Minecraft/NeoForge entity API (see each class's own doc comment for the
specific differences: no manual position-lerp code, `VehicleEntity` as the base instead of plain
`Entity`, no clientbound sync payload, current `ValueInput`/`ValueOutput` save-data API, etc.).
This notice exists to satisfy MIT's attribution requirement regardless.

This file does not change this project's own license (see `gradle.properties`' `mod_license`) --
see this repo's own CLAUDE.md for the full reasoning on how an MIT dependency's attribution
obligation and this suite's own closed licensing convention coexist.
