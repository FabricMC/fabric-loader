/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.minecraft.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

@Mixin(value = TitleScreen.class, remap = false)
public abstract class MixinGuiMain extends Screen {
	protected MixinGuiMain(Text textComponent_1) {
		super(textComponent_1);
	}

	@Inject(method = "render", at = @At("RETURN"))
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta, CallbackInfo info) {
		this.textRenderer.draw(matrixStack, "Fabric Test Mod", 2, this.height - 30, -1);
	}
}
