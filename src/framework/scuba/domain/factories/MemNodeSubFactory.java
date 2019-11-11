package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.SummariesEnv;

public class MemNodeSubFactory {

	protected final MemGraph memGraph;

	private final Map<MemLoc, MemNode> factory = new HashMap<MemLoc, MemNode>();

	private final Map<Integer, MemNode> idToNode = new HashMap<Integer, MemNode>();

	// numbers of MemNode start from 1 for every method
	private int maxNum;

	public MemNodeSubFactory(MemGraph memGraph) {
		this.memGraph = memGraph;
	}

	public MemNode get(MemLoc loc) {
		MemNode ret = null;
		ret = factory.get(loc);
		if (ret == null) {
			if (SummariesEnv.v().shareSum) {
				ret = new MemNode(memGraph, loc, ++MemNodeFactory.f().maxNum);
				memGraph.addHeapNode(ret);
				update(loc, MemNodeFactory.f().maxNum, ret);
			} else {
				ret = new MemNode(memGraph, loc, ++maxNum);
				memGraph.addHeapNode(ret);
				update(loc, maxNum, ret);
			}
			loc.add1Parent(ret);
		}
		assert (ret != null) : loc;
		return ret;
	}

	public boolean contains(MemLoc loc) {
		return factory.containsKey(loc);
	}

	public Iterator<MemNode> iterator() {
		return factory.values().iterator();
	}

	private void update(MemLoc loc, int number, MemNode node) {
		factory.put(loc, node);
		idToNode.put(number, node);
	}

}
