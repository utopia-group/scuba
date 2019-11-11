package framework.scuba.domain.context;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;

public class CstWrapper {
	Register recv;
	jq_Type statType;
	final Set<Pair<Integer, Integer>> types;
	BoolExpr expr;
	Quad callsite;
	MemNode location;
	Set<MemNode> p2Nodes;

	public CstWrapper(Register n, jq_Type t, MemNode node,
			Set<Pair<Integer, Integer>> s, BoolExpr e, Quad c) {
		recv = n;
		statType = t;
		types = s;
		expr = e;
		callsite = c;
		location = node;
	}

	public Quad getCallsite() {
		return callsite;
	}

	public Set<Pair<Integer, Integer>> getTypes() {
		return types;
	}

	public Register getRecv() {
		return recv;
	}

	public jq_Type getType() {
		return statType;
	}

	public boolean sameP2Nodes(Set<MemNode> set) {
		return p2Nodes.equals(set);
	}

	public BoolExpr evalCst() {
		return null;
	}

	public void instnRecv(MemNode n) {
		location = n;
	}

	public void setReg(Register r) {
		recv = r;
	}

	public void setP2Node(Set<MemNode> s) {
		this.p2Nodes = s;
	}

	public MemNode getMemNode() {
		return location;
	}

	public Set<MemNode> getP2Nodes() {
		return this.p2Nodes;
	}

	public boolean allAllocs() {
		for (MemNode n : p2Nodes) {
			Set<MemLoc> locs = n.getLocs();
			for (MemLoc loc : locs) {
				if (!(loc instanceof AllocLoc))
					return false;
			}
		}
		return true;
	}

	public boolean hasType(CstWrapper w, jq_Type t) {
		for (Pair<Integer, Integer> p : w.getTypes()) {
			int term = Env.v().class2Term.get(t);
			int v1 = p.val0;
			int v2 = p.val1;
			assert term > 0;
			if (!((term >= v1) && (term <= v2))) {
				System.out.println("false edge: " + term + " in " + v1 + ","
						+ v2);
				return false;
			}
		}
		return true;
	}

	public BoolExpr eval() {
		BoolExpr ret = CstFactory.f().genFalse();
		for (MemNode n : p2Nodes) {
			Set<MemLoc> locs = n.getLocs();
			for (MemLoc loc : locs) {
				assert loc instanceof AllocLoc;
				jq_Type t = ((AllocLoc) loc).getType();
				int term = Env.v().class2Term.get(t);
				for (Pair<Integer, Integer> p : this.types) {
					int v1 = p.val0;
					int v2 = p.val1;
					assert term > 0;
					if ((term >= v1) && (term <= v2))
						return CstFactory.f().genTrue();
				}
			}
		}
		return ret;
	}

	public String toString() {
		Set<jq_Type> set = new HashSet<jq_Type>();
		for (Pair<Integer, Integer> p : types) {
			int v1 = p.val0;
			int v2 = p.val1;
			jq_Type t1 = Env.v().class2TermRev.get(v1);
			jq_Type t2 = Env.v().class2TermRev.get(v2);
			set.add(t1);
			set.add(t2);
		}
		return recv + "|hasType|" + set;
	}

	public CstWrapper clone() {
		CstWrapper cst = new CstWrapper(recv, statType, location, types, expr,
				callsite);
		return cst;
	}
}
