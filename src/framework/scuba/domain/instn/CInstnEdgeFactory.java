package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.Map;

import framework.scuba.controller.CMemGraphController;
import framework.scuba.domain.memgraph.MemGraph;

public class CInstnEdgeFactory {

	private static CInstnEdgeFactory instance = new CInstnEdgeFactory();

	public static CInstnEdgeFactory f() {
		return instance;
	}

	protected final Map<MemGraph, CInstnEdgeSubFactory> edgeInstns = new HashMap<MemGraph, CInstnEdgeSubFactory>();

	protected CInstnEdgeSubFactory currEdgeInstn;

	public void initSubFactory(MemGraph callerGraph, MemGraph calleeGraph) {
		currEdgeInstn = edgeInstns.get(calleeGraph);
		if (currEdgeInstn == null) {
			currEdgeInstn = new CInstnEdgeSubFactory(callerGraph, calleeGraph);
			edgeInstns.put(calleeGraph, currEdgeInstn);
		}
	}

	public void setEverything(CMemGraphController controller,
			CInstnCstFactory currCstInstn) {
		assert (currEdgeInstn != null) : "Initializing before setting everything";
		currEdgeInstn.setEverything(controller, currCstInstn);
	}

	public void loadAndInit() {
		currEdgeInstn.loadAndInit();
	}

	public boolean refresh() {
		return currEdgeInstn.refresh();
	}
}
