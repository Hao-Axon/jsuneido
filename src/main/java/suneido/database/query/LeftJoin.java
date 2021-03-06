/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.query;

import static suneido.SuInternalError.unreachable;
import static suneido.util.Verify.verify;

import java.util.List;

public class LeftJoin extends Join {
	private boolean row1_output = false;

	LeftJoin(Query source1, Query source2, List<String> by) {
		super(source1, source2, by);
	}

	@Override
	protected String name() {
		return "LEFTJOIN";
	}

	@Override
	protected boolean can_swap() {
		return false;
	}

	@Override
	public List<List<String>> keys() {
		switch (type) {
		case ONE_ONE:
		case N_ONE:
			return source.keys();
		case ONE_N:
		case N_N:
			return keypairs();
		default:
			throw unreachable();
		}
	}

	@Override
	double nrecords() {
		verify(nrecs >= 0);
		return source.nrecords();
	}

	@Override
	protected boolean next_row1(Dir dir) {
		row1_output = false;
		return super.next_row1(dir);
	}

	@Override
	protected boolean should_output(Row row) {
		if (!row1_output) {
			row1_output = true;
			return true;
		}
		return super.should_output(row);
	}
}
