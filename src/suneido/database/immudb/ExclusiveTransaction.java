/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;


public class ExclusiveTransaction extends UpdateTransaction {

	ExclusiveTransaction(Database db) {
		super(db);
		tran.allowStore();
	}

	@Override
	protected void lock(Database db) {
		if (! db.exclusiveLock.writeLock().tryLock())
			throw new RuntimeException("ExclusiveTransaction: could not get lock");
	}

	@Override
	protected void unlock() {
		db.exclusiveLock.writeLock().unlock();
	}

	public void loadRecord(int tblnum, Record rec, Btree btree, int[] fields) {
		int adr = rec.store(stor);
		Record key = IndexedData.key(rec, fields, adr);
		btree.add(key);
		dbinfo.addrow(tblnum, rec.length());
	}

	@Override
	public void commit() {
		try {
			synchronized(db.commitLock) {
				tran.startStore();
				Btree.store(tran);

				updateOurDbInfo();
				updateDatabaseDbInfo();

				int redirsAdr = updateRedirs();
				int dbinfoAdr = dbinfo.store();
				store(dbinfoAdr, redirsAdr);
				tran.endStore();
			}
		} finally {
			unlock();
		}
	}

}
