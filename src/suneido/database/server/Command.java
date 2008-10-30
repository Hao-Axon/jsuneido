package suneido.database.server;

import static suneido.Util.*;

import java.nio.ByteBuffer;
import java.util.List;

import org.ronsoft.nioserver.OutputQueue;

import suneido.SuException;
import suneido.SuValue.Pack;
import suneido.database.*;
import suneido.database.query.*;
import suneido.database.query.Query.Dir;
import suneido.database.server.Dbms.HeaderAndRow;

/**
 * Implements the server protocol commands.
 *
 * @author Andrew McKinlay
 * <p><small>Copyright (c) 2008 Suneido Software Corp.
 * All rights reserved. Licensed under GPLv2.</small></p>
 */
public enum Command {
	BADCMD {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			badcmd.position(0);
			outputQueue.enqueue(badcmd);
			return line;
		}
	},
	ADMIN {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			theDbms.admin(bufferToString(line));
			return OK;
		}
	},
	TRANSACTION {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			boolean readwrite = false;
			if (match(line, "update"))
				readwrite = true;
			else if (!match(line, "read"))
				return stringToBuffer("ERR invalid transaction mode\r\n");
			String session_id = ""; // TODO
			int tn = serverData.addTransaction(
					theDbms.transaction(readwrite, session_id));
			return stringToBuffer("T" + tn + "\r\n");
		}
	},
	COMMIT {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			int tn = ck_getnum('T', line);
			DbmsTran tran = serverData.getTransaction(tn);
			serverData.endTransaction(tn);
			String conflict = tran.complete();
			return conflict == null ? OK : stringToBuffer(conflict + "\r\n");
		}
	},
	ABORT {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			int tn = ck_getnum('T', line);
			DbmsTran tran = serverData.getTransaction(tn);
			serverData.endTransaction(tn);
			tran.abort();
			return OK;
		}
	},
	REQUEST {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsTran tran = serverData.getTransaction(ck_getnum('T', line));
			theDbms.request(tran, bufferToString(line));
			return OK;
		}
	},
	QUERY {
		@Override
		public int extra(ByteBuffer buf) {
			ck_getnum('T', buf);
			return ck_getnum('Q', buf);
		}

		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			line.rewind();
			int tn = ck_getnum('T', line);
			DbmsTran tran = serverData.getTransaction(tn);
			Query dq = (Query) theDbms.query(tran, bufferToString(extra));
			int qn = serverData.addQuery(tn, dq);
			return stringToBuffer("Q" + qn + "\r\n");
		}
	},
	CURSOR {
		@Override
		public int extra(ByteBuffer buf) {
			return ck_getnum('Q', buf);
		}

		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			Query dq = (Query) theDbms.cursor(bufferToString(extra));
			int cn = serverData.addCursor(dq);
			return stringToBuffer("C" + cn + "\r\n");
		}
	},
	CLOSE {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			int n;
			if (-1 != (n = getnum('Q', line)))
				serverData.endQuery(n);
			else if (-1 != (n = getnum('C', line)))
				serverData.endCursor(n);
			else
				throw new SuException("CLOSE expects Q# or C#");
			return OK;
		}
	},

	HEADER {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsQuery q = q_or_c(line, serverData);
			return stringToBuffer(listToParens(q.header().schema()) + "\r\n");
		}
	},
	ORDER {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsQuery q = q_or_c(line, serverData);
			return stringToBuffer(listToParens(q.ordering()) + "\r\n");
		}
	},
	KEYS {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsQuery q = q_or_c(line, serverData);
			return stringToBuffer(listToParens(q.keys()) + "\r\n");
		}
	},
	EXPLAIN {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsQuery q = q_or_c(line, serverData);
			return stringToBuffer(q.toString() + "\r\n");
		}
	},
	REWIND {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsQuery q = q_or_c(line, serverData);
			q.rewind();
			return OK;
		}
	},

	GET {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			Dir dir = getDir(line);
			Query q = q_or_tc(line, serverData);
			get(q, dir, outputQueue);
			return null;
		}
	},
	GET1 {
		@Override
		public int extra(ByteBuffer buf) {
			getDir(buf);
			ck_getnum('T', buf);
			return ck_getnum('Q', buf);
		}
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			line.rewind();
			Dir dir = getDir(line);
			boolean one = line.get(line.position() - 2) == '1';
			int tn = ck_getnum('T', line);
			HeaderAndRow hr = theDbms.get(dir, bufferToString(extra), one,
					serverData.getTransaction(tn));
			row_result(hr.row, hr.header, true, outputQueue);
			return null;
		}
	},
	OUTPUT {
		@Override
		public int extra(ByteBuffer buf) {
			if (-1 == getnum('T', buf) || -1 == getnum('C', buf))
				ck_getnum('Q', buf);
			return ck_getnum('R', buf);
		}

		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			Query q = q_or_tc(line, serverData);
			q.output(new Record(extra));
			return OK;
		}
	},
	UPDATE {
		@Override
		public int extra(ByteBuffer buf) {
			ck_getnum('T', buf);
			ck_getnum('A', buf);
			return ck_getnum('R', buf);
		}

		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsTran tran = serverData.getTransaction(ck_getnum('T', line));
			long recadr = Mmfile.intToOffset(ck_getnum('A', line));
			Record newrec = new Record(extra);
			theDbms.update(tran, recadr, newrec);
			return OK;
		}
	},
	ERASE {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			DbmsTran tran = serverData.getTransaction(ck_getnum('T', line));
			long recadr = Mmfile.intToOffset(ck_getnum('A', line));
			theDbms.erase(tran, recadr);
			return OK;
		}
	},

	LIBGET {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			List<String> srcs = theDbms.libget(bufferToString(line).trim());

			String resp = "";
			for (int i = 1; i < srcs.size(); i += 2)
				resp += "L" + (srcs.get(i).length() + 1) + " ";
			resp += "\r\n";
			outputQueue.enqueue(stringToBuffer(resp));

			for (int i = 0; i < srcs.size(); i += 2) {
				outputQueue.enqueue(stringToBuffer(srcs.get(i) + "\r\n"
						+ (char) Pack.STRING));
				outputQueue.enqueue(stringToBuffer(srcs.get(i + 1)));
			}
			return null;
		}
	},
	LIBRARIES,
	TIMESTAMP,
	DUMP,
	COPY,
	RUN,
	TEXT,
	BINARY {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			// TODO BINARY
			return OK;
		}
	},
	TRANLIST,
	SIZE,
	CONNECTIONS,
	CURSORS,
	SESSIONID {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			// TODO SESSIONID
			// outputQueue.enqueue(line);
			return line;
		}
	},
	FINAL,
	LOG {
		@Override
		public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
				OutputQueue outputQueue, ServerData serverData) {
			// TODO LOG
			return OK;
		}
	},
	KILL;

	/**
	 * @param buf A ByteBuffer containing the command line.
	 * @return The amount of "extra" data required by the command in the buffer.
	 */
	public int extra(ByteBuffer buf) {
		return 0;
	}
	/**
	 * @param line
	 *            Current position is past the first (command) word.
	 * @param extra
	 * @param outputQueue
	 * @param serverData
	 * @return
	 */
	public ByteBuffer execute(ByteBuffer line, ByteBuffer extra,
			OutputQueue outputQueue, ServerData serverData) {
		outputQueue.enqueue(notimp);
		return line;
	}
	private final static ByteBuffer badcmd = stringToBuffer("ERR bad command: ");
	private final static ByteBuffer notimp = stringToBuffer("ERR not implemented: ");
	private final static ByteBuffer OK = stringToBuffer("OK\r\n");
	private final static ByteBuffer EOF = stringToBuffer("EOF\r\n");

	static Dbms theDbms = new DbmsLocal();

	/**
	 * Skips whitespace then looks for 'type' char followed by digits, starting
	 * at buf's current position. If successful, advances buf's position to past
	 * digits and following whitespace
	 *
	 * @param type
	 * @param buf
	 * @return The digits converted to an int, or -1 if unsuccessful.
	 */
	static int getnum(char type, ByteBuffer buf) {
		int i = buf.position();
		while (i < buf.limit() && Character.isWhitespace(buf.get(i)))
			++i;
		if (i >= buf.limit()
				|| Character.toUpperCase(buf.get(i)) != type
				|| !Character.isDigit(buf.get(i + 1)))
			return -1;
		++i;
		String s = "";
		while (i < buf.limit() && Character.isDigit(buf.get(i)))
			s += (char) buf.get(i++);
		int n = Integer.valueOf(s);
		while (i < buf.limit() && Character.isWhitespace(buf.get(i)))
			++i;
		buf.position(i);
		return n;
	}

	private static int ck_getnum(char type, ByteBuffer buf) {
		int num = getnum(type, buf);
		if (num == -1)
			throw new SuException("expecting: " + type + "#");
		return num;
	}

	private static Query q_or_c(ByteBuffer buf, ServerData serverData) {
		Query q = null;
		int n;
		if (-1 != (n = getnum('Q', buf)))
			q = serverData.getQuery(n);
		else if (-1 != (n = getnum('C', buf)))
			q = serverData.getCursor(n);
		else
			throw new SuException("expecting Q# or C#");
		if (q == null)
			throw new SuException("valid query or cursor required");
		return q;
	}

	private static Query q_or_tc(ByteBuffer buf, ServerData serverData) {
		Query q = null;
		int n, tn;
		if (-1 != (n = getnum('Q', buf)))
			q = serverData.getQuery(n);
		else if (-1 != (tn = getnum('T', buf)) && -1 != (n = getnum('C', buf))) {
			q = serverData.getCursor(n);
			q.setTransaction((Transaction) serverData.getTransaction(tn));
		} else
			throw new SuException("expecting Q# or T# C#");
		if (q == null)
			throw new SuException("valid query or cursor required");
		return q;
	}

	private static Dir getDir(ByteBuffer line) {
		Dir dir;
		switch (line.get()) {
		case '+':
			dir = Dir.NEXT;
			break;
		case '-':
			dir = Dir.PREV;
			break;
		default:
			throw new SuException("get expects + or -");
		}
		// skip space
		if (line.get() == '1')
			line.get();
		return dir;
	}

	private static void get(Query q, Dir dir, OutputQueue outputQueue) {
		Row row = q.get(dir);
		if (row != null && q.updateable())
			row.recadr = row.getFirstData().off();
		Header hdr = q.header();
		row_result(row, hdr, false, outputQueue);
	}

	private static void row_result(Row row, Header hdr, boolean sendhdr, OutputQueue outputQueue) {
		if (row == null) {
			outputQueue.enqueue(EOF);
			return;
		}
		Record rec;
		if (row.size() <= 2)
			rec = row.getFirstData();
		else
			{
			rec = new Record(1000);
			for (String f : hdr.fields())
				rec.add(row.getraw(hdr, f));

			// strip trailing empty fields
			int n = rec.size();
			while (rec.getraw(n - 1).remaining() == 0)
				--n;
			rec.truncate(n);
			}

		String s = "A" + Mmfile.offsetToInt(row.recadr) + " R" + rec.packSize();
		if (sendhdr)
			s += ' ' + listToParens(hdr.schema());
		s += "\r\n";
		outputQueue.enqueue(stringToBuffer(s));

		rec = rec.dup(); // compact
		outputQueue.enqueue(rec.getBuf());
	}


	private static boolean match(ByteBuffer linebuf, String string) {
		String line = bufferToString(linebuf).toLowerCase();
		if (!line.startsWith(string))
			return false;
		int n = string.length();
		return line.length() == n || line.charAt(n) == ' ';
		// TODO advance line position
	}
}
