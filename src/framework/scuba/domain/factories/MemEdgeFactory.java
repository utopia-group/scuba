package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;

public class MemEdgeFactory {

	private static MemEdgeFactory instance = new MemEdgeFactory();

	public static MemEdgeFactory f() {
		return instance;
	}

	private final Map<MemGraph, MemEdgeSubFactory> factory = new HashMap<MemGraph, MemEdgeSubFactory>();

	public MemEdge get(MemNode src, FieldSelector field, MemNode tgt,
			BoolExpr cst) {
		MemGraph graph = null;
		if (SummariesEnv.v().shareSum) {
			if (src.getMemGraph() == Env.v().shared
					&& tgt.getMemGraph() == Env.v().shared) {
				graph = Env.v().shared;
				cst = CstFactory.f().genTrue();
			} else if (src.getMemGraph() == Env.v().shared) {
				graph = tgt.getMemGraph();
				cst = CstFactory.f().genTrue();
			} else if (tgt.getMemGraph() == Env.v().shared) {
				graph = src.getMemGraph();
				cst = CstFactory.f().genTrue();
			} else {
				assert (src.getMemGraph() == tgt.getMemGraph());
				graph = src.getMemGraph();
			}
		} else {
			assert (src.getMemGraph() == tgt.getMemGraph()) : src + " " + tgt;
			graph = src.getMemGraph();
		}
		assert (graph != null) : src + " " + tgt + " " + field;
		MemEdgeSubFactory sub = factory.get(graph);
		if (sub == null) {
			sub = new MemEdgeSubFactory(graph);
			factory.put(graph, sub);
		}
		MemEdge ret = sub.get(src, field, tgt, cst);
		return ret;
	}

}
