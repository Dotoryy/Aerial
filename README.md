# Aerial

A Fabric client mod for Minecraft 26.2. It bundles 86 modules across seven
categories, its own ClickGUI, an account manager, and a HUD layer that does not
use vanilla's font or shape rendering at all.

Requires Java 25, Fabric Loader 0.19.3 or newer, and Fabric API.

```
./gradlew build          # jar lands in build/libs
./gradlew runClient      # dev launch
```

## Rendering

None of the interface goes through `GuiGraphics.drawString` or `fill`. Text and
shapes each have their own atlas, shader and `RenderPipeline`, registered with
Blaze3D and submitted as custom render states through `GuiGraphicsExtractor`.

**Text is MSDF.** Ten atlases live in `assets/aerial/msdf`, generated with
`msdf-atlas-gen` at a distance range of 10 px. The shader takes the median of
the three channels, converts it to screen-pixel coverage using the texture-space
derivative of the sample point, and supersamples on a rotated 2×2 grid. The
rotation matters more than the count: a square grid puts two samples on the same
scanline and two in the same column, so a horizontal edge — most of a latin
glyph — still resolves to two distinct answers out of four.

Taking `fwidth` of the median instead of the sample point is the obvious
implementation and it is wrong exactly where MSDF earns its keep. At a corner
the median switches channels, the value jumps, and the edge gets treated as far
blurrier than it is. `text.fsh` has the long version.

Not everything is MSDF. `AerialFont` also bakes AWT fonts at runtime — a fixed
ASCII atlas, a dynamic one that grows as it meets new glyphs, and an icon mode
that bakes a named subset. The dynamic path exists because remote strings
(usernames, server MOTDs) are not ASCII and a fixed atlas renders them as
fallback boxes.

**Shapes come from one distance field.** `RoundedFieldAtlas` bakes a single
256 px field once at startup; every rounded rectangle, outline, asymmetric
corner set and rounded image samples it rather than computing SDFs per fragment.
`RenderUtil` is the surface: `roundedRect`, `roundedRectGradient`,
`roundedOutline`, `roundedRectAsym` (per-corner radii), `roundedHead`,
`dropShadow`, `flatRect`, `image`.

**Blur is captured once per frame and consumed many times.** An offscreen
`TextureTarget` takes the framebuffer, runs it through a vanilla `PostChain`,
and hands back a texture that any number of panels can sample with their own UV
window. Three chains are selectable: Gaussian, dual-pass Kawase, and a
Rise-style variant. The alternative — vanilla's `blurBeforeThisStratum()` — is a
single global cut per frame and throws as soon as two elements want a blurred
backdrop.

**Bloom draws the HUD twice.** Marked render states are appended to the draw
list a second time, executed into a glow target with text suppressed, blurred,
and composited underneath the real HUD with premultiplied alpha. Text is left
out on purpose, so the glow is the silhouette of the shapes rather than a halo
around every letter.

## Renderer compatibility

Everything is drawn in the GUI layer. There is no world-space render hook —
ESP projects world coordinates to screen space with `ESPUtility.project` and
draws the result as 2D. That is the main reason this coexists with
performance mods instead of fighting them.

Developed and run against **Sodium 0.9.1**, **Lithium**, **ImmediatelyFast** and
**EntityCulling** together. Custom pipelines are registered through Blaze3D
rather than by touching GL state, so Sodium's renderer is not disturbed.

Iris and shaderpacks are untested. Nothing here is known to break them, but no
claim is being made either.

Mixins are on the conservative side where other mods are likely to reach for the
same instruction. Where a redirect would have collided — `Mth.abs` in
`LivingEntity.tick`, `Mth.clamp` in `HumanoidModel.poseBlockingArm` — MixinExtras
value modifiers are used instead so both mods apply rather than one being
silently dropped and crashing on its own injection check.

## If you build on this

Licensed under GPL-3.0. In practice that means anything you distribute which
contains a meaningful part of this code has to ship its source under the same
terms, with the changes you made. Forking it into a closed client is not
something the license allows.

Beyond what the license requires: tell me. Open an issue or reach me through the
repository before you release, and credit Aerial in your README or client UI. I
would rather hear about it from you than find it later.

## License

GNU General Public License v3.0 — see `LICENSE` for the full text.

Copyright (C) 2026 Dotoryy
