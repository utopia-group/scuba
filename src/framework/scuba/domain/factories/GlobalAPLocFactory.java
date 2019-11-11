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
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.helper.G;
import framework.scuba.helper.MemLocHelper;

public class GlobalAPLocFactory extends AccessPathLocFactory {

	private static GlobalAPLocFactory instance = new GlobalAPLocFactory();

	public static GlobalAPLocFactory f() {
		return instance;
	}

	private final Pair<MemLoc, FieldSelector> wrapper = new Pair<MemLoc, FieldSelector>(
			null, null);

	private final Map<Pair<MemLoc, FieldSelector>, GlobalAccessPathLoc> locFactory = new HashMap<Pair<MemLoc, FieldSelector>, GlobalAccessPathLoc>();

	private final Map<Integer, GlobalAccessPathLoc> idToLoc = new HashMap<Integer, GlobalAccessPathLoc>();

	public GlobalAccessPathLoc get(MemLoc inner, FieldSelector outer) {
		wrapper.val0 = inner;
		wrapper.val1 = outer;
		GlobalAccessPathLoc ret = locFactory.get(wrapper);
		if (ret == null) {
			Set<jq_Type> types = MemLocHelper.h().getTypes(inner, outer);
			ret = new GlobalAccessPathLoc(inner, outer, types, ++maxNum);
			update(inner, outer, maxNum, ret);
		}
		return ret;
	}

	private void update(MemLoc inner, FieldSelector outer, int number,
			GlobalAccessPathLoc ret) {
		Pair<MemLoc, FieldSelector> pair = new Pair<MemLoc, FieldSelector>(
				inner, outer);
		locFactory.put(pair, ret);
		idToLoc.put(number, ret);
	}

	public GlobalAccessPathLoc get(int number) {
		assert (idToLoc.containsKey(number)) : number;
		return idToLoc.get(number);
	}

	public Iterator<GlobalAccessPathLoc> iterator() {
		return locFactory.values().iterator();
	}

	public void dumpIdToLoc(String file) {
		StringBuilder sb = new StringBuilder();
		for (int id : idToLoc.keySet()) {
			GlobalAccessPathLoc sAPLoc = idToLoc.get(id);
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
