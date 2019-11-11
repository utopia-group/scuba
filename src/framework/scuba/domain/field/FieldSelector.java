package framework.scuba.domain.field;

import framework.scuba.domain.location.Numberable;
import framework.scuba.domain.summary.SummariesEnv;

public abstract class FieldSelector implements Numberable {

	// no type information for FieldElem
	// but RegFieldElem has type information
	protected int number;

	protected final SummariesEnv.FieldType fType;

	public FieldSelector(int number, SummariesEnv.FieldType fType) {
		setNumber(number);
		this.fType = fType;
	}

	public SummariesEnv.FieldType getFieldType() {
		return fType;
	}

	public boolean isBack() {
		return (fType == SummariesEnv.FieldType.BACK);
	}

	public boolean isForward() {
		return (fType == SummariesEnv.FieldType.FORWARD);
	}

	public boolean isStay() {
		return (fType == SummariesEnv.FieldType.STAY);
	}

	public abstract String shortName();

	// -------------- Numberable -------------
	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	@Override
	public int getNumber() {
		return number;
	}

	// --------------- Regular --------------
	@Override
	public int hashCode() {
		assert (number > 0) : "Field selector should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

}