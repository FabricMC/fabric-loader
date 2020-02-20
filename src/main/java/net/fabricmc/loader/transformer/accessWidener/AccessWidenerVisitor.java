package net.fabricmc.loader.transformer.accessWidener;

import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class AccessWidenerVisitor extends ClassVisitor {
	private final AccessWidener accessWidener;

	private String className;

	public AccessWidenerVisitor(int api, ClassVisitor classVisitor, AccessWidener accessWidener) {
		super(api, classVisitor);
		this.accessWidener = accessWidener;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		super.visit(
				version,
				accessWidener.getClassAccess(name).apply(access),
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
				accessWidener.getClassAccess(name).apply(access)
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				accessWidener.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return super.visitMethod(
				accessWidener.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access),
				name,
				descriptor,
				signature,
				exceptions
		);
	}
}
