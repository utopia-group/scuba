package framework.scuba.domain.field;

import framework.scuba.domain.summary.SummariesEnv;

public class IndexFieldSelector extends FieldSelector {

	public IndexFieldSelector() {
		super(1, SummariesEnv.FieldType.FORWARD);
	}

	// a shorter representation
	public String shortName() {
		return "i";
	}

	// ---------- Regular ------------
	@Override
	public String toString() {
		return "[I]i";
	}

}
