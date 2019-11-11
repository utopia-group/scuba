package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;

import chord.util.tuple.object.Pair;
import framework.scuba.domain.context.Ctxt;
import framework.scuba.domain.context.ProgPoint;

public class ContextFactory {

	private static ContextFactory instance = new ContextFactory();

	public static ContextFactory f() {
		return instance;
	}

	private final Pair<ProgPoint, Ctxt> wrapper = new Pair<ProgPoint, Ctxt>(
			null, null);

	private final Map<Pair<ProgPoint, Ctxt>, Ctxt> ctxtFactory = new HashMap<Pair<ProgPoint, Ctxt>, Ctxt>();
	private final Map<Pair<ProgPoint, Ctxt>, Integer> ctxtToId = new HashMap<Pair<ProgPoint, Ctxt>, Integer>();

	// numbers of contexts start from 1
	private int maxNum;

	public Ctxt get(ProgPoint point, Ctxt prevCtxt) {
		assert !(prevCtxt == null ^ point == null);
		// first see if smash
		if (prevCtxt != null && prevCtxt.contains(point)) {
			return prevCtxt;
		}
		// not smash
		wrapper.val0 = point;
		wrapper.val1 = prevCtxt;
		Ctxt ret = ctxtFactory.get(wrapper);
		if (ret == null) {
			ret = new Ctxt(point, prevCtxt, ++maxNum);
			update(point, prevCtxt, maxNum, ret);
		}
		return ret;
	}

	private void update(ProgPoint point, Ctxt prev, int number, Ctxt ctx) {
		Pair<ProgPoint, Ctxt> pair = new Pair<ProgPoint, Ctxt>(point, prev);
		ctxtFactory.put(pair, ctx);
		ctxtToId.put(pair, number);
	}

}
