package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;

import framework.scuba.domain.context.ProgPoint;
import joeq.Compiler.Quad.Quad;

public class ProgPointFactory {

	private static ProgPointFactory instance = new ProgPointFactory();

	public static ProgPointFactory f() {
		return instance;
	}

	private final Map<Quad, ProgPoint> ppFactory = new HashMap<Quad, ProgPoint>();

	private final Map<Quad, Integer> ppToId = new HashMap<Quad, Integer>();

	// numbers of program points start from 1
	private int maxNum;

	public ProgPoint get(Quad stmt) {
		ProgPoint ret = ppFactory.get(stmt);
		if (ret == null) {
			ret = new ProgPoint(stmt, ++maxNum);
			update(stmt, maxNum, ret);
		}
		return ret;
	}

	private void update(Quad stmt, int number, ProgPoint pp) {
		ppFactory.put(stmt, pp);
		ppToId.put(stmt, number);
	}

	public void clear() {
		instance = new ProgPointFactory();
	}
}
