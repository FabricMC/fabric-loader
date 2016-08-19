package net.fabricmc.base;

import net.fabricmc.api.Side;
import net.minecraft.sortme.EntityPlayerAbstract;

public interface ISidedHandler {

	Side getSide();

	EntityPlayerAbstract getClientPlayer();

	void runOnMainThread(Runnable runnable);

}
