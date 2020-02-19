package net.fabricmc.loader.transformer.escalator;

import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class AccessEscalatorVisitor extends ClassVisitor {
	private final AccessEscalator accessEscalator;

	private String className;

	public AccessEscalatorVisitor(int api, ClassVisitor classVisitor, AccessEscalator accessEscalator) {
		super(api, classVisitor);
		this.accessEscalator = accessEscalator;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		super.visit(
				version,
				accessEscalator.getClassAccess(name).apply(access),
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
				accessEscalator.getClassAccess(name).apply(access)
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				accessEscalator.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return super.visitMethod(
				accessEscalator.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				exceptions
		);
	}
}
