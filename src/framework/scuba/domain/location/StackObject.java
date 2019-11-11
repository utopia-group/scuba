package framework.scuba.domain.location;

import joeq.Class.jq_Type;

public abstract class StackObject extends MemLoc {

	public StackObject(jq_Type type, int number, int length) {
		super(type, number, length);
	}

}
