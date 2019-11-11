package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Z3Exception;

import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.CstFactory.CstType;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.G;

public class MInstnCstFactory {

	private static MInstnCstFactory instance = new MInstnCstFactory();

	public static MInstnCstFactory f() {
		return instance;
	}

	// sub-factories
	private final Map<Pair<Quad, jq_Method>, MInstnCstSubFactory> subs = new HashMap<Pair<Quad, jq_Method>, MInstnCstSubFactory>();
	private final Pair<Quad, jq_Method> wrapper = new Pair<Quad, jq_Method>(
			null, null);

	// current on-going sub-factory
	private MInstnCstSubFactory currSub;

	/* instantiation cache, 1 is non-erasable, 2 is erasable */
	private final Map<Expr, MemLocP2Set> termCache1 = new HashMap<Expr, MemLocP2Set>();
	private final Map<BoolExpr, BoolExpr> subCache1 = new HashMap<BoolExpr, BoolExpr>();

	public void initSubFactory(Quad callsite, jq_Method callee) {
		wrapper.val0 = callsite;
		wrapper.val1 = callee;
		currSub = subs.get(wrapper);
		if (currSub == null) {
			currSub = new MInstnCstSubFactory(callsite, callee);
			Pair<Quad, jq_Method> pair = new Pair<Quad, jq_Method>(callsite,
					callee);
			subs.put(pair, currSub);
		}
	}

	public void setEverything(MMemGraphController controller) {
		currSub.setEverything(controller);
	}

	public boolean status(BoolExpr cst) {
		return currSub.status(cst);
	}

	/* load initialize */
	public void loadAndInit() {
		currSub.loadAndInit();
	}

	/* refresh the erasable cache */
	public void refresh() {
		currSub.refresh();
	}

	/* get the constraint instantiation result */
	public BoolExpr get(BoolExpr cst) {
		return currSub.get(cst);
	}

	/* --------- helper methods ----------- */
	protected BoolExpr getSub(BoolExpr sub) {
		BoolExpr ret = null;
		Expr term = null;
		try {
			term = sub.getArgs()[0].getArgs()[0];
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		assert (term != null) : sub;
		MemLoc loc = CstFactory.f().liftInv(term.toString());
		if (loc instanceof LocalAccessPathLoc) {
			ret = currSub.getSub(sub);
		} else if (loc instanceof GlobalAccessPathLoc) {
			ret = getSub1(sub);
		} else {
			assert false : sub + " " + loc;
		}
		assert (ret != null) : sub;
		return ret;
	}

	private BoolExpr getSub1(BoolExpr sub) {
		BoolExpr ret = subCache1.get(sub);
		if (ret == null) {
			ret = instnSub1(sub);
		}
		return ret;
	}

	private MemLocP2Set getTerm1(Expr term) {
		MemLocP2Set ret = termCache1.get(term);
		if (ret == null) {
			ret = instnTerm1(term);
		}
		return ret;
	}

	/* --------- low-level instantiation engines ---------- */
	private MemLocP2Set instnTerm1(Expr term) {
		MemLocP2Set ret = null;
		MemLoc loc = CstFactory.f().liftInv(term.toString());
		assert (loc instanceof GlobalAccessPathLoc) : term + " " + loc;
		ret = MInstnNodeFactory.f().get(loc);
		assert (ret != null) : term + " " + loc;
		termCache1.put(term, ret);
		return ret;
	}

	protected BoolExpr instnSub1(BoolExpr sub) {
		BoolExpr ret = null;
		try {
			Expr term = sub.getArgs()[0].getArgs()[0];
			if (G.assertion) {
				MemLoc loc = CstFactory.f().liftInv(term.toString());
				assert (loc instanceof GlobalAccessPathLoc) : loc;
			}
			MemLocP2Set p2Set = getTerm1(term);
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
		subCache1.put(sub, ret);
		return ret;
	}

}
