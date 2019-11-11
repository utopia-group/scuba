package framework.scuba.domain.instn;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;

public class MInstnEdgeSubFactory {

	protected final Quad callsite;
	protected final jq_Method callee;
	protected final jq_Method caller;
	protected final MMemGraph calleeGraph;
	protected final MMemGraph callerGraph;

	protected MMemGraphController controller;
	protected BoolExpr hasType;
	protected boolean same;

	// this includes: local/return --> allocation/global access path, global -->
	// allocation, allocation --> allocation/global access path, global access
	// path --> allocation
	protected final Set<MemEdge> visited = new HashSet<MemEdge>();
	// this includes: either source of target is a local access path
	protected final Set<MemEdge> wl = new HashSet<MemEdge>();

	protected boolean terminated;

	private final Set<BoolExpr> cstSet = new HashSet<BoolExpr>(4);
	private final Trio<MemNode, FieldSelector, MemNode> wrapper = new Trio<MemNode, FieldSelector, MemNode>(
			null, null, null);

	public MInstnEdgeSubFactory(Quad callsite, jq_Method callee) {
		this.callsite = callsite;
		this.callee = callee;
		this.caller = callsite.getMethod();
		this.calleeGraph = SummariesEnv.v().getMMemGraph(callee);
		this.callerGraph = SummariesEnv.v().getMMemGraph(caller);
	}

	public void setEverything(MMemGraphController controller, BoolExpr hasType) {
		this.controller = controller;
		this.same = CstFactory.f().equal(hasType, this.hasType);
		this.hasType = hasType;
		if (same) {
			G.hitHashTypeCache++;
		}
	}

	/* load work-list and initialize visited */
	public boolean loadAndInit() {
		boolean ret = false;
		if (terminated) {
			if (Env.v().isWidening()) {
				return ret;
			}
		}
		Iterator<MemEdge> it = calleeGraph.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			if ((src.isAppLocalVarNode() || src.isPropLocalVarNode() || src
					.isRetNode())
					&& (tgt.isAllocNode() || tgt.isGlobalAPNode())) {
				if (!visited.contains(edge)) {
					ret = ret | instn(edge);
					visited.add(edge);
				}
			} else if (src.isGlobalNode() && tgt.isAllocNode()) {
				if (!visited.contains(edge)) {
					ret = ret | instn(edge);
					visited.add(edge);
				}
			} else if (src.isAllocNode()
					&& (tgt.isAllocNode() || tgt.isGlobalAPNode())) {
				if (!visited.contains(edge)) {
					ret = ret | instn(edge);
					visited.add(edge);
				}
			} else if (src.isGlobalAPNode() && tgt.isAllocNode()) {
				if (!visited.contains(edge)) {
					ret = ret | instn(edge);
					visited.add(edge);
				}
			} else if (src.isLocalAPNode() || tgt.isLocalAPNode()) {
				wl.add(edge);
			} else if ((src.isGlobalNode() || src.isGlobalAPNode())
					&& (tgt.isGlobalNode() || tgt.isGlobalAPNode())) {
				assert (!SummariesEnv.v().shareSum) : edge;
				if (!visited.contains(edge)) {
					ret = ret | instn(edge);
					visited.add(edge);
				}
			} else {
				assert false : edge;
			}
		}
		terminated = calleeGraph.isTerminated();
		return ret;
	}

	/* refresh the work-list (instantiation) */
	public boolean refresh() {
		boolean ret = false;
		if (CstFactory.f().eqFalse(hasType)) {
			return ret;
		}
		Iterator<MemEdge> it = wl.iterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			if (SummariesEnv.v().instnSkip) {
				if (notInstn(edge)) {
					continue;
				}
			}
			boolean changed = instn(edge);
			ret = ret || changed;
		}
		return ret;
	}

	/* ------- helper methods --------- */
	private boolean notInstn(MemEdge edge) {
		boolean ret = false;
		MemNode src = edge.getSrc();
		MemNode tgt = edge.getTgt();
		BoolExpr cst = edge.getCst();
		if (Env.v().isWidening()) {
			ret = same && MInstnNodeFactory.f().status(src)
					&& MInstnNodeFactory.f().status(tgt);
		} else {
			ret = same && MInstnNodeFactory.f().status(src)
					&& MInstnNodeFactory.f().status(tgt)
					&& MInstnCstFactory.f().status(cst);
		}
		if (ret) {
			G.hit++;
		}
		return ret;
	}

	private boolean instn(MemEdge edge) {
		boolean ret = false;
		FieldSelector field = edge.getField();
		MemNode src = edge.getSrc();
		MemNode tgt = edge.getTgt();
		BoolExpr cst = edge.getCst();
		P2Set instnSrcs = MInstnNodeFactory.f().get(src);
		P2Set instnTgts = MInstnNodeFactory.f().get(tgt);
		BoolExpr instnCst = MInstnCstFactory.f().get(cst);
		for (MemNode instnSrc : instnSrcs.keySet()) {
			for (MemNode instnTgt : instnTgts.keySet()) {
				G.wk++;
				cstSet.clear();
				BoolExpr instnSrcCst = instnSrcs.get(instnSrc);
				BoolExpr instnTgtCst = instnTgts.get(instnTgt);
				cstSet.add(instnSrcCst);
				cstSet.add(instnTgtCst);
				cstSet.add(hasType);
				cstSet.add(instnCst);
				BoolExpr newCst = CstFactory.f().intersect(cstSet);
				if (SummariesEnv.v().weakUpdateCache) {
					wrapper.val0 = instnSrc;
					wrapper.val1 = field;
					wrapper.val2 = instnTgt;
					BoolExpr currCst = controller.instnCtrlor.cache
							.get(wrapper);
					if (CstFactory.f().equal(currCst, newCst)) {
						continue;
					}
					Trio<MemNode, FieldSelector, MemNode> trio = new Trio<MemNode, FieldSelector, MemNode>(
							instnSrc, field, instnTgt);
					controller.instnCtrlor.cache.put(trio, newCst);
				}
				G.doneWK++;
				if (instnSrc.isLocalVarNode() && instnTgt.isLocalAPNode()) {
					if (Env.v().isBadLocal(src)) {
						Env.v().addBadLocal(instnSrc);
					} else if (Env.v().multiCallees) {
						Env.v().addBadLocal(instnSrc);
					}
				}
				boolean changed = instnSrc.weakUpdate(field, instnTgt, newCst);
				if (changed) {
					G.trueDoneWK++;
				}
				ret = ret || changed;
			}
		}
		return ret;
	}
}
