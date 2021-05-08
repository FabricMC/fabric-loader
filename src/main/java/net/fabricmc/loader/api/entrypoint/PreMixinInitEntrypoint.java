package net.fabricmc.loader.api.entrypoint;

@FunctionalInterface
public interface PreMixinInitEntrypoint {
	void onPreMixinInit();
}
