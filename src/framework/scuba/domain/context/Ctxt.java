package framework.scuba.domain.context;

import framework.scuba.domain.location.Numberable;

// Context is in the following format:
// ctx_1 || ctx_2 || .. || ctx_n
// where ctx_n is the latest one

public class Ctxt implements Numberable {

	final protected ProgPoint curr;

	final protected Ctxt prevCtxt;

	// number starts from 1, and 1 is for empty context
	private int number;

	// curr = xxx, prevCtx = null, number = 1 is the base case
	public Ctxt(ProgPoint curr, Ctxt prevCtx, int number) {
		this.curr = curr;
		this.prevCtxt = prevCtx;
		setNumber(number);
	}

	// context length is always greater than 0
	public int length() {
		return curr == null ? 1 : 1 + prevCtxt.length();
	}

	public ProgPoint getCurr() {
		return curr;
	}

	public boolean contains(ProgPoint point) {
		assert !(curr == null ^ prevCtxt == null);
		if (curr == null) {
			return (curr == point);
		} else {
			return (curr == point) || (prevCtxt.contains(point));
		}
	}

	public boolean contains(Ctxt other) {
		if (curr == null) {
			return (this == other);
		} else {
			return (this == other) || (prevCtxt.contains(other));
		}
	}

	// --------------- Numberable ------------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	// --------------- Regular ------------------
	@Override
	public int hashCode() {
		assert number > 0 : "Ctx should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		if (curr == null) {
			return "";
		} else if (prevCtxt.curr == null) {
			return curr.toString();
		} else {
			return prevCtxt + "," + curr;
		}
	}
}
