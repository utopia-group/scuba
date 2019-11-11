package framework.scuba.domain.summary;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import joeq.Compiler.Quad.RegisterFactory.Register;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.location.HeapObject;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2SetWrapper;
import framework.scuba.helper.G;

public class ScubaResult {

	protected final Map<Register, ScubaResultP2SetWrapper> results = new HashMap<Register, ScubaResultP2SetWrapper>();

	public void put(Register r, ScubaResultP2SetWrapper wrapper) {
		results.put(r, wrapper);
	}

	public void add(Register r, MemLocP2SetWrapper wrapper) {
		ScubaResultP2SetWrapper curr = results.get(r);
		if (curr == null) {
			curr = new ScubaResultP2SetWrapper();
			results.put(r, curr);
		}
		curr.join(wrapper);
	}

	public void add(Register r, MemLoc loc, BoolExpr cst) {
		assert (loc instanceof HeapObject) : loc;
		ScubaResultP2SetWrapper curr = results.get(r);
		if (curr == null) {
			curr = new ScubaResultP2SetWrapper();
			results.put(r, curr);
		}
		curr.join(loc, cst);
	}

	public ScubaResultP2SetWrapper get(Register r) {
		return results.get(r);
	}

	public Iterator<Register> keySetIterator() {
		return results.keySet().iterator();
	}

	public boolean contains(Register r) {
		return results.containsKey(r);
	}

	public boolean isEmpty() {
		return results.isEmpty();
	}

	public void dumpToFile(String file) {
		StringBuilder sb = new StringBuilder();
		for (Register r : results.keySet()) {
			ScubaResultP2SetWrapper wrapper = results.get(r);
			sb.append(r + " " + Env.v().getMethodByReg(r) + " : "
					+ wrapper.keySet() + "\n");
		}
		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + file));
			bufw.write(sb.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

}
