package framework.scuba.domain.memgraph;

import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Method;
import framework.scuba.controller.S2MMemGraphController;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.RetLoc;

public class S2MMemGraph extends MMemGraph {

	// whether the summary has been constructed
	protected boolean constructed = false;

	public S2MMemGraph(jq_Method meth, S2MMemGraphController controller) {
		super(meth, controller);
	}

	public boolean constructed() {
		return constructed;
	}

	public void set(boolean constructed) {
		this.constructed = constructed;
	}

	// ----------- MMemGraph -----------
	@Override
	public void initFormals() {
		formals = new ArrayList<AbsMemLoc>();
	}

	@Override
	public boolean hasInitedFormals() {
		return (formals != null);
	}

	@Override
	public List<AbsMemLoc> getFormalLocs() {
		return formals;
	}

	@Override
	public void addFormal(AbsMemLoc loc) {
		formals.add(loc);
	}

	@Override
	public void setRetLoc(RetLoc loc) {
		assert (retLoc == null) || (retLoc == loc) : retLoc + " " + loc;
		retLoc = loc;
	}

	@Override
	public RetLoc getRetLoc() {
		return retLoc;
	}
}
