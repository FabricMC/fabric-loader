package net.fabricmc.minecraft.test.info;

import net.fabricmc.loader.api.info.ProgressBar;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DisplayRemote extends Remote {
	void progressBars(ProgressBar[] progressBars) throws RemoteException;
}
