package net.fabricmc.base;

import net.fabricmc.api.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.sortme.EntityPlayerAbstract;

public class ClientSidedHandler implements ISidedHandler {

	@Override
	public Side getSide() {
		return Side.CLIENT;
	}

	@Override
	public EntityPlayerAbstract getClientPlayer() {
		return Minecraft.getInstance().player;
	}

	@Override
	public void runOnMainThread(Runnable runnable) {
		Minecraft.getInstance().scheduleOnMainThread(runnable);
	}

}
