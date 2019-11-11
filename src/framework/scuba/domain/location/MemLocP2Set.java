package framework.scuba.domain.location;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;

public class MemLocP2Set {

	protected final Map<MemLoc, BoolExpr> locToCst = new HashMap<MemLoc, BoolExpr>();

	public MemLocP2Set() {

	}

	public MemLocP2Set(MemLoc loc, BoolExpr cst) {
		locToCst.put(loc, cst);
	}

	// ------- Basic Operations ----------
	public boolean isEmpty() {
		return locToCst.isEmpty();
	}

	public int size() {
		return locToCst.size();
	}

	public BoolExpr put(MemLoc loc, BoolExpr cst) {
		return locToCst.put(loc, cst);
	}

	public Set<MemLoc> keySet() {
		return locToCst.keySet();
	}

	public boolean containsKey(MemLoc loc) {
		return locToCst.containsKey(loc);
	}

	public boolean contains(MemLoc loc, BoolExpr cst) {
		BoolExpr curr = locToCst.get(loc);
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

	public BoolExpr get(MemLoc loc) {
		return locToCst.get(loc);
	}

	public Iterator<MemLoc> iterator() {
		return locToCst.keySet().iterator();
	}

	public void remove(MemLoc loc) {
		locToCst.remove(loc);
	}

	public void join(MemLoc loc, BoolExpr cst) {
		BoolExpr curr = locToCst.get(loc);
		if (curr == null) {
			locToCst.put(loc, cst);
		} else {
			curr = CstFactory.f().union(curr, cst);
		}
	}

	public void join(MemLocP2Set other) {
		for (MemLoc loc : other.keySet()) {
			BoolExpr cst = other.get(loc);
			join(loc, cst);
		}
	}

	public void project(BoolExpr cst) {
		for (MemLoc loc : locToCst.keySet()) {
			BoolExpr curr = locToCst.get(loc);
			curr = CstFactory.f().intersect(curr, cst);
		}
	}

	// ------- Object --------
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (MemLoc loc : locToCst.keySet()) {
			BoolExpr cst = locToCst.get(loc);
			str.append(loc + " " + cst + "\n");
		}
		return str.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MemLocP2Set))
			return false;
		MemLocP2Set tgt = (MemLocP2Set) obj;
		if (tgt.locToCst.size() != locToCst.size()) {
			return false;
		}
		return tgt.locToCst.equals(locToCst);
	}

	@Override
	public int hashCode() {
		return locToCst.hashCode();
	}
}
