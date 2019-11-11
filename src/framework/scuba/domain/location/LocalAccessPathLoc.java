package framework.scuba.domain.location;

import java.util.Set;

import framework.scuba.domain.field.FieldSelector;
import joeq.Class.jq_Type;

public class LocalAccessPathLoc extends AccessPathObject {

	public LocalAccessPathLoc(MemLoc inner, FieldSelector outer,
			Set<jq_Type> types, int number) {
		super(inner, outer, types, number);
	}

}