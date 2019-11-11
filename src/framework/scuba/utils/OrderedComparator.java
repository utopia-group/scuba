package framework.scuba.utils;

import java.util.Comparator;

import joeq.Class.jq_Method;
import chord.analyses.alias.CICG;

public class OrderedComparator implements Comparator<jq_Method> {

	private CICG cg;

	public OrderedComparator(CICG callgraph) {
		this.cg = callgraph;
	}

	@Override
	public int compare(jq_Method m1, jq_Method m2) {
		// no duplicated element.
		if (m1.equals(m2))
			return 0;

		// constraint 1: respect to the call relation.
		if (cg.hasEdge(m1, m2))
			return 1;
		else if (cg.hasEdge(m2, m1))
			return -1;
		// default: lexicographical order.
		else
			return m1.toString().compareTo(m2.toString());
	}
}
