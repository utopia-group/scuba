package framework.scuba.helper;

import java.util.HashSet;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2SetWrapper;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;

public class MemGraphHelper {

	private static MemGraphHelper instance = new MemGraphHelper();

	public static MemGraphHelper h() {
		return instance;
	}

	// conservatively return if a node should be in the summary
	public boolean isSumNode(MemNode node) {
		if (node.isGlobalNode() || node.isRetNode()
				|| node.isPropLocalVarNode() || node.isAllocNode()
				|| node.isAPNode()) {
			return true;
		} else if (node.isAppLocalVarNode()) {
			if (node.toProp()) {
				return true;
			} else {
				return false;
			}
		} else if (node.isLibLocalVarNode() || node.isParamNode()) {
			return false;
		} else {
			assert false : node;
			return false;
		}
	}

	// decide if a node should be in the final summary
	public boolean isTopLevelSumNode(MemNode node) {
		if (node.isGlobalNode() || node.isPropLocalVarNode()
				|| node.isAllocNode() || node.isGlobalAPNode()) {
			return true;
		} else if (node.isAppLocalVarNode() || node.isParamNode()
				|| node.isLibLocalVarNode() || node.isRetNode()
				|| node.isLocalAPNode()) {
			return false;
		} else {
			assert false : node;
			return false;
		}
	}

	// wrapper MemLocP2Set
	public MemLocP2SetWrapper convertP2SetToMemLocP2SetWrapper(P2Set from) {
		MemLocP2SetWrapper ret = new MemLocP2SetWrapper();
		for (MemNode node : from.keySet()) {
			Set<MemLoc> locs = node.getLocs();
			assert (locs.size() == 1) : node + " " + locs;
			MemLoc loc = locs.iterator().next();
			BoolExpr cst = from.get(node);
			ret.join(loc, cst);
		}
		return ret;
	}

	// a naive hasDefault method
	public boolean hasDefault(MMemGraph memGraph, MemNode node,
			FieldSelector field) {
		if (node.isAPNode() || node.isParamNode()) {
			return true;
		} else if (node.isGlobalNode()) {
			return !Env.v().shared.hasCIPANode(node);
			// return true;
		} else if (node.isAppLocalVarNode() || node.isLibLocalVarNode()
				|| node.isPropLocalVarNode()) {
			return false;
		} else if (node.isAllocNode() || node.isRetNode()) {
			return false;
		} else {
			assert false : node + " " + field;
			return false;
		}
	}

	// this is actually the access path manufacture
	public MemNode getDefault(MMemGraph memGraph, MemNode node,
			FieldSelector field, boolean notSmash) {
		MemNode ret = null;
		Set<MemLoc> locs = node.getLocs();
		Set<MemLoc> dLocs = new HashSet<MemLoc>();
		// get all the default locations
		for (MemLoc loc : locs) {
			MemLoc dLoc = MemLocHelper.h().getDefault(loc, field, notSmash);
			if (dLoc != null) {
				dLocs.add(dLoc);
			}
		}
		/* all default targets should be merged */
		// have not done that for now (only one default target possible)
		assert (dLocs.size() <= 1) : node + " " + locs;
		for (MemLoc dLoc : dLocs) {
			ret = MemNodeFactory.f().get(memGraph, dLoc);
		}
		return ret;
	}

}
