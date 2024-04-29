package net.fabricmc.minecraft.test.info;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.info.EntrypointInfoReceiver;
import net.fabricmc.loader.api.info.EntrypointInvocationSession;
import net.fabricmc.loader.api.info.Message;
import net.fabricmc.loader.api.info.ModMessageReceiver;
import net.fabricmc.loader.api.info.ProgressBar;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.UrlUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;

public class EarlyDisplayEntrypoint implements PreLaunchEntrypoint, Runnable, EntrypointInfoReceiver, ModMessageReceiver {

	private DisplayRemote remote;

	@Override
	public void onPreLaunch() {
		try {
			openForked();
			Thread.sleep(1000);
			Registry registry = LocateRegistry.getRegistry(null, 1099);
			System.out.println(Arrays.toString(registry.list()));
			remote = (DisplayRemote) registry.lookup("Remote");
		} catch (NotBoundException | InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
		new Thread(this).start();

		new Thread(() -> {
			ProgressBar progressBar = FabricLoader.getInstance().getModMessageSession().progressBar("Test Progress Bar", 1000);
			for (int i = 0; i < 1000; i++) {
				progressBar.increment();
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			progressBar.close();
		}).start();
		FabricLoader.getInstance().invokeEntrypoints("test", Runnable.class, Runnable::run);
	}

	private static void openForked() throws IOException, InterruptedException {
		Path javaBinDir = LoaderUtil.normalizePath(Paths.get(System.getProperty("java.home"), "bin"));
		String[] executables = { "javaw.exe", "java.exe", "java" };
		Path javaPath = null;

		for (String executable : executables) {
			Path path = javaBinDir.resolve(executable);

			if (Files.isRegularFile(path)) {
				javaPath = path;
				break;
			}
		}

		if (javaPath == null) throw new RuntimeException("can't find java executable in "+javaBinDir);
		System.out.println(javaPath.toString() + " -Xmx1G" + " -cp " + UrlUtil.getCodeSource(EarlyDisplayInit.class).toString() + ":" + UrlUtil.LOADER_CODE_SOURCE.toString() + " " + FabricGuiEntry.class.getName());
		Process process = new ProcessBuilder(javaPath.toString(), "-Xmx1G", "-cp", UrlUtil.getCodeSource(EarlyDisplayInit.class).toString() + ":" + UrlUtil.LOADER_CODE_SOURCE.toString(), EarlyDisplayInit.class.getName())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		final Thread shutdownHook = new Thread(process::destroy);

		Runtime.getRuntime().addShutdownHook(shutdownHook);


//		int rVal = process.waitFor();
//
//		Runtime.getRuntime().removeShutdownHook(shutdownHook);
//
//		if (rVal != 0) throw new IOException("subprocess exited with code "+rVal);
	}

	private static final ConcurrentLinkedDeque<ProgressBar> progressBars = new ConcurrentLinkedDeque<>();
	private static final ConcurrentLinkedDeque<Message> pinnedMessages = new ConcurrentLinkedDeque<>();
	private static final ConcurrentLinkedDeque<Message> messages = new ConcurrentLinkedDeque<>();

	@Override
	public void run() {
		while (true) {
			progressBars.removeIf(ProgressBar::isCompleted);
			try {
				remote.progressBars(progressBars.toArray(new ProgressBar[0]));
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public EntrypointInvocationSession createEntrypointInvocationSession(String entrypointName, int size) {
		MyEntrypointInvocationSession myEntrypointInvocationSession = new MyEntrypointInvocationSession();
		myEntrypointInvocationSession.name = entrypointName;
		myEntrypointInvocationSession.size = size;
		myEntrypointInvocationSession.progressBar = FabricLoader.getInstance().getModMessageSession().progressBar(entrypointName, size);
		return myEntrypointInvocationSession;
	}

	@Override
	public void progressBar(ProgressBar progressBar) {
		synchronized (progressBars) {
			progressBars.add(progressBar);
		}
	}

	@Override
	public void message(Message message) {
		synchronized (pinnedMessages) {
			pinnedMessages.add(message);
		}
	}

	private static class MyEntrypointInvocationSession implements EntrypointInvocationSession {
		private String name;
		private int size;
		private ProgressBar progressBar;

		@Override
		public void preInvoke(ModContainer mod, int index, int size) {
			progressBar.set(index);
		}

		@Override
		public Throwable error(ModContainer mod, Throwable throwable, int index, int size) {
			return throwable;
		}

		@Override
		public void close() {
			progressBar.close();
		}
	}
}
