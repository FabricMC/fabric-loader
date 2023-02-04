package net.fabricmc.loader.impl.junit;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

import org.junit.platform.launcher.LauncherInterceptor;

public class FabricLoaderLauncherInterceptor implements LauncherInterceptor {
	private final Knot knot;
	private final ClassLoader classLoader;

	public FabricLoaderLauncherInterceptor() {
		knot = new Knot(EnvType.CLIENT);
		classLoader = knot.init(new String[]{});
	}

	@Override
	public <T> T intercept(Invocation<T> invocation) {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		currentThread.setContextClassLoader(classLoader);

		try {
			return invocation.proceed();
		}
		finally {
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public void close() {
		// TODO close knot?
	}
}
