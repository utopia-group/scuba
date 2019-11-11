package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.MMemGraphController;

public class MInstnEdgeFactory {

	private static MInstnEdgeFactory instance = new MInstnEdgeFactory();

	public static MInstnEdgeFactory f() {
		return instance;
	}

	// sub-factories
	private final Map<Pair<Quad, jq_Method>, MInstnEdgeSubFactory> subs = new HashMap<Pair<Quad, jq_Method>, MInstnEdgeSubFactory>();
	private final Pair<Quad, jq_Method> wrapper = new Pair<Quad, jq_Method>(
			null, null);

	// current on-going sub-factory
	private MInstnEdgeSubFactory currSub;

	public void initSubFactory(Quad callsite, jq_Method callee) {
		wrapper.val0 = callsite;
		wrapper.val1 = callee;
		currSub = subs.get(wrapper);
		if (currSub == null) {
			currSub = new MInstnEdgeSubFactory(callsite, callee);
			Pair<Quad, jq_Method> pair = new Pair<Quad, jq_Method>(callsite,
					callee);
			subs.put(pair, currSub);
		}
	}

	public void setEverything(MMemGraphController controller, BoolExpr hasType) {
		currSub.setEverything(controller, hasType);
	}

	/* load and initialize */
	public boolean loadAndInit() {
		return currSub.loadAndInit();
	}

	public boolean refresh() {
		return currSub.refresh();
	}

}
