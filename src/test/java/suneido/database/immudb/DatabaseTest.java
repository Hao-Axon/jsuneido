/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class DatabaseTest extends TestBase {

	@Test
	public void address() {
		makeTable(1);
		assertThat(get().get(0).address(), not(equalTo(0)));
	}

	@Test
	public void empty_table() {
		makeTable();
		Transaction t = db.readTransaction();
		Table tbl = t.getTable("test");
		assertThat(t.tableCount(tbl.num()), equalTo(0));
		assertThat(t.tableSize(tbl.num()), equalTo(0L));
		assertThat(tbl.getColumns().toString(), equalTo("[a, b]"));
		assertThat(tbl.indexesColumns().toString(), equalTo("[[a], [b, a]]"));
		t.ck_complete();
	}

	@Test
	public void add_remove() {
		makeTable();

		Record r = rec(12, 34);
		Transaction t = db.updateTransaction();
		assertThat(t.readCount(), equalTo(0));
		assertThat(t.writeCount(), equalTo(0));
		t.addRecord("test", r);
		assertThat(t.readCount(), equalTo(0));
		assertThat(t.writeCount(), equalTo(1));
		t.ck_complete();

		List<Record> recs = get("test");
		assertThat(recs.size(), equalTo(1));
		assertThat(recs.get(0), equalTo(r));
		assertThat(getNrecords("test"), equalTo(1));

		db = db.reopen();

		recs = get("test");
		assertThat(recs.size(), equalTo(1));
		assertThat(recs.get(0), equalTo(r));
		assertThat(getNrecords("test"), equalTo(1));

		t = db.updateTransaction();
		t.removeRecord(getTable("test").num(), recs.get(0));
		assertThat(t.readCount(), equalTo(0));
		assertThat(t.writeCount(), equalTo(1));
		t.ck_complete();

		assertThat(count("test"), equalTo(0));
		assertThat(getNrecords("test"), equalTo(0));
	}

	@Test
	public void test_multi_node_index() {
		final int N = 2000;
		makeTable(N);
		assertThat(getNrecords("test"), equalTo(N));
		assertThat(count("test"), equalTo(N));
	}

	@Test
	public void add_index_to_existing_table() {
		makeTable(3);
		TableBuilder tb = db.alterTable("test");
		tb.addColumn("c");
		tb.addIndex("c", false, false, null, null, 0);
		tb.finish();
		Transaction t = db.readTransaction();
		Table table = t.getTable("test");
		int i = 0;
		IndexIter iter = t.iter(table.num(), "c");
		for (iter.next(); ! iter.eof(); iter.next())
			assertEquals(record(i++), t.input(iter.keyadr()));
		t.ck_complete();
	}

	@Test
	public void duplicate_key_add() {
		makeTable(3);

		Transaction t = db.updateTransaction();
		try {
			t.addRecord("test", record(1));
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("duplicate key"));
		} finally {
			t.ck_complete();
		}

		t = db.readTransaction();
		try {
			assertEquals(3, getNrecords("test"));
		} finally {
			t.ck_complete();
		}
	}

	@Test
	public void duplicate_key_update() {
		makeTable(3);

		Transaction t = db.updateTransaction();
		List<Record> recs = get(t);
		try {
			t.updateRecord(t.getTable("test").num(), recs.get(1), record(2));
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("duplicate key"));
		} finally {
			t.abortIfNotComplete();
		}

		t = db.readTransaction();
		try {
			assertEquals(3, getNrecords("test"));
		} finally {
			t.ck_complete();
		}
	}

	@Test
	public void foreign_key_block() {
		// create test2 before test
		// to check that when test is created it picks up foreign keys properly
		db.createTable("test2")
			.addColumn("b")
			.addColumn("f1")
			.addColumn("f2")
			.addIndex("b", true, false, "", "", 0)
			.addIndex("f1", false, false, "test", "a", Fkmode.BLOCK)
			.addIndex("f2", false, false, "test", "a", Fkmode.BLOCK)
			.finish();
		assertThat(db.getSchema("test2"),
				equalTo("(b,f1,f2) key(b) index(f1) in test(a) index(f2) in test(a)"));

		makeTable(3);
		List<Record> recs = get("test");

		assertRecords(0);
		Transaction t = db.updateTransaction();
		Record rec2 = rec(10, 1, 2);
		t.addRecord("test2", rec2);
		t.ck_complete();

		addShouldBlock(rec(11, 5, 1));
		addShouldBlock(rec(11, 1, 5));
		assertRecords(1);

		removeShouldBlock(recs.get(1));
		removeShouldBlock(recs.get(2));
		assertRecords(1);

		updateShouldBlock("test", recs.get(1), record(9)); // test2 => 1
		updateShouldBlock("test2", get("test2").get(0), rec(10, 1, 9));

		assertRecords(1);
	}

	@Test
	public void add_should_block_with_cascade() {
		makeTable(3);
		make_test2(Fkmode.CASCADE);
		addShouldBlock(rec(11, 5, 1));
	}

	@Test
	public void add_should_block_with_cascade_deletes() {
		makeTable(3);
		make_test2(Fkmode.CASCADE_DELETES);
		addShouldBlock(rec(11, 5, 1));
	}

	@Test
	public void add_should_block_with_cascade_updates() {
		makeTable(3);
		make_test2(Fkmode.CASCADE_UPDATES);
		addShouldBlock(rec(11, 5, 1));
	}

	private void assertRecords(int n) {
		Transaction t;
		t = db.readTransaction();
		assertThat(t.tableCount(t.getTable("test2").num()), equalTo(n));
		t.ck_complete();
	}

	private void addShouldBlock(Record rec) {
		Transaction t = db.updateTransaction();
		try {
			t.addRecord("test2", rec);
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("blocked by foreign key"));
		}
		t.ck_complete();
	}

	private void removeShouldBlock(Record rec) {
		Transaction t = db.updateTransaction();
		Table tbl = getTable("test");
		try {
			t.removeRecord(tbl.num(), rec);
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("blocked by foreign key"));
		}
		t.ck_complete();
	}

	private void updateShouldBlock(String table, Record oldrec, Record newrec) {
		Transaction t = db.updateTransaction();
		Table tbl = getTable(table);
		try {
			t.updateRecord(tbl.num(), oldrec, newrec);
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("blocked by foreign key"));
		}
		t.ck_complete();
	}

	@Test
	public void foreign_key_addIndex1() {
		makeTable(3);

		try {
			db.alterTable("test")
					.addIndex("b", false, false, "foo", "a", Fkmode.BLOCK).finish();
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("blocked by foreign key"));
		}
	}

	@Test
	public void foreign_key_addIndex2() {
		makeTable(3);

		db.createTable("test2")
			.addColumn("a")
			.addColumn("f1")
			.addColumn("f2")
			.addIndex("a", true, false, "", "", 0)
			.finish();

		add("test2", rec(10, 1, 5));

		db.alterTable("test2")
				.addIndex("f1", false, false, "test", "a", Fkmode.BLOCK)
				.finish();

		try {
			db.alterTable("test2")
					.addIndex("f2", false, false, "test", "a", Fkmode.BLOCK)
					.finish();
			fail("expected exception");
		} catch (RuntimeException e) {
			assertThat(e.toString(), containsString("blocked by foreign key"));
		}
	}

	@Test
	public void foreign_key_cascade_deletes() {
		makeTable(3);
		make_test2(Fkmode.CASCADE_DELETES);

		List<Record> recs = get("test2");
		assertEquals(4, recs.size());
		assertEquals(rec(100, 0), recs.get(0));
		assertEquals(rec(110, 1), recs.get(1));
		assertEquals(rec(111, 1), recs.get(2));
		assertEquals(rec(120, 2), recs.get(3));

		Transaction t = db.updateTransaction();
		t.removeRecord(t.getTable("test").num(), get("test").get(1));
		t.ck_complete();

		recs = get("test2");
		assertEquals(2, recs.size());
		assertEquals(rec(100, 0), recs.get(0));
		assertEquals(rec(120, 2), recs.get(1));
	}

	@Test
	public void foreign_key_cascade_updates() {
		makeTable(3);
		make_test2(Fkmode.CASCADE_UPDATES);

		Transaction t = db.updateTransaction();
		t.addRecord("test", record(99));
		t.ck_complete();

		t = db.updateTransaction();
		t.updateRecord(t.getTable("test").num(), get("test").get(1), record(11));
		t.ck_complete();

		List<Record> recs = get("test2");
		assertEquals(4, recs.size());
		assertEquals(rec(100, 0), recs.get(0));
		assertEquals(rec(110, 11), recs.get(1));
		assertEquals(rec(111, 11), recs.get(2));
		assertEquals(rec(120, 2), recs.get(3));
	}

	@Test
	public void create_index_blocked() {
		db.createTable("source")
			.addColumn("id")
			.addColumn("date")
			.addIndex("id,date", true, false, "", "", 0)
			.finish();
		add("source", rec(1, 990101));
		assertThat(count("source"), equalTo(1));
		db.createTable("target")
			.addColumn("id")
			.addColumn("name")
			.addIndex("id", true, false, "", "", 0)
			.finish();
		try {
			db.alterTable("source")
				.addIndex("id", false, false, "target", "id", Fkmode.BLOCK)
				.finish();
			fail();
		} catch (Exception e) {
			assertThat(e.toString(), containsString("blocked"));
		}
	}

	private void add(String table, Record rec) {
		Transaction t = db.updateTransaction();
		t.addRecord(table, rec);
		t.ck_complete();
	}

	private void make_test2(int fkmode) {
		db.createTable("test2")
			.addColumn("a")
			.addColumn("f")
			.addIndex("f", false, false, "test", "a", fkmode)
			.addIndex("a", true, false, "", "", 0)
			.finish();

		Transaction t1 = db.updateTransaction();
		t1.addRecord("test2", rec(100, 0));
		t1.addRecord("test2", rec(110, 1));
		t1.addRecord("test2", rec(111, 1));
		t1.addRecord("test2", rec(120, 2));
		t1.ck_complete();
	}

}
