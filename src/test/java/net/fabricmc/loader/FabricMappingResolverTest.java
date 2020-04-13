package net.fabricmc.loader;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class FabricMappingResolverTest {
	public static void main(String[] args) {
		FabricMappingResolver mappingResolver = new FabricMappingResolver(FabricMappingResolverTest::createTestMappings, "bar");

		assertEquals("class_bar", mappingResolver.mapClassName("foo", "class_foo"));
		assertEquals("class_bar", mappingResolver.mapClassName("baz", "class_baz"));
		assertEquals("field_bar", mappingResolver.mapFieldName("foo", "class_foo", "field_foo", "Lclass_foo;"));
		assertEquals("field_bar", mappingResolver.mapFieldName("baz", "class_baz", "field_baz", "Lclass_baz;"));
		assertEquals("method_bar", mappingResolver.mapMethodName("foo", "class_foo", "method_foo", "(ILclass_foo;)Lclass_foo;"));
		assertEquals("method_bar", mappingResolver.mapMethodName("baz", "class_baz", "method_baz", "(ILclass_baz;)Lclass_baz;"));

		assertEquals("class_foo", mappingResolver.unmapClassName("foo", "class_bar"));
		assertEquals("class_baz", mappingResolver.unmapClassName("baz", "class_bar"));
		assertEquals("field_foo", mappingResolver.unmapFieldName("foo", "class_bar", "field_bar", "Lclass_bar;"));
		assertEquals("field_baz", mappingResolver.unmapFieldName("baz", "class_bar", "field_bar", "Lclass_bar;"));
		assertEquals("method_foo", mappingResolver.unmapMethodName("foo", "class_bar", "method_bar", "(ILclass_bar;)Lclass_bar;"));
		assertEquals("method_baz", mappingResolver.unmapMethodName("baz", "class_bar", "method_bar", "(ILclass_bar;)Lclass_bar;"));

		System.out.println("All tests successful");
	}

	private static void assertEquals(String a, String b) {
		if (!a.equals(b)) {
			throw new AssertionError(a + " != " + b);
		}
	}

	private static TinyTree createTestMappings() {
		String mappings = "tiny\t2\t0\tfoo\tbar\tbaz\n" +
			"c\tclass_foo\tclass_bar\tclass_baz\n" +
			"\tf\tLclass_foo;\tfield_foo\tfield_bar\tfield_baz\n" +
			"\tm\t(ILclass_foo;)Lclass_foo;\tmethod_foo\tmethod_bar\tmethod_baz\n";
		try {
			return TinyMappingFactory.load(new BufferedReader(new StringReader(mappings)));
		} catch (IOException e) {
			throw new AssertionError("Failed to load mappings", e);
		}
	}
}
