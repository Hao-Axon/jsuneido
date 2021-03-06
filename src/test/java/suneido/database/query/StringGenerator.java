/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.query;

import static suneido.compiler.Token.ADD;
import static suneido.compiler.Token.AND;
import static suneido.compiler.Token.OR;
import static suneido.compiler.Token.SUB;

import java.util.Map;

import suneido.SuContainer;
import suneido.compiler.Token;
import suneido.runtime.Showable;

public class StringGenerator extends QueryGenerator<String> {

	@Override
	public String assignment(String term, Token op, String expression) {
		return term + " " + expression + " " + op;
	}

	@Override
	public String conditional(String expression, String first, String second) {
		return "(" + expression + " ? " + first + " : " + second + ")";
	}

	@Override
	public String function(String parameters, String statementList,
			boolean isMethod, int lineNumber) {
		return "function (" + str(parameters) + ") { "
				+ str("", statementList, " ") + "}";
	}

	@Override
	public String identifier(String text) {
		return text;
	}

	@Override
	public String ifStatement(String expr, String t, String f) {
		return "if (" + expr + ") {" + str(" ", t, "") + " }"
				+ str(" else { ", f, " }");
	}

	@Override
	public String returnStatement(String expression, Object context,
			int lineNumber) {
		return "return" + str(" ", expression, "") + ";";
	}

	@Override
	public String statementList(String list, String statement) {
		return str("", list, " ") + str(statement);
	}

	@Override
	public String whileStatement(String expression, String statement) {
		return "while (" + expression + ") {" + str(" ", statement, "") + " }";
	}

	@Override
	public String dowhileStatement(String body, String expr) {
		return "do {" + str(" ", body, "") + " } while (" + expr + ");";
	}

	@Override
	public String binaryExpression(Token op, String expr1, String expr2) {
		return str(expr1) + " " + str(expr2) + " " + op;
	}

	@Override
	public String unaryExpression(Token op, String expression) {
		return expression + " " + (op == ADD | op == SUB ? "u" : "") + op;
	}

	@Override
	public String foreverStatement(String statement) {
		return "forever { " + statement.trim() + " }";
	}

	@Override
	public String breakStatement(int lineNumber) {
		return "break;";
	}

	@Override
	public String continueStatement(int lineNumber) {
		return "continue;";
	}

	@Override
	public String throwStatement(String expression, int lineNumber) {
		return "throw " + expression + ";";
	}

	@Override
	public String catcher(String variable, String pattern, String statement) {
		return "catch" + str("(", variable, str(", '", pattern, "'") + ")")
				+ " { " + statement + " }";
	}

	@Override
	public String tryStatement(String tryStatement, String catcher) {
		return "try { " + tryStatement + " }" + str(" ", catcher, "");
	}

	@Override
	public String caseValues(String list, String expression) {
		return str("", list, ", ") + expression;
	}

	@Override
	public String switchCases(String list, String values, String statements) {
		return str("", list, " ")
				+ (values == null ? "default:" : "case " + values + ":") + " "
				+ statements;
	}

	@Override
	public String switchStatement(String expression, String cases) {
		return "switch (" + expression + ") {" + str(" ", cases, "") + " }";
	}

	@Override
	public String forInStatement(String var, String expr, String statement) {
		return "for (" + var + " in " + expr + ") { " + statement + " }";
	}

	@Override
	public String expressionList(String list, String expression) {
		return str("", list, ", ") + expression;
	}

	@Override
	public String forClassicStatement(String expr1, String expr2, String expr3,
			String statement) {
		return "for (" + str(expr1) + "; " + str(expr2) + "; " + str(expr3)
				+ ") { " + statement + " }";
	}

	@Override
	public String preIncDec(String term, Token incdec) {
		return term + " pre" + incdec;
	}

	@Override
	public String postIncDec(String term, Token incdec) {
		return term + " post" + incdec;
	}

	@Override
	public String memberRef(String term, String identifier, int lineNumber) {
		return term + " ." + identifier;
	}

	@Override
	public String subscript(String term, String expression) {
		return term + " " + expression + " []";
	}

	@Override
	public String functionCall(String function, String arguments) {
		return function + "(" + str(arguments) + ")";
	}

	@Override
	public String superCallTarget(String method, int lineNumber) {
		return "super" + (method.equals("New") ? "" : "." + method);
	}

	@Override
	public String selfRef(int lineNumber) {
		return "<this>";
	}

	@Override
	public String newExpression(String term, String arguments) {
		return "new " + term + str("(", arguments, ")");
	}

	@Override
	public String atArgument(String n, String expression) {
		return "@" + ("0".equals(n) ? "" : str("+", n, " ")) + expression;
	}

	@Override
	public String block(String params, String statements, int lineNumber) {
		return "{" + str("|", params, "|") + " " + str("", statements, " ")
				+ "}";
	}

	@Override
	public String parameters(String list, String name, String defaultValue) {
		return str("", list, ", ") + name + str(" = ", defaultValue, "");
	}

	@Override
	public String argumentList(String list, String keyword, String expression) {
		String k = keyword;
		if (k != null)
			k = k.substring(2, k.length() - 1);
		return str("", list, ", ") + str("", k, ": ")
				+ expression;
	}

	@Override
	public String clazz(String name, String base, Map<String,Object> members, int lineNumber) {
		if ("Object".equals(base))
			base = null;
		return "class" + str(" : ", base, "") + " { " + str("", members, " ")
				+ "}";
	}

	@Override
	public String object(SuContainer members, int lineNumber) {
		return members.show();
	}

	private static String str(String x) {
		return x == null ? "" : x;
	}

	protected String str(String s, Object x, String t) {
		return x == null ? "" : s + x.toString() + t;
	}

	@Override
	public String and(String expr1, String expr2) {
		return (expr1 == null) ? expr2 : binaryExpression(AND, expr1, expr2);
	}

	@Override
	public String or(String expr1, String expr2) {
		return (expr1 == null) ? expr2 : binaryExpression(OR, expr1, expr2);
	}

	@Override
	public String in(String expression, String constants) {
			return expression + " in (" + constants + ")";
	}

	@Override
	public String rvalue(String expr) {
		return expr;
	}

	@Override
	public String columns(String columns, String column) {
		return str("", columns, ", ") + column;
	}

	@Override
	public String delete(String query) {
		return "delete " + query;
	}

	@Override
	public String history(String table) {
		return "history(" + table + ")";
	}

	@Override
	public String insertQuery(String query, String table) {
		return "insert " + query + " into " + table;
	}

	@Override
	public String insertRecord(String record, String query) {
		return "insert " + record + " into " + query;
	}

	@Override
	public String sort(String query, boolean reverse, String columns) {
		return query + " sort" + (reverse ? " reverse" : "") + " " + columns;
	}

	@Override
	public String table(String table) {
		return table;
	}

	@Override
	public String update(String query, String updates) {
		return "update " + query + " set " + updates;
	}

	@Override
	public String updates(String updates, String column, String expr) {
		return str("", updates, ", ") + column + " = " + expr;
	}

	@Override
	public String project(String query, String columns) {
		return query + " project " + columns;
	}

	@Override
	public String remove(String query, String columns) {
		return query + " remove " + columns;
	}

	@Override
	public String times(String query1, String query2) {
		return query1 + " times " + query2;
	}

	@Override
	public String union(String query1, String query2) {
		return query1 + " union " + query2;
	}

	@Override
	public String minus(String query1, String query2) {
		return query1 + " minus " + query2;
	}

	@Override
	public String intersect(String query1, String query2) {
		return query1 + " intersect " + query2;
	}

	@Override
	public String join(String query1, String by, String query2) {
		return query1 + " join " + str("by(", by, ") ") + query2;
	}

	@Override
	public String leftjoin(String query1, String by, String query2) {
		return query1 + " leftjoin " + str("by(", by, ") ") + query2;
	}

	@Override
	public String rename(String query, String renames) {
		return query + " rename " + renames;
	}

	@Override
	public String renames(String renames, String from, String to) {
		return str("", renames, ", ") + from + " to " + to;
	}

	@Override
	public String extend(String query, String list) {
		return query + " extend " + list;
	}

	@Override
	public String extendList(String list, String column, String expr) {
		return str("", list, ", ") + column + " = " + expr;
	}

	@Override
	public String where(String query, String expr) {
		return query + " where " + expr;
	}

	@Override
	public String summarize(String query, String by, String ops) {
		return query + " summarize " + str("", by, ", ") + ops;
	}

	@Override
	public String sumops(String sumops, String name, Token op, String field) {
		return str("", sumops, ", ") + str("", name, " = ") + op.string
				+ str(" ", field, "");
	}

	@Override
	public String value(Object value) {
		return Showable.show(value);
	}

}
