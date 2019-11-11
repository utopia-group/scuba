package framework.scuba.domain.instn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Z3Exception;

import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.CstFactory.CstType;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;

public class MInstnCstSubFactory {

	protected final Quad callsite;
	protected final jq_Method callee;
	protected final jq_Method caller;
	protected final MMemGraph calleeGraph;
	protected final MMemGraph callerGraph;

	protected MMemGraphController controller;

	/* instantiation caches, 1 is non-erasable, 2 is erasable */
	// followings include: local access paths
	private final Map<Expr, MemLocP2Set> termCache2 = new HashMap<Expr, MemLocP2Set>();
	private final Map<BoolExpr, BoolExpr> subCache2 = new HashMap<BoolExpr, BoolExpr>();
	// this includes all the constraints
	protected final Map<BoolExpr, BoolExpr> cstCache2 = new HashMap<BoolExpr, BoolExpr>();

	// true: not changed, false: changed
	protected final Map<BoolExpr, Boolean> status = new HashMap<BoolExpr, Boolean>();

	protected boolean terminated;

	public MInstnCstSubFactory(Quad callsite, jq_Method callee) {
		this.callsite = callsite;
		this.callee = callee;
		this.caller = callsite.getMethod();
		this.calleeGraph = SummariesEnv.v().getMMemGraph(callee);
		this.callerGraph = SummariesEnv.v().getMMemGraph(caller);
	}

	public void setEverything(MMemGraphController controller) {
		this.controller = controller;
	}

	public boolean status(BoolExpr cst) {
		assert (status.containsKey(cst)) : cst;
		boolean ret = status.get(cst);
		if (ret) {
			G.hitCstCache++;
		}
		return ret;
	}

	/* read cache */
	public BoolExpr get(BoolExpr cst) {
		BoolExpr ret = getCst2(cst);
		return ret;
	}

	public MemLocP2Set getTerm(Expr term) {
		return getTerm2(term);
	}

	/* load and initialize */
	public void loadAndInit() {
		if (terminated) {
			return;
		}
		Iterator<MemEdge> it = calleeGraph.sumEdgesIterator();
		// we assume we need to refresh every constraint, so only load
		while (it.hasNext()) {
			MemEdge edge = it.next();
			BoolExpr cst = edge.getCst();
			BoolExpr curr = cstCache2.get(cst);
			cstCache2.put(cst, curr);
		}
		terminated = calleeGraph.isTerminated();
	}

	/* refresh the erasable cstCache2 */
	public void refresh() {
		// clean-up
		termCache2.clear();
		subCache2.clear();
		// refreshing
		Iterator<BoolExpr> it = cstCache2.keySet().iterator();
		while (it.hasNext()) {
			BoolExpr cst = it.next();
			instnCst(cst);
		}
	}

	/* --------- helper methods ------------ */
	protected BoolExpr getSub(BoolExpr sub) {
		return getSub2(sub);
	}

	protected BoolExpr getCst2(BoolExpr cst) {
		BoolExpr ret = cstCache2.get(cst);
		if (ret == null) {
			ret = instnCst(cst);
		}
		return ret;
	}

	protected BoolExpr getSub2(BoolExpr sub) {
		BoolExpr ret = subCache2.get(sub);
		if (ret == null) {
			ret = instnSub2(sub);
		}
		return ret;
	}

	protected MemLocP2Set getTerm2(Expr term) {
		MemLocP2Set ret = termCache2.get(term);
		if (ret == null) {
			ret = instnTerm2(term);
		}
		return ret;
	}

	/* --------- low-level instantiation engines ---------- */
	private MemLocP2Set instnTerm2(Expr term) {
		MemLocP2Set ret = null;
		MemLoc loc = CstFactory.f().liftInv(term.toString());
		assert (loc instanceof LocalAccessPathLoc) : term + " " + loc;
		ret = MInstnNodeFactory.f().get(loc);
		assert (ret != null) : term + " " + loc;
		termCache2.put(term, ret);
		return ret;
	}

	private BoolExpr instnSub2(BoolExpr sub) {
		BoolExpr ret = null;
		try {
			Expr term = sub.getArgs()[0].getArgs()[0];
			if (G.assertion) {
				MemLoc loc = CstFactory.f().liftInv(term.toString());
				assert (loc instanceof LocalAccessPathLoc) : loc;
			}
			MemLocP2Set p2Set = getTerm2(term);
			IntNum typeInt = (IntNum) sub.getArgs()[1];
			jq_Class t = Env.v().class2TermRev.get(typeInt.getInt());
			if (sub.isEq()) {
				ret = CstFactory.f().instExpr(t, p2Set, CstType.EQ);
			} else if (sub.isLE()) {
				ret = CstFactory.f().instExpr(t, p2Set, CstType.LE);
			} else if (sub.isGE()) {
				ret = CstFactory.f().instExpr(t, p2Set, CstType.GE);
			} else {
				assert false : sub;
			}
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		assert (ret != null) : sub;
		subCache2.put(sub, ret);
		return ret;
	}

	private BoolExpr instnCst(BoolExpr cst) {
		BoolExpr ret = null;
		if (Env.v().isWidening()) {
			ret = CstFactory.f().genTrue();
		} else if (CstFactory.f().isTrue(cst)) {
			ret = CstFactory.f().genTrue();
		} else {
			BoolExpr org = cst;
			List<BoolExpr> from = new ArrayList<BoolExpr>();
			List<BoolExpr> to = new ArrayList<BoolExpr>();
			for (BoolExpr sub : CstFactory.f().getSubCsts(cst)) {
				BoolExpr instnSub = MInstnCstFactory.f().getSub(sub);
				from.add(sub);
				to.add(instnSub);
			}
			// FIXME
			ret = CstFactory.f().substitute(from, to, org, false);
		}
		assert (ret != null);
		if (SummariesEnv.v().instnSkip) {
			BoolExpr curr = cstCache2.get(cst);
			status.put(cst, CstFactory.f().equal(curr, ret));
			cstCache2.put(cst, ret);
		} else {
			cstCache2.put(cst, ret);
		}
		return ret;
	}

}
