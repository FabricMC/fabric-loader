pluginManagement {
	repositories {
		maven {
			url = "https://maven.fabricmc.net"
			name = "FabricMC"
		}
		gradlePluginPortal()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name='fabric-loader'

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
	throw new UnsupportedOperationException("Fabric Loader requires Java 21+ to build.")
}

include "minecraft"
include "junit"
include "minecraft:minecraft-test"