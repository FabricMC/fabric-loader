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

package net.fabricmc.loader.impl.launch.knot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;

interface KnotClassLoaderInterface {
	KnotClassDelegate getDelegate();
	boolean isClassLoaded(String name);
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;
	void addURL(URL url);
	URL getResource(String name);
	InputStream getResourceAsStream(String filename, boolean skipOriginalLoader) throws IOException;

	Package getPackage(String name);
	Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException;
	Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs);
}
