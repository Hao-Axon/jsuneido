/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import suneido.SuContainer;
import suneido.TheDbms;
import suneido.compiler.Compiler;
import suneido.runtime.Args;
import suneido.runtime.Ops;

public class ServerEval {

	public static Object ServerEval(Object... args) {
		SuContainer c = Args.collectArgs(new SuContainer(), args);
		return TheDbms.dbms().exec(c);
	}

	public static Object exec(SuContainer c) {
		String fname = Ops.toStr(c.get(0));
		Object f = Compiler.eval(fname);
		Object[] args = new Object[] { Args.Special.EACH1, c };
		return Ops.call(f, args);
	}

}
