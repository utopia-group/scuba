package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.ApplyResult;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Goal;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Params;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Tactic;
import com.microsoft.z3.Z3Exception;

import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.utils.ChordUtil;

/**
 * Class for generating/solving constraints, since right now our system can only
 * have true | false | Type(v) = T.
 * 
 * Integrate Z3 to perform the constraint solving and simplification.
 * 
 * @author yufeng
 * 
 */
public class CstFactory {

	private static CstFactory instance = new CstFactory();

	public enum CstType {
		EQ, GE, LE;
	}

	private Context ctx;
	private Solver solver;
	private Tactic tactic;
	private Goal goal;
	// define the uninterpreted function. type(o)=T
	private FuncDecl typeFun;
	// constant expr.
	static BoolExpr trueExpr;
	static BoolExpr falseExpr;

	// map from term to heapObject. for unlifting.
	private Map<String, AccessPathObject> term2Ap = new HashMap<String, AccessPathObject>();

	private final Map<BoolExpr, BoolExpr> simplifyCache = new HashMap<BoolExpr, BoolExpr>();
	private final Map<Pair<BoolExpr, BoolExpr>, BoolExpr> conjoinCache = new HashMap<Pair<BoolExpr, BoolExpr>, BoolExpr>();
	private final Map<Set<BoolExpr>, BoolExpr> conjoinSetCache = new HashMap<Set<BoolExpr>, BoolExpr>();
	private final Map<Pair<BoolExpr, BoolExpr>, BoolExpr> disjoinCache = new HashMap<Pair<BoolExpr, BoolExpr>, BoolExpr>();
	private final Map<BoolExpr, Set<BoolExpr>> extractCache = new HashMap<BoolExpr, Set<BoolExpr>>();
	private final Map<Pair<BoolExpr, BoolExpr>, Boolean> eqCache = new HashMap<Pair<BoolExpr, BoolExpr>, Boolean>();
	private final Map<Trio<BoolExpr, List<BoolExpr>, List<BoolExpr>>, BoolExpr> substitueCache = new HashMap<Trio<BoolExpr, List<BoolExpr>, List<BoolExpr>>, BoolExpr>();
	private final Map<BoolExpr, Expr> atomicCache = new HashMap<BoolExpr, Expr>();

	private final Set<AccessPathObject> apSetWrapper = new HashSet<AccessPathObject>();

	public CstFactory() {
		try {
			ctx = new Context();
			trueExpr = ctx.mkBool(true);
			falseExpr = ctx.mkBool(false);
			tactic = ctx.mkTactic("ctx-solver-simplify");
			goal = ctx.mkGoal(true, false, false);

			solver = ctx.mkSolver();
			Params solver_params = ctx.mkParams();
			solver_params.add("ignore_solver1", true);
			solver.setParameters(solver_params);

			typeFun = ctx
					.mkFuncDecl("type", ctx.getIntSort(), ctx.getIntSort());
		} catch (Z3Exception e) {
			e.printStackTrace();
		}

	}

	public static CstFactory f() {
		return instance;
	}

	public BoolExpr genTrue() {
		return trueExpr;
	}

	public BoolExpr genFalse() {
		return falseExpr;
	}

	public BoolExpr substitute(List<BoolExpr> from, List<BoolExpr> to,
			BoolExpr src, boolean simplify) {
		int len = from.size();
		BoolExpr ret = null;
		Trio<BoolExpr, List<BoolExpr>, List<BoolExpr>> trio = new Trio<BoolExpr, List<BoolExpr>, List<BoolExpr>>(
				src, from, to);
		if (substitueCache.containsKey(trio))
			ret = substitueCache.get(trio);
		else {
			try {
				ret = (BoolExpr) src.substitute(from.toArray(new Expr[len]),
						to.toArray(new Expr[len]));
				if (simplify)
					ret = (BoolExpr) ret.simplify();
			} catch (Z3Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			substitueCache.put(trio, ret);
		}
		return ret;
	}

	public BoolExpr intersect(BoolExpr first, BoolExpr second) {
		BoolExpr ret = null;
		try {
			Pair<BoolExpr, BoolExpr> key = new Pair<BoolExpr, BoolExpr>(first,
					second);
			if (conjoinCache.containsKey(key))
				return conjoinCache.get(key);

			if (eqFalse(first) || eqFalse(second)) {
				ret = falseExpr;
			} else if (eqTrue(first)) {
				ret = second;
			} else if (eqTrue(second)) {
				ret = first;
			} else {
				ret = ctx.mkAnd(new BoolExpr[] { first, second });
				BoolExpr newRet = simplify(ret);
				conjoinCache.put(key, newRet);
				return newRet;
			}
			return ret;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public BoolExpr intersect(Set<BoolExpr> set) {
		BoolExpr ret = null;
		try {
			set.remove(trueExpr);
			if (conjoinSetCache.containsKey(set))
				return conjoinSetCache.get(set);

			if (set.size() == 1)
				return set.iterator().next();

			if (set.contains(falseExpr))
				return falseExpr;

			ret = ctx.mkAnd(set.toArray(new BoolExpr[set.size()]));

			BoolExpr newRet = simplify(ret);
			Set<BoolExpr> cacheSet = new HashSet<BoolExpr>(4);
			cacheSet.addAll(set);
			conjoinSetCache.put(cacheSet, newRet);

			return newRet;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public BoolExpr union(BoolExpr first, BoolExpr second) {
		BoolExpr ret = null;
		try {
			Pair<BoolExpr, BoolExpr> key = new Pair<BoolExpr, BoolExpr>(first,
					second);
			if (disjoinCache.containsKey(key))
				return disjoinCache.get(key);
			if (first.equals(second))
				return first;

			if (eqTrue(first) || eqTrue(second)) {
				ret = trueExpr;
			} else if (eqFalse(first)) {
				ret = second;
			} else if (eqFalse(second)) {
				ret = first;
			} else {
				ret = ctx.mkOr(new BoolExpr[] { first, second });
				disjoinCache.put(key, ret);
			}
			return ret;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// check if cst is a scala constraint, e.g, true, false
	public boolean isScala(BoolExpr cst) {
		try {
			if (cst.isTrue() || cst.isFalse())
				return true;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean eqTrue(BoolExpr cst) {
		try {
			return cst.isTrue();
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean isTrue(BoolExpr cst) {
		return cst.equals(trueExpr);
	}

	public boolean eqFalse(BoolExpr cst) {
		try {
			return cst.isFalse();
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean isFalse(BoolExpr cst) {
		return cst.equals(falseExpr);
	}

	/* Check whether expr1 and expr2 are equivalent. iff \neg(expr1 iff expr2) */
	public boolean isEq(BoolExpr expr1, BoolExpr expr2) {
		Pair<BoolExpr, BoolExpr> pair = new Pair<BoolExpr, BoolExpr>(expr1,
				expr2);
		if (eqCache.containsKey(pair))
			return eqCache.get(pair);
		boolean ret = false;
		try {
			BoolExpr e1;
			e1 = ctx.mkIff(expr1, expr2);
			BoolExpr e2 = ctx.mkNot(e1);
			solver.push();
			solver.add(e2);
			Status status = solver.check();
			solver.pop();
			if (status == Status.UNSATISFIABLE) {
				ret = true;
				eqCache.put(pair, true);
				return ret;
			}
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		ret = false;
		eqCache.put(pair, false);
		return ret;
	}

	public boolean equal(BoolExpr expr1, BoolExpr expr2) {
		if (expr1 == null && expr2 == null) {
			return true;
		}
		if (expr1 == null || expr2 == null) {
			return false;
		}
		return expr1.equals(expr2);
	}

	// simplify using z3's ctx-solver-simplify
	public BoolExpr simplify(BoolExpr expr) {
		try {
			if (expr.isTrue())
				return trueExpr;
			if (simplifyCache.containsKey(expr))
				return simplifyCache.get(expr);

			goal.reset();
			goal.add(expr);
			ApplyResult res = tactic.apply(goal);
			assert res.getSubgoals().length > 0;
			Goal subgoal = res.getSubgoals()[0];

			if (subgoal.isDecidedUnsat()) {
				simplifyCache.put(expr, falseExpr);
				return falseExpr;
			} else {
				int len = subgoal.getFormulas().length;
				BoolExpr simplified = expr;
				if (len > 0)
					simplified = ctx.mkAnd(subgoal.getFormulas());

				BoolExpr sim;
				if (simplifyCache.containsKey(simplified))
					sim = simplifyCache.get(simplified);
				else {
					sim = (BoolExpr) simplified.simplify();
					simplifyCache.put(simplified, sim);
				}

				simplifyCache.put(expr, sim);
				return sim;
			}
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		assert false;
		return expr;
	}

	/**
	 * generate subtyping constraint. if t has no subclass, then we generate
	 * type(o)=t. else we generate an interval for it: t_min <= type(o) <= t
	 * t_min is the least lower bound.
	 */
	public BoolExpr genExpr(MemLoc loc, jq_Class t, CstType exprType) {
		Expr term = lift(loc);
		Expr cur = genTrue();
		int typeInt = Env.v().class2Term.get(t);
		// Suppose each node only rep one location.
		try {
			if (loc instanceof AllocLoc) {
				// FIXME: for jq_Array.
				if (term.isTrue())
					return trueExpr;

				assert term.isInt() : term;
				int srcInt = ((IntNum) term).getInt();
				int tgtInt = Env.v().getConstTerm4Class(t);
				if (CstType.EQ == exprType) {
					if (srcInt == tgtInt)
						return genTrue();
				} else if (CstType.LE == exprType) {
					if (srcInt <= tgtInt)
						return genTrue();
				} else {
					if (srcInt >= tgtInt)
						return genTrue();
				}
				return genFalse();
			} else if (loc instanceof AccessPathObject) {
				cur = typeFun.apply(term);
			}
			BoolExpr expr = null;
			if (CstType.EQ == exprType) {
				expr = ctx.mkEq(cur, ctx.mkInt(typeInt));
			} else if (CstType.LE == exprType) {
				expr = ctx.mkLe((IntExpr) cur, ctx.mkInt(typeInt));
			} else {
				expr = ctx.mkGe((IntExpr) cur, ctx.mkInt(typeInt));
			}
			return expr;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return (BoolExpr) cur;
	}

	public BoolExpr instExpr(jq_Class t, MemLocP2Set p2Set, CstType instT) {
		BoolExpr b = genFalse();

		if (p2Set.isEmpty())
			return genTrue();

		Iterator<MemLoc> it = p2Set.iterator();
		while (it.hasNext()) {
			MemLoc node = it.next();
			BoolExpr orgCst = p2Set.get(node);
			BoolExpr genExpr = genExpr(node, t, instT);
			BoolExpr newCst = intersect(orgCst, genExpr);
			if (eqTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	// hasType(o)= T
	public BoolExpr hasEqType(MemLocP2Set p2Set, jq_Class t) {
		BoolExpr b = genFalse();
		// assert !p2Set.isEmpty() : "p2set can not be empty.";
		if (p2Set.isEmpty())
			return genTrue();
		Iterator<MemLoc> it = p2Set.iterator();
		while (it.hasNext()) {
			MemLoc node = it.next();
			BoolExpr orgCst = p2Set.get(node);
			BoolExpr newCst = intersect(orgCst, genExpr(node, t, CstType.EQ));
			if (eqTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	// T1 <= hasType(o) <=T
	public BoolExpr hasIntervalType(MemLocP2Set p2Set, jq_Class t) {
		BoolExpr b = genFalse();
		// assert !p2Set.isEmpty() : "p2set can not be empty.";
		if (p2Set.isEmpty())
			return genTrue();

		Iterator<MemLoc> it = p2Set.iterator();
		while (it.hasNext()) {
			MemLoc node = it.next();
			BoolExpr orgCst = p2Set.get(node);
			int minInt = Env.v().getMinSubclass(t);
			jq_Class subMin = Env.v().class2TermRev.get(minInt);
			assert subMin != null : minInt;

			BoolExpr le = genExpr(node, t, CstType.LE);
			BoolExpr ge = genExpr(node, subMin, CstType.GE);
			BoolExpr interval = intersect(le, ge);
			BoolExpr newCst = intersect(orgCst, interval);
			if (eqTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	/** lift: Convert heap object to term. */
	private Expr lift(MemLoc loc) {
		Expr cur = genTrue();
		try {
			if (loc instanceof AllocLoc) {
				// return the number of its class.
				AllocLoc ae = (AllocLoc) loc;
				jq_Type jType = ae.getType();
				// FIXME: This could lose precision.always true for jq_array.
				if (jType instanceof jq_Array)
					return (BoolExpr) cur;

				cur = ctx.mkInt(Env.v().getConstTerm4Class((jq_Class) jType));
			} else if (loc instanceof AccessPathObject) {
				AccessPathObject ap = (AccessPathObject) loc;
				String symbol = "";
				symbol = "v" + ap.getNumber();
				term2Ap.put(symbol, ap);
				cur = ctx.mkConst(symbol, ctx.getIntSort());
			}
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		return cur;
	}

	/** lift inverse: Convert term to heap object. */
	public AccessPathObject liftInv(String term) {
		return term2Ap.get(term);
	}

	/* Given a specific method and access path o, return its constraint */
	public BoolExpr genCst(MemLocP2Set p2Set, jq_Method callee, jq_Class statT,
			Set<jq_Method> tgtSet) {
		// 1. Base case: No subtype of T override m: type(o) <= T
		if (!ChordUtil.overrideByAnySubclass(callee, statT, tgtSet)) {
			if (statT.getSubClasses().length == 0)
				return hasEqType(p2Set, statT);
			else
				return hasIntervalType(p2Set, statT);

		} else {
			// 2. Inductive case: for each its *direct* subclasses that
			// do not override current method, call genCst recursively.
			BoolExpr t = CstFactory.f().genFalse();
			for (jq_Class sub : Env.v().getSuccessors(statT)) {
				jq_Method m = sub.getVirtualMethod(callee.getNameAndDesc());
				if (m != null && m.getDeclaringClass().equals(sub)) {
					continue;
				}
				BoolExpr phi = genCst(p2Set, callee, sub, tgtSet);
				t = union(t, phi);
			}
			// do the union.
			return union(t, hasEqType(p2Set, statT));
		}
	}

	/* resolve the constraint */
	public boolean rslvCst(BoolExpr cst) {
		// ignore trivial cases.
		if (eqTrue(cst))
			return true;
		else if (eqFalse(cst))
			return false;

		BoolExpr ret1 = cst;
		try {
			// 1. first extract all the access paths encoded in the constraint
			for (BoolExpr sub : getSubCsts(cst)) {
				assert sub.isEq() || sub.isLE() || sub.isGE() : "invalid sub expr";
				Expr term = sub.getArgs()[0].getArgs()[0];
				String termStr = term.toString();
				AccessPathObject ap = (AccessPathObject) liftInv(termStr);
				assert ap != null;
				Set<AllocLoc> allocs = Env.v().interpretAPLoc(ap);
				IntNum typeInt = (IntNum) sub.getArgs()[1];
				jq_Class tt = Env.v().class2TermRev.get(typeInt.getInt());
				assert tt != null : typeInt;

				BoolExpr val = evalExpr(allocs, sub, typeInt.getInt());
				ret1 = (BoolExpr) ret1.substitute(sub, val);
			}
			ret1 = simplify(ret1);
			assert isScala(ret1) : ret1;
			// 2. then for each access path, use the interpretAPLoc(*)
			// to get the allocations represented by this access path
			// 3. finally check the satisfiability of the constraint
			return eqTrue(ret1) ? true : false;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		assert false;
		return true;
	}

	// given an expr, extract all its sub terms for instantiating.
	public void extractTerm(Expr expr, Set<BoolExpr> set) {
		try {
			if (expr.isEq() || expr.isLE() || expr.isGE()) {
				set.add((BoolExpr) expr);
				return;
			}
			if (expr.isAnd() || expr.isOr()) {
				for (int i = 0; i < expr.getNumArgs(); i++) {
					assert expr.getArgs()[i] instanceof BoolExpr : "Not BoolExpr:"
							+ expr.getArgs()[i];
					BoolExpr sub = (BoolExpr) expr.getArgs()[i];
					extractTerm(sub, set);
				}
			}
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
	}

	public Expr getAtomicTerm(BoolExpr expr) {
		Expr ret;
		try {
			assert (expr.isEq() || expr.isLE() || expr.isGE()) : expr;
			if (atomicCache.containsKey(expr))
				ret = atomicCache.get(expr);
			else {
				ret = expr.getArgs()[0].getArgs()[0];
				atomicCache.put(expr, ret);
			}
			return ret;
		} catch (Z3Exception e) {
			e.printStackTrace();
		}
		assert false;
		return null;
	}

	public Set<BoolExpr> getSubCsts(BoolExpr expr) {
		Set<BoolExpr> set = null;
		if (extractCache.containsKey(expr)) {
			set = extractCache.get(expr);
		} else {
			set = new HashSet<BoolExpr>();
			extractTerm(expr, set);
			extractCache.put(expr, set);
		}
		return set;
	}

	public Set<AccessPathObject> getAPsFromCst(BoolExpr expr) {
		Set<BoolExpr> subCsts = getSubCsts(expr);
		apSetWrapper.clear();
		for (BoolExpr subCst : subCsts) {
			Expr term = getAtomicTerm(subCst);
			AccessPathObject ap = liftInv(term.toString());
			apSetWrapper.add(ap);
		}
		return apSetWrapper;
	}

	public BoolExpr evalExpr(Set<AllocLoc> allocs, BoolExpr sub, int typeInt) {
		BoolExpr ret = falseExpr;

		for (AllocLoc loc : allocs) {
			jq_Type type = loc.getType();
			if (!(type instanceof jq_Class))
				continue;
			jq_Class t = (jq_Class) type;
			int i = Env.v().getConstTerm4Class(t);
			try {
				if (sub.isEq() && (i == typeInt))
					return trueExpr;

				if (sub.isGE() && (i >= typeInt))
					return trueExpr;

				if (sub.isLE() && (i <= typeInt))
					return trueExpr;
			} catch (Z3Exception e) {
				e.printStackTrace();
			}
		}
		return ret;
	}
}
