package framework.scuba.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CIObj;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.instn.MInstnCstFactory;
import framework.scuba.domain.instn.MInstnEdgeFactory;
import framework.scuba.domain.instn.MInstnNodeFactory;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;
import framework.scuba.helper.TypeHelper;

/* 1. set everything 2. initialize helper factories 3. initialize basic mapping 4. instantiate */
public class MInstnController {

	protected Quad callsite;
	protected jq_Method callee;
	protected jq_Method caller;
	protected BoolExpr hasType;
	protected MMemGraph calleeGraph;
	protected MMemGraph callerGraph;

	protected final MMemGraphController controller;

	// a weak-update cache
	public final Map<Trio<MemNode, FieldSelector, MemNode>, BoolExpr> cache = new HashMap<Trio<MemNode, FieldSelector, MemNode>, BoolExpr>();

	public MInstnController(MMemGraphController controller) {
		this.controller = controller;
	}

	public void setEverything(Quad callsite, jq_Method callee, BoolExpr hasType) {
		this.callsite = callsite;
		this.callee = callee;
		this.caller = callsite.getMethod();
		this.hasType = hasType;
		this.calleeGraph = SummariesEnv.v().getMMemGraph(callee);
		this.callerGraph = SummariesEnv.v().getMMemGraph(caller);
	}

	public void initFactories() {
		MInstnNodeFactory.f().initSubFactory(callsite, callee);
		MInstnNodeFactory.f().setEverything(controller);
		MInstnCstFactory.f().initSubFactory(callsite, callee);
		MInstnCstFactory.f().setEverything(controller);
		MInstnEdgeFactory.f().initSubFactory(callsite, callee);
		MInstnEdgeFactory.f().setEverything(controller, hasType);
	}

	public void initBasicMapping(jq_Method meth, Quad callsite, Register lr,
			jq_Type lt, SummariesEnv.LocType lvt, List<Register> rrs,
			List<jq_Type> rts, List<SummariesEnv.LocType> rvts) {
		if (MInstnNodeFactory.f().hasBasicMapping()) {
			return;
		}
		// lhs value
		AbsMemLoc lhs = null;
		if (lr != null) {
			if (TypeHelper.h().isRefType(lt)) {
				CIObj ciP2Set = Env.v().findCIP2Set(lr);
				if (ciP2Set.pts.isEmpty()) {
					lhs = Env.v().getPrimitiveLoc();
				} else {
					assert (lvt == SummariesEnv.LocType.LOCAL_VAR) : lvt;
					lhs = Env.v().getLocalVarLoc(lr, meth, lt);
				}
			} else {
				lhs = Env.v().getPrimitiveLoc();
			}
			assert (lhs != null) : lr + " " + lt + " " + lvt;
		}
		// actuals
		List<AbsMemLoc> actuals = new ArrayList<AbsMemLoc>();
		for (int i = 0; i < rrs.size(); i++) {
			jq_Type rt = rts.get(i);
			Register rr = rrs.get(i);
			SummariesEnv.LocType rvt = rvts.get(i);
			AbsMemLoc actual = null;
			if (TypeHelper.h().isRefType(rt)) {
				CIObj ciP2Set = Env.v().findCIP2Set(rr);
				if (ciP2Set.pts.isEmpty()) {
					actual = Env.v().getPrimitiveLoc();
				} else {
					if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
						actual = Env.v().getLocalVarLoc(rr, meth, rt);
					} else if (rvt == SummariesEnv.LocType.PARAMETER) {
						actual = Env.v().getParamLoc(rr, meth, rt);
					}
				}
			} else {
				actual = Env.v().getPrimitiveLoc();
			}
			assert (actual != null) : rr + " " + rt + " " + rvt;
			actuals.add(actual);
		}
		List<AbsMemLoc> formals = calleeGraph.getFormalLocs();
		AbsMemLoc ret = calleeGraph.getRetLoc();
		MInstnNodeFactory.f().initBasicMapping(formals, actuals, ret, lhs);
	}

	private long start, end;

	public boolean instn() {
		boolean ret = false;
		/* a fix-point computation */
		while (true) {
			boolean again = false;
			// load and initialize
			start = System.nanoTime();
			MInstnNodeFactory.f().loadAndInit();
			end = System.nanoTime();
			G.instnNodeTime += (end - start);

			start = System.nanoTime();
			MInstnCstFactory.f().loadAndInit();
			end = System.nanoTime();
			G.instnCstTime += (end - start);

			start = System.nanoTime();
			again = again | MInstnEdgeFactory.f().loadAndInit();
			end = System.nanoTime();
			G.instnEdgeTime += (end - start);

			// refresh
			start = System.nanoTime();
			MInstnNodeFactory.f().refresh();
			end = System.nanoTime();
			G.instnNodeTime += (end - start);

			start = System.nanoTime();
			MInstnCstFactory.f().refresh();
			end = System.nanoTime();
			G.instnCstTime += (end - start);

			start = System.nanoTime();
			again = again | MInstnEdgeFactory.f().refresh();
			end = System.nanoTime();
			G.instnEdgeTime += (end - start);

			ret = ret || again;
			if (!again) {
				break;
			}
			if (!G.instnFixPoint) {
				break;
			}
		}
		return ret;
	}
}
