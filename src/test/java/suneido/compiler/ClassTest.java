/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static suneido.compiler.Compiler.compile;
import static suneido.compiler.Compiler.eval;
import static suneido.compiler.ExecuteTest.def;
import static suneido.compiler.ExecuteTest.test;
import static suneido.compiler.ExecuteTest.testDisp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import suneido.runtime.Ops;

public class ClassTest {

	@Before
	public void setQuoting() {
		Ops.default_single_quotes = true;
	}

	@After
	public void restoreQuoting() {
		Ops.default_single_quotes = false;
	}

	@Test
	public void test1() {
		def("A", "class { }");
		test("new A", "A()");

		def("A", "class { N: 123 }");
		test("A.N", "123");
		notFound("A.M");

		def("A", "class { F() { 123 } }");
		test("A.F()", "123");
		test("A().F()", "123");

		def("A", "class { F?() { 123 } G!() { 456 } }");
		test("A.F?()", "123");
		test("A.G!()", "456");

		def("A", "class { F() { .G() } G() { 123 } }");
		test("A().F()", "123");

		def("A", "class { F() { .x } x: 123 }");
		test("A().F()", "123");

		def("A", "class { F() { .g() } g() { 123 } }");
		test("A().F()", "123");

		def("A", "class { New(x) { .X = x } }");
		test("A(456).X", "456");

		def("A", "class { Default(method) { 'missing: ' $ method } }");
		test("A().F()", "'missing: F'");

		def("A", "class { F(n) { n * 2 } }");
		def("B", "A { }");
		test("B().F(123)", "246");

		def("A", "class { G() { .x = 456 } }");
		def("B", "A { F() { .x = 123; .G(); .x } }");
		test("B().F()", "123");

		def("A", "class { G() { .X = 123 } }");
		def("B", "A { F() { .G(); .X } }");
		test("B().F()", "123");

		def("A", "class { Call() { 123 } CallClass() { 456 } }");
		test("A()", "456");
		test("a = new A; a()", "123");

		def("A", "class { N: 123 F() { .N } }");
		test("A.N", "123");
		test("A.F()", "123");
		test("A().N", "123");
		test("A().F()", "123");
		def("B", "A { G() { .N } }");
		test("B.N", "123");
		test("B.F()", "123");
		test("B.G()", "123");
		test("B().N", "123");
		test("B().F()", "123");
		test("B().G()", "123");

		def("A", "class { New() { .A = 123 } }");
		def("B", "A { New() { .B = 456 } }");
		test("b = B(); b.A + b.B", "579");

		def("A", "class { New(n) { .A = n } }");
		def("B", "A { New() { super(123) } }");
		test("B().A", "123");

		def("A", "class { New(n) { .A = n } }");
		def("B", "A { }"); // New args pass through
		def("C", "B { New(n) { super(123) } }");
		test("C(123).A", "123");

		def("A", "class { F() { 123 } }");
		def("B", "A { F() { 456 } G() { super.F() } }");
		test("B.G()", "123");
		test("B().G()", "123");

		// super call in a block
		def("A", "class { F() { 123 } }");
		def("B", "A { F() { 456 } G() { b = { super.F() }; b() } }");
		test("B.G()", "123");
		test("B().G()", "123");

		def("A", "class { B: class { F() { 123 } } }");
		test("(new A.B).F()", "123");
		testDisp("new A.B", "A.B()");

		def("A", "class { F() { 123 } N: 123 }");
		def("B", "A { }");
		testDisp("A.F", "A.F /* method */");
		testDisp("B.F", "A.F /* method */");
		testDisp("B.N", "123");
		notFound("B.M");

		def("A", "class { New(args) { super(@args) } }");

		def("A", "class { ToString() { 'an A' } }");
		testDisp("A()", "an A");
		def("A", "class { New(n) { .n = n } ToString() { 'A' $ .n } }");
		testDisp("A(123)", "A123");

		def("A", "class { CallClass() { 123 } }");
		def("B", "A { }");
		test("A()", "123");
		test("B()", "123");

		def("A", "class { F() { } }");
		def("B", "A { G() { } }");
		test("B.Members()", "#('G')");

		def("A", "class { F() { b = { .G() }; b() } }");
		def("B", "A { G() { 123 } }");
		test("B.F()", "123");

		def("C", "class { X: function () { 123 } }");
		test("C.X()", "123");

		def("C", "#( X: (function () {}) )");
		test("Type(C.X[0])", "'Function'");

		def("C", "class { X: (function () {}) }");
		test("Type(C.X[0])", "'Function'");

		def("X", "#(function () { 123 })");
		test("(X[0])()", "123");

		def("X", "#(func: function () { 123 })");
		test("(X.func)()", "123");

		def("A", "class { CallClass() { new this() } }");
		test("A()", "A()");

		def("A", "class { New() { .a = 123; } }");
		test("A().Members()", "#('A_a')");

		def("A", "class { New() { this.a = 123; } }");
		test("A().Members()", "#('a')");

		def("A", "class { New() { this['a'] = 123; } }");
		test("A().Members()", "#('a')");

		// method should be bound to starting point, not where found
		def("A", "class { F() { .G() } }");
		def("B", "class : A { G() { 123 } }");
		test("f = B.F; f()", "123");
	}
	@Test public void test_static_getter() {
		def("A", "class { " +
				"Get_N() { 'getter' }" +
				" }");
		test("A.N", "'getter'");
		notFound("A.X");

		def("B", "A { }");
		test("B.N", "'getter'");
		notFound("B.X");

		def("A", "class { " +
				"Get_N() { 'getter' }" + // will never be used
				"Get_(m) { 'get ' $ m }" +
				" }");
		test("A.N", "'get N'");
		test("A.X", "'get X'");

		def("B", "A { }");
		test("B.N", "'get N'");
		test("B.X", "'get X'");

	}
	@Test public void test_instance_getter() {
		def("A", "class { "
				+ "New(x) { .X = x } "
				+ "Get_N() { .X $ ' getter' } "
				+ "}");
		test("A(1).N", "'1 getter'");
		notFound("A(1).Z");

		def("B", "A { }");
		test("B(2).N", "'2 getter'");
		notFound("B(2).Z");

		def("A", "class { "
				+ "New(x) { .X = x } "
				+ "Get_N() { .X $ ' getter' } " // will never be used
				+ "Get_(m) { .X $ ' get ' $ m } "
				+ "}");
		test("A(1).N", "'1 get N'");
		test("A(1).Z", "'1 get Z'");

		def("B", "A { }");
		test("B(2).N", "'2 get N'");
		test("B(2).Z", "'2 get Z'");

	}
	@Test public void test_private_instance_getter() {
		def("A", "class { "
				+ "New(x) { .x = x } "
				+ "get_n() { .x $ ' getter' } "
				+ "N() { .n }"
				+ "Z() { .z }"
				+ "}");
		test("A(1).N()", "'1 getter'");
	}

	@Test
	public void test_eval() {
		def("F", "function () { this }");
		test("#(1).Eval(F)", "#(1)");

		def("C", "class { CallClass() { this } }");
		test("#(1).Eval(C)", "#(1)");

		testDisp("C.Eval(F)", "C /* class */");
		testDisp("(new C).Eval(F)", "C()");

		def("B", "C { }");
		testDisp("B.Eval(F)", "B /* class : C */");
	}

	@Test
	public void test_privatize() {
		def("C", "class { F() { .p() }; Default(@a) { a } }");
		test("C.F()", "#('C_p')");
	}

	@Test
	public void test_super() { // super must be first
		try {
			compile("A", "class { New() { F(); super() } }");
			fail("call to super must come first");
		} catch (Exception e) {
			assertEquals("call to super must come first", e.toString());
		}
	}

	@Test
	public void test_super2() { // super must be in New
		try {
			compile("A", "class { F() { super() } }");
			fail("should only allow super(...) in New");
		} catch (Exception e) {
			assertEquals("super call only allowed in New", e.toString());
		}
	}

	@Test
	public void test_super_Default() {
		def("A", "class { }");
		def("B", "class : A { Default(@args) { super.Default(@args) } }");
		try {
			test("B.Fn()", "");
			fail();
		} catch (Exception e) {
			assert(e.toString().contains("method not found"));
		}
	}

	@Test
	public void test_getdefault() {
		def("C", "class { X: 123 }");
		test("C.GetDefault('X', 456)", "123");
		test("C.GetDefault('Y', 456)", "456");
		test("C.GetDefault('Y', { 456 })", "456");
		testDisp("C.GetDefault('Y', function () { 456 })", "/* function */");
		test("x = C(); x.GetDefault('X', 456)", "123");
		test("x = C(); x.GetDefault('Y', 456)", "456");
		test("x = C(); x.GetDefault('Y', { 456 })", "456");
		test("x = C(); x.GetDefault('Y', { x; 456 })", "456"); // closure
		testDisp("x = C(); x.GetDefault('Y', function () { 456 })", "/* function */");
		test("x = C(); x.X = 999; x.GetDefault('X', 456)", "999");
	}

	private static void notFound(String expr) {
		try {
			eval(expr);
			fail();
		} catch (Exception e) {
			assert e.toString().startsWith("uninitialized member");
		}
	}

}
