package framework.scuba.domain.location;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class LocalVarLoc extends StackObject {

	protected Register local;

	protected jq_Method meth;

	public LocalVarLoc(Register local, jq_Method meth, jq_Type type, int number) {
		super(type, number, 1);
		this.local = local;
		this.meth = meth;
	}

	public Register getRegister() {
		return local;
	}

	public jq_Method getMethod() {
		return meth;
	}

	// a shorter representation
	public String shortName() {
		return local.toString();
	}

	// ------------- Regular --------------
	@Override
	public String toString() {
		return "[Local]" + local + " " + meth + " " + type;
	}

}
