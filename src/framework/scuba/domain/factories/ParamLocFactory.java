package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.location.ParamLoc;

public class ParamLocFactory {

	private static ParamLocFactory instance = new ParamLocFactory();

	public static ParamLocFactory f() {
		return instance;
	}

	private final Map<Register, ParamLoc> locFactory = new HashMap<Register, ParamLoc>();
	private final Map<Register, Integer> locToId = new HashMap<Register, Integer>();
	private final Map<Integer, ParamLoc> idToLoc = new HashMap<Integer, ParamLoc>();

	// numbers of ParamLoc start from 1
	private int maxNum;

	public ParamLoc get(Register r, jq_Method meth, jq_Type type) {
		ParamLoc ret = locFactory.get(r);
		if (ret == null) {
			ret = new ParamLoc(r, meth, type, ++maxNum);
			update(r, maxNum, ret);
		}
		return ret;
	}

	public ParamLoc get(Register r) {
		return locFactory.get(r);
	}

	private void update(Register r, int number, ParamLoc loc) {
		locFactory.put(r, loc);
		locToId.put(r, number);
		idToLoc.put(number, loc);
	}

	public Iterator<ParamLoc> iterator() {
		return locFactory.values().iterator();
	}

}