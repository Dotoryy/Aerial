package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.ModeProperty;
import net.minecraft.resources.Identifier;

public final class CapeModule extends Module {
    public static final CapeModule INSTANCE = new CapeModule();

    public enum Mode {
        AERIAL("Aerial", "aerial.png"),
        KETA1("Keta1", "keta1.png"),
        KETA2("Keta2", "keta2.png"),
        MIGRATOR("Migrator", "migrator.png"),
        MINECON_2011("2011", "minecon_2011.png"),
        MINECON_2012("2012", "minecon_2012.png"),
        MINECON_2013("2013", "minecon_2013.png"),
        MINECON_2015("2015", "minecon_2015.png"),
        MINECON_2016("2016", "minecon_2016.png"),
        MOJANG("Mojang", "mojang.png"),
        MOJANG_STUDIOS("MojangStudios", "mojang_studios.png"),
        OPAL("Opal", "opal.png"),
        PRISMARINE("Prismarine", "prismarine.png"),
        SENOE("Senoe", "senoe.png"),
        BILLYK("BillyK", "billyk.png"),
        COBALT("Cobalt", "cobalt.png"),
        EDGE("Edge", "edge.png"),
        EXHIBITION1("Exhibition1", "exhibition_1.png"),
        EXHIBITION2("Exhibition2", "exhibition_2.png"),
        FIREFOX("Firefox", "firefox.png");

        private final String label;
        private final Identifier texture;

        Mode(String label, String fileName) {
            this.label = label;

            String baseName = fileName.substring(0, fileName.length() - ".png".length());
            this.texture = Identifier.fromNamespaceAndPath("aerial", "capes/" + baseName);
        }

        public Identifier getTexture() {
            return texture;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.AERIAL);

    private CapeModule() {
        super("Cape", "cape is cape", ModuleCategory.VISUAL);
        addProperties(mode);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public Identifier getTexture() {
        return mode.getValue().getTexture();
    }
}
