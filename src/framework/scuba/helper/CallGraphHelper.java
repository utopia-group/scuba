package framework.scuba.helper;

import framework.scuba.domain.summary.Env;
import joeq.Class.jq_Method;

public class CallGraphHelper {

	private static CallGraphHelper instance = new CallGraphHelper();

	public static CallGraphHelper h() {
		return instance;
	}

	public boolean isTopLevelMethod(jq_Method method) {
		boolean ret = true;
		for (jq_Method pred : Env.v().cg.getPreds(method)) {
			if (pred != method) {
				ret = false;
				break;
			}
		}
		return ret;
	}

}
