package framework.scuba.domain.memgraph;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import joeq.Class.jq_Method;
import framework.scuba.controller.CMemGraphController;

public class CMemGraph extends MemGraph {

	protected final MMemGraph mainMemGraph;

	protected final Set<MMemGraph> clinits = new TreeSet<MMemGraph>();

	public CMemGraph(MMemGraph mainMemGraph, Set<MMemGraph> clinits,
			CMemGraphController controller) {
		super(controller);
		this.mainMemGraph = mainMemGraph;
		this.clinits.addAll(clinits);
	}

	public Iterator<MMemGraph> clinitsIterator() {
		return clinits.iterator();
	}

	public MMemGraph getMainMemGraph() {
		return mainMemGraph;
	}

	@Override
	public jq_Method getMethod() {
		return null;
	}

}
