package net.fabricmc.minecraft.test.info;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class EarlyDisplayInit {
	public static void main(String[] args) throws RemoteException {
		Registry registry = LocateRegistry.createRegistry(1099);
		registry.rebind("Remote", new DisplayRemoteObject());
	}
}
