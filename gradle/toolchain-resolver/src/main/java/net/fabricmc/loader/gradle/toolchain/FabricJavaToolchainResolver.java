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

package net.fabricmc.loader.gradle.toolchain;

import java.net.URI;
import java.util.Optional;

import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainResolver;
import org.gradle.platform.Architecture;
import org.gradle.platform.OperatingSystem;

// On ARM64 macs we want to use x64 Java via Rosetta, as the LWJGL natives are not compiled for ARM64.
@SuppressWarnings("UnstableApiUsage")
public abstract class FabricJavaToolchainResolver implements JavaToolchainResolver {
	private static final String JAVA8_MACOS_X64 = "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u392-b08/OpenJDK8U-jdk_x64_mac_hotspot_8u392b08.tar.gz";

	@Override
	public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
		final int javaVersion = request.getJavaToolchainSpec().getLanguageVersion().get().asInt();
		final OperatingSystem operatingSystem = request.getBuildPlatform().getOperatingSystem();
		final Architecture architecture = request.getBuildPlatform().getArchitecture();

		if (javaVersion == 8
				&& operatingSystem == OperatingSystem.MAC_OS
				&& architecture == Architecture.AARCH64) {
			return Optional.of(JavaToolchainDownload.fromUri(URI.create(JAVA8_MACOS_X64)));
		}

		return Optional.empty();
	}
}
