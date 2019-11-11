package framework.scuba.helper;

import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Type;

public class TypeHelper {

	private static TypeHelper instance = new TypeHelper();

	public static TypeHelper h() {
		return instance;
	}

	public boolean isRefType(jq_Type type) {
		if (type instanceof jq_Array || type instanceof jq_Class) {
			return true;
		}
		return false;
	}

	public boolean isSubType(jq_Type type1, Set<jq_Type> types) {
		for (jq_Type type : types) {
			if (type1.isSubtypeOf(type)) {
				return true;
			}
		}
		return false;
	}

}
