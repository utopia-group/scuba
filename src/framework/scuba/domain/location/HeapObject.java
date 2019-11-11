package framework.scuba.domain.location;

import joeq.Class.jq_Type;

public abstract class HeapObject extends MemLoc {

	public HeapObject(jq_Type type, int number, int length) {
		super(type, number, length);
	}

}
