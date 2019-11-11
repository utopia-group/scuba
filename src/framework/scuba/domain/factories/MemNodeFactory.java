package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.MemLocHelper;

public class MemNodeFactory {

	private static MemNodeFactory instance = new MemNodeFactory();

	public static MemNodeFactory f() {
		return instance;
	}

	protected int maxNum;

	private final Map<MemGraph, MemNodeSubFactory> factory = new HashMap<MemGraph, MemNodeSubFactory>();

	public void init(MemGraph memGraph) {
		MemNodeSubFactory sub = factory.get(memGraph);
		if (sub == null) {
			sub = new MemNodeSubFactory(memGraph);
			factory.put(memGraph, sub);
		}
	}

	// produce nodes in shared summary
	// redirect to the right sub-factory
	public MemNode get(MemGraph memGraph, MemLoc loc) {
		MemNode ret = null;
		if (SummariesEnv.v().shareSum) {
			if (memGraph instanceof CMemGraph) {
				MemNodeSubFactory sub = factory.get(memGraph);
				assert (sub != null);
				ret = sub.get(loc);
			} else if (MemLocHelper.h().isShared(loc)) {
				MemNodeSubFactory sub = factory.get(Env.v().shared);
				assert (sub != null);
				ret = sub.get(loc);
			} else {
				MemNodeSubFactory sub = factory.get(memGraph);
				assert (sub != null);
				ret = sub.get(loc);
			}
		} else {
			MemNodeSubFactory sub = factory.get(memGraph);
			assert (sub != null);
			ret = sub.get(loc);
		}
		return ret;
	}

	public boolean contains(MemGraph memGraph, MemLoc loc) {
		MemNodeSubFactory factoryPerMemGraph = factory.get(memGraph);
		return factoryPerMemGraph.contains(loc);
	}

	public Iterator<MemNodeSubFactory> iterator() {
		return factory.values().iterator();
	}

}
