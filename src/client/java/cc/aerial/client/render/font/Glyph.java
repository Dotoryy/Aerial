package cc.aerial.client.render.font;

final class Glyph {
    final float u0, v0, u1, v1;

    final int width, height;

    final int bearingX, bearingY;

    final float advance;

    final boolean hasInk;

    Glyph(float u0, float v0, float u1, float v1,
          int width, int height, int bearingX, int bearingY, float advance, boolean hasInk) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.width = width;
        this.height = height;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
        this.advance = advance;
        this.hasInk = hasInk;
    }
}
