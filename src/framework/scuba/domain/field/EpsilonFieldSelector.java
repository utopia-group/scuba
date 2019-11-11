package framework.scuba.domain.field;

import framework.scuba.domain.summary.SummariesEnv;

public class EpsilonFieldSelector extends FieldSelector {

	public EpsilonFieldSelector() {
		super(1, SummariesEnv.FieldType.FORWARD);
	}

	// a shorter representation
	public String shortName() {
		return "e";
	}

	// ---------- Regular ------------
	@Override
	public String toString() {
		return "[F]e";
	}

}
