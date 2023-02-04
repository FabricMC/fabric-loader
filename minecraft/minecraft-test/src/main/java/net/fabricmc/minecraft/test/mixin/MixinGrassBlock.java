package net.fabricmc.minecraft.test.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.GrassBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrassBlock.class)
public class MixinGrassBlock {
	@Inject(method = "canGrow", at = @At("HEAD"), cancellable = true)
	public void canGrow(World world, Random random, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
