package framework.scuba.domain.location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Type;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.field.IndexFieldSelector;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.G;
import framework.scuba.helper.MemLocHelper;
import framework.scuba.helper.TypeHelper;

public abstract class AccessPathObject extends HeapObject implements AccessPath {

	protected Set<FieldSelector> smashed = new HashSet<FieldSelector>();
	final protected MemLoc inner;
	final protected FieldSelector outer;

	// possible types include all the possible DYNAMIC types
	// we cannot use static type for access paths
	final protected Set<jq_Type> possibleTypes = new HashSet<jq_Type>();

	public AccessPathObject(MemLoc inner, FieldSelector outer,
			Set<jq_Type> types, int number) {
		super(null, number, inner.getLength() + 1);
		this.inner = inner;
		this.outer = outer;
		addPossibleTypes(types);
		assert (possibleTypes.size() == 1 || outer instanceof IndexFieldSelector) : this;
	}

	// call this method every time adding a type
	private void add1Field(FieldSelector field) {
		fields.add(field);
		for (MemNode parent : parents) {
			parent.add1Field(this, field);
		}
	}

	public Set<jq_Type> getPossibleTypes() {
		assert (possibleTypes.size() == 1 || outer instanceof IndexFieldSelector) : this;
		return possibleTypes;
	}

	public void addPossibleType(jq_Type type) {
		if (TypeHelper.h().isRefType(type)) {
			possibleTypes.add(type);
			for (MemNode parent : parents) {
				parent.add1Type(type);
			}
			Set<FieldSelector> fs = MemLocHelper.h().getFields(this, type);
			for (FieldSelector f : fs) {
				add1Field(f);
			}
		} else {
			if (G.warning) {
				System.out.println("[Warning]" + " Add non-ref type [" + type
						+ "] to possible types of [" + this + "]");
			}
		}
	}

	public void addPossibleTypes(Set<jq_Type> types) {
		for (jq_Type t : types) {
			addPossibleType(t);
		}
	}

	public MemLoc getInner() {
		return inner;
	}

	public FieldSelector getOuter() {
		return outer;
	}

	public List<FieldSelector> getAllFieldSelectors() {
		List<FieldSelector> ret = new ArrayList<FieldSelector>();
		if (inner instanceof AccessPathObject) {
			ret.addAll(((AccessPathObject) inner).getAllFieldSelectors());
		}
		ret.add(outer);
		return ret;
	}

	public Set<FieldSelector> getSmashedFields() {
		return smashed;
	}

	public void addSmashedField(FieldSelector f) {
		assert (f instanceof RegFieldSelector || f instanceof IndexFieldSelector) : ""
				+ "only normal field and index field can be smashed!";
		smashed.add(f);
	}

	public void addSmashedFields(Set<FieldSelector> fields) {
		smashed.addAll(fields);
	}

	public boolean isSmashed() {
		return !smashed.isEmpty();
	}

	public Set<FieldSelector> getPreSmashedFields(FieldSelector f) {
		Set<FieldSelector> ret = new HashSet<FieldSelector>();
		addFieldAsSmashed(f, ret);
		return ret;
	}

	private void addFieldAsSmashed(FieldSelector f, Set<FieldSelector> set) {
		if (outer.equals(f)) {
			set.addAll(smashed);
			set.add(f);
			return;
		}
		set.addAll(smashed);
		set.add(outer);
		assert (inner instanceof AccessPathObject);
		((AccessPathObject) inner).addFieldAsSmashed(f, set);
		return;
	}

	// a shorter representation
	public String shortName() {
		return inner.shortName() + "." + outer.shortName();
	}

	// -------------- Object ----------------
	@Override
	public int hashCode() {
		assert (number > 0) : "AccessPathObject should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		return inner + "." + outer + " " + smashed;
	}

	// --------------- MemLoc ----------------
	@Override
	public jq_Type getType() {
		assert false : this;
		return null;
	}

	@Override
	public void initFields(jq_Type type) {
		assert false : this;
	}

	// --------------- AccessPath ---------------
	@Override
	public StackObject getBase() {
		if (inner instanceof StackObject) {
			return (StackObject) inner;
		}
		assert (inner instanceof AccessPath);
		return ((AccessPathObject) inner).getBase();
	}

	@Override
	public AccessPathObject getPrefix(FieldSelector f) {
		int dup = Env.v().getDupFiled(f);
		if (dup == 0) {
			// for early return
			return null;
		} else if (countFieldSelector(f) >= dup) {
			return getPrefix1(f);
		} else {
			return null;
		}
	}

	// find the right-most field
	public AccessPathObject getPrefix1(FieldSelector f) {
		if (outer.equals(f)) {
			return this;
		} else if (inner instanceof AccessPathObject) {
			return ((AccessPathObject) inner).getPrefix(f);
		}
		return null;
	}

	@Override
	public int countFieldSelector(FieldSelector field) {
		if (outer.equals(field)) {
			if (inner instanceof AccessPathObject) {
				return 1 + ((AccessPathObject) inner).countFieldSelector(field);
			} else {
				return 1;
			}
		} else {
			if (inner instanceof AccessPathObject) {
				return ((AccessPathObject) inner).countFieldSelector(field);
			} else {
				return 0;
			}
		}
	}

}