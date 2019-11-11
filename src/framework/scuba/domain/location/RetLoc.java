package framework.scuba.domain.location;

import joeq.Class.jq_Method;

public class RetLoc extends StackObject {

	protected jq_Method meth;

	public RetLoc(jq_Method meth, int number) {
		super(meth.getReturnType(), number, 1);
		this.meth = meth;
	}

	public jq_Method getMethod() {
		return meth;
	}

	public String shortName() {
		return "[Ret]";
	}

	// -------------- Regular ----------------
	@Override
	public String toString() {
		return "[Ret]";
	}

}
