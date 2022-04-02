package net.fabricmc.loader.impl.entrypoint;

@FunctionalInterface
public interface MixinLoadingEntrypoint {
	void onMixinLoading();
}
