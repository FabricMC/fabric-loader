package net.fabricmc.loader.launch.nolauncher;

import com.google.common.collect.ImmutableList;
import net.fabricmc.loader.launch.common.CommonLauncherUtils;
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

public class MixinServiceNoLauncher implements IMixinService, IClassProvider, IClassBytecodeProvider {
	private final ReEntranceLock lock;

	public MixinServiceNoLauncher() {
		lock = new ReEntranceLock(1);
	}

	@Override
	public byte[] getClassBytes(String name, String transformedName) throws IOException {
		return NoLauncher.INSTANCE.getClassByteArray(name);
	}

	@Override
	public byte[] getClassBytes(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		return NoLauncher.INSTANCE.getClassByteArray(name);
	}

	@Override
	public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
		ClassReader reader = new ClassReader(NoLauncher.INSTANCE.getClassByteArray(name));
		ClassNode node = new ClassNode();
		reader.accept(node, 0);
		return node;
	}

	@Override
	public URL[] getClassPath() {
		return NoLauncher.INSTANCE.getClasspathURLs().toArray(new URL[0]);
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return NoLauncher.INSTANCE.getTargetClassLoader().loadClass(name);
	}

	@Override
	public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, NoLauncher.INSTANCE.getTargetClassLoader());
	}

	@Override
	public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, NoLauncher.class.getClassLoader());
	}

	@Override
	public String getName() {
		return "NoLauncher";
	}

	@Override
	public boolean isValid() {
		return CommonLauncherUtils.getLauncher() instanceof NoLauncher;
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
		return NoLauncher.INSTANCE.getResourceAsStream(name);
	}

	@Override
	public void registerInvalidClass(String className) {

	}

	@Override
	public boolean isClassLoaded(String className) {
		return NoLauncher.INSTANCE.isClassLoaded(className);
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
		return NoLauncher.INSTANCE.getEnvironmentType().name();
	}
}
