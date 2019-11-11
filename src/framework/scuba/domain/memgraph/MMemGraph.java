package framework.scuba.domain.memgraph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.RetLoc;

public abstract class MMemGraph extends MemGraph implements
		Comparable<MMemGraph> {

	protected final jq_Method method;
	// all app locals in the summary
	protected final Set<MemNode> appLocalNodes = new HashSet<MemNode>();
	// return location and formal parameter locations
	protected RetLoc retLoc = null;
	protected List<AbsMemLoc> formals = null;

	public MMemGraph(jq_Method meth, MMemGraphController controller) {
		super(controller);
		this.method = meth;
	}

	/*----------- formals and return value -----------*/
	public abstract boolean hasInitedFormals();

	public abstract void initFormals();

	public abstract List<AbsMemLoc> getFormalLocs();

	public abstract void addFormal(AbsMemLoc loc);

	public abstract void setRetLoc(RetLoc loc);

	public abstract RetLoc getRetLoc();

	/*----------- memory graph operations -----------*/
	public jq_Method getMethod() {
		return method;
	}

	public Iterator<MemNode> appLocalNodesIterator() {
		return appLocalNodes.iterator();
	}

	public void addAppLocalNode(MemNode node) {
		appLocalNodes.add(node);
	}

	public void clearAppLocalNodes() {
		appLocalNodes.clear();
	}

	/*----------- enforce determinism -----------*/
	@Override
	public int compareTo(MMemGraph other) {
		return this.method.toString().compareTo(other.getMethod().toString());
	}

}