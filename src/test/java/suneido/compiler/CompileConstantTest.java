/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import suneido.SuContainer;
import suneido.SuDate;
import suneido.SuRecord;
import suneido.runtime.SuCallBase;
import suneido.util.Dnum;

public class CompileConstantTest {

	@Test
	public void constant() {
		assertEquals(Boolean.TRUE, compile("true"));
		assertEquals(Boolean.FALSE, compile("false"));

		assertEquals(123, compile("123"));
		assertEquals(-123, compile("-123"));
		assertEquals(Dnum.parse("12.34"), compile("12.34"));

		assertEquals("hello world", compile("'hello world'"));
		assertEquals("symbol", compile("#symbol"));
		assertEquals("identifier", compile("identifier"));

		assertEquals(SuDate.fromLiteral("20090310"), compile("#20090310"));
		assertEquals(SuDate.fromLiteral("20090310.1026"),
				compile("#20090310.1026"));

		SuContainer c = new SuContainer();
		c.add(12);
		c.put("ab", "cd");
		assertEquals(c, compile("#(12, ab: cd)"));

		SuContainer cc = new SuContainer();
		cc.add(0);
		cc.add(c);
		assertEquals(cc, compile("#(0, (12, ab: cd))"));

		compile("#(function () { }, function () { })");
		compile("#(a: (b: function () { }))");

		assertEquals(new SuContainer(), compile("#()"));
		assertEquals(new SuRecord(), compile("#{}"));

		assertTrue(compile("function () { }") instanceof SuCallBase);
	}

	private static Object compile(String s) {
		return Compiler.compile("Test", s);
	}
}
