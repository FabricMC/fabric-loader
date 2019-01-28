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

package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.transformer.FabricTransformer;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

class KnotClassDelegate {
	static class Metadata {
		final Manifest manifest;
		final CodeSource codeSource;

		Metadata(Manifest manifest, CodeSource codeSource) {
			this.manifest = manifest;
			this.codeSource = codeSource;
		}
	}

	private final KnotClassLoaderInterface itf;
	private final boolean isDevelopment;
	private final EnvType envType;
	private MixinTransformer mixinTransformer;

	KnotClassDelegate(boolean isDevelopment, EnvType envType, KnotClassLoaderInterface itf) {
		this.isDevelopment = isDevelopment;
		this.envType = envType;
		this.itf = itf;
	}

	private MixinTransformer getMixinTransformer() {
		if (mixinTransformer == null) {
			try {
				Constructor<MixinTransformer> constructor = MixinTransformer.class.getDeclaredConstructor();
				constructor.setAccessible(true);
				mixinTransformer = constructor.newInstance();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		return mixinTransformer;
	}

	Metadata getMetadata(String name, URL resourceURL) {
		Manifest manifest = null;
		CodeSource codeSource = null;

		if (resourceURL != null) {
			URL codeSourceURL = null;
			String filename = name.replace('.', '/') + ".class";

			try {
				codeSourceURL = UrlUtil.getSource(filename, resourceURL);
			} catch (UrlConversionException e) {
				e.printStackTrace();
			}

			if (codeSourceURL != null) {
				try {
					File codeSourceFile = UrlUtil.asFile(codeSourceURL);
					if (codeSourceFile.isFile()) {
						try {
							JarFile codeSourceJar = new JarFile(codeSourceFile);
							manifest = codeSourceJar.getManifest();
							JarEntry codeEntry = codeSourceJar.getJarEntry(filename);
							if (codeEntry != null) {
								codeSource = new CodeSource(codeSourceURL, codeEntry.getCodeSigners());
							}
						} catch (IOException e) {
							// pass
						}
					}
				} catch (UrlConversionException e) {
					// pass
				}

				if (codeSource == null) {
					codeSource = new CodeSource(codeSourceURL, (CodeSigner[]) null);
				}
			}
		}

		return new Metadata(manifest, codeSource);
	}

	public byte[] transform(String name, byte[] data) {
		byte[] b = FabricTransformer.transform(isDevelopment, envType, name, data);
		b = getMixinTransformer().transformClassBytes(name, name, b);
		return b;
	}

	public byte[] loadClassData(String name, boolean resolve) {
		if (!"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && !name.startsWith("org.apache.logging.log4j")) {
			byte[] input = EntrypointTransformer.INSTANCE.transform(name);
			if (input == null) {
				try {
					input = getClassByteArray(name, true);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			if (input != null) {
				return transform(name, input);
			} else {
				return null;
			}
		} else {
			// Allow injecting fake loader classes.
			return EntrypointTransformer.INSTANCE.transform(name);
		}
	}

	String getClassFileName(String name) {
		return name.replace('.', '/') + ".class";
	}

	public byte[] getClassByteArray(String name, boolean skipOriginalLoader) throws IOException {
		String classFile = getClassFileName(name);
		InputStream inputStream = itf.getResourceAsStream(classFile, skipOriginalLoader);
		if (inputStream == null) {
			return null;
		}

		int a = inputStream.available();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(a < 32 ? 32768 : a);
		byte[] buffer = new byte[8192];
		int len;
		while ((len = inputStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, len);
		}

		inputStream.close();
		return outputStream.toByteArray();
	}
}
