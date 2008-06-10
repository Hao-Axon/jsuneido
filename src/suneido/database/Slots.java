package suneido.database;

import java.nio.ByteBuffer;

/**
 * Collection of {@link Slot}'s for a {@link Btree} node,
 * plus next and prev addresses (offsets in the database).
 * Next and prev are stored at the start of the buffer
 * followed by a {@link BufRecord} holding the slots.
 * @author Andrew McKinlay
 */
public class Slots {
	final private static int NEXT_OFFSET = 0;
	final private static int PREV_OFFSET = 4;
	final private static int REC_OFFSET = 8;
	final protected static int BUFSIZE = Btree.NODESIZE - 8;
	
	private ByteBuffer buf;
	private BufRecord rec;
	
	public Slots(ByteBuffer buf) {
		this.buf = buf;
		buf.position(REC_OFFSET);
		rec = new BufRecord(buf.slice());
	}

	public boolean empty() {
		return rec.bufSize() == 0;
	}
	public int size() {
		return rec.bufSize();
	}
	public Slot front() {
		return get(0);
	}
	public Slot back() {
		return get(size() - 1);
	}
	public Slot get(int i) {
		return null;
	}
	public boolean insert(int i, Slot slot) {
		return true;
	}
	public void append(Slot slot) {
		rec.add(slot);
	}
	public void append(Slots slots, int begin, int end) {
		for (int i = begin; i < end; ++i) {
			append(slots.get(i));
		}
	}
	public void erase(int i) {
	}
	public void erase(Slot slot) {
	}
	public void erase(int begin, int end) {
	}
	
	public long next() {
		return buf.getLong(NEXT_OFFSET);
	}
	public long prev() {
		return buf.getLong(PREV_OFFSET);
	}
	public void setNext(long value) {
		buf.putLong(NEXT_OFFSET, value);
	}
	public void setPrev(long value) {
		buf.putLong(PREV_OFFSET, value);
	}

	public static void setBufNext(ByteBuffer buf, long value) {
		buf.putLong(NEXT_OFFSET, value);
	}
	public static void setBufPrev(ByteBuffer buf, long value) {
		buf.putLong(PREV_OFFSET, value);
	}
	
	public int lower_bound(Slot slot) {
		return 0;
	}
}
