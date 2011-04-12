/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.Strings;

/**
 * Persistent immutable hash tree used for storing redirections.
 * Immutability relaxed during store as values are converted to addresses.
 * <p>
 * Based on <a href="http://lampwww.epfl.ch/papers/idealhashtrees.pdf">
 * Bagwell's Ideal Hash Trees</a>
 * Key and value are both int's so no hashing or overflow is required.
 * <p>
 * Similar to {@link suneido.util.PersistentMap}
 */
public abstract class DbHashTree {
	private static final int BITS_PER_LEVEL = 5;
	private static final int HASH_BITS = 1 << BITS_PER_LEVEL;
	private static final int LEVEL_MASK = HASH_BITS - 1;
	private static final int INT_BYTES = Integer.SIZE / 8;

	public static DbHashTree empty(Storage stor) {
		return new MemNode(stor);
	}

	public static DbHashTree from(Storage stor, int at) {
		return new DbNode(stor, at);
	}

	/** returns 0 if key not present */
	public abstract int get(int key);

	/** key and value must be non-zero */
	public abstract DbHashTree with(int key, int value);

	public abstract int store(Storage stor, Translator translator);

	public void print() {
		((Node) this).print(0);
	}

	/**
	 * parent for DbNode and MemNode
	 */
	private abstract static class Node extends DbHashTree {
		static final int KEY_FOR_CHILD = 0;
		protected final Storage stor;

		Node(Storage stor) {
			this.stor = stor;
		}

		@Override
		public int get(int key) {
			checkArgument(key != 0);
			return get(key, 0);
		}
		protected int get(int key, int shift) {
			assert shift < 32;
			int bit = bit(key, shift);
			int present = present();
			if ((present & bit) == 0)
				return 0;
			int i = Integer.bitCount(present & (bit - 1));
			int entryKey = key(i);
			if (entryKey != KEY_FOR_CHILD)
				return entryKey == key ? value(i) : 0;
			else { // pointer
				Node child = child(i);
				return child.get(key, shift + BITS_PER_LEVEL);
			}
		}

		protected static int bit(int key, int shift) {
			int h = (key >>> shift) & LEVEL_MASK;
			return 1 << h;
		}

		protected int size() {
			return Integer.bitCount(present());
		}

		abstract protected int present();
		abstract protected int key(int i);
		abstract protected int value(int i);
		abstract protected Node child(int i);

		@Override
		public Node with(int key, int value) {
			checkArgument(key != 0);
			checkArgument(value != 0);
			return with(key, value, 0);
		}
		abstract protected Node with(int key, int value, int shift);

		protected boolean isPointer(int i) {
			return key(i) == KEY_FOR_CHILD;
		}

		private void print(int shift) {
			String indent = Strings.repeat(" ", shift);
			System.out.println(indent + this.getClass().getSimpleName());
			for (int i = 0; i < size(); ++i) {
				if (! isPointer(i))
					System.out.println(indent + fmt(key(i)) +
							"\t" + Integer.toHexString(value(i)));
				else {
					System.out.println(indent + ">>>>>>>>");
					child(i).print(shift + BITS_PER_LEVEL);
				}
			}
		}

	}

	/** DbNode consists of:
	 * 		present	- bitmap (in an int) specifying which entries are present
	 * 		entries	- up to 32 entries, each a pair of int's
	 * 		if entry key is 0 then value points to a child node
	 */
	@Immutable
	private static class DbNode extends Node {
		private static final int ENTRIES = INT_BYTES;
		private static final int ENTRY_SIZE = 2 * INT_BYTES;
		private final ByteBuffer buf;

		DbNode(Storage stor, int adr) {
			super(stor);
			buf = stor.buffer(adr);
		}

		@Override
		protected Node with(int key, int value, int shift) {
			return new MemNode(this, key, value, shift);
		}

		@Override
		public int store(Storage stor, Translator translator) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected int present() {
			return buf.getInt(0);
		}

		@Override
		protected int key(int i) {
			return buf.getInt(ENTRIES + i * ENTRY_SIZE);
		}
		@Override
		protected int value(int i) {
			return buf.getInt(ENTRIES + i * ENTRY_SIZE + INT_BYTES);
		}

		@Override
		protected Node child(int i) {
			return load(stor, value(i));
		}

	}

	/**
	 * In-memory node used while transaction is in progress.
	 */
	@Immutable
	private static class MemNode extends Node {
		private int present;
		private final int[] keys;
		private final Object[] values;

		MemNode(Storage stor) {
			super(stor);
			present = 0;
			keys = new int[0];
			values = new Object[0];
		}

		/** clone node allowing room for a new entry */
		private MemNode(Node node) {
			super(node.stor);
			present = node.present();
			int n = size();
			if (node instanceof MemNode) {
				MemNode mnode = (MemNode) node;
				keys = Arrays.copyOf(mnode.keys, n + 1);
				values = Arrays.copyOf(mnode.values, n + 1);
			} else {
				keys = new int[n + 1];
				values = new Object[n + 1];
				for (int i = 0; i < n; ++i) {
					keys[i] = node.key(i);
					values[i] = node.value(i); // boxing
				}
			}
		}

		/** node + key,value */
		private MemNode(Node node, int key, int value, int shift) {
			this(node);
			assert shift < 32;
			int bit = bit(key, shift);
			int i = Integer.bitCount(present & (bit - 1));
			if ((present & bit) == 0) {
				insert(keys, i, key);
				insert(values, i, value);
				present |= bit;
			} else if (keys[i] == key) {
				values[i] = value;
			} else if (isPointer(i)) {
				Object ptr = values[i];
				if (ptr instanceof Integer)
					ptr = load(stor, (Integer) ptr);
				values[i] = ((Node) ptr).with(key, value, shift + BITS_PER_LEVEL);
			} else { // collision, change entry to pointer
				values[i] = new MemNode(stor, keys[i], values[i], key, value,
					shift + BITS_PER_LEVEL);
				keys[i] = KEY_FOR_CHILD;
			}
		}

		private static void insert(int[] data, int i, int value) {
			System.arraycopy(data, i, data, i + 1, data.length - i - 1);
			data[i] = value;
		}
		private static void insert(Object[] data, int i, int value) {
			System.arraycopy(data, i, data, i + 1, data.length - i - 1);
			data[i] = value;
		}

		@Override
		protected MemNode with(int key, int value, int shift) {
			return new MemNode(this, key, value, shift);
		}

		/**
		 * create a node with two entries
		 * used to create a new child when there is a collision
		 */
		private MemNode(Storage stor, int key1, Object value1, int key2, int value2, int shift) {
			super(stor);
			assert shift < 32;
			assert key1 != key2;
			int bits1 = (key1 >>> shift) & LEVEL_MASK;
			int bits2 = (key2 >>> shift) & LEVEL_MASK;
			if (bits1 == bits2) { // collision
				keys = new int[] { KEY_FOR_CHILD };
				values = new Object[] { new MemNode(stor,
						key1, value1, key2, value2,	shift + BITS_PER_LEVEL) };
				present = (1 << bits1);
			} else {
				if (bits1 < bits2) {
					keys = new int[] { key1, key2 };
					values = new Object[] { value1, value2 };
				} else {
					keys = new int[] { key2, key1 };
					values = new Object[] { value2, value1 };
				}
				present = (1 << bits1) | (1 << bits2);
			}
		}

		@Override
		public int store(Storage stor, Translator translator) {
			for (int i = 0; i < size(); ++i)
				if (isPointer(i)) {
					if (values[i] instanceof MemNode) {
						int adr = ((MemNode) values[i]).store(stor, translator);
						values[i] = adr;
					}
				} else {
					int value = (Integer) values[i];
					value = translator.translate(value);
					values[i] = value;
				}
			int adr = stor.alloc(byteBufSize());
			ByteBuffer buf = stor.buffer(adr);
			toByteBuf(buf);
			return adr;
		}


		public void toByteBuf(ByteBuffer buf) {
			buf.putInt(present);
			for (int i = 0; i < size(); ++i) {
				buf.putInt(keys[i]);
				buf.putInt((Integer) values[i]);
			}
		}

		public int byteBufSize() {
			return INT_BYTES + // present
					(2 * size() * INT_BYTES); // keys and values
		}

		@Override
		protected int present() {
			return present;
		}

		@Override
		protected int key(int i) {
			return keys[i];
		}

		@Override
		protected int value(int i) {
			return (Integer) values[i];
		}

		@Override
		protected Node child(int i) {
			Object ptr = values[i];
			if (ptr instanceof Node)
				return (Node) ptr;
			else
				return new DbNode(stor, (Integer) ptr);
		}

	} // end of MemNode

	public static String fmt(int n) {
		if (n == 0)
			return "0";
		String s = "";
		for (; n != 0; n >>>= 5)
			s = (n & 0x1f) + "." + s;
		return s.substring(0, s.length() - 1);
	}

	private static Node load(Storage stor, int at) {
		return new DbNode(stor, at);
	}

}
