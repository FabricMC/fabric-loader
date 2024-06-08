package net.fabricmc.loader.impl.info;

import net.fabricmc.loader.api.info.Message;
import net.fabricmc.loader.api.info.ModMessageReceiver;
import net.fabricmc.loader.api.info.ModMessageSender;
import net.fabricmc.loader.api.info.ProgressBar;
import net.fabricmc.loader.impl.FabricLoaderImpl;

public class ModMessageSenderImpl implements ModMessageSender {

	@Override
	public Message message(String title) {
		MessageImpl message = new MessageImpl(title);
		FabricLoaderImpl.INSTANCE.getEntrypoints("modMessageReceiver", ModMessageReceiver.class).forEach(entrypoint -> entrypoint.message(message));
		return message;
	}

	@Override
	public ProgressBar progressBar(String name, int steps) {
		ProgressBarImpl progressBar = new ProgressBarImpl(name, steps);
		FabricLoaderImpl.INSTANCE.getEntrypoints("modMessageReceiver", ModMessageReceiver.class).forEach(entrypoint -> entrypoint.progressBar(progressBar));
		return progressBar;
	}
}
