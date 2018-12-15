package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;

public class KnotServer {
	public static void main(String[] args) {
		new Knot(EnvType.SERVER).init(args);
	}
}
