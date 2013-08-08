package suneido.language.jsdi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

import suneido.SuContainer;
import suneido.language.SuCallable;
import suneido.language.jsdi.type.Callback;

/**
 * Maps a set of {@link SuCallable"} objects to the Java-side {@link Callback}
 * object and native-side thunk, if any, to which they are bound. 
 * 
 * @author Victor Schappert
 * @since 20130806
 */
@DllInterface
@Allocates
public final class ThunkManager {

	//
	// STATIC MEMBERS
	//

	public static final int THUNK_FUNC_ADDR_INDEX = 0;
	public static final int THUNK_OBJECT_ADDR_INDEX = 1;

	//
	// TYPES
	//

	private static class BoundThunk implements Comparable<BoundThunk> {
		public final SuCallable callable;
		public final Callback callback;
		public final int thunkFuncAddr;
		public final int thunkObjectAddr;
		public final Date createTime;

		public BoundThunk(SuCallable callable, Callback callback,
				int thunkFuncAddr, int thunkObjectAddr) {
			assert null != callable && null != callback;
			if (0 == thunkFuncAddr) {
				throw new IllegalArgumentException(
						"native address of thunk function cannot be 0");
			}
			if (0 == thunkObjectAddr) {
				throw new IllegalArgumentException(
						"native address of thunk object cannot be 0");
			}
			this.callable = callable;
			this.callback = callback;
			this.thunkFuncAddr = thunkFuncAddr;
			this.thunkObjectAddr = thunkObjectAddr;
			this.createTime = new Date();
		}

		//
		// INTERFACE: Comparable<BoundThunk>
		//

		public int compareTo(BoundThunk other)
		{ return createTime.compareTo(other.createTime); }

		//
		// ANCESTOR CLASS: Object
		//

		@Override
		public String toString()
		{
			return new StringBuilder(128).append(callable.toString())
					.append(" + ").append(callback.toString())
					.append(" => (0x").append(Integer.toHexString(thunkFuncAddr))
					.append(", 0x").append(Integer.toHexString(thunkObjectAddr))
					.append(')').toString();
		}
	}

	//
	// DATA
	//

	private final JSDI jsdi;
	private final HashMap<SuCallable, BoundThunk> callableMap;

	//
	// CONSTRUCTORS
	//

	/**
	 * Deliberately package-internal. Please use {@link JSDI#getThunkManager()}.
	 * @param jsdi JSDI instance which owns this thunk manager
	 */
	ThunkManager(JSDI jsdi) {
		this.jsdi = jsdi;
		this.callableMap = new HashMap<SuCallable, BoundThunk>();
	}

	//
	// ACCESSORS
	//

	/**
	 * If {@code callable} is already bound to an existing thunk, returns the
	 * native-side function address of that thunk. If {@code callable} is not
	 * yet bound, binds it to a thunk and returns the native-side function
	 * address of the newly-created thunk.
	 * @param callable SuCallable to bind
	 * @param callback Callback instance which knows how to unmarshall the
	 *                 arguments sent from the native side and pass them on to
	 *                 {@code callable}
	 * @return Native side function address
	 * @throws JSDIException If {@code callable} has already been bound to a
	 *         {@link Callback} that is not reference-equal to {@code callback}
	 */
	public int lookupOrCreateBoundThunk(
		SuCallable callable,
		Callback callback
	)
	{
		if (null == callable) {
			throw new IllegalArgumentException("callable cannot be null");
		}
		if (null == callback) {
			throw new IllegalArgumentException("callback cannot be null");
		}
		BoundThunk boundThunk = null;
		MarshallPlan plan = callback.getMarshallPlan();
		synchronized (this) {
			boundThunk = callableMap.get(callable);
			if (null == boundThunk)
			{
				int[] addrs = new int[2];
				newThunk(callback, callable, plan.getSizeDirect(),
						plan.getSizeIndirect(), plan.getPtrArray(),
						plan.getVariableIndirectCount(), addrs);
				boundThunk = new BoundThunk(callable, callback,
						addrs[THUNK_FUNC_ADDR_INDEX],
						addrs[THUNK_OBJECT_ADDR_INDEX]);
				callableMap.put(callable, boundThunk);
			}
		}
		if (boundThunk.callback != callback) {
			// Don't permit the same SuCallable instance to be bound to multiple
			// different instances of Callback (or indeed multiple different
			// thunks). This limitation isn't forced by technical requirements,
			// but rather by the historical definition of the Suneido
			// 'ClearCallback()' global function, which takes a reference to the
			// callable object to be cleared.
			StringBuilder error = new StringBuilder();
			error.append("can't bind callable ")
					.append(callable.toString())
					.append(" to multiple different callback definitions [original: ")
					.append(boundThunk.callback.toString()).append(" versus: ")
					.append(callback.toString()).append(']');
			throw new JSDIException(error.toString());
		}
		return boundThunk.thunkFuncAddr;
	}

	//
	// INTERNALS
	//

	private ArrayList<BoundThunk> thunkSnapshot() {
		ArrayList<BoundThunk> thunks = new ArrayList<BoundThunk>(
				callableMap.size());
		thunks.addAll(callableMap.values());
		Collections.sort(thunks);
		return thunks;
	}

	private synchronized SuContainer callbacks() {
		ArrayList<BoundThunk> thunks = thunkSnapshot();
		SuContainer result = new SuContainer(thunks.size());
		for (BoundThunk boundThunk : thunks) {
			result.add(boundThunk.callable);
		}
		return result;
	}

	private synchronized Boolean clearCallback(SuCallable callable) {
		BoundThunk boundThunk = callableMap.remove(callable);
		if (null == boundThunk) {
			return Boolean.FALSE;
		} else {
			deleteThunk(boundThunk.thunkObjectAddr);
			return Boolean.TRUE;
		}
	}

	//
	// NATIVE CALLS
	//

	private static native void newThunk(Callback callback, SuCallable callable,
			int sizeDirect, int sizeIndirect, int[] ptrArray,
			int variableIndirectCount, int[] outThunkAddrs);

	private static native void deleteThunk(int thunkObjectAddr);

	//
	// BUILT-IN FUNCTIONS
	//

	/**
	 * Class which {@link suneido.language.BuiltinMethods} can translate into
	 * the Suneido built-in function {@code Callbacks()}.
	 * @see ClearCallback
	 * @see suneido.language.Builtins
	 */
	public static final class Callbacks {
		public static final SuContainer Callbacks() {
			return JSDI.getInstance().getThunkManager().callbacks();
		}
	}

	/**
	 * Class which {@link suneido.language.BuiltinMethods} can translate into
	 * the Suneido built-in function {@code ClearCallback()}.
	 * @see Callbacks
	 * @see suneido.language.Builtins
	 */
	public static final class ClearCallback {
		public static final Boolean ClearCallback(Object callable) {
			return callable instanceof SuCallable ? JSDI.getInstance()
					.getThunkManager().clearCallback((SuCallable) callable)
					: Boolean.FALSE;
		}
	}

	//
	// ANCESTOR CLASS: Object
	//

	@Override
	public String toString()
	{
		StringBuilder result = new StringBuilder(2048);
		result.append("ThunkManager[");
		synchronized(this)
		{
			ArrayList<BoundThunk> thunks = thunkSnapshot();
			for (BoundThunk boundThunk : thunks) {
				result.append("\n\t").append(boundThunk.toString());
			}
		}
		return result.append("\n]").toString();
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			for (BoundThunk boundThunk : callableMap.values()) {
				deleteThunk(boundThunk.thunkObjectAddr);
			}
		} finally {
			super.finalize();
		}
	}
}
