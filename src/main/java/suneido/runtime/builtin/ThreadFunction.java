/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import suneido.runtime.Ops;
import suneido.runtime.Params;
import suneido.util.Errlog;

public class ThreadFunction {

	@Params("callable")
	public static Object ThreadFunction(Object fn) {
		Thread thread = new Thread(new Callable(fn));
		thread.setDaemon(true); // so it won't stop Suneido exiting
		thread.start();
		return null;
	}

	private static class Callable implements Runnable {
		private final Object callable;

		public Callable(Object callable) {
			this.callable = callable;
		}

		@Override
		public void run() {
			try {
				Ops.call(callable);
			} catch (Throwable e ) {
				Errlog.uncaught("in thread", e);
			}
		}

	}

}
