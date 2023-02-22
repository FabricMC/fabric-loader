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

package net.fabricmc.loader.impl;

import net.fabricmc.loader.impl.util.Localization;

@SuppressWarnings("serial")
public final class FormattedException extends RuntimeException {
	private final String mainText;
	private String translatedText;

	public FormattedException(String mainText, String message) {
		super(message);

		this.mainText = mainText;
	}

	public FormattedException(String mainText, String format, Object... args) {
		super(String.format(format, args));

		this.mainText = mainText;
	}

	public FormattedException(String mainText, String message, Throwable cause) {
		super(message, cause);

		this.mainText = mainText;
	}

	public FormattedException(String mainText, Throwable cause) {
		super(cause);

		this.mainText = mainText;
	}

	public static FormattedException ofLocalized(String key, String message) {
		return new FormattedException(Localization.formatRoot(key), message).addTranslation(key);
	}

	public static FormattedException ofLocalized(String key, String format, Object... args) {
		return new FormattedException(Localization.formatRoot(key), format, args).addTranslation(key);
	}

	public static FormattedException ofLocalized(String key, String message, Throwable cause) {
		return new FormattedException(Localization.formatRoot(key), message, cause).addTranslation(key);
	}

	public static FormattedException ofLocalized(String key, Throwable cause) {
		return new FormattedException(Localization.formatRoot(key), cause).addTranslation(key);
	}

	public String getMainText() {
		return mainText;
	}

	public String getDisplayedText() {
		return translatedText == null || translatedText.equals(mainText) ? mainText : translatedText + " (" + mainText + ")";
	}

	private FormattedException addTranslation(String key) {
		this.translatedText = Localization.format(key);
		return this;
	}
}
