package net.fabricmc.loader.transformer.decapsulator;

import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class DecapsulatorVisitor extends ClassVisitor {
	private final Decapsulator decapsulator;

	private String className;

	public DecapsulatorVisitor(int api, ClassVisitor classVisitor, Decapsulator decapsulator) {
		super(api, classVisitor);
		this.decapsulator = decapsulator;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		super.visit(
				version,
				decapsulator.getClassAccess(name).apply(access),
				name,
				signature,
				superName,
				interfaces
		);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		super.visitInnerClass(
				name,
				outerName,
				innerName,
				decapsulator.getClassAccess(name).apply(access)
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				decapsulator.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return super.visitMethod(
				decapsulator.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				exceptions
		);
	}
}
