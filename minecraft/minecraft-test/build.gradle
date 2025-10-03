apply plugin: "fabric-loom"

loom {
	runConfigs.configureEach {
		ideConfigGenerated = true
		property("fabric.debug.replaceVersion", "fabricloader:$version")
	}
}

def minecraft_version = "1.20.2"

repositories {
	mavenCentral()
}

dependencies {
	minecraft "com.mojang:minecraft:${minecraft_version}"
	mappings "net.fabricmc:yarn:${minecraft_version}+build.1:v2"

	implementation project(":minecraft")
	implementation project(":minecraft").sourceSets.main.output
	implementation project(":").sourceSets.main.output

	// Required for mixin annotation processor
	annotationProcessor "org.ow2.asm:asm:${project.asm_version}"
	annotationProcessor "org.ow2.asm:asm-analysis:${project.asm_version}"
	annotationProcessor "org.ow2.asm:asm-commons:${project.asm_version}"
	annotationProcessor "org.ow2.asm:asm-tree:${project.asm_version}"
	annotationProcessor "org.ow2.asm:asm-util:${project.asm_version}"

	/**
	 * Ensure we are using the mixin version loader is built against to test the AP.
	 * Otherwise Loom will default to an older version (due to no mod loader on the mod* configs)
	 */
	annotationProcessor ("net.fabricmc:sponge-mixin:${project.mixin_version}") {
		exclude module: 'launchwrapper'
		exclude module: 'guava'
	}
	annotationProcessor "io.github.llamalad7:mixinextras-fabric:$mixin_extras_version"

	testImplementation project(":junit")
	testRuntimeOnly('org.junit.platform:junit-platform-launcher')
}

test {
	useJUnitPlatform()
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(JavaCompile).configureEach {
	it.options.encoding = "UTF-8"
	it.options.release = 17
}

import groovy.json.JsonSlurper

configurations {
	productionRuntimeMods {
		transitive = false
	}
}

dependencies {
	minecraftTestClientRuntimeLibraries "net.fabricmc:intermediary:${minecraft_version}"

	// Include the external libraries on the classpath
	def installerJson = new JsonSlurper().parse(rootProject.file("src/main/resources/fabric-installer.json"))
	installerJson.libraries.common.each {
		minecraftTestClientRuntimeLibraries it.name
	}

	// Use Fabric's auto client test
	productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:0.89.3+1.20.2"
	productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:0.89.3+1.20.2:testmod"
}

def loaderJarTask = project(":").tasks.finalJar

tasks.register('runProductionAutoTestClient', net.fabricmc.loom.task.prod.ClientProductionRunTask) {
	mods.from configurations.productionRuntimeMods
	mods.from remapJar

	classpath.setFrom loaderJarTask
	classpath.from configurations.minecraftTestClientRuntimeLibraries
	classpath.from loom.minecraftProvider.minecraftClientJar

	jvmArgs.add("-Dfabric.autoTest")
	programArgs.add("nogui")
}