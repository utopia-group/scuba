package framework.scuba.domain.summary;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.helper.G;

public class ScubaLUT {

	protected final Map<Register, Set<AllocLoc>> results = new HashMap<Register, Set<AllocLoc>>();

	public Set<Register> keySet() {
		return results.keySet();
	}

	public boolean isEmpty() {
		return results.isEmpty();
	}

	public Iterator<Register> iterator() {
		return results.keySet().iterator();
	}

	public void add(Register r, AllocLoc alloc) {
		Set<AllocLoc> curr = results.get(r);
		if (curr == null) {
			curr = new HashSet<AllocLoc>();
			results.put(r, curr);
		}
		curr.add(alloc);
	}

	public void addAll(Register r, Set<AllocLoc> allocs) {
		Set<AllocLoc> curr = results.get(r);
		if (curr == null) {
			curr = new HashSet<AllocLoc>();
			results.put(r, curr);
		}
		curr.addAll(allocs);
	}

	public Set<AllocLoc> get(Register r) {
		return results.get(r);
	}

	public void dumpToFile(String file) {
		StringBuilder sb = new StringBuilder();
		for (Register r : results.keySet()) {
			Set<AllocLoc> allocs = results.get(r);
			sb.append(r + "\n");
			for (AllocLoc alloc : allocs) {
				sb.append(alloc + "\n");
			}
			sb.append("---------------------------------\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + file + ".dot"));
			bufw.write(sb.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

}
