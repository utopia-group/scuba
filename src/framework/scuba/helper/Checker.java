package framework.scuba.helper;

import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.SummariesEnv;

public class Checker {

	private static Checker instance = new Checker();

	public static Checker c() {
		return instance;
	}

	public void checkController(MMemGraphController controller,
			ControlFlowGraph g) {
		MMemGraph graph1 = controller.getMemGraph();
		jq_Method meth = g.getMethod();
		MMemGraph graph2 = SummariesEnv.v().getMMemGraph(meth);
		assert (graph1 == graph2) : graph1.getMethod() + " "
				+ graph2.getMethod();
	}

	// check all the types of a memory node are jq_Array
	public void checkMemNodeIsArrayType(MemNode node) {
		for (Iterator<MemLoc> it = node.getLocs().iterator(); it.hasNext();) {
			MemLoc loc = it.next();
			Set<jq_Type> types = MemLocHelper.h().getTypes(loc);
			for (jq_Type type : types) {
				assert (type instanceof jq_Array) : node + " " + types;
			}
		}
	}

}
