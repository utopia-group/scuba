package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import framework.scuba.domain.location.GlobalLoc;
import joeq.Class.jq_Field;

public class GlobalLocFactory {

	private static GlobalLocFactory instance = new GlobalLocFactory();

	public static GlobalLocFactory f() {
		return instance;
	}

	private final Map<jq_Field, GlobalLoc> locFactory = new HashMap<jq_Field, GlobalLoc>();

	private final Map<Integer, GlobalLoc> idToLoc = new HashMap<Integer, GlobalLoc>();

	// numbers of global start from 1
	private int maxNum;

	public GlobalLoc get(jq_Field field) {
		GlobalLoc ret = locFactory.get(field);
		if (ret == null) {
			ret = new GlobalLoc(field, ++maxNum);
			update(field, ++maxNum, ret);
		}
		return ret;
	}

	private void update(jq_Field Field, int number, GlobalLoc ret) {
		locFactory.put(Field, ret);
		idToLoc.put(number, ret);
	}

	public Iterator<GlobalLoc> iterator() {
		return locFactory.values().iterator();
	}

}