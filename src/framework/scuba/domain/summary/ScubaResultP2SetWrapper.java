package framework.scuba.domain.summary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2SetWrapper;

public class ScubaResultP2SetWrapper {

	protected final Map<MemLoc, BoolExpr> locToCst = new HashMap<MemLoc, BoolExpr>();
	protected final Map<MemLoc, Set<FieldSelector>> smasheds = new HashMap<MemLoc, Set<FieldSelector>>();

	public ScubaResultP2SetWrapper() {

	}

	public ScubaResultP2SetWrapper(MemLoc loc, BoolExpr cst) {
		put(loc, cst);
	}

	// ------- Basic Operations ----------
	public boolean isEmpty() {
		return locToCst.isEmpty();
	}

	public int size() {
		return locToCst.size();
	}

	public BoolExpr put(MemLoc loc, BoolExpr cst) {
		Set<FieldSelector> smasheds = new HashSet<FieldSelector>();
		if (loc instanceof AccessPathObject) {
			smasheds.addAll(((AccessPathObject) loc).getSmashedFields());
		} else if (loc instanceof AllocLoc) {

		} else {
			assert false;
		}
		return locToCst.put(loc, cst);
	}

	public Set<MemLoc> keySet() {
		return locToCst.keySet();
	}

	public boolean containsKey(MemLoc loc) {
		return locToCst.containsKey(loc);
	}

	public BoolExpr getCst(MemLoc loc) {
		return locToCst.get(loc);
	}

	public Set<FieldSelector> getSmasheds(MemLoc loc) {
		return smasheds.get(loc);
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
		Set<FieldSelector> set = smasheds.get(loc);
		if (set == null) {
			set = new HashSet<FieldSelector>();
			smasheds.put(loc, set);
		}
		if (loc instanceof AccessPathObject) {
			set.addAll(((AccessPathObject) loc).getSmashedFields());
		}

	}

	public void join(MemLocP2SetWrapper other) {
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

	// ------- Regular --------
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (MemLoc loc : locToCst.keySet()) {
			BoolExpr cst = locToCst.get(loc);
			Set<FieldSelector> smash = smasheds.get(loc);
			str.append(loc + " " + cst + " " + smash + "\n");
		}
		return str.toString();
	}

}
