package framework.scuba.domain.memgraph;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.Numberable;

public class MemEdge implements Numberable {

	protected final MemGraph memGraph;
	protected final MemNode src;
	protected final MemNode tgt;
	protected final FieldSelector field;
	protected BoolExpr cst;

	protected int number;

	public MemEdge(MemGraph memGraph, MemNode src, FieldSelector field,
			MemNode tgt, BoolExpr cst, int number) {
		assert (cst != null) : src + " --> " + tgt;
		this.memGraph = memGraph;
		this.field = field;
		this.src = src;
		this.tgt = tgt;
		this.cst = cst;
		setNumber(number);
	}

	public MemGraph getMemGraph() {
		return memGraph;
	}

	public MemNode getSrc() {
		return src;
	}

	public MemNode getTgt() {
		return tgt;
	}

	public FieldSelector getField() {
		return field;
	}

	public BoolExpr getCst() {
		return cst;
	}

	public BoolExpr unionCst(BoolExpr other) {
		BoolExpr tmp = CstFactory.f().union(cst, other);
		return (cst = CstFactory.f().simplify(tmp));
	}

	public BoolExpr interCst(BoolExpr other) {
		return (cst = CstFactory.f().intersect(cst, other));
	}

	public void setCst(BoolExpr cst) {
		this.cst = cst;
	}

	/* ----------- Numberable ------------ */
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	/* ------------- Object --------------- */
	@Override
	public int hashCode() {
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		return src + "-->" + tgt + " (" + field + "," + cst + ")";
	}

}
