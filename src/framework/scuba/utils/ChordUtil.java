package framework.scuba.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.Program;
import framework.scuba.analyses.librariesfilter.RelLibrariesT;
import framework.scuba.domain.summary.Env;

public class ChordUtil {

	public static final Set<jq_Method> clinitSet = new HashSet<jq_Method>();
	public static final Map<jq_Method, Integer> clinitMap = new HashMap<jq_Method, Integer>();

	// either a is the subclass of b or b is the subclass of a.
	// or one implement other's interface.
	public static boolean checkCompatible(jq_Class a, jq_Class b) {
		return a.implementsInterface(b) || b.implementsInterface(a)
				|| a.extendsClass(b) || b.extendsClass(a);
	}

	public static boolean prefixMatch(String str, String[] prefixes) {
		for (String prefix : prefixes) {
			if (str.startsWith(prefix))
				return true;

			// haiyan added regular expression matching
			if (str.matches(prefix))
				return true;
		}
		return false;
	}

	// is this a library method?
	public static boolean isLibMeth(jq_Method meth) {
		String str = meth.getDeclaringClass().getName();

		for (String prefix : RelLibrariesT.ExcludeStdLibs) {
			if (str.startsWith(prefix))
				return true;

			// haiyan added regular expression matching
			if (str.matches(prefix))
				return true;
		}
		return false;
	}

	// is this a library method?
	public static boolean isLib(jq_Class clz) {
		String str = clz.getName();

		for (String prefix : RelLibrariesT.ExcludeStdLibs) {
			if (str.startsWith(prefix))
				return true;

			// haiyan added regular expression matching
			if (str.matches(prefix))
				return true;
		}
		return false;
	}

	/**
	 * Check whether given method is override by any of its subclasses.
	 * 
	 * @param callee
	 * @param statT
	 * @param tgt
	 * @return
	 */
	public static boolean overrideByAnySubclass(jq_Method callee,
			jq_Class statT, Set<jq_Method> tgtSet) {
		for (jq_Method tgt : tgtSet) {
			if (tgt.equals(callee))
				continue;
			jq_Class dyClz = tgt.getDeclaringClass();
			if (dyClz.extendsClass(statT))
				return true;
		}
		return false;
	}

	public static Set<jq_Method> dfs(jq_Method root) {
		Set<jq_Method> visited = new LinkedHashSet<jq_Method>();
		Set<jq_Method> worklist = new LinkedHashSet<jq_Method>();
		worklist.add(root);
		while (!worklist.isEmpty()) {
			jq_Method worker = worklist.iterator().next();
			worklist.remove(worker);

			if (visited.contains(worker))
				continue;

			visited.add(worker);
			worklist.addAll(Env.v().cg.getSuccs(worker));
		}
		return visited;
	}

	public static jq_Class getMainClass() {
		return Program.g().getMainMethod().getDeclaringClass();
	}
	
	// sun.,com.sun.,com.ibm.,org.apache.harmony
	public static boolean isInSecurity(jq_Class clz) {
		if (ChordUtil.prefixMatch(clz.getName(), RelLibrariesT.SecurityLibs)) {
			return true;
		}
		return false;
	}
	
	// equals, hashCode have no side effect
	public static boolean hasSideEffect(jq_Method meth) {
		String str = meth.toString();
		if (str.startsWith("equals:") || str.startsWith("hashCode"))
			return false;

		return true;
	}
}