package framework.scuba.domain.location;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;

public class GlobalLoc extends StackObject {

	protected jq_Field staticField;

	public GlobalLoc(jq_Field staticField, int number) {
		super(staticField.getType(), number, 1);
		this.staticField = staticField;
	}

	public jq_Class getDeclaringClass() {
		return staticField.getDeclaringClass();
	}

	public jq_Field getStaticField() {
		return staticField;
	}

	// a shorter representation
	public String shortName() {
		return staticField.getName().toString();
	}

	// --------------- Regular -----------------
	@Override
	public String toString() {
		return "[SF]" + staticField;
	}

}
