package net.fabricmc.test;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.info.EntrypointInfoReceiver;
import net.fabricmc.loader.api.info.EntrypointInvocationSession;
import net.fabricmc.loader.api.info.Message;
import net.fabricmc.loader.api.info.ModMessageSession;
import net.fabricmc.loader.api.info.ProgressBar;

public class ProgressBarTest {
	public void progressBarTest() {
		ModMessageSession modMessageSession = FabricLoader.getInstance().getModMessageSession();
		Message message = modMessageSession.message("0/2");
		message.pin();
		message.title("1/2");
		message.title("2/2");
		message.unpin();

		ProgressBar progressBar = modMessageSession.progressBar("Progress Bar", 100);
		for (int i = 0; i < 100; i++) {
			progressBar.increment();
		}
		progressBar.close();
	}

	// Specified in `fabric.mod.json`
	class EntrypointSession implements EntrypointInfoReceiver {

		@Override
		public EntrypointInvocationSession createEntrypointInvocationSession(String entrypointName, int size) {
			return new EntrypointInvocationSession() {
				ProgressBar progressBar;
				@Override
				public void preInvoke(ModContainer mod, int index, int size) {
					if (progressBar == null) {
						progressBar = FabricLoader.getInstance().getModMessageSession().progressBar(entrypointName, size);
					}
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
			};
		}
	}
}
