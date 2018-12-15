package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;

public class KnotClient {
	public static void main(String[] args) {
		new Knot(EnvType.CLIENT).init(args);
	}
}
