/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class RecordTest {

	@Test
	public void main() {
		Record r = record("one", "two", "three");
		assertEquals(3, r.size());
		assertEquals("one", r.get(0));
		assertEquals("two", r.get(1));
		assertEquals("three", r.get(2));
	}

	@Test
	public void test() {
		Record r = new RecordBuilder().add(123).add("hello").build();
		assertThat(r.size(), equalTo(2));
		assertThat(r.get(0), instanceOf(Integer.class));
		assertThat(r.get(0), equalTo(123));
		assertThat(r.get(1), instanceOf(String.class));
		assertThat(r.get(1), equalTo("hello"));
	}

	@Test
	public void compare() {
		Record[] data = new Record[] {
				record(), record("one"), record("one", "three"),
				record("one", "two"), record("three"), record("two") };
		for (int i = 0; i < data.length; ++i)
			for (int j = 0; j < data.length; ++j)
				assertEquals("i " + i + " j " + j, Integer.signum(i - j),
						Integer.signum(data[i].compareTo(data[j])));
	}

	@Test
	public void int_pack() {
		Record r = record("one", 9, 0xffff0000);
		assertEquals("one", r.get(0));
		assertEquals(9, r.get(1));
		assertEquals(0xffff0000, r.get(2));
	}

	@Test
	public void length() {
		assertThat(ArrayRecord.length(0, 0), equalTo(5));
		assertThat(ArrayRecord.length(1, 1), equalTo(7));
		assertThat(ArrayRecord.length(1, 200), equalTo(206));
		assertThat(ArrayRecord.length(1, 248), equalTo(254));

		assertThat(ArrayRecord.length(1, 250), equalTo(258));
		assertThat(ArrayRecord.length(1, 300), equalTo(308));

		assertThat(ArrayRecord.length(1, 0x10000), equalTo(0x1000c));
	}

	@Test
	public void prefixSize() {
		Record rec = record("hi", "world");
		assertThat(rec.dataSize(), equalTo(9));
	}

	@Test
	public void truncate() {
		RecordBuilder rb = new RecordBuilder();
		rb.truncate(0);
		assertThat(rb.size(), equalTo(0));
		rb.truncate(5);
		assertThat(rb.size(), equalTo(0));
		rb.add("a").add("b").add("c");
		rb.truncate(5);
		assertThat(rb.size(), equalTo(3));
		rb.truncate(2);
		assertThat(rb.size(), equalTo(2));
	}

	public static Record record(Object... data) {
		RecordBuilder rb = new RecordBuilder();
		for (Object d : data)
			if (d instanceof String)
				rb.add(d);
			else // if (d instanceof Integer)
				rb.add((int) (Integer) d);
		return rb.build();
	}

}
