/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido;

import static suneido.SuException.methodNotFound;

import java.nio.ByteBuffer;

import suneido.runtime.CallableType;
import suneido.runtime.Ops;
import suneido.runtime.SuCallable;
import suneido.runtime.SuClass;

/**
 * Base class for Suneido data types.
 * e.g. {@link SuContainer}, {@link SuRecord}, {@link SuClass}
 */
public class SuValue implements Packable {

	@Override
	public String toString() {
		return "a" + typeName();
	}

	/**
	 * <p>Computes the contribution of a value to the hash code of a container
	 * which it belongs to. Overridden by {@link SuContainer}</p>
	 * <p>The {@link #hashCode()} contract applies equally to this method. In
	 * particular, for any two SuValue objects {@code A} and {@code B} such that
	 * {@code A.equals(B)}, {@code A.hashCodeContrib()} must be equal to
	 * {@code B.hashCodeContrib()}.</p>
	 */
	public int hashCodeContrib() {
		return hashCode();
	}

	/**
	 * used for non-method calls
	 */
	public Object call(Object... args) {
		throw new SuException("can't call " + typeName());
	}

	/**
	 * used for method calls
	 */
	public Object eval(Object self, Object... args) {
		throw new SuException("can't call " + typeName());
	}

	public String typeName() {
		return simplifyType(getClass().getName());
	}

	static String simplifyType(String s) {
		// prefix
		assert(s.startsWith("suneido."));
		int i = s.lastIndexOf('.');
		s = s.substring(i + 1);
		if (s.startsWith("Su"))
			s = s.substring(2);

		// suffix
		i = s.indexOf('$');
		if (i != -1)
			s = s.substring(0, i);
		if (s.endsWith("Class"))
			s = s.substring(0, s.length() - 5);

		return s;
	}

	/**
	 * <p>
	 * Returns the name of this value instance within Suneido, as opposed to
	 * the name of its type. This function is used to implement the Suneido
	 * {@code Name(value)} built-in function.
	 * </p>
	 * <p>
	 * For example, if you have a global Suneido class named "Control", then:
	 * <ul>
	 * <li>
	 * <pre>Name(Control)
	 *     => "Control"  // value returned by SuValue.valueName()</pre>
	 * </li>
	 * <li>
	 * <pre>Type(Control)
	 *     => "Class"    // name returned by SuValue.typeName()</pre>
	 * </li>
	 * </ul>
	 * </p>
	 * @see #typeName()
	 */
	public String valueName() {
		String name = internalName();
		if (null == name || "eval".equals(name) || name.startsWith("eval$")) {
			return "";
		} else {
			return name;
		}
	}

	public Object get(Object member) {
		throw new SuException(typeName() + " " + this
				+ " does not support get " + member);
	}

	public void put(Object member, Object value) {
		throw new SuException(typeName() + " does not support put");
	}

	public Object rangeTo(int i, int j) {
		throw new SuException(typeName() + " does not support range");
	}

	public Object rangeLen(int i, int n) {
		throw new SuException(typeName() + " does not support range");
	}

	public int packSize() {
		return packSize(0);
	}

	@Override
	public int packSize(int nest) {
		throw new SuException("can't pack " + typeName());
	}

	@Override
	public void pack(ByteBuffer buf) {
		throw new SuException("can't pack " + typeName());
	}

	public SuContainer toContainer() {
		return null;
	}

	public static boolean isCallable(Object x) {
		return x instanceof SuValue && ((SuValue) x).isCallable();
	}

	protected boolean isCallable() {
		return false;
	}

	public String internalName() {
		return null;
	}

	public Object call0() {
		return call();
	}
	public Object call1(Object a) {
		return call(a);
	}
	public Object call2(Object a, Object b) {
		return call(a, b);
	}
	public Object call3(Object a, Object b, Object c) {
		return call(a, b, c);
	}
	public Object call4(Object a, Object b, Object c, Object d) {
		return call(a, b, c, d);
	}

	public Object eval0(Object self) {
		return eval(self);
	}
	public Object eval1(Object self, Object a) {
		return eval(self, a);
	}
	public Object eval2(Object self, Object a, Object b) {
		return eval(self, a, b);
	}
	public Object eval3(Object self, Object a, Object b,
			Object c) {
		return eval(self, a, b, c);
	}
	public Object eval4(Object self, Object a, Object b,
			Object c, Object d) {
		return eval(self, a, b, c, d);
	}

	/** used by {@link Ops} invoke to get a named method */
	public SuValue lookup(String method) {
		return new NotFound(method);
	}

	public static class NotFound extends SuCallable {
		String method;
		public NotFound(String method) {
			this.callableType = CallableType.METHOD_NOT_FOUND;
			this.method = method;
		}
		@Override
		public Object eval(Object self, Object... args) {
			throw methodNotFound(self, method);
		}
	}

	public String display() {
		return toString();
	}

}
