package framework.scuba.domain.memgraph;

import java.util.List;

import joeq.Class.jq_Method;
import framework.scuba.controller.S3MMemGraphController;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.RetLoc;

public class S3MMemGraph extends MMemGraph {

	public S3MMemGraph(jq_Method meth, S3MMemGraphController controller) {
		super(meth, controller);
	}

	@Override
	public void initFormals() {
		// do nothing
	}

	@Override
	public boolean hasInitedFormals() {
		return true;
	}

	@Override
	public List<AbsMemLoc> getFormalLocs() {
		assert false : method;
		return formals;
	}

	@Override
	public void addFormal(AbsMemLoc loc) {
		assert false : method;
	}

	@Override
	public void setRetLoc(RetLoc loc) {
		assert false : method;
	}

	@Override
	public RetLoc getRetLoc() {
		assert false : method;
		return retLoc;
	}
}
