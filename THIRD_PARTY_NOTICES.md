# Third-party notices

This project's core towing-physics/hitching design (`entity/AbstractDrawnEntity`,
`entity/AbstractDrawnInventoryEntity`, `entity/HandCartEntity`, `item/HandCartItem`,
`util/CartWheel`, `util/CaryvanWorld`, `network/ActionKeyPayload`, `network/ToggleSlowPayload`)
is adapted from **NiftyCarts**, a Fabric mod by jmb05:

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

None of this project's code is copied verbatim -- every adapted class was reworked against this
environment's current Minecraft/NeoForge entity API (see each class's own doc comment for the
specific differences: no manual position-lerp code, `VehicleEntity` as the base instead of plain
`Entity`, no clientbound sync payload, current `ValueInput`/`ValueOutput` save-data API, etc.).
This notice exists to satisfy MIT's attribution requirement regardless.

This file does not change this project's own license (see `gradle.properties`' `mod_license`) --
see this repo's own CLAUDE.md for the full reasoning on how an MIT dependency's attribution
obligation and this suite's own closed licensing convention coexist.
