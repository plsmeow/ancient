package tech.onetap.module.list.render;

import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "Custom Cape", moduleCategory = ModuleCategory.RENDER)
public class CustomCape extends Module {

    private static final String CLIENT = "Client";
    private static final String LAVANDER = "Lavander";
    private static final String NURSULTAN = "Nursultan";
    private static final String NURSULTAN_PREM = "Nursultan Prem";
    private static final String RAINBOW = "Rainbow";
    private static final String GRADIENT = "Gradient";
    private static final String SKY = "Sky";

    private static final Identifier CAPE_CLIENT = texture("cape_client");
    private static final Identifier CAPE_LAVANDER = texture("cape_lavander");
    private static final Identifier CAPE_NURSULTAN = texture("cape_nursultan");
    private static final Identifier CAPE_NURSULTAN_PREM = texture("cape_nursultan_prem");
    private static final Identifier CAPE_RAINBOW = texture("cape_rainbow");
    private static final Identifier CAPE_GRADIENT = texture("cape_gradient");
    private static final Identifier CAPE_SKY = texture("cape_sky");

    private final ModeSetting cape = new ModeSetting("Плащ", CLIENT,
            CLIENT, LAVANDER, NURSULTAN, NURSULTAN_PREM, RAINBOW, GRADIENT, SKY);

    private SkinTextures cachedSource;
    private SkinTextures cachedResult;
    private Identifier cachedCape;

    private static Identifier texture(String name) {
        return Identifier.of("mre", "textures/entity/" + name + ".png");
    }

    private Identifier selected() {
        return switch (cape.getValue()) {
            case LAVANDER -> CAPE_LAVANDER;
            case NURSULTAN -> CAPE_NURSULTAN;
            case NURSULTAN_PREM -> CAPE_NURSULTAN_PREM;
            case RAINBOW -> CAPE_RAINBOW;
            case GRADIENT -> CAPE_GRADIENT;
            case SKY -> CAPE_SKY;
            default -> CAPE_CLIENT;
        };
    }

    public SkinTextures apply(SkinTextures source) {
        Identifier texture = selected();
        if (source == cachedSource && texture == cachedCape) return cachedResult;

        cachedSource = source;
        cachedCape = texture;
        cachedResult = new SkinTextures(
                source.texture(),
                source.textureUrl(),
                texture,
                texture,
                source.model(),
                source.secure()
        );
        return cachedResult;
    }

    @Override
    public void onDisable() {
        cachedSource = null;
        cachedResult = null;
        super.onDisable();
    }
}
