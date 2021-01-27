package net.fabricmc.loader.api.config.serialization.toml;

/**
 * Thrown when a problem occur during parsing or writing NBT data.
 *
 * @author TheElectronWill
 */
public class TomlException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	public TomlException() {}
	
	public TomlException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public TomlException(String message) {
		super(message);
	}
	
	public TomlException(Throwable cause) {
		super(cause);
	}
	
}
