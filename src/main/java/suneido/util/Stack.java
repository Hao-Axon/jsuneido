/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.util;

import java.util.ArrayList;
import java.util.List;

public class Stack<T> {
	private final List<T> list = new ArrayList<>();

	public void pop() {
		list.remove(list.size() - 1);
	}
	public void push(T x) {
		list.add(x);
	}
	public T top() {
		return list.get(list.size() - 1);
	}
	public int size() {
		return list.size();
	}
	public T peek(int i) {
		return list.get(i);
	}
	public boolean isEmpty() {
		return list.isEmpty();
	}
}
