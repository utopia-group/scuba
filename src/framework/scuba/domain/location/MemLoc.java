package framework.scuba.domain.location;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.helper.MemLocHelper;
import framework.scuba.helper.TypeHelper;

public abstract class MemLoc extends AbsMemLoc implements Numberable {

	protected int number;

	// static type of this location (not for access path)
	// access path might have multiple *only* because of smashing index fields
	protected final jq_Type type;

	// fields that this location has
	protected final Set<FieldSelector> fields = new HashSet<FieldSelector>();

	// memory nodes that enclose this memory location
	protected final Set<MemNode> parents = new HashSet<MemNode>();

	// the length of this location
	protected final int length;

	public MemLoc(jq_Type type, int number, int length) {
		// for AccessPathLoc, we do not use type and set it to null
		if (!(this instanceof AccessPathObject)) {
			assert (TypeHelper.h().isRefType(type)) : type;
		} else {
			assert (type == null) : type;
		}
		// prepare the type
		if (type != null && !type.isPrepared()) {
			type.prepare();
		}
		setNumber(number);
		this.length = length;
		// type and initFields() are not for access paths
		this.type = type;
		if (!(this instanceof AccessPathObject)) {
			initFields(type);
		}
	}

	// not for access paths
	public jq_Type getType() {
		return type;
	}

	public int getLength() {
		return length;
	}

	public Set<MemNode> getParents() {
		return parents;
	}

	public void add1Parent(MemNode parent) {
		parents.add(parent);
	}

	// not for access paths
	public void initFields(jq_Type type) {
		Set<FieldSelector> fs = MemLocHelper.h().getFields(this, type);
		fields.addAll(fs);
	}

	public Set<FieldSelector> getFields() {
		return fields;
	}

	public boolean hasField(FieldSelector field) {
		return fields.contains(field);
	}

	public abstract String shortName();

	// -------------- Regular ------------------
	@Override
	public int hashCode() {
		assert number > 0 : "AbsMemLoc should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	// ---------------- Numberable ------------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

}