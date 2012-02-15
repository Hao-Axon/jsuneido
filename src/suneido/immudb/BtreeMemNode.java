/* Copyright 2012 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * New and modified btree nodes.
 * Stored immutable nodes are in {@link BtreeDbNode}
 * Mutable within a transaction.
 * Changed to immutable via freeze before going into global database state.
 */
public class BtreeMemNode extends BtreeNode {
	private final Record rec;
	private final TByteArrayList index;
	private ArrayList<Record> added;
	private boolean immutable = false;

	/** Create a new node, not based on an existing record */
	BtreeMemNode(int level) {
		this(level, null);
	}

	BtreeMemNode(BtreeDbNode node) {
		this(node.level, node.rec);
	}

	/** copy constructor, used when "modifying" an immutable node */
	private BtreeMemNode(BtreeMemNode node) {
		this(node.level, node.rec, new TByteArrayList(node.index), node.added);
	}

	BtreeMemNode(int level, Record rec) {
		this(level, rec, new TByteArrayList(), new ArrayList<Record>());
		if (rec != null)
			for (int i = 0; i < rec.size(); ++i)
				index.add((byte) i);
	}

	static BtreeMemNode from(int level, Record... data) {
		TByteArrayList index = new TByteArrayList();
		for (int i = 0; i < data.length; ++i)
			index.add((byte) (-i - 1));
		return new BtreeMemNode(level, null, index, Lists.newArrayList(data));
	}

	private BtreeMemNode(BtreeMemNode node, TByteArrayList index) {
		this(node.level, node.rec, index, node.added);
	}

	private BtreeMemNode(int level, Record rec, TByteArrayList index, ArrayList<Record> added) {
		super(level);
		this.rec = rec;
		this.index = index;
		this.added = added;
	}

	static BtreeNode newRoot(Tran tran, BtreeSplit split) {
		Record minkey = minimalKey(split.key.size(), split.left);
		return BtreeMemNode.from(split.level + 1, minkey, split.key);
	}

	static BtreeNode newRoot(Tran tran, BtreeSplit2 split) {
		Record minkey = minimalKey(split.key.size(), split.left);
		return BtreeMemNode.from(split.level + 1, minkey, split.key);
	}

	@Override
	BtreeMemNode with(Record key) {
		if (immutable)
			return new BtreeMemNode(this).with(key);
		else {
			int at = lowerBound(key);
			add(key);
			index.insert(at, (byte) -added.size());
			return this;
		}
	}

	private void add(Record key) {
		if (added.size() >= Byte.MAX_VALUE)
			cleanAdded();
		added.add(key);
	}

	private void cleanAdded() {
		ArrayList<Record> clean = Lists.newArrayList();
		for (int i = 0; i < index.size(); ++i) {
			int idx = index.get(i);
			if (idx < 0) {
				index.set(i, (byte) (-1 - clean.size()));
				clean.add(added.get(-idx - 1));
			}
		}
		assert clean.size() < Byte.MAX_VALUE;
		added = clean;
	}

	@Override
	protected BtreeNode without(int i) {
		if (immutable)
			return new BtreeMemNode(this).without(i);
		else {
			index.removeAt(i);
			return this;
		}
	}

	@Override
	BtreeNode without(int from, int to) {
		if (immutable)
			return new BtreeMemNode(this).without(from, to);
		else {
			index.remove(from, to - from);
			return this;
		}
	}

	@Override
	public BtreeNode withUpdate(int i, BtreeNode child) {
		if (immutable)
			return new BtreeMemNode(this).withUpdate(i, child);
		else {
			Record key = get(i);
			Record newkey = new RecordBuilder().addAll(key).addRef(child).build();
			add(newkey); // added is shared so can't update directly
			index.set(i, (byte) -added.size());
			return this;
		}
	}

	/** returns a new node containing a range of this node */
	@Override
	BtreeNode slice(int from, int to) {
		TByteArrayList index2 = new TByteArrayList(index.subList(from, to));
		return new BtreeMemNode(this, index2);
	}

	// called from BtreeDbNode
	static BtreeNode slice(BtreeDbNode node, int from, int to) {
		Preconditions.checkArgument(from < to && to <= node.size());
		TByteArrayList index = new TByteArrayList(to - from);
		for (int i = from; i < to; ++i)
			index.add((byte) i);
		return new BtreeMemNode(node.level, node.rec, index, new ArrayList<Record>());
	}

	@Override
	Record get(int i) {
		int idx = index.get(i);
		return idx >= 0
				? Record.from(rec.fieldBuffer(idx), rec.fieldOffset(idx))
				: added.get(-idx - 1);
	}

	@Override
	int size() {
		return index.size();
	}

	protected void translate(Tran tran) {
		for (int i = 0; i < size(); ++i) {
			int idx = index.get(i);
			if (idx < 0)
				added.set(-idx - 1, translate(tran, added.get(-idx - 1)));
		}
	}

	protected int length(int i) {
		int idx = index.get(i);
		return idx >= 0
				? rec.fieldLength(idx)
				: added.get(-idx - 1).bufSize();
	}

	@Override
	void minimizeLeftMost() {
		assert ! immutable;
		assert isTree();
		Record key = get(0);
		if (isMinimalKey(key))
			return;
		add(minimize(key));
		index.set(0, (byte) -added.size());
	}

	/** NOTE: Assumes immutable nodes cannot have mutable children */
	@Override
	void freeze() {
		if (immutable)
			return;
		immutable = true;
		for (int i = 0; i < size(); ++i) {
			Record slot = get(i);
			BtreeNode child = (BtreeNode) slot.childRef();
			if (child != null)
				child.freeze();
		}
	}

	// store -------------------------------------------------------------------

	@Override
	int store(Tran tran) {
		translate(tran);
		int adr = tran.stor.alloc(length());
		ByteBuffer buf = tran.stor.buffer(adr);
		pack(buf);
		return adr;
	}

	int length() {
		int datasize = 0;
		for (int i = 0; i < size(); ++i)
			datasize += length(i);
		return ArrayRecord.length(size(), datasize);
	}

	void pack(ByteBuffer buf) {
		ArrayRecord.packHeader(buf, length(), getLengths());
		for (int i = size() - 1; i >= 0; --i)
			pack(buf, i);
	}

	protected TIntArrayList getLengths() {
		TIntArrayList lens = new TIntArrayList(size());
		for (int i = 0; i < size(); ++i)
			lens.add(length(i));
		return lens;
	}

	protected void pack(ByteBuffer buf, int i) {
		int idx = index.get(i);
		if (idx < 0)
			added.get(-idx - 1).pack(buf);
		else {
			int len = rec.fieldLength(idx);
			int off = rec.fieldOffset(idx);
			ByteBuffer src = rec.fieldBuffer(idx);
			for (int j = 0; j < len; ++j)
				buf.put(src.get(off + j));
		}
	}

	protected Record translate(Tran tran, Record rec) {
		boolean translate = false;

		int dref = dataref(rec);
		if (dref != 0) {
			dref = tran.redir(dref);
			if (IntRefs.isIntRef(dref)) {
				dref = tran.getAdr(dref);
				assert dref != 0;
				translate = true;
			}
		}

		int adr = 0;
		if (level > 0) {
			int adr1 = adr = adr(rec);
			if (! IntRefs.isIntRef(adr1))
				adr = tran.redir(adr1);
			if (IntRefs.isIntRef(adr))
				adr = tran.getAdr(adr);
			assert adr != 0;
			assert ! IntRefs.isIntRef(adr) : "adr " + adr1 + " => " + (adr & 0xffffffffL);
			if (adr1 != adr)
				translate = true;
			//TODO if redirections are unique then we can remove this one
		}

		if (! translate)
			return rec;

		int prefix = rec.size() - (level > 0 ? 2 : 1);
		RecordBuilder rb = new RecordBuilder().addPrefix(rec, prefix);
		if (dref == 0)
			rb.add("");
		else
			rb.adduint(dref);
		if (level > 0)
			rb.adduint(adr);
		return rb.build();
	}

	private int dataref(Record rec) {
		int size = rec.size();
		int i = size - (level > 0 ? 2 : 1);
		if (rec.fieldLength(i) == 0)
			return 0;
		return (int) rec.getLong(i);
	}

	@Override
	int getAdr() {
		return 0;
	}

	@Override
	String printName() {
		return "MemNode" + (immutable ? "(frozen)" : "");
	}

}
