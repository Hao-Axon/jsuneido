/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import java.io.*;
import java.util.*;

import javax.annotation.concurrent.NotThreadSafe;

import suneido.util.IntArrayList;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

/**
 * Controls access to an append-only immutable btree.
 * <p>
 * Note: remove does not merge nodes. The tree will stay balanced in terms of nodes
 * but not necessarily in terms of keys. But the number of tree levels will never
 * shrink unless all the keys are deleted. This is based on the assumption that
 * adds are much more common than removes. Since nodes are not a fixed size
 * small nodes do not waste much space. And compacting the database will rebuild
 * btrees anyway.
 * <p>
 * The first key in tree nodes is always "nil", less than any real key.
 * <p>
 * Note: If a key is unique without its data address
 * then it can be updated via redirection
 * otherwise it must be updated by delete and insert
 * since it's position may change, potentially to a different node.
 * @see BtreeNode, BtreeDbNode, BtreeMemNode
 */
@NotThreadSafe
public class Btree {
	public int maxNodeSize() { return 20; } // overridden by test
	private final Tran tran;
	private int root;
	private int treeLevels = 0;

	public Btree(Tran tran) {
		this.tran = tran;
		root = tran.refToInt(BtreeNode.emptyLeaf());
		treeLevels = 0;
	}

	public Btree(Tran tran, int root, int treeLevels) {
		this.tran = tran;
		this.root = root;
		this.treeLevels = treeLevels;
	}

	public boolean isEmpty() {
		return treeLevels == 0 && nodeAt(0, root).isEmpty();
	}

	/**
	 * @param key A key without the final record address.
	 * @return The record address or 0 if the key wasn't found.
	 */
	public int get(Record key) {
		int adr = root;
		for (int i = 0; i < treeLevels; ++i) {
			int level = treeLevels - i;
			BtreeNode node = nodeAt(level, adr);
			Record slot = node.find(key);
			if (slot == null)
				return 0; // not found
			adr = getAddress(slot);
		}
		BtreeNode leaf = nodeAt(0, adr);
		Record slot = leaf.find(key);
		return slot != null && slot.startsWith(key) ? getAddress(slot) : 0;
	}

	/**
	 * Add a key to the btree.
	 */
	public void add(Record key) {
		// search down the tree
		int adr = root;
		List<BtreeNode> treeNodes = Lists.newArrayList();
		IntArrayList adrs = new IntArrayList();
		for (int level = treeLevels; level > 0; --level) {
			adrs.add(adr);
			BtreeNode node = nodeAt(level, adr);
			treeNodes.add(node);
			Record slot = node.find(key);
			adr = getAddress(slot);
		}

		BtreeNode leaf = nodeAt(0, adr);
		if (leaf.size() < maxNodeSize()) {
			// normal/fast path - simply insert into leaf
			BtreeNode before = leaf;
			leaf = leaf.with(key);
			if (adr == root) {
				if (leaf != before)
					root = tran.refToInt(leaf);
			} else
				tran.redir(adr, leaf);
			return;
		}
		// else split leaf
		Split split = leaf.split(tran, key, adr);

		// insert up the tree
		for (int i = treeNodes.size() - 1; i >= 0; --i) {
			BtreeNode treeNode = treeNodes.get(i);
			if (treeNode.size() < maxNodeSize()) {
				treeNode = treeNode.with(split.key);
				if (adrs.get(i) == root)
					root = tran.refToInt(treeNode);
				else
					tran.redir(adrs.get(i), treeNode);
				return;
			}
			// else split
			split = treeNode.split(tran, split.key, adrs.get(i));
		}
		// getting here means root was split so a new root is needed
		newRoot(split);
	}

	private void newRoot(Split split) {
		++treeLevels;
		root = tran.refToInt(BtreeMemNode.newRoot(tran, split));
	}

	/** used to return the results of a split */
	static class Split {
		final int level; // of node being split
		final int left;
		final int right;
		final Record key; // new value to go in parent, points to right half

		Split(int level, int left, int right, Record key) {
			this.level = level;
			this.left = left;
			this.right = right;
			this.key = key;
		}
	}

	/**
	 * Remove a key from the btree.
	 * <p>
	 * Does <u>not</u> merge nodes.
	 * Tree levels will only shrink when the <u>last</u> key is removed.
	 * @return false if the key was not found
	 */
	public boolean remove(Record key) {

		// search down the tree
		int adr = root;
		List<BtreeNode> treeNodes = Lists.newArrayList();
		IntArrayList adrs = new IntArrayList();
		for (int level = treeLevels; level > 0; --level) {
			adrs.add(adr);
			BtreeNode node = nodeAt(level, adr);
			treeNodes.add(node);
			Record slot = node.find(key);
			if (slot == null)
				return false; // not found
			adr = getAddress(slot);
		}

		// remove from leaf
		BtreeNode leaf = nodeAt(0, adr);
		leaf = leaf.without(key);
		if (leaf == null)
			return false; // not found
		if (adr == root)
			root = tran.refToInt(leaf);
		else
			tran.redir(adr, leaf);
		if (! leaf.isEmpty() || treeLevels == 0)
			return true;	// this is the usual path

		// remove up the tree
		for (int i = treeNodes.size() - 1; i >= 0; --i) {
			BtreeNode treeNode = treeNodes.get(i);
			if (treeNode.size() > 1) {
				treeNode = treeNode.without(key);
				assert treeNode != null;
				if (adrs.get(i) == root)
					root = tran.refToInt(treeNode);
				else
					tran.redir(adrs.get(i), treeNode);
				return true;
			}
		}

		// if we get to here, root node is now empty
		treeLevels = 0;
		root = tran.refToInt(BtreeNode.emptyLeaf());

		return true;
	}

	public Iter iterator() {
		return new Iter(Record.EMPTY);
	}

	// TODO handle concurrent modification

	public class Iter {
		// top of stack is leaf
		private final Deque<Info> stack = new ArrayDeque<Info>();
		private Record cur = null;

		private Iter(Record key) {
			seek(key);
		}

		private void seek(Record key) {
			if (isEmpty())
				return;
			int adr = root;
			for (int level = treeLevels; level >= 0; --level) {
				BtreeNode node = nodeAt(level, adr);
				int pos = node.findPos(key);
				stack.push(new Info(node, pos));
				Record slot = node.get(pos);
				if (level == 0) {
					cur = slot;
					break;
				}
				adr = getAddress(slot);
			}
		}

		public void next() {
			if (eof())
				return;
			while (! stack.isEmpty() &&
					stack.peek().pos + 1 >= stack.peek().node.size())
				stack.pop();
			if (stack.isEmpty()) {
				cur = null;
				return;
			}
			++stack.peek().pos;
			while (stack.size() < treeLevels + 1) {
				Info info = stack.peek();
				Record slot = info.node.get(info.pos);
				int adr = getAddress(slot);
				int level = info.node.level - 1;
				BtreeNode node = nodeAt(level, adr);
				stack.push(new Info(node, 0));
			}
			Info leaf = stack.peek();
			cur = leaf.node.get(leaf.pos);
		}

		public boolean eof() {
			return cur == null;
		}

		public Record cur() {
			return cur;
		}

	}
	private static class Info {
		BtreeNode node;
		int pos;
		Info(BtreeNode node, int pos) {
			this.node = node;
			this.pos = pos;
		}
		@Override
		public String toString() {
			return Objects.toStringHelper(this)
				.add("pos", pos)
				.add("node", node)
				.toString();
		}
	}

	public static int getAddress(Record slot) {
		return ((Number) slot.get(slot.size() - 1)).intValue();
	}

	BtreeNode nodeAt(int level, int adr) {
		return nodeAt(tran, level, adr);
	}

	static BtreeNode nodeAt(Tran tran, int level, int adr) {
		adr = tran.redir(adr);
		return IntRefs.isIntRef(adr)
			? (BtreeNode) tran.intToRef(adr)
			: new BtreeDbNode(level, tran.context.stor.buffer(adr));
	}

	public void print() {
		print(new PrintWriter(System.out));
	}

	public void print(Writer writer) {
		try {
			nodeAt(treeLevels, root).print(writer, tran);
			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public int root() {
		return root;
	}

	public int treeLevels() {
		return treeLevels;
	}

	public void store() {
		// need to store BtreeNodes bottom up
		// sort by level without allocation
		// by packing level and intref into a long
		IntRefs intrefs = tran.context.intrefs;
		long a[] = new long[intrefs.size()];
		int i = -1;
		for (Object x : intrefs) {
			++i;
			if (x instanceof BtreeNode) {
				BtreeNode node = (BtreeNode) x;
				a[i] = ((long) node.level() << 32) | i;
			}
		}
		Arrays.sort(a);
		for (long n : a) {
			int intref = (int) n | IntRefs.MASK;
			BtreeNode node = (BtreeNode) intrefs.intToRef(intref);
			int adr = node.store(tran);
			if (adr != 0)
				tran.setAdr(intref, adr);
		}
		if (IntRefs.isIntRef(root))
			root = tran.getAdr(root);
	}

	public void check() {
		nodeAt(treeLevels, root).check(tran, Record.EMPTY);
	}

}
