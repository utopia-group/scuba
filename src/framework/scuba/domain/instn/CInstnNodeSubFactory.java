package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.CMemGraphController;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.SummariesEnv;

public class CInstnNodeSubFactory {

	protected final MemGraph callerGraph;
	protected final MemGraph calleeGraph;
	protected CMemGraphController controller;

	// 1. locals 2. allocations 3. globals
	protected final Map<MemNode, P2Set> nodesInstnCache1 = new HashMap<MemNode, P2Set>();
	// 1. global access paths
	protected final Map<MemNode, P2Set> nodesInstnCache2 = new HashMap<MemNode, P2Set>();
	// true: not changed, false: changed
	protected final Map<MemNode, Boolean> status = new HashMap<MemNode, Boolean>();

	public CInstnNodeSubFactory(MemGraph callerGraph, MemGraph calleeGraph) {
		this.callerGraph = callerGraph;
		this.calleeGraph = calleeGraph;
	}

	public void setEverything(CMemGraphController controller) {
		this.controller = controller;
	}

	/* read cache */
	public P2Set get(MemNode node) {
		P2Set ret = null;
		if (node.isGlobalAPNode()) {
			ret = get2(node);
		} else if (node.isAllocNode() || node.isPropLocalVarNode()
				|| node.isGlobalNode()) {
			ret = get1(node);
		}
		assert (ret != null) : node;
		return ret;
	}

	/* load the memory nodes */
	// this can support recursive call
	public void loadAndInit() {
		Iterator<MemNode> it = calleeGraph.sumNodesIterator();
		while (it.hasNext()) {
			MemNode node = it.next();
			if (node.isGlobalAPNode()) {
				P2Set curr = nodesInstnCache2.get(node);
				nodesInstnCache2.put(node, curr);
			} else if (node.isAllocNode() || node.isPropLocalVarNode()
					|| node.isGlobalNode()) {
				P2Set curr = nodesInstnCache1.get(node);
				assert (curr == null) : node;
				if (curr == null) {
					P2Set val = instn(node);
					assert (val != null) : node;
					nodesInstnCache1.put(node, val);
				}
			} else {
				assert false : node.getNodeType();
			}
			status.put(node, true);
		}
	}

	/* refresh erasable cache */
	public void refresh() {
		Iterator<Map.Entry<MemNode, P2Set>> it = nodesInstnCache2.entrySet()
				.iterator();
		while (it.hasNext()) {
			Map.Entry<MemNode, P2Set> entry = it.next();
			MemNode key = entry.getKey();
			P2Set currVal = entry.getValue();
			P2Set newVal = instn(key);
			if (SummariesEnv.v().instnSkip) {
				assert (status.containsKey(key)) : key;
				status.put(key, newVal.equals(currVal));
				nodesInstnCache2.put(key, newVal);
			} else {
				nodesInstnCache2.put(key, newVal);
			}
		}
	}

	private P2Set get1(MemNode node) {
		P2Set ret = nodesInstnCache1.get(node);
		assert (ret != null);
		return ret;
	}

	private P2Set get2(MemNode node) {
		P2Set ret = nodesInstnCache2.get(node);
		assert (ret != null);
		return ret;
	}

	/* instantiate and update nodesInstnCache1 */
	private P2Set instn(MemNode node) {
		P2Set ret = new P2Set();
		Set<MemLoc> locs = node.getLocs();
		assert (locs.size() == 1) : locs;
		MemLoc loc = locs.iterator().next();
		MemLocP2Set instnP2Set = CInstnNodeFactory.f().instn(loc);
		for (MemLoc instnLoc : instnP2Set.keySet()) {
			BoolExpr instnCst = instnP2Set.get(instnLoc);
			MemNode instnNode = MemNodeFactory.f().get(callerGraph, instnLoc);
			ret.join(instnNode, instnCst);
		}
		return ret;
	}

}
