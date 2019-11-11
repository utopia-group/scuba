package framework.scuba.domain.memgraph;

import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Method;
import framework.scuba.controller.S1MMemGraphController;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.summary.Env;

public class S1MMemGraph extends MMemGraph {

	public S1MMemGraph(jq_Method meth, S1MMemGraphController controller) {
		super(meth, controller);
	}

	@Override
	public void addSumNode(MemNode node) {
		assert (heap.contains(node) || Env.v().shared.hasHeapNode(node));
		summary.add(node);
		if (node.isAppLocalVarNode()) {
			assert (node.toProp()) : node;
			appLocalNodes.add(node);
		}
	}

	@Override
	public void initFormals() {
		formals = new ArrayList<AbsMemLoc>();
	}

	@Override
	public boolean hasInitedFormals() {
		return (formals != null);
	}

	@Override
	public List<AbsMemLoc> getFormalLocs() {
		return formals;
	}

	@Override
	public void addFormal(AbsMemLoc loc) {
		formals.add(loc);
	}

	@Override
	public void setRetLoc(RetLoc loc) {
		assert (retLoc == null) || (retLoc == loc) : retLoc + " " + loc;
		retLoc = loc;
	}

	@Override
	public RetLoc getRetLoc() {
		return retLoc;
	}
}