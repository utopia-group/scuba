package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.G;

public class MInstnNodeFactory {

	private static MInstnNodeFactory instance = new MInstnNodeFactory();

	public static MInstnNodeFactory f() {
		return instance;
	}

	// sub-factories
	private final Map<Pair<Quad, jq_Method>, MInstnNodeSubFactory> subs = new HashMap<Pair<Quad, jq_Method>, MInstnNodeSubFactory>();

	private final Pair<Quad, jq_Method> wrapper = new Pair<Quad, jq_Method>(
			null, null);

	// current on-going sub-factory
	private MInstnNodeSubFactory currSub;

	/* location instantiation cache, 1 is non-erasable, 2 is erasable */
	// this includes: locals, globals, global access paths
	private final Map<MemLoc, MemLocP2Set> cache1 = new HashMap<MemLoc, MemLocP2Set>();

	public void initSubFactory(Quad callsite, jq_Method callee) {
		wrapper.val0 = callsite;
		wrapper.val1 = callee;
		currSub = subs.get(wrapper);
		if (currSub == null) {
			currSub = new MInstnNodeSubFactory(callsite, callee);
			Pair<Quad, jq_Method> pair = new Pair<Quad, jq_Method>(callsite,
					callee);
			subs.put(pair, currSub);
		}
	}

	public void setEverything(MMemGraphController controller) {
		assert (currSub != null) : "Initialize before setting everything";
		currSub.setEverything(controller);
	}

	public void initBasicMapping(List<AbsMemLoc> formals,
			List<AbsMemLoc> actuals, AbsMemLoc retNode, AbsMemLoc lhsValue) {
		assert (currSub != null) : "Initialize before initializing basic mapping";
		currSub.initBasicMapping(formals, actuals, retNode, lhsValue);
	}

	public boolean hasBasicMapping() {
		assert (currSub != null) : "Initialize before checking basic mapping";
		return currSub.hasBasicMapping();
	}

	public boolean status(MemNode node) {
		return currSub.status(node);
	}

	/* load and initialize */
	public void loadAndInit() {
		currSub.loadAndInit();
	}

	/* refresh erasable cache */
	public void refresh() {
		currSub.refresh();
	}

	/* get the points-to set of a location */
	public MemLocP2Set get(MemLoc loc) {
		MemLocP2Set ret = null;
		if (loc instanceof LocalVarLoc || loc instanceof GlobalLoc
				|| loc instanceof GlobalAccessPathLoc) {
			ret = get1(loc);
		} else if (loc instanceof ParamLoc || loc instanceof RetLoc
				|| loc instanceof AllocLoc || loc instanceof LocalAccessPathLoc) {
			ret = currSub.get(loc);
		} else {
			assert false : loc;
		}
		assert (ret != null) : loc;
		return ret;
	}

	/* instantiate a location */
	public MemLocP2Set instn(MemLoc loc) {
		MemLocP2Set ret = null;
		if (loc instanceof LocalVarLoc || loc instanceof GlobalLoc
				|| loc instanceof GlobalAccessPathLoc) {
			ret = instn1(loc);
		} else {
			assert false : loc;
		}
		assert (ret != null) : loc;
		return ret;
	}

	/* get the points-to set of a node */
	public P2Set get(MemNode node) {
		return currSub.get(node);
	}

	/* ------------- helper methods ----------- */
	private MemLocP2Set get1(MemLoc loc) {
		MemLocP2Set ret = cache1.get(loc);
		if (ret == null) {
			ret = instn(loc);
		}
		return ret;
	}

	/* --------- low-level instantiation engine ----------- */
	private MemLocP2Set instn1(MemLoc loc) {
		MemLocP2Set ret = new MemLocP2Set();
		if (loc instanceof LocalVarLoc) {
			instnLocalVarLoc((LocalVarLoc) loc, ret);
		} else if (loc instanceof GlobalLoc) {
			instnGlobalLoc((GlobalLoc) loc, ret);
		} else if (loc instanceof GlobalAccessPathLoc) {
			instnGlobalAPLoc((GlobalAccessPathLoc) loc, ret);
		} else {
			assert false : loc;
		}
		if (G.assertion) {
			assert (!cache1.containsKey(loc)) : loc;
		}
		cache1.put(loc, ret);
		return ret;
	}

	private void instnLocalVarLoc(LocalVarLoc local, MemLocP2Set ret) {
		if (G.assertion) {
			Register r = local.getRegister();
			assert (Env.v().isAppLocal(r) || Env.v().isPropLocal(r)) : local;
		}
		ret.join(local, CstFactory.f().genTrue());
	}

	private void instnGlobalLoc(GlobalLoc global, MemLocP2Set ret) {
		ret.join(global, CstFactory.f().genTrue());
	}

	private void instnGlobalAPLoc(GlobalAccessPathLoc gap, MemLocP2Set ret) {
		ret.join(gap, CstFactory.f().genTrue());
	}

}
