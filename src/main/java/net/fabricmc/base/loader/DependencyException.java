package net.fabricmc.base.loader;

/**
 * @author shadowfacts
 */
public class DependencyException extends RuntimeException {

	public DependencyException() {
	}

	public DependencyException(String message) {
		super(message);
	}

	public DependencyException(String message, Throwable cause) {
		super(message, cause);
	}

	public DependencyException(Throwable cause) {
		super(cause);
	}

	public DependencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
