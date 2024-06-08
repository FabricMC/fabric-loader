package net.fabricmc.loader.api.info;

public interface ModMessageReceiver {
	void progressBar(ProgressBar progressBar);
	void message(Message message);
}
