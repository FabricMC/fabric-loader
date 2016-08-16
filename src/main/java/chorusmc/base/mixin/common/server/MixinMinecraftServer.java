package chorusmc.base.mixin.common.server;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MixinMinecraftServer {

    @Overwrite
    public String getServerModName() {
        return "Chorus";
    }

}
