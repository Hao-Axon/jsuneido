package suneido.language.builtin;

import suneido.Suneido;
import suneido.TheDbms;
import suneido.language.FunctionSpec;
import suneido.language.Ops;
import suneido.language.SuFunction1;

public class Unuse extends SuFunction1 {

	{ params = new FunctionSpec("library"); }

	@Override
	public Object call1(Object a) {
		if (! TheDbms.dbms().unuse(Ops.toStr(a)))
			return false;
		Suneido.context.clearAll();
		return true;
	}

}
