package net.fabricmc.loader.launch.knot;

import com.google.common.collect.ImmutableList;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

public class MixinServiceKnot implements IMixinService, IClassProvider, IClassBytecodeProvider {
	private final ReEntranceLock lock;

	public MixinServiceKnot() {
		lock = new ReEntranceLock(1);
	}

	@Override
	public byte[] getClassBytes(String name, String transformedName) throws IOException {
		return ((Knot) FabricLauncherBase.getLauncher()).getClassByteArray(name);
	}

	@Override
	public byte[] getClassBytes(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		return ((Knot) FabricLauncherBase.getLauncher()).getClassByteArray(name);
	}

	@Override
	public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
		ClassReader reader = new ClassReader(((Knot) FabricLauncherBase.getLauncher()).getClassByteArray(name));
		ClassNode node = new ClassNode();
		reader.accept(node, 0);
		return node;
	}

	@Override
	public URL[] getClassPath() {
		return FabricLauncherBase.getLauncher().getClasspathURLs().toArray(new URL[0]);
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return FabricLauncherBase.getLauncher().getTargetClassLoader().loadClass(name);
	}

	@Override
	public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, FabricLauncherBase.getLauncher().getTargetClassLoader());
	}

	@Override
	public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, Knot.class.getClassLoader());
	}

	@Override
	public String getName() {
		return "Knot";
	}

	@Override
	public boolean isValid() {
		return FabricLauncherBase.getLauncher() instanceof Knot;
	}

	@Override
	public void prepare() {

	}

	@Override
	public MixinEnvironment.Phase getInitialPhase() {
		return MixinEnvironment.Phase.PREINIT;
	}

	@Override
	public void init() {
	}

	@Override
	public void beginPhase() {

	}

	@Override
	public void checkEnv(Object bootSource) {

	}

	@Override
	public ReEntranceLock getReEntranceLock() {
		return lock;
	}

	@Override
	public IClassProvider getClassProvider() {
		return this;
	}

	@Override
	public IClassBytecodeProvider getBytecodeProvider() {
		return this;
	}

	@Override
	public Collection<String> getPlatformAgents() {
		return ImmutableList.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return FabricLauncherBase.getLauncher().getResourceAsStream(name);
	}

	@Override
	public void registerInvalidClass(String className) {

	}

	@Override
	public boolean isClassLoaded(String className) {
		return FabricLauncherBase.getLauncher().isClassLoaded(className);
	}

	@Override
	public String getClassRestrictions(String className) {
		return "";
	}

	@Override
	public Collection<ITransformer> getTransformers() {
		return Collections.emptyList();
	}

	@Override
	public String getSideName() {
		return FabricLauncherBase.getLauncher().getEnvironmentType().name();
	}
}
