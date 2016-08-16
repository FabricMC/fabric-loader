package chorusmc.base.mixin.client.gui.impl;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.impl.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMainMenu.class, remap = false)
public abstract class MixinGuiMain extends GuiScreen {

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    public void draw(int a1, int a2, float a3, CallbackInfo info) {
        this.fontRenderer.drawString("Chorus Base", 2, this.height - 20, -1);
    }

}
