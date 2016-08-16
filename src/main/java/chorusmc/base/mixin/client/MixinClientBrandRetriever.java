package chorusmc.base.mixin.client;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ClientBrandRetriever.class, remap = false)
public abstract class MixinClientBrandRetriever {

    @Overwrite
    public static String getClientModName() {
        return "Chorus";
    }

}
