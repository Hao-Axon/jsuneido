/* Copyright 2012 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.language;

import java.util.Arrays;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Keeps an array of strings (Pieces) which may be larger than required.
 * Shares this array when possible.
 * When possible, adds to the end of existing array rather than alloc/copy.
 * Merges small strings to avoid growing the array as much.
 */
@ThreadSafe
public class Concats extends String2 {
	private final Pieces p;
	private final int len; // total length

	public Concats(String s, String t) {
		this(new Pieces(s, t));
	}

	private Concats(Pieces a) {
		this.p = a;
		this.len = a.len;
	}

	public Concats append(String s) {
		if (s.length() == 0)
			return this;
		return new Concats(p.append(s, len));
	}

	@Override
	public String toString() {
		// Pieces may be longer than len
		return p.flatten().substring(0, len);
	}

	@Override
	public int length() {
		return len;
	}

	@ThreadSafe
	private static class Pieces {
		private String[] a;
		private int n; // how much of the array is used
		private int len; // total length of strings
		private final int LARGE = 256;
		private final int MAX_SMALL = 256;

		Pieces(String s, String t) {
			a = new String[4];
			a[0] = s;
			a[1] = t;
			n = 2;
			len = s.length() + t.length();
		}

		/** at is chars length, not index */
		synchronized Pieces append(String s, int at) {
			if (at == len) {
				if (n >= a.length)
					makeRoom();
				a[n++] = s;
				len += s.length();
				return this; // share
			} else
				// another Concats has already appended to this Pieces
				// so we have to make a new array copy
				return new Pieces(flatten().substring(0, at), s);
		}

		private void makeRoom() {
			if (! compact())
				a = Arrays.copyOf(a, 2 * n);
		}

		/** returns true if it compacted */
		private boolean compact() {
			if (a.length < MAX_SMALL)
				return false;
			int i = 0;
			int dst = 0;
			while (i < n) {
				int j = i;
				while (j < n && a[j].length() < LARGE)
					++j;
				if (j - i >= MAX_SMALL) {
					a[dst++] = merge(i, j);
					i = j;
				}
				while (j < n && a[j].length() >= LARGE)
					++j;
				if (dst != i)
					System.arraycopy(a, i, a, dst, j - i);
				dst += j - i;
				i = j;
			}
			n = dst;
			return n < a.length;
		}

		private String merge(int org, int end) {
			int size = 0;
			for (int i = org; i < end; ++i)
				size += a[i].length();
			StringBuilder sb = new StringBuilder(size);
			for (int i = org; i < end; ++i)
				sb.append(a[i]);
			return sb.toString();
		}

		synchronized String flatten() {
			if (n > 1) {
				StringBuilder sb = new StringBuilder(len);
				for (int i = 0; i < n; ++i)
					sb.append(a[i]);
				// compact everything = cache result
				a = new String[2];
				a[0] = sb.toString();
				n = 1;
			}
			return a[0];
		}
	}

}
