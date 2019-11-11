package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;

import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;

public class MemEdgeSubFactory {

	protected final MemGraph memGraph;

	private final Trio<MemNode, FieldSelector, MemNode> wrapper = new Trio<MemNode, FieldSelector, MemNode>(
			null, null, null);

	private final Map<Trio<MemNode, FieldSelector, MemNode>, MemEdge> factoryPerMemGraph = new HashMap<Trio<MemNode, FieldSelector, MemNode>, MemEdge>();

	// number of edges start from 1 for every method
	private int maxNum;

	public MemEdgeSubFactory(MemGraph absHeap) {
		this.memGraph = absHeap;
	}

	public MemEdge get(MemNode src, FieldSelector field, MemNode tgt,
			BoolExpr cst) {
		wrapper.val0 = src;
		wrapper.val1 = field;
		wrapper.val2 = tgt;
		MemEdge ret = factoryPerMemGraph.get(wrapper);
		if (ret == null) {
			ret = new MemEdge(memGraph, src, field, tgt, cst, ++maxNum);
			memGraph.addHeapEdge(ret);
			update(src, field, tgt, maxNum, ret);
		}
		return ret;
	}

	private void update(MemNode src, FieldSelector field, MemNode tgt,
			int number, MemEdge edge) {
		Trio<MemNode, FieldSelector, MemNode> trio = new Trio<MemNode, FieldSelector, MemNode>(
				src, field, tgt);
		factoryPerMemGraph.put(trio, edge);
	}

}
