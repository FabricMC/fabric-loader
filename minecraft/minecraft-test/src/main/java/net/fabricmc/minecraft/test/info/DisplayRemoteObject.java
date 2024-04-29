package net.fabricmc.minecraft.test.info;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.info.ProgressBar;

import javax.swing.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayRemoteObject extends UnicastRemoteObject implements DisplayRemote {
	private ProgressBar[] progressBars = new ProgressBar[0];
	protected DisplayRemoteObject() throws RemoteException {
		new Thread(() -> {
			JFrame frame = new JFrame();
			frame.setVisible(false);
			System.setProperty("apple.awt.application.appearance", "system");
			System.setProperty("apple.awt.application.name", "Loading Window");

			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
					 UnsupportedLookAndFeelException e) {
				throw new RuntimeException(e);
			}
			JLabel modMessages = new JLabel("Mod Messages");
			frame.add(modMessages);
			frame.setSize(480, 300);
			frame.setVisible(true);
			ConcurrentHashMap<String, JProgressBar> cache = new ConcurrentHashMap<>();
			try {
				while (true) {
					int i = 0;
					for (ProgressBar progressBar : progressBars) {
						JProgressBar jProgressBar = cache.computeIfAbsent(progressBar.title(), f -> {
							JProgressBar bar = progressBar.steps() == 0 ? new JProgressBar(0, 1) : new JProgressBar(0, progressBar.steps());
							frame.add(bar);
							return bar;
						});
						jProgressBar.setStringPainted(true);
						if (progressBar.steps() == 0) jProgressBar.setValue(1);
						jProgressBar.setString(progressBar.title() + " (" + progressBar.progress() + "/" + progressBar.steps() + ")");
						jProgressBar.setBounds(10, 30 + i * 10, 300, 30 * 10);
						if (progressBar.steps() != 0) jProgressBar.setValue(progressBar.progress());
						i++;
					}
					frame.repaint();
					cache.forEach((s, jProgressBar) -> {
						if (Arrays.stream(progressBars).map(ProgressBar::title).noneMatch(s::equals)) {
							frame.remove(jProgressBar);
							cache.remove(s);
						}
					});
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}).start();
	}

	@Override
	public void progressBars(ProgressBar[] progressBars) {
		this.progressBars = progressBars;
	}
}
