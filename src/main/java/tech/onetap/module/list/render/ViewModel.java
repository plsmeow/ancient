package tech.onetap.module.list.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "View Model", moduleCategory = ModuleCategory.RENDER)
public class ViewModel extends Module {
    private final SliderSetting offsetLeftX = new SliderSetting("Левая рука по X",0.0F, -2F, 2F, 0.1F);
    private final SliderSetting offsetLeftY = new SliderSetting("Левая рука по Y",0.0F, -2F, 2F, 0.1F);
    private final SliderSetting offsetLeftZ = new SliderSetting("Левая рука по Z",0.0F, -2F, 2F, 0.1F);
    private final SliderSetting offsetRightX = new SliderSetting("Правая рука по X",0.0F, -2F, 2F, 0.1F);
    private final SliderSetting offsetRightY = new SliderSetting("Правая рука по Y",0.0F, -2F, 2F, 0.1F);
    private final SliderSetting offsetRightZ = new SliderSetting("Правая рука по Z",0.0F, -2F, 2F, 0.1F);

    public void applyHandPosition(MatrixStack matrices, Arm arm) {
        if (this.isEnabled()) {
            if (arm == Arm.LEFT) {
                matrices.translate(offsetLeftX.getValue(), offsetLeftY.getValue(), offsetLeftZ.getValue());
            } else {
                matrices.translate(offsetRightX.getValue(), offsetRightY.getValue(), offsetRightZ.getValue());
            }
        } else {
            matrices.translate(0.0f, 0.0f, 0.0f);
        }
    }
}