package framework.scuba.domain.memgraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;

// this is just a wrapper
public class P2Set {

	protected final Map<MemNode, BoolExpr> nodeToCst = new HashMap<MemNode, BoolExpr>();

	// make an empty P2Set
	public P2Set() {
	}

	public P2Set(MemNode node, BoolExpr cst) {
		nodeToCst.put(node, cst);
	}

	// -------------- basic operations ----------------
	public boolean isEmpty() {
		return nodeToCst.isEmpty();
	}

	public int size() {
		return nodeToCst.size();
	}

	public void clear() {
		nodeToCst.clear();
	}

	public BoolExpr put(MemNode node, BoolExpr cst) {
		return nodeToCst.put(node, cst);
	}

	public boolean containsKey(MemNode node) {
		return nodeToCst.containsKey(node);
	}

	public boolean contains(MemNode node, BoolExpr cst) {
		BoolExpr curr = nodeToCst.get(node);
		if (curr == null) {
			return false;
		}
		BoolExpr next = CstFactory.f().union(cst, curr);
		if (CstFactory.f().isEq(curr, next)) {
			return true;
		} else {
			return false;
		}
	}

	public Set<MemNode> keySet() {
		return nodeToCst.keySet();
	}

	public BoolExpr get(MemNode node) {
		return nodeToCst.get(node);
	}

	public Collection<BoolExpr> values() {
		return nodeToCst.values();
	}

	public boolean allAllocs() {
		boolean ret = true;
		for (MemNode node : nodeToCst.keySet()) {
			if (!node.isAllocNode()) {
				ret = false;
			}
		}
		return ret;
	}

	public void join(MemNode node, BoolExpr cst) {
		BoolExpr curr = nodeToCst.get(node);
		if (curr == null) {
			nodeToCst.put(node, cst);
		} else {
			curr = CstFactory.f().union(curr, cst);
		}
	}

	public void project(BoolExpr cst) {
		for (MemNode node : nodeToCst.keySet()) {
			BoolExpr curr = nodeToCst.get(node);
			curr = CstFactory.f().intersect(curr, cst);
		}
	}

	public void join(P2Set other) {
		for (MemNode node : other.keySet()) {
			BoolExpr cst = other.get(node);
			join(node, cst);
		}
	}

	public Iterator<MemNode> iterator() {
		return nodeToCst.keySet().iterator();
	}

	public void remove(MemNode node) {
		nodeToCst.remove(node);
	}

	// ---------------- Regular ----------------
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("\n");
		for (MemNode node : nodeToCst.keySet()) {
			BoolExpr cst = nodeToCst.get(node);
			sb.append(node + " " + cst + "\n");
		}
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof P2Set))
			return false;
		P2Set tgt = (P2Set) obj;
		if (tgt.nodeToCst.size() != nodeToCst.size()) {
			return false;
		}
		return tgt.nodeToCst.equals(nodeToCst);
	}

	@Override
	public int hashCode() {
		return nodeToCst.hashCode();
	}
}
