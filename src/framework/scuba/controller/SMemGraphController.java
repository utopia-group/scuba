package framework.scuba.controller;

import java.util.Iterator;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.memgraph.SMemGraph;
import framework.scuba.domain.summary.Env;

public class SMemGraphController extends MemGraphController {

	protected SMemGraph memGraph;

	public SMemGraphController() {
	}

	public void setEverything(SMemGraph memGraph) {
		this.memGraph = memGraph;
	}

	@Override
	public MemGraph getMemGraph() {
		return Env.v().shared;
	}

	public P2Set lookup(MemNode node, FieldSelector field) {
		P2Set ret = node.getP2Set(field);
		assert (ret != null) : "null P2Set";
		return ret;
	}

	public void generateSummaryFromHeap() {
		Iterator<MemEdge> it = memGraph.heapEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			memGraph.addSumEdge(edge);
			memGraph.addSumNode(src);
			memGraph.addSumNode(tgt);
		}
	}
}
