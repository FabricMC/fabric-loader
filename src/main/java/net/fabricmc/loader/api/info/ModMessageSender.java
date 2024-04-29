package net.fabricmc.loader.api.info;

public interface ModMessageSender {
	Message message(String string);

	ProgressBar progressBar(String name, int steps);
}
