package net.fabricmc.loader.api.info;

public interface ModMessageSession {
	Message message(String string);

	ProgressBar progressBar(String name, int steps);
}
