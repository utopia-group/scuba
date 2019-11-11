package framework.scuba.domain.instn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Compiler.Quad.RegisterFactory.Register;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.CMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2Set;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.G;

public class CInstnNodeFactory {

	private static CInstnNodeFactory instance = new CInstnNodeFactory();

	public static CInstnNodeFactory f() {
		return instance;
	}

	protected CInstnNodeSubFactory currNodeInstn;
	protected final Map<MemGraph, CInstnNodeSubFactory> nodeInstns = new HashMap<MemGraph, CInstnNodeSubFactory>();

	protected CMemGraph conclusion;
	protected CMemGraphController controller;
	// 1. globals 2. allocations 3. locals 4. global access paths
	protected final Map<MemLoc, MemLocP2Set> locsInstnCache = new HashMap<MemLoc, MemLocP2Set>();

	public void initFactory(CMemGraph conclusion, CMemGraphController controller) {
		this.conclusion = conclusion;
		this.controller = controller;
	}

	public void initSubFactory(MemGraph conclusion, MemGraph calleeGraph) {
		currNodeInstn = nodeInstns.get(calleeGraph);
		if (currNodeInstn == null) {
			currNodeInstn = new CInstnNodeSubFactory(conclusion, calleeGraph);
			nodeInstns.put(calleeGraph, currNodeInstn);
		}
	}

	public void setEverything(CMemGraphController controller) {
		assert (currNodeInstn != null) : "Initializing before setting everything";
		currNodeInstn.setEverything(controller);
	}

	public void loadAndInit() {
		currNodeInstn.loadAndInit();
	}

	public void refresh() {
		currNodeInstn.refresh();
	}

	public P2Set get(MemNode node) {
		return currNodeInstn.get(node);
	}

	private MemLocP2Set get(MemLoc loc) {
		MemLocP2Set ret = locsInstnCache.get(loc);
		if (ret == null) {
			ret = instn(loc);
		}
		assert (ret != null) : loc;
		return ret;
	}

	/* instantiate and update locsInstnCache1 */
	protected MemLocP2Set instn(MemLoc loc) {
		MemLocP2Set ret = new MemLocP2Set();
		if (loc instanceof LocalVarLoc) {
			if (G.assertion) {
				Register r = ((LocalVarLoc) loc).getRegister();
				assert (Env.v().isPropLocal(r)) : r + " "
						+ Env.v().getMethodByReg(r);
			}
			instnLocalVarLoc((LocalVarLoc) loc, ret);
			locsInstnCache.put(loc, ret);
		} else if (loc instanceof GlobalLoc) {
			instnGlobalLoc((GlobalLoc) loc, ret);
			locsInstnCache.put(loc, ret);
		} else if (loc instanceof AllocLoc) {
			instnAllocLoc((AllocLoc) loc, ret);
			locsInstnCache.put(loc, ret);
		} else if (loc instanceof GlobalAccessPathLoc) {
			instnGlobalAPLoc((GlobalAccessPathLoc) loc, ret);
			locsInstnCache.put(loc, ret);
		} else {
			assert false : loc;
		}
		return ret;
	}

	/* locsInstnCache1 */
	private void instnLocalVarLoc(LocalVarLoc local, MemLocP2Set ret) {
		ret.join(local, CstFactory.f().genTrue());
	}

	private void instnGlobalLoc(GlobalLoc global, MemLocP2Set ret) {
		ret.join(global, CstFactory.f().genTrue());
	}

	private void instnAllocLoc(AllocLoc alloc, MemLocP2Set ret) {
		ret.join(alloc, CstFactory.f().genTrue());
	}

	/* locsInstnCache2 */
	private void instnGlobalAPLoc(GlobalAccessPathLoc ap, MemLocP2Set ret) {
		MemLoc inner = ap.getInner();
		Set<FieldSelector> smasheds = ap.getSmashedFields();
		FieldSelector outer = ap.getOuter();
		MemLocP2Set srcCstPairs = null;
		if (inner instanceof GlobalAccessPathLoc) {
			srcCstPairs = instn(inner);
		} else if ((inner instanceof AllocLoc)
				|| (inner instanceof LocalVarLoc || (inner instanceof GlobalLoc))) {
			srcCstPairs = get(inner);
		}
		assert (srcCstPairs != null) : inner;
		for (MemLoc src : srcCstPairs.keySet()) {
			MemNode srcNode = MemNodeFactory.f().get(conclusion, src);
			BoolExpr cst = srcCstPairs.get(src);
			P2Set p2set = controller.lookup(srcNode, outer);
			for (MemNode instnNode : p2set.keySet()) {
				BoolExpr cst1 = p2set.get(instnNode);
				BoolExpr instnCst = CstFactory.f().intersect(cst, cst1);
				Set<MemLoc> instnLocs = instnNode.getLocs();
				assert (instnLocs.size() == 1) : instnNode;
				MemLoc instnLoc = instnLocs.iterator().next();
				/* save time */
				// TODO
				if (ret.containsKey(instnLoc)) {
					continue;
				}
				// add to results
				ret.join(instnLoc, instnCst);
				/* check transitive closure */
				if (smasheds.isEmpty()) {
					continue;
				}
				/* do transitive closure */
				P2Set wl = new P2Set(instnNode, instnCst);
				Set<MemNode> visited = new HashSet<MemNode>();
				while (!wl.isEmpty()) {
					MemNode curr = wl.iterator().next();
					BoolExpr cst2 = wl.get(curr);
					wl.remove(curr);
					visited.add(curr);
					// collect results
					P2Set p2set1 = controller.lookup(curr, outer);
					for (MemNode instnNode1 : p2set1.keySet()) {
						BoolExpr cst3 = p2set1.get(instnNode1);
						BoolExpr instnCst1 = CstFactory.f().intersect(cst2,
								cst3);
						Set<MemLoc> instnLocs1 = instnNode1.getLocs();
						assert (instnLocs1.size() == 1) : instnNode1;
						MemLoc instnLoc1 = instnLocs1.iterator().next();
						ret.join(instnLoc1, instnCst1);
					}
					// add candidates to wl
					for (FieldSelector smashed : smasheds) {
						P2Set p2set2 = controller.lookup(curr, smashed);
						for (MemNode node : p2set2.keySet()) {
							if (visited.contains(node) || wl.containsKey(node)) {
								continue;
							}
							BoolExpr cst3 = p2set2.get(node);
							BoolExpr cst4 = CstFactory.f()
									.intersect(cst2, cst3);
							wl.join(node, cst4);
						}
					}
				}
			}
		}
	}
}
