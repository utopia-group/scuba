package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.MMemGraphController;
import framework.scuba.domain.context.ProgPoint;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.factories.ProgPointFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.PrimitiveLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;
import framework.scuba.helper.TypeHelper;
import framework.scuba.utils.ChordUtil;

public class MInstnNodeSubFactory {

	protected final Quad callsite;
	protected final jq_Method callee;
	protected final jq_Method caller;
	protected final MMemGraph calleeGraph;
	protected final MMemGraph callerGraph;

	protected MMemGraphController controller;
	/* basic mapping */
	protected Map<AbsMemLoc, AbsMemLoc> basicMapping = null;
	protected Map<AbsMemLoc, AbsMemLoc> basicMappingInv = null;

	/* node instantiation cache, 1 is non-erasable, 2 is erasable */
	// this includes: locals, globals, global access paths, allocations, return
	protected final Map<MemNode, P2Set> nodeCache1 = new HashMap<MemNode, P2Set>();
	// this includes: local access paths
	protected final Map<MemNode, P2Set> nodeCache2 = new HashMap<MemNode, P2Set>();
	// mark the change of the point-to set, true: not changed, false: changed
	protected final Map<MemNode, Boolean> status = new HashMap<MemNode, Boolean>();

	/* location instantiation cache, 1 is non-erasable, 2 is erasable */
	// this includes: allocations, return, parameters
	protected final Map<MemLoc, MemLocP2Set> locCache1 = new HashMap<MemLoc, MemLocP2Set>();
	// this includes: local access paths
	protected final Map<MemLoc, MemLocP2Set> locCache2 = new HashMap<MemLoc, MemLocP2Set>();

	protected boolean terminated;

	public MInstnNodeSubFactory(Quad callsite, jq_Method callee) {
		this.callsite = callsite;
		this.callee = callee;
		this.caller = callsite.getMethod();
		this.calleeGraph = SummariesEnv.v().getMMemGraph(callee);
		this.callerGraph = SummariesEnv.v().getMMemGraph(caller);
	}

	public void setEverything(MMemGraphController controller) {
		this.controller = controller;
	}

	public Quad getCallsite() {
		return callsite;
	}

	public jq_Method getCaller() {
		return caller;
	}

	public jq_Method getCallee() {
		return callee;
	}

	public MMemGraph getCallerGraph() {
		return callerGraph;
	}

	public MMemGraph getCalleeGraph() {
		return calleeGraph;
	}

	public boolean hasBasicMapping() {
		return (basicMapping != null);
	}

	/* initialize the basic mapping when first time analyzing the call site */
	public void initBasicMapping(List<AbsMemLoc> formals,
			List<AbsMemLoc> actuals, AbsMemLoc ret, AbsMemLoc lhs) {
		basicMapping = new HashMap<AbsMemLoc, AbsMemLoc>();
		basicMappingInv = new HashMap<AbsMemLoc, AbsMemLoc>();
		assert (formals.size() == actuals.size()) : formals + " " + actuals;
		for (int i = 0; i < formals.size(); i++) {
			AbsMemLoc formal = formals.get(i);
			AbsMemLoc actual = actuals.get(i);
			basicMapping.put(formal, actual);
			basicMappingInv.put(actual, formal);
		}
		if (ret != null && lhs != null) {
			basicMapping.put(ret, lhs);
			basicMappingInv.put(lhs, ret);
		}
	}

	/* read cache */
	// should guarantee read after write
	public P2Set get(MemNode node) {
		P2Set ret = null;
		if (node.isAppLocalVarNode() || node.isPropLocalVarNode()
				|| node.isAllocNode() || node.isRetNode()
				|| node.isGlobalNode() || node.isGlobalAPNode()) {
			ret = get1(node);
		} else if (node.isLocalAPNode()) {
			ret = get2(node);
		} else {
			assert false : node;
		}
		assert (ret != null) : node;
		return ret;
	}

	/* read cache */
	// do not need to guarantee read after write
	public MemLocP2Set get(MemLoc loc) {
		MemLocP2Set ret = null;
		if (loc instanceof ParamLoc || loc instanceof RetLoc
				|| loc instanceof AllocLoc) {
			ret = get1(loc);
		} else if (loc instanceof LocalAccessPathLoc) {
			ret = get2(loc);
		} else {
			assert false : loc;
		}
		assert (ret != null) : loc;
		return ret;
	}

	/* load and initialize */
	public void loadAndInit() {
		if (terminated) {
			return;
		}
		Iterator<MemNode> it = calleeGraph.sumNodesIterator();
		while (it.hasNext()) {
			MemNode node = it.next();
			if (node.isAppLocalVarNode() || node.isPropLocalVarNode()
					|| node.isAllocNode() || node.isRetNode()
					|| node.isGlobalNode() || node.isGlobalAPNode()) {
				P2Set curr = nodeCache1.get(node);
				if (curr == null) {
					curr = instn(node);
					nodeCache1.put(node, curr);
					status.put(node, false);
				} else {
					status.put(node, true);
				}
			} else if (node.isLocalAPNode()) {
				P2Set curr = nodeCache2.get(node);
				nodeCache2.put(node, curr);
			} else {
				assert false : node + " " + node.getNodeType();
			}
		}
		updatePropLevel();
		terminated = calleeGraph.isTerminated();
	}

	/* refresh the erasable cache */
	public void refresh() {
		// clean-up
		locCache2.clear();
		// instantiation
		Iterator<MemNode> it = nodeCache2.keySet().iterator();
		while (it.hasNext()) {
			MemNode key = it.next();
			instn(key);
		}
	}

	/* instantiate a node */
	public P2Set instn(MemNode node) {
		P2Set ret = new P2Set();
		if (node.isAppLocalVarNode() || node.isPropLocalVarNode()
				|| node.isAllocNode() || node.isRetNode()
				|| node.isGlobalNode() || node.isGlobalAPNode()) {
			instn1(node, ret);
		} else if (node.isLocalAPNode()) {
			instn2(node, ret);
		} else {
			assert false : node;
		}
		return ret;
	}

	/* check status */
	public boolean status(MemNode node) {
		assert (status.containsKey(node)) : node;
		boolean ret = status.get(node);
		if (ret) {
			G.hitNodeCache++;
		}
		return ret;
	}

	/* ---------------- helper methods ----------------- */
	/* update the application local propagation level */
	private void updatePropLevel() {
		for (Iterator<MemNode> it = calleeGraph.appLocalNodesIterator(); it
				.hasNext();) {
			MemNode src = it.next();
			assert (src.isAppLocalVarNode()) : src;
			if (!src.toProp()) {
				continue;
			}
			Set<MemLoc> locs = src.getLocs();
			assert (locs.size() == 1) : locs;
			MemLoc loc = locs.iterator().next();
			MemNode instnSrc = MemNodeFactory.f().get(callerGraph, loc);
			int calleeLevel = src.getCurrPropLevel();
			int callerLevel = instnSrc.getCurrPropLevel();
			int setLevel = 0;
			if (instnSrc == src) {
				setLevel = Math.max(calleeLevel, callerLevel);
			} else {
				setLevel = Math.max(calleeLevel + 1, callerLevel);
			}
			instnSrc.setCurrPropLevel(setLevel);
		}
	}

	/* instantiate a location */
	private MemLocP2Set instn(MemLoc loc) {
		MemLocP2Set ret = new MemLocP2Set();
		if (loc instanceof ParamLoc || loc instanceof RetLoc
				|| loc instanceof AllocLoc) {
			instn1(loc, ret);
		} else if (loc instanceof LocalAccessPathLoc) {
			instn2(loc, ret);
		} else {
			assert false : loc;
		}
		return ret;
	}

	/* read non-erasable cache1 */
	private P2Set get1(MemNode node) {
		P2Set ret = nodeCache1.get(node);
		assert (ret != null) : node;
		return ret;
	}

	/* read erasable cache2 */
	private P2Set get2(MemNode node) {
		P2Set ret = nodeCache2.get(node);
		assert (ret != null) : node;
		return ret;
	}

	/* read non-erasable locCache1 */
	private MemLocP2Set get1(MemLoc loc) {
		MemLocP2Set ret = locCache1.get(loc);
		if (ret == null) {
			ret = instn(loc);
		}
		return ret;
	}

	/* read erasable locCache2 */
	private MemLocP2Set get2(MemLoc loc) {
		MemLocP2Set ret = locCache2.get(loc);
		if (ret == null) {
			ret = instn(loc);
		}
		return ret;
	}

	/* write non-erasable cache1 */
	private void instn1(MemNode node, P2Set ret) {
		if (node.getMemGraph() == Env.v().shared) {
			// return the node itself if it's in shared summary
			ret.join(node, CstFactory.f().genTrue());
		} else {
			Set<MemLoc> locs = node.getLocs();
			assert (locs.size() == 1) : locs;
			MemLoc loc = locs.iterator().next();
			MemLocP2Set p2set = MInstnNodeFactory.f().get(loc);
			Iterator<MemLoc> it = p2set.iterator();
			while (it.hasNext()) {
				MemLoc instnLoc = it.next();
				MemNode instnNode = MemNodeFactory.f().get(callerGraph,
						instnLoc);
				BoolExpr cst = p2set.get(instnLoc);
				ret.join(instnNode, cst);
			}
		}
		if (G.assertion) {
			assert (!nodeCache1.containsKey(node)) : node;
		}
		if (SummariesEnv.v().instnSkip) {
			P2Set curr = nodeCache1.get(node);
			status.put(node, ret.equals(curr));
			nodeCache1.put(node, ret);
		} else {
			nodeCache1.put(node, ret);
		}
	}

	/* write erasable cache2 */
	private void instn2(MemNode node, P2Set ret) {
		assert (node.getMemGraph() != Env.v().shared) : node;
		Set<MemLoc> locs = node.getLocs();
		assert (locs.size() == 1) : locs;
		MemLoc loc = locs.iterator().next();
		MemLocP2Set p2set = MInstnNodeFactory.f().get(loc);
		Iterator<MemLoc> it = p2set.iterator();
		while (it.hasNext()) {
			MemLoc instnLoc = it.next();
			MemNode instnNode = MemNodeFactory.f().get(callerGraph, instnLoc);
			BoolExpr cst = p2set.get(instnLoc);
			if (SummariesEnv.v().typeObserver) {
				if (instnNode.isAPNode()) {
					Set<jq_Type> tfs = node.getTypeFilter();
					if (Env.v().multiCallees) {
						for (jq_Type tf : tfs) {
							instnNode.addTypeFilter(tf);
						}
					} else {
						// for (jq_Type tf : tfs) {
						// instnNode.addTypeFilter(tf);
						// }
						instnNode.joinTypeFilter(tfs);
					}
					ret.join(instnNode, cst);
				} else if (instnNode.isAllocNode()) {
					Set<jq_Type> instnTypes = instnNode.getTypes();
					assert (instnTypes.size() == 1) : instnTypes;
					jq_Type instnType = instnTypes.iterator().next();
					Set<jq_Type> tfs = node.getTypeFilter();
					if (TypeHelper.h().isSubType(instnType, tfs)) {
						ret.join(instnNode, cst);
					}
				} else {
					assert false : node + " --> " + instnNode;
				}
			} else {
				ret.join(instnNode, cst);

			}
		}
		if (SummariesEnv.v().instnSkip) {
			P2Set curr = nodeCache2.get(node);
			status.put(node, ret.equals(curr));
			nodeCache2.put(node, ret);
		} else {
			nodeCache2.put(node, ret);
		}
	}

	/* write non-erasable locCache1 */
	private void instn1(MemLoc loc, MemLocP2Set ret) {
		if (loc instanceof ParamLoc) {
			instnParamLoc((ParamLoc) loc, ret);
		} else if (loc instanceof RetLoc) {
			instnRetLoc((RetLoc) loc, ret);
		} else if (loc instanceof AllocLoc) {
			instnAllocLoc((AllocLoc) loc, ret);
		} else {
			assert false : loc;
		}
		if (G.assertion) {
			assert (!locCache1.containsKey(loc)) : loc;
		}
		locCache1.put(loc, ret);
	}

	/* write erasable locCache2 */
	private void instn2(MemLoc loc, MemLocP2Set ret) {
		if (loc instanceof LocalAccessPathLoc) {
			instnLocalAPLoc((LocalAccessPathLoc) loc, ret);
		} else {
			assert false : loc;
		}
		locCache2.put(loc, ret);
	}

	/* ---------- low-level instantiation engines ---------- */
	private void instnParamLoc(ParamLoc formal, MemLocP2Set ret) {
		assert (basicMapping.containsKey(formal)) : formal;
		AbsMemLoc actual = basicMapping.get(formal);
		if (actual instanceof PrimitiveLoc) {
			// do not fill ret
		} else if (actual instanceof MemLoc) {
			assert (actual instanceof ParamLoc)
					|| (actual instanceof LocalVarLoc) : actual;
			ret.join((MemLoc) actual, CstFactory.f().genTrue());
		} else {
			assert false : formal;
		}
	}

	private void instnRetLoc(RetLoc retLoc, MemLocP2Set ret) {
		AbsMemLoc lhsValue = basicMapping.get(retLoc);
		if (lhsValue == null) {
			// do not fill ret
		} else {
			if (lhsValue instanceof PrimitiveLoc) {
				// do not fill ret
			} else if (lhsValue instanceof MemLoc) {
				ret.join((MemLoc) lhsValue, CstFactory.f().genTrue());
			} else {
				assert false : retLoc;
			}
		}
	}

	private void instnAllocLoc(AllocLoc alloc, MemLocP2Set ret) {
		int ctxtLength = alloc.ctxtLength();
		Quad site = alloc.getSite();
		jq_Type type = alloc.getType();
		int maxLength = Env.v().getAllocDepth(site, type);
		if (SummariesEnv.v().ctxtCtrl) {
			if (SummariesEnv.v().shareSum) {
				if (SummariesEnv.v().allocDepth != 0 && ctxtLength >= maxLength) {
					ret.join(alloc, CstFactory.f().genTrue());
				} else if (ChordUtil.isLibMeth(caller)) {
					ret.join(alloc, CstFactory.f().genTrue());
				} else {
					ProgPoint point = ProgPointFactory.f().get(callsite);
					AllocLoc instnAlloc = Env.v().getAllocLoc(alloc, point);
					ret.join(instnAlloc, CstFactory.f().genTrue());
				}
			} else {
				if (SummariesEnv.v().allocDepth != 0 && ctxtLength >= maxLength) {
					ret.join(alloc, CstFactory.f().genTrue());
				} else if (ChordUtil.isLibMeth(caller)) {
					ret.join(alloc, CstFactory.f().genTrue());
				} else {
					ProgPoint point = ProgPointFactory.f().get(callsite);
					AllocLoc instnAlloc = Env.v().getAllocLoc(alloc, point);
					ret.join(instnAlloc, CstFactory.f().genTrue());
				}
			}
		} else {
			if (SummariesEnv.v().allocDepth != 0 && ctxtLength >= maxLength) {
				ret.join(alloc, CstFactory.f().genTrue());
			} else {
				ProgPoint point = ProgPointFactory.f().get(callsite);
				AllocLoc instnAlloc = Env.v().getAllocLoc(alloc, point);
				ret.join(instnAlloc, CstFactory.f().genTrue());
			}
		}
	}

	private void instnLocalAPLoc(LocalAccessPathLoc lap, MemLocP2Set ret) {
		if (Env.v().isWidening()) {
			MemLoc inner = lap.getInner();
			Set<FieldSelector> smasheds = lap.getSmashedFields();
			FieldSelector outer = lap.getOuter();
			MemLocP2Set srcCstPairs = MInstnNodeFactory.f().get(inner);
			assert (srcCstPairs != null) : lap + " " + inner;
			/* collect direct results */
			Set<MemNode> wl = new HashSet<MemNode>();
			for (MemLoc src : srcCstPairs.keySet()) {
				MemNode srcNode = MemNodeFactory.f().get(callerGraph, src);
				P2Set p2set = controller.lookup(srcNode, outer);
				for (MemNode instnNode : p2set.keySet()) {
					BoolExpr instnCst = CstFactory.f().genTrue();
					Set<MemLoc> instnLocs = instnNode.getLocs();
					assert (instnLocs.size() == 1) : instnNode;
					MemLoc instnLoc = instnLocs.iterator().next();
					// add to results
					ret.join(instnLoc, instnCst);
					// add to work-list for transitive closure
					wl.add(instnNode);
				}
			}
			/* check transitive closure */
			if (smasheds.isEmpty()) {
				return;
			}
			if (!G.doTranClosure) {
				return;
			}
			/* do transitive closure */
			Set<MemNode> visited = new HashSet<MemNode>();
			while (!wl.isEmpty()) {
				MemNode curr = wl.iterator().next();
				visited.add(curr);
				wl.remove(curr);
				/* add into results */
				P2Set p2set = controller.lookup(curr, outer);
				for (MemNode instnNode : p2set.keySet()) {
					Set<MemLoc> instnLocs = instnNode.getLocs();
					assert (instnLocs.size() == 1) : instnLocs;
					MemLoc instnLoc = instnLocs.iterator().next();
					BoolExpr instnCst = CstFactory.f().genTrue();
					if (ret.containsKey(instnLoc)) {
						continue;
					}
					ret.join(instnLoc, instnCst);
				}
				/* add into work-list */
				for (FieldSelector smashed : smasheds) {
					P2Set p2set1 = controller.lookup(curr, smashed);
					for (MemNode wlNode : p2set1.keySet()) {
						if (visited.contains(wlNode) || wl.contains(wlNode)) {
							continue;
						}
						wl.add(wlNode);
					}
				}
			}
		} else {
			MemLoc inner = lap.getInner();
			Set<FieldSelector> smasheds = lap.getSmashedFields();
			FieldSelector outer = lap.getOuter();
			MemLocP2Set srcCstPairs = MInstnNodeFactory.f().get(inner);
			assert (srcCstPairs != null) : lap + " " + inner;
			/* collect direct results */
			P2Set wl = new P2Set();
			for (MemLoc src : srcCstPairs.keySet()) {
				MemNode srcNode = MemNodeFactory.f().get(callerGraph, src);
				BoolExpr cst = srcCstPairs.get(src);
				P2Set p2set = controller.lookup(srcNode, outer);
				for (MemNode instnNode : p2set.keySet()) {
					BoolExpr cst1 = p2set.get(instnNode);
					BoolExpr instnCst = CstFactory.f().intersect(cst, cst1);
					Set<MemLoc> instnLocs = instnNode.getLocs();
					assert (instnLocs.size() == 1) : instnNode;
					MemLoc instnLoc = instnLocs.iterator().next();
					// add to results
					ret.join(instnLoc, instnCst);
					// add to work-list for transitive closure
					wl.join(instnNode, instnCst);
				}
			}
			/* check transitive closure */
			if (smasheds.isEmpty()) {
				return;
			}
			if (!G.doTranClosure) {
				return;
			}
			/* do transitive closure */
			P2Set visited = new P2Set();
			while (!wl.isEmpty()) {
				MemNode curr = wl.iterator().next();
				BoolExpr cst = wl.get(curr);
				visited.join(curr, cst);
				wl.remove(curr);
				/* add into results */
				P2Set p2set = controller.lookup(curr, outer);
				for (MemNode instnNode : p2set.keySet()) {
					Set<MemLoc> instnLocs = instnNode.getLocs();
					assert (instnLocs.size() == 1) : instnLocs;
					MemLoc instnLoc = instnLocs.iterator().next();
					BoolExpr cst1 = p2set.get(instnNode);
					BoolExpr instnCst = CstFactory.f().intersect(cst, cst1);
					if (ret.contains(instnLoc, instnCst)) {
						continue;
					}
					ret.join(instnLoc, instnCst);
				}
				/* add into work-list */
				for (FieldSelector smashed : smasheds) {
					P2Set p2set1 = controller.lookup(curr, smashed);
					for (MemNode wlNode : p2set1.keySet()) {
						BoolExpr cst1 = p2set1.get(wlNode);
						BoolExpr wlCst = CstFactory.f().intersect(cst, cst1);
						if (visited.contains(wlNode, wlCst)
								|| wl.contains(wlNode, wlCst)) {
							continue;
						}
						wl.join(wlNode, wlCst);
					}
				}
			}
		}
	}
}