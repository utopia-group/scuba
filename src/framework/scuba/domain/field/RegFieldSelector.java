package framework.scuba.domain.field;

import framework.scuba.domain.summary.SummariesEnv;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;

public class RegFieldSelector extends FieldSelector {

	protected jq_Field field;

	public RegFieldSelector(jq_Field field, int number,
			SummariesEnv.FieldType fType) {
		super(number, fType);
		this.field = field;
	}

	public jq_Field getField() {
		return field;
	}

	public jq_Type getType() {
		return field.getType();
	}

	// a shorter representation
	public String shortName() {
		return field.getName().toString();
	}

	// ---------- Regular ------------
	@Override
	public String toString() {
		return "[F]" + field;
	}

}