package framework.scuba.domain.location;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class ParamLoc extends StackObject {

	protected Register parameter;

	protected jq_Method meth;

	public ParamLoc(Register parameter, jq_Method meth, jq_Type type, int number) {
		super(type, number, 1);
		this.parameter = parameter;
		this.meth = meth;
	}

	public Register getRegister() {
		return parameter;
	}

	public jq_Method getMethod() {
		return meth;
	}

	// a shorter representation
	public String shortName() {
		return parameter.toString();
	}

	// -------------- Regular ---------------
	@Override
	public String toString() {
		return "[P]" + parameter + " " + meth;
	}
}
