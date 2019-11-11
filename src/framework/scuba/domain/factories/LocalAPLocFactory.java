package framework.scuba.domain.factories;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Type;
import chord.util.tuple.object.Pair;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.helper.G;
import framework.scuba.helper.MemLocHelper;

public class LocalAPLocFactory extends AccessPathLocFactory {

	private static LocalAPLocFactory instance = new LocalAPLocFactory();

	public static LocalAPLocFactory f() {
		return instance;
	}

	private final Pair<MemLoc, FieldSelector> pair = new Pair<MemLoc, FieldSelector>(
			null, null);

	private final Map<Pair<MemLoc, FieldSelector>, LocalAccessPathLoc> locFactory = new HashMap<Pair<MemLoc, FieldSelector>, LocalAccessPathLoc>();

	private final Map<Integer, LocalAccessPathLoc> idToLoc = new HashMap<Integer, LocalAccessPathLoc>();

	public LocalAccessPathLoc get(MemLoc inner, FieldSelector outer) {
		pair.val0 = inner;
		pair.val1 = outer;
		LocalAccessPathLoc ret = locFactory.get(pair);
		if (ret == null) {
			Set<jq_Type> types = MemLocHelper.h().getTypes(inner, outer);
			ret = new LocalAccessPathLoc(inner, outer, types, ++maxNum);
			update(inner, outer, maxNum, ret);
		}
		return ret;
	}

	private void update(MemLoc inner, FieldSelector outer, int number,
			LocalAccessPathLoc ret) {
		Pair<MemLoc, FieldSelector> pair = new Pair<MemLoc, FieldSelector>(
				inner, outer);
		locFactory.put(pair, ret);
		idToLoc.put(number, ret);
	}

	public LocalAccessPathLoc get(int number) {
		assert (idToLoc.containsKey(number)) : number;
		return idToLoc.get(number);
	}

	public Iterator<LocalAccessPathLoc> iterator() {
		return locFactory.values().iterator();
	}

	public void dumpIdToLoc(String file) {
		StringBuilder sb = new StringBuilder();
		for (int id : idToLoc.keySet()) {
			LocalAccessPathLoc sAPLoc = idToLoc.get(id);
			sb.append(id + " : " + sAPLoc + "\n");
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