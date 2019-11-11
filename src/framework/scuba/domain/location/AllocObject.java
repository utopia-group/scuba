package framework.scuba.domain.location;

import framework.scuba.domain.context.Ctxt;
import framework.scuba.domain.context.ProgPoint;
import joeq.Class.jq_Type;

public abstract class AllocObject extends HeapObject {

	final protected Ctxt context;

	public AllocObject(jq_Type type, Ctxt context, int number, int length) {
		super(type, number, length);
		this.context = context;
	}

	public Ctxt getContext() {
		return context;
	}

	public int ctxtLength() {
		return context.length();
	}

	public boolean contains(ProgPoint point) {
		return context.contains(point);
	}

	// ------------ Object --------------
	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public int hashCode() {
		return number;
	}
}
