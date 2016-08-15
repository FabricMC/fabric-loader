package chorusmc.base.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import none.bgk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends bgk {

    @Inject(method = "a(IIF)V", at = @At("RETURN"), remap = false)
    public void draw(int a1, int a2, float a3, CallbackInfo info) {
        this.q.a("Chorus Base", 2, this.m - 20, -1);
    }

}
