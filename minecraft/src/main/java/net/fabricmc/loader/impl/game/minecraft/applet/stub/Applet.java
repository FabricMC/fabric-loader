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

package net.fabricmc.loader.impl.game.minecraft.applet.stub;

import net.fabricmc.loader.impl.game.minecraft.applet.AppletLauncher;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;
import java.util.Locale;

/**
 * A minimal stub implementation of an Applet, just enough to make Minecraft happy.
 */
public class Applet extends Panel {
    private @Nullable AppletLauncher appletLauncher;

    public final void setStub(AppletLauncher appletLauncher) {
        this.appletLauncher = appletLauncher;
    }

    public boolean isActive() {
        if (appletLauncher != null) {
            return appletLauncher.isActive();
        } else {
            return false;
        }
    }

    public URL getDocumentBase() {
        return appletLauncher.getDocumentBase();
    }

    public URL getCodeBase() {
        return appletLauncher.getCodeBase();
    }

    public String getParameter(String name) {
        return appletLauncher.getParameter(name);
    }

    @SuppressWarnings("deprecation")
	@Override
    public void resize(int width, int height) {
        Dimension d = size();
        if ((d.width != width) || (d.height != height)) {
            super.resize(width, height);
        }
    }

    @SuppressWarnings("deprecation")
	@Override
    public void resize(Dimension d) {
        resize(d.width, d.height);
    }

    @Override
    public boolean isValidateRoot() {
        return true;
    }

	@Override
    public Locale getLocale() {
        Locale locale = super.getLocale();
        if (locale == null) {
            return Locale.getDefault();
        }
        return locale;
    }

    public void init() {
    }

    public void start() {
    }

    public void stop() {
    }

    public void destroy() {
    }
}
