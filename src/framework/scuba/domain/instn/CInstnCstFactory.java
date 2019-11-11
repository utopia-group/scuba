package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;

import framework.scuba.controller.CMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MemGraph;

public class CInstnCstFactory {

	protected final CMemGraph callerGraph;
	protected MemGraph calleeGraph;
	protected final CMemGraphController controller;

	// terms including: 1. local access path
	private final Map<Expr, MemLocP2Set> termInstnCache = new HashMap<Expr, MemLocP2Set>();
	private final Map<BoolExpr, BoolExpr> subCstInstnCache = new HashMap<BoolExpr, BoolExpr>();
	private final Map<BoolExpr, BoolExpr> cstInstnCache = new HashMap<BoolExpr, BoolExpr>();
	// true: not changed, false: changed
	private final Map<BoolExpr, Boolean> cstsStatus = new HashMap<BoolExpr, Boolean>();

	public CInstnCstFactory(CMemGraph callerGraph,
			CMemGraphController controller) {
		this.callerGraph = callerGraph;
		this.controller = controller;
	}

	public void setEverything(MemGraph calleeGraph) {
		this.calleeGraph = calleeGraph;
		BoolExpr t = CstFactory.f().genTrue();
		subCstInstnCache.put(t, t);
		cstInstnCache.put(t, t);
	}

	public BoolExpr get(BoolExpr cst) {
		// BoolExpr ret = cstInstnCache.get(cst);
		BoolExpr ret = CstFactory.f().genTrue();
		assert (ret != null) : cst;
		return ret;
	}

	/* load all constraints in the summary */
	public void load() {
		// for (Iterator<MemEdge> it = calleeGraph.sumEdgesIterator(); it
		// .hasNext();) {
		// MemEdge edge = it.next();
		// BoolExpr cst = edge.getCst();
		// BoolExpr curr = cstInstnCache.get(cst);
		// cstInstnCache.put(cst, curr);
		// cstsStatus.put(cst, true);
		// }
	}

	/* populate non-erasable cache */
	public void init() {
		// nothing to initialize
	}

	/* refresh the erasable cache */
	public void refresh() {
		// Iterator<Map.Entry<BoolExpr, BoolExpr>> it = cstInstnCache.entrySet()
		// .iterator();
		// while (it.hasNext()) {
		// Map.Entry<BoolExpr, BoolExpr> entry = it.next();
		// BoolExpr key = entry.getKey();
		// BoolExpr val = entry.getValue();
		// BoolExpr newVal = instn(key);
		// if (SummariesEnv.v().instnSkip) {
		// assert (cstsStatus.containsKey(key)) : key;
		// cstsStatus.put(key, newVal.equals(val));
		// cstInstnCache.put(key, newVal);
		// } else {
		// cstInstnCache.put(key, newVal);
		// }
		// }
	}

	private BoolExpr instn(BoolExpr cst) {
		BoolExpr ret = CstFactory.f().genTrue();
		return ret;
	}
}
