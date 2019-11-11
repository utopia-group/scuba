package framework.scuba.domain.location;

import framework.scuba.domain.field.FieldSelector;

public interface AccessPath {

	public StackObject getBase();

	public AccessPathObject getPrefix(FieldSelector f);

	public int countFieldSelector(FieldSelector field);

}