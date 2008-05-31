package suneido;

import java.util.HashMap;

/**
 * The Java base class for Suneido classes.
 * The Java class hierarchy is "flat".
 * All compiled Suneido classes derive directly from SuClass.
 * Suneido inheritance is handled by invoke.
 * A Suneido class with "no" parent calls super.invoke2 from it's invoke2 default
 * else it calls Globals.get(parent).invoke2
 * There is one instance that is the class value,
 * and other instances for the instances of the class.
 * Class data is stored in the class instance m.
 * Instance data is stored in the instance m.
 */
public class SuClass extends SuValue {
	private HashMap<SuValue,SuValue> m;

	@Override
	public String toString() {
		return "a Suneido class";
	}

	public SuValue invoke(SuValue self, int method, SuValue ... args) {
		return invoke2(self, method, args);
	}
	public SuValue invoke2(SuValue self, int method, SuValue[] args) {
		return method == SuSymbol.DEFAULTi
			? super.invoke2(self, method, args)
			: invoke2(self, SuSymbol.DEFAULTi, args);
	}
	
	/**
	 * Implements Suneido's argument handling.
	 * Called at the start of generated sub-class methods.
	 *  
	 * @param nlocals	The number of local variables required, including parameters.
	 * @param args		The arguments as an SuValue array.<br />
	 * 					fn(... @args ...) => ... EACH, args ...<br />
	 * 					fn(... name: arg ...) => ... NAMED, name, arg ...<br />
	 * 					Unlike cSuneido, multiple EACH's are allowed.
	 * @param params	A variable number of parameter names as symbol indexes.<br />
	 * 					function (@args) => EACH, args<br />
	 * 					No other params are allowed with EACH.
	 * @return	The locals SuValue array initialized from args.
	 */
	public static SuValue[] massage(int nlocals, final SuValue[] args, final int ... params) {
		SuValue[] locals = new SuValue[nlocals];
		if (args.length == 0)
			return locals;
		if (params.length > 0 && params[0] == SuSymbol.EACHi) {
			// function (@params)
			if (args[0] == SuSymbol.EACH && args.length == 2)
				// optimize function (@params) (@args)
				locals[0] = new SuContainer((SuContainer) args[1]);
			else {
				SuContainer c = new SuContainer();
				locals[0] = c;
				for (int i = 0; i < args.length; ++i) {
					if (args[i] == SuSymbol.NAMED) {
						c.putdata(args[i + 1], args[i + 2]);
						i += 2;
					}
					else if (args[i] == SuSymbol.EACH)
						c.merge((SuContainer) args[++i]);
					else
						c.vec.add(args[i]);
				}
			}
		} else {
			assert nlocals >= params.length;
			int li = 0;
			for (int i = 0; i < args.length; ++i) {
				if (args[i] == SuSymbol.NAMED) {
					for (int j = 0; j < params.length; ++j)
						if (SuSymbol.symbol(params[j]) == args[i + 1])
							locals[j] = args[i + 2];
					// else ignore named arg not matching param
					i += 2;
				}
				else if (args[i] == SuSymbol.EACH) {
					SuContainer c = (SuContainer) args[++i];
					if (c.vecsize() > nlocals - li)
						throw new SuException("too many arguments");
					for (SuValue x : c.vec)
						locals[li++] = x;
					for (int j = 0; j < params.length; ++j) {
						SuValue x = c.map.get(SuSymbol.symbol(params[j]));
						if (x != null)
							locals[j] = x;
					}
				}
				else
					locals[li++] = args[i];
			}
		}
		return locals;
	}

	//TODO handle @+# args, maybe just add EACH1 since we only ever use @+1
	//TODO check for missing arguments (but what about defaults?)
}
