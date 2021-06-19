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

package net.fabricmc.loader.impl.discovery;

import static com.google.common.jimfs.Feature.FILE_CHANNEL;
import static com.google.common.jimfs.Feature.SECURE_DIRECTORY_STREAM;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipError;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.lib.gson.MalformedJsonException;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class ModDiscoverer {
	// nested JAR store
	private static final FileSystem inMemoryFs = Jimfs.newFileSystem(
			"nestedJarStore",
			Configuration.builder(PathType.unix())
			.setRoots("/")
			.setWorkingDirectory("/")
			.setAttributeViews("basic")
			.setSupportedFeatures(SECURE_DIRECTORY_STREAM, FILE_CHANNEL)
			.build()
			);
	static final Map<String, List<Path>> inMemoryCache = new ConcurrentHashMap<>();
	private static final Pattern MOD_ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{1,63}");
	private static final Object launcherSyncObject = new Object();

	private final List<ModCandidateFinder> candidateFinders = new ArrayList<>();

	public void addCandidateFinder(ModCandidateFinder f) {
		candidateFinders.add(f);
	}

	public Map<String, ModCandidateSet> discoverMods(FabricLoaderImpl loader) throws ModResolutionException {
		ConcurrentMap<String, ModCandidateSet> candidatesById = new ConcurrentHashMap<>();

		long time1 = System.currentTimeMillis();
		Queue<UrlProcessAction> allActions = new ConcurrentLinkedQueue<>();
		ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

		for (ModCandidateFinder f : candidateFinders) {
			f.findCandidates(loader, (u, requiresRemap) -> {
				UrlProcessAction action = new UrlProcessAction(loader, candidatesById, u, 0, requiresRemap);
				allActions.add(action);
				pool.execute(action);
			});
		}

		// add builtin mods
		for (BuiltinMod mod : loader.getGameProvider().getBuiltinMods()) {
			addBuiltinMod(candidatesById, mod);
		}

		// Add the current Java version
		try {
			addBuiltinMod(candidatesById, new BuiltinMod(
					new File(System.getProperty("java.home")).toURI().toURL(),
					new BuiltinModMetadata.Builder("java", System.getProperty("java.specification.version").replaceFirst("^1\\.", ""))
					.setName(System.getProperty("java.vm.name"))
					.build()));
		} catch (MalformedURLException e) {
			throw new ModResolutionException("Could not add Java to the dependency constraints", e);
		}

		boolean tookTooLong = false;
		Throwable exception = null;

		try {
			pool.shutdown();
			// Comment out for debugging
			pool.awaitTermination(30, TimeUnit.SECONDS);

			for (UrlProcessAction action : allActions) {
				if (!action.isDone()) {
					tookTooLong = true;
				} else {
					Throwable t = action.getException();

					if (t != null) {
						if (exception == null) {
							exception = t;
						} else {
							exception.addSuppressed(t);
						}
					}
				}
			}
		} catch (InterruptedException e) {
			throw new ModResolutionException("Mod resolution took too long!", e);
		}

		if (tookTooLong) {
			throw new ModResolutionException("Mod resolution took too long!");
		}

		if (exception != null) {
			throw new ModResolutionException("Mod resolution failed!", exception);
		}

		long time2 = System.currentTimeMillis();

		Log.debug(LogCategory.DISCOVERY, "Mod discovery time: " + (time2 - time1) + "ms");

		return candidatesById;
	}

	@SuppressWarnings("serial")
	static class UrlProcessAction extends RecursiveAction {
		private final FabricLoaderImpl loader;
		private final Map<String, ModCandidateSet> candidatesById;
		private final URL url;
		private final int depth;
		private final boolean requiresRemap;

		UrlProcessAction(FabricLoaderImpl loader, Map<String, ModCandidateSet> candidatesById, URL url, int depth, boolean requiresRemap) {
			this.loader = loader;
			this.candidatesById = candidatesById;
			this.url = url;
			this.depth = depth;
			this.requiresRemap = requiresRemap;
		}

		@Override
		protected void compute() {
			FileSystemUtil.FileSystemDelegate jarFs;
			Path path, modJson, rootDir;
			URL normalizedUrl;

			Log.debug(LogCategory.DISCOVERY, "Testing %s", url);

			try {
				path = UrlUtil.asPath(url).normalize();
				// normalize URL (used as key for nested JAR lookup)
				normalizedUrl = UrlUtil.asUrl(path);
			} catch (UrlConversionException e) {
				throw new RuntimeException("Failed to convert URL " + url + "!", e);
			}

			if (Files.isDirectory(path)) {
				// Directory
				modJson = path.resolve("fabric.mod.json");
				rootDir = path;

				if (loader.isDevelopmentEnvironment() && !Files.exists(modJson)) {
					Log.warn(LogCategory.DISCOVERY, "Adding directory %s to mod classpath in development environment - workaround for Gradle splitting mods into two directories", path);
					synchronized (launcherSyncObject) {
						FabricLauncherBase.getLauncher().propose(url);
					}
				}
			} else {
				// JAR file
				try {
					jarFs = FileSystemUtil.getJarFileSystem(path, false);
					modJson = jarFs.get().getPath("fabric.mod.json");
					rootDir = jarFs.get().getRootDirectories().iterator().next();
				} catch (IOException e) {
					throw new RuntimeException("Failed to open mod JAR at " + path + "!");
				} catch (ZipError e) {
					throw new RuntimeException("Jar at " + path + " is corrupted, please redownload it!");
				}
			}

			LoaderModMetadata[] info;

			try {
				info = new LoaderModMetadata[] { ModMetadataParser.parseMetadata(modJson) };
			} catch (ParseMetadataException.MissingRequired e) {
				throw new RuntimeException(String.format("Mod at \"%s\" has an invalid fabric.mod.json file! The mod is missing the following required field!", path), e);
			} catch (MalformedJsonException | ParseMetadataException e) {
				throw new RuntimeException(String.format("Mod at \"%s\" has an invalid fabric.mod.json file!", path), e);
			} catch (NoSuchFileException e) {
				Log.warn(LogCategory.DISCOVERY, "Non-Fabric mod JAR at \"%s\", ignoring", path);
				info = new LoaderModMetadata[0];
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to open fabric.mod.json for mod at \"%s\"!", path), e);
			} catch (Throwable t) {
				throw new RuntimeException(String.format("Failed to parse mod metadata for mod at \"%s\"", path), t);
			}

			for (LoaderModMetadata i : info) {
				ModCandidate candidate = new ModCandidate(i, normalizedUrl, depth, requiresRemap);
				boolean added;

				if (candidate.getInfo().getId() == null || candidate.getInfo().getId().isEmpty()) {
					throw new RuntimeException(String.format("Mod file `%s` has no id", candidate.getOriginUrl().getFile()));
				}

				if (!MOD_ID_PATTERN.matcher(candidate.getInfo().getId()).matches()) {
					List<String> errorList = new ArrayList<>();
					isModIdValid(candidate.getInfo().getId(), errorList);
					StringBuilder fullError = new StringBuilder("Mod id `");
					fullError.append(candidate.getInfo().getId()).append("` does not match the requirements because");

					if (errorList.size() == 1) {
						fullError.append(" it ").append(errorList.get(0));
					} else {
						fullError.append(":");

						for (String error : errorList) {
							fullError.append("\n  - It ").append(error);
						}
					}

					throw new RuntimeException(fullError.toString());
				}

				for (String provides : candidate.getInfo().getProvides()) {
					if (!MOD_ID_PATTERN.matcher(provides).matches()) {
						List<String> errorList = new ArrayList<>();
						isModIdValid(provides, errorList);
						StringBuilder fullError = new StringBuilder("Mod id provides `");
						fullError.append(provides).append("` does not match the requirements because");

						if (errorList.size() == 1) {
							fullError.append(" it ").append(errorList.get(0));
						} else {
							fullError.append(":");

							for (String error : errorList) {
								fullError.append("\n  - It ").append(error);
							}
						}

						throw new RuntimeException(fullError.toString());
					}
				}

				added = candidatesById.computeIfAbsent(candidate.getInfo().getId(), ModCandidateSet::new).add(candidate);

				if (!added) {
					Log.debug(LogCategory.DISCOVERY, "%s already present as %s", candidate.getOriginUrl(), candidate);
				} else {
					Log.debug(LogCategory.DISCOVERY, "Adding %s as %s", candidate.getOriginUrl(), candidate);

					List<Path> jarInJars = inMemoryCache.computeIfAbsent(candidate.getOriginUrl().toString(), (u) -> {
						Log.debug(LogCategory.DISCOVERY, "Searching for nested JARs in %s", candidate);
						Log.debug(LogCategory.DISCOVERY, u);
						Collection<NestedJarEntry> jars = candidate.getInfo().getJars();
						List<Path> list = new ArrayList<>(jars.size());

						for (NestedJarEntry jar : jars) {
							Path modPath = rootDir.resolve(jar.getFile().replace("/", rootDir.getFileSystem().getSeparator()));

							if (!Files.isDirectory(modPath) && modPath.toString().endsWith(".jar")) {
								// TODO: pre-check the JAR before loading it, if possible
								Log.debug(LogCategory.DISCOVERY, "Found nested JAR: %s", modPath);
								Path dest = inMemoryFs.getPath(UUID.randomUUID() + ".jar");

								try {
									Files.copy(modPath, dest);
								} catch (IOException e) {
									throw new RuntimeException("Failed to load nested JAR " + modPath + " into memory (" + dest + ")!", e);
								}

								list.add(dest);
							}
						}

						return list;
					});

					if (!jarInJars.isEmpty()) {
						invokeAll(
								jarInJars.stream()
								.map((p) -> {
									try {
										return new UrlProcessAction(loader, candidatesById, UrlUtil.asUrl(p.normalize()), depth + 1, requiresRemap);
									} catch (UrlConversionException e) {
										throw new RuntimeException("Failed to turn path '" + p.normalize() + "' into URL!", e);
									}
								}).collect(Collectors.toList()));
					}
				}
			}

			/* if (jarFs != null) {
				jarFs.close();
			} */
		}
	}

	/** @param errorList The list of errors. The returned list of errors all need to be prefixed with "it " in order to make sense. */
	static boolean isModIdValid(String modId, List<String> errorList) {
		// A more useful error list for MOD_ID_PATTERN
		if (modId.isEmpty()) {
			errorList.add("is empty!");
			return false;
		}

		if (modId.length() == 1) {
			errorList.add("is only a single character! (It must be at least 2 characters long)!");
		} else if (modId.length() > 64) {
			errorList.add("has more than 64 characters!");
		}

		char first = modId.charAt(0);

		if (first < 'a' || first > 'z') {
			errorList.add("starts with an invalid character '" + first + "' (it must be a lowercase a-z - uppercase isn't allowed anywhere in the ID)");
		}

		Set<Character> invalidChars = null;

		for (int i = 1; i < modId.length(); i++) {
			char c = modId.charAt(i);

			if (c == '-' || c == '_' || ('0' <= c && c <= '9') || ('a' <= c && c <= 'z')) {
				continue;
			}

			if (invalidChars == null) {
				invalidChars = new HashSet<>();
			}

			invalidChars.add(c);
		}

		if (invalidChars != null) {
			StringBuilder error = new StringBuilder("contains invalid characters: ");
			error.append(invalidChars.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", ")));
			errorList.add(error.append("!").toString());
		}

		assert errorList.isEmpty() == MOD_ID_PATTERN.matcher(modId).matches() : "Errors list " + errorList + " didn't match the mod ID pattern!";
		return errorList.isEmpty();
	}

	private void addBuiltinMod(ConcurrentMap<String, ModCandidateSet> candidatesById, BuiltinMod mod) {
		candidatesById.computeIfAbsent(mod.metadata.getId(), ModCandidateSet::new).add(new ModCandidate(new BuiltinMetadataWrapper(mod.metadata), mod.url, 0, false));
	}

	public static FileSystem getInMemoryFs() {
		return inMemoryFs;
	}
}
