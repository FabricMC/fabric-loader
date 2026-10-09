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

package net.fabricmc.loader.impl.game.minecraft.patch;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncher;

class ModsMenuPatchTest {
	@Test
	void backgroundIsNotRepeatedWhenTheGameWrapperAlreadyDrawsIt() {
		ClassNode screen = new ClassNode();
		screen.name = "Screen";
		MethodNode renderer = new MethodNode(Opcodes.ACC_PUBLIC, "extractRenderState", "(Ljava/lang/Object;IIF)V", null, null);
		MethodNode background = new MethodNode(Opcodes.ACC_PUBLIC, "extractBackground", renderer.desc, null, null);
		screen.methods.add(renderer);
		screen.methods.add(background);
		assertFalse(ModsMenuPatch.drawsBackground(screen, renderer, background));
		MethodNode wrapper = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "extractRenderStateWithTooltipAndSubtitles", renderer.desc, null, null);
		wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, screen.name, background.name, background.desc, false));
		wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, screen.name, renderer.name, renderer.desc, false));
		screen.methods.add(wrapper);
		assertTrue(ModsMenuPatch.drawsBackground(screen, renderer, background));
		screen.methods.remove(wrapper);
		renderer.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, screen.name, background.name, background.desc, false));
		assertTrue(ModsMenuPatch.drawsBackground(screen, renderer, background));
	}

	@Test
	void dedicatedServerDoesNotReadOrGenerateClientClasses() {
		FabricLauncher launcher = mock(FabricLauncher.class);
		when(launcher.getEnvironmentType()).thenReturn(EnvType.SERVER);
		new ModsMenuPatch().process(launcher, name -> {
			fail("Server tried to read " + name);
			return null;
		}, node -> fail("Server tried to emit " + node.name));
	}

	@Test
	void explicitOptOutDoesNotReadGameClasses() {
		FabricLauncher launcher = mock(FabricLauncher.class);
		when(launcher.getEnvironmentType()).thenReturn(EnvType.CLIENT);
		String previous = System.getProperty("fabric.modsMenu");

		try {
			System.setProperty("fabric.modsMenu", "false");
			new ModsMenuPatch().process(launcher, name -> {
				fail("Disabled menu tried to read " + name);
				return null;
			}, node -> fail("Disabled menu tried to emit " + node.name));
		} finally {
			if (previous == null) {
				System.clearProperty("fabric.modsMenu");
			} else {
				System.setProperty("fabric.modsMenu", previous);
			}
		}
	}
}
