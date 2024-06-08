package net.fabricmc.loader.api.info;

public interface EntrypointInfoReceiver {
	EntrypointInvocationSession createEntrypointInvocationSession(String entrypointName, int size);
}
