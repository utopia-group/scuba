package framework.scuba.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.instn.CInstnCstFactory;
import framework.scuba.domain.instn.CInstnEdgeFactory;
import framework.scuba.domain.instn.CInstnNodeFactory;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;

public class CMemGraphController extends MemGraphController {

	protected final MMemGraph mainGraph;
	protected final Set<MMemGraph> clinits = new TreeSet<MMemGraph>();

	protected MemGraph calleeGraph;
	protected CInstnCstFactory currCstInstn;
	protected final Set<MemGraph> loaded = new HashSet<MemGraph>();

	protected CMemGraph conclusion;

	public CMemGraphController(MMemGraph mainGraph, Set<MMemGraph> clinits) {
		this.mainGraph = mainGraph;
		this.clinits.addAll(clinits);
	}

	public void setEverything(CMemGraph conclusion) {
		this.conclusion = conclusion;
		this.currCstInstn = new CInstnCstFactory(conclusion, this);
		CInstnNodeFactory.f().initFactory(conclusion, this);
	}

	@Override
	public CMemGraph getMemGraph() {
		return conclusion;
	}

	public P2Set lookup(MemNode node, FieldSelector field) {
		P2Set ret = node.getP2Set(field);
		for (MemNode node1 : ret.keySet()) {
			assert (node1.isAllocNode()) : node + " " + field + " " + node1;
		}
		assert (ret != null) : "null P2Set";
		return ret;
	}

	/* instantiate the current callee's summary */
	public boolean instn(MemGraph calleeGraph) {
		// set up
		this.calleeGraph = calleeGraph;
		CInstnNodeFactory.f().initSubFactory(conclusion, calleeGraph);
		CInstnNodeFactory.f().setEverything(this);
		currCstInstn.setEverything(calleeGraph);
		CInstnEdgeFactory.f().initSubFactory(conclusion, calleeGraph);
		CInstnEdgeFactory.f().setEverything(this, currCstInstn);
		// load and initialize
		if (loaded.contains(calleeGraph) && calleeGraph.isTerminated()) {
			// do nothing
		} else {
			CInstnNodeFactory.f().loadAndInit();
			currCstInstn.load();
			currCstInstn.init();
			CInstnEdgeFactory.f().loadAndInit();
			loaded.add(calleeGraph);
		}

		boolean ret = false;
		/* fix-point computation */
		while (true) {
			CInstnNodeFactory.f().refresh();
			currCstInstn.refresh();
			boolean changed = CInstnEdgeFactory.f().refresh();
			ret = ret || changed;

			if (!changed) {
				break;
			}
			if (!G.instnFixPoint) {
				break;
			}
		}
		return ret;
	}

	// ensure a partial order
	public void conclude() {
		// TODO
		if (SummariesEnv.v().shareSum) {
			instn(Env.v().shared);
		}
		/* clinits */
		while (true) {
			boolean again = false;
			// instn shared summary
			if (SummariesEnv.v().shareSum) {
				boolean changed = instn(Env.v().shared);
				again = again || changed;
			}
			// instn <clinit>'s
			for (MMemGraph clinit : clinits) {
				boolean changed = instn(clinit);
				again = again || changed;
			}
			if (!again) {
				break;
			}
			if (!G.clinitFixPoint) {
				break;
			}
		}
		/* main method */
		if (SummariesEnv.v().shareSum) {
			while (true) {
				boolean again = false;
				boolean changed = instn(mainGraph);
				again = again || changed;
				changed = instn(Env.v().shared);
				again = again || changed;
				if (!again) {
					break;
				}
			}
		} else {
			instn(mainGraph);
		}
	}

}