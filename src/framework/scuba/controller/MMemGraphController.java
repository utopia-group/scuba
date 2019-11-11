package framework.scuba.controller;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.helper.MemGraphHelper;

public abstract class MMemGraphController extends MemGraphController {

	public MInstnController instnCtrlor = new MInstnController(this);

	protected MMemGraph memGraph;

	public void setMemGraph(MMemGraph memGraph) {
		this.memGraph = memGraph;
	}

	@Override
	public MMemGraph getMemGraph() {
		return memGraph;
	}

	// ----------------- Reading Heap Operations --------------------
	// set lookup
	public P2Set lookup(P2Set p2Set, FieldSelector field, boolean notSmash) {
		P2Set ret = new P2Set();
		for (MemNode node : p2Set.keySet()) {
			BoolExpr cst = p2Set.get(node);
			P2Set tgt = lookup(node, field, notSmash);
			tgt.project(cst);
			ret.join(tgt);
		}
		assert (ret != null) : "null P2Set";
		return ret;
	}

	// by default, set lookup is smashing
	public P2Set lookup(P2Set p2Set, FieldSelector field) {
		return lookup(p2Set, field, false);
	}

	// node lookup
	public P2Set lookup(MemNode node, FieldSelector field, boolean notSmash) {
		P2Set ret = null;
		if (MemGraphHelper.h().hasDefault(memGraph, node, field)) {
			MemNode dt = MemGraphHelper.h().getDefault(memGraph, node, field,
					notSmash);
			if (dt != null) {
				ret = new P2Set(dt, CstFactory.f().genTrue());
			} else {
				ret = new P2Set();
			}
		} else {
			ret = node.getP2Set(field);
		}
		assert (ret != null) : "null P2Set";
		return ret;
	}

	// by default, node lookup is smashing
	public P2Set lookup(MemNode node, FieldSelector field) {
		return lookup(node, field, false);
	}

	public abstract void setFormals();

	public abstract void generateSummaryFromHeap();

}