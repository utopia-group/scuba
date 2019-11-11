package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.CMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.SummariesEnv;

public class CInstnEdgeSubFactory {

	protected final MemGraph callerGraph;
	protected final MemGraph calleeGraph;

	protected CMemGraphController controller;
	protected CInstnCstFactory currCstInstn;

	// a weak-update cache
	private final Map<Trio<MemNode, FieldSelector, MemNode>, BoolExpr> cache = new HashMap<Trio<MemNode, FieldSelector, MemNode>, BoolExpr>();
	private final Trio<MemNode, FieldSelector, MemNode> trioWrapper = new Trio<MemNode, FieldSelector, MemNode>(
			null, null, null);

	// edges we need to refresh
	protected final Set<MemEdge> instnEdges = new HashSet<MemEdge>();

	public CInstnEdgeSubFactory(MemGraph callerGraph, MemGraph calleeGraph) {
		this.callerGraph = callerGraph;
		this.calleeGraph = calleeGraph;
	}

	public void setEverything(CMemGraphController controller,
			CInstnCstFactory currCstInstn) {
		this.controller = controller;
		this.currCstInstn = currCstInstn;
	}

	/* load all edges in the summary */
	public void loadAndInit() {
		Iterator<MemEdge> it = calleeGraph.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			if (src.isGlobalAPNode() || tgt.isGlobalAPNode()) {
				instnEdges.add(edge);
			} else {
				instn(edge);
			}
		}
	}

	/* instantiate edges in instnEdges */
	public boolean refresh() {
		boolean ret = false;
		Iterator<MemEdge> it = instnEdges.iterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			ret = ret | instn(edge);
		}
		return ret;
	}

	/* instantiate one edge */
	private boolean instn(MemEdge edge) {
		boolean ret = false;
		FieldSelector field = edge.getField();
		P2Set instnSrcs = CInstnNodeFactory.f().get(edge.getSrc());
		P2Set instnTgts = CInstnNodeFactory.f().get(edge.getTgt());
		// BoolExpr instnCst = currCstInstn.get(edge.getCst());
		BoolExpr instnCst = CstFactory.f().genTrue();

		for (MemNode instnSrc : instnSrcs.keySet()) {
			for (MemNode instnTgt : instnTgts.keySet()) {
				if (SummariesEnv.v().weakUpdateCache) {
					trioWrapper.val0 = instnSrc;
					trioWrapper.val1 = field;
					trioWrapper.val2 = instnTgt;
					BoolExpr currCst = cache.get(trioWrapper);
					if (currCst != null && currCst.equals(instnCst)) {
						continue;
					}
					Trio<MemNode, FieldSelector, MemNode> trio = new Trio<MemNode, FieldSelector, MemNode>(
							instnSrc, field, instnTgt);
					cache.put(trio, instnCst);
				}
				boolean changed = instnSrc
						.weakUpdate(field, instnTgt, instnCst);
				ret = ret || changed;
			}
		}
		return ret;
	}
}
