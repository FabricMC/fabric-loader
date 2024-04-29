package net.fabricmc.minecraft.test.info;

public class TestEntrypoint implements Runnable {
	@Override
	public void run() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
