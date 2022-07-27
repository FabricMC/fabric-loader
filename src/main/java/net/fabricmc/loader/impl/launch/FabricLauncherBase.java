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

package net.fabricmc.loader.impl.launch;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

import org.spongepowered.asm.mixin.MixinEnvironment;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public abstract class FabricLauncherBase implements FabricLauncher {
	private static boolean mixinReady;
	private static Map<String, Object> properties;
	private static FabricLauncher launcher;
	private static MappingConfiguration mappingConfiguration = new MappingConfiguration();

	protected FabricLauncherBase() {
		setLauncher(this);
	}

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return mappingConfiguration;
	}

	protected static void setProperties(Map<String, Object> propertiesA) {
		if (properties != null && properties != propertiesA) {
			throw new RuntimeException("Duplicate setProperties call!");
		}

		properties = propertiesA;
	}

	private static void setLauncher(FabricLauncher launcherA) {
		if (launcher != null && launcher != launcherA) {
			throw new RuntimeException("Duplicate setLauncher call!");
		}

		launcher = launcherA;
	}

	public static FabricLauncher getLauncher() {
		return launcher;
	}

	public static Map<String, Object> getProperties() {
		return properties;
	}

	protected static void handleFormattedException(FormattedException exc) {
		Throwable actualExc = exc.getMessage() != null ? exc : exc.getCause();
		Log.error(LogCategory.GENERAL, exc.getMainText(), actualExc);

		GameProvider gameProvider = FabricLoaderImpl.INSTANCE.tryGetGameProvider();

		if (gameProvider == null || !gameProvider.displayCrash(actualExc, exc.getMainText())) {
			FabricGuiEntry.displayError(exc.getMainText(), actualExc, true);
		} else {
			System.exit(1);
		}

		throw new AssertionError("exited");
	}

	protected static void setupUncaughtExceptionHandler() {
		Thread mainThread = Thread.currentThread();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				try {
					if (e instanceof FormattedException) {
						handleFormattedException((FormattedException) e);
					} else {
						String mainText = String.format("Uncaught exception in thread \"%s\"", t.getName());
						Log.error(LogCategory.GENERAL, mainText, e);

						GameProvider gameProvider = FabricLoaderImpl.INSTANCE.tryGetGameProvider();

						if (Thread.currentThread() == mainThread
								&& (gameProvider == null || !gameProvider.displayCrash(e, mainText))) {
							FabricGuiEntry.displayError(mainText, e, false);
						}
					}
				} catch (Throwable e2) { // just in case
					e.addSuppressed(e2);

					try {
						e.printStackTrace();
					} catch (Throwable e3) {
						PrintWriter pw = new PrintWriter(new FileOutputStream(FileDescriptor.err));
						e.printStackTrace(pw);
						pw.flush();
					}
				}
			}
		});
	}

	protected static void finishMixinBootstrapping() {
		if (mixinReady) {
			throw new RuntimeException("Must not call FabricLauncherBase.finishMixinBootstrapping() twice!");
		}

		try {
			Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
			m.setAccessible(true);
			m.invoke(null, MixinEnvironment.Phase.INIT);
			m.invoke(null, MixinEnvironment.Phase.DEFAULT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		mixinReady = true;
	}

	public static boolean isMixinReady() {
		return mixinReady;
	}

	/**
	 * Normalize the class path by normalizing the paths, deduplicating, resolving/expanding indirect references and
	 * suppressing purely other-jar referencing jars.
	 */
	protected static List<Path> normalizeClassPath(List<Path> cp) {
		Set<Path> checkedPaths = new HashSet<>(cp.size());
		List<Path> ret = new ArrayList<>(cp.size());
		List<Path> missing = new ArrayList<>();
		Deque<Path> queue = new ArrayDeque<>(cp);
		Path path;

		while ((path = queue.pollFirst()) != null) {
			if (!Files.exists(path)) {
				missing.add(path);
				continue;
			}

			path = LoaderUtil.normalizeExistingPath(path);
			if (!checkedPaths.add(path)) continue;

			if (Files.isRegularFile(path)) { // jar, expand (resolve additional manifest-referenced cp entries) and suppress file if it is only such a referencing container
				try (ZipFile zf = new ZipFile(path.toFile())) {
					Manifest manifest = ManifestUtil.readManifest(zf);
					List<URL> urls;

					if (manifest != null
							&& (urls = ManifestUtil.getClassPath(manifest, path)) != null) {
						for (int i = urls.size() - 1; i >= 0; i--) {
							queue.addFirst(UrlUtil.asPath(urls.get(i)));
						}

						// check for non-manifest/signature files to determine whether the jar can be ignored

						final String prefix = ManifestUtil.MANIFEST_DIR+"/";
						boolean foundNonManifest = false;

						for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
							ZipEntry entry = e.nextElement();
							if (entry.isDirectory()) continue;

							String name = entry.getName();
							String fileName;

							if (!name.startsWith(prefix) // file outside META-INF
									|| (fileName = name.substring(prefix.length())).indexOf('/') >= 0 // file not directly within META-INF
									|| !fileName.equals(ManifestUtil.MANIFEST_FILE) && !ManifestUtil.isSignatureFile(fileName)) { // neither MANIFEST.MF nor one of the sig files
								foundNonManifest = true;
								break;
							}
						}

						if (!foundNonManifest) { // only a jar with a manifest and potentially signature, ignore
							// don't add to ret
							continue;
						}
					}
				} catch (ZipError | Exception e) {
					throw new RuntimeException("error reading "+path, e);
				}
			}

			ret.add(path);
		}

		if (!missing.isEmpty()) {
			Log.warn(LogCategory.GENERAL, "Class path entries reference missing files: %s - the game may not load properly!",
					missing.stream().map(Path::toString).collect(Collectors.joining(", ")));
		}

		return ret;
	}
}
