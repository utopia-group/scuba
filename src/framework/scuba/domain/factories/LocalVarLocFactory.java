package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.location.LocalVarLoc;

public class LocalVarLocFactory {

	private static LocalVarLocFactory instance = new LocalVarLocFactory();

	public static LocalVarLocFactory f() {
		return instance;
	}

	private final Map<Register, LocalVarLoc> locFactory = new HashMap<Register, LocalVarLoc>();

	private final Map<Integer, LocalVarLoc> idToLoc = new HashMap<Integer, LocalVarLoc>();

	// numbers of LocalVarLoc start from 1
	private int maxNum;

	public LocalVarLoc get(Register r, jq_Method meth, jq_Type type) {
		LocalVarLoc ret = locFactory.get(r);
		if (ret == null) {
			ret = new LocalVarLoc(r, meth, type, ++maxNum);
			update(r, maxNum, ret);
		}
		return ret;
	}

	public LocalVarLoc get(Register r) {
		return locFactory.get(r);
	}

	private void update(Register r, int number, LocalVarLoc ret) {
		locFactory.put(r, ret);
		idToLoc.put(number, ret);
	}

	public Iterator<LocalVarLoc> iterator() {
		return locFactory.values().iterator();
	}

}
