package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import framework.scuba.domain.location.RetLoc;
import joeq.Class.jq_Method;

public class RetLocFactory {

	private static RetLocFactory instance = new RetLocFactory();

	public static RetLocFactory f() {
		return instance;
	}

	private final Map<jq_Method, RetLoc> locFactory = new HashMap<jq_Method, RetLoc>();

	private final Map<Integer, RetLoc> idToLoc = new HashMap<Integer, RetLoc>();

	// numbers of RetLoc start from 1
	private int maxNum;

	public RetLoc get(jq_Method meth) {
		RetLoc ret = locFactory.get(meth);
		if (ret == null) {
			ret = new RetLoc(meth, ++maxNum);
			update(meth, maxNum, ret);
		}
		return ret;
	}

	private void update(jq_Method meth, int number, RetLoc node) {
		locFactory.put(meth, node);
		idToLoc.put(number, node);
	}

	public Iterator<RetLoc> iterator() {
		return locFactory.values().iterator();
	}
}
