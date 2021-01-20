package net.fabricmc.loader.api.config.exceptions;

public class ConfigSerializationException extends RuntimeException {
	public ConfigSerializationException(Exception e) {
		super(e);
	}
}
