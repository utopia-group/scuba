package framework.scuba.domain.memgraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CIObj;
import framework.scuba.controller.SMemGraphController;
import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.field.IndexFieldSelector;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.G;

// memory graph for shared summary
public class SMemGraph extends MemGraph {

	// nodes created using CIPA
	protected final Set<MemNode> cipaNodes = new HashSet<MemNode>();
	// nodes created on-demand
	protected final vMap constructed = new vMap();

	// associated with global access paths
	protected final Set<MemEdge> sumEdges1 = new HashSet<MemEdge>();
	// only need to instantiate once
	protected final Set<MemEdge> sumEdges2 = new HashSet<MemEdge>();

	protected class vMap {
		final Map<MemNode, Set<FieldSelector>> map = new HashMap<MemNode, Set<FieldSelector>>();

		void add(MemNode node, FieldSelector field) {
			Set<FieldSelector> curr = map.get(node);
			if (curr == null) {
				curr = new HashSet<FieldSelector>();
				map.put(node, curr);
			}
			curr.add(field);
		}

		boolean contains(MemNode node, FieldSelector field) {
			Set<FieldSelector> curr = map.get(node);
			if (curr == null) {
				return false;
			}
			return curr.contains(field);
		}

		Iterator<MemNode> iterator() {
			return map.keySet().iterator();
		}

		Set<FieldSelector> get(MemNode node) {
			return map.get(node);
		}

		Set<MemNode> keySet() {
			return map.keySet();
		}
	}

	public SMemGraph(SMemGraphController controller) {
		super(controller);
	}

	public void addCIPANode(MemNode node) {
		cipaNodes.add(node);
	}

	public boolean hasCIPANode(MemNode node) {
		return cipaNodes.contains(node);
	}

	public boolean hasConstructed(MemNode node, FieldSelector field) {
		assert (cipaNodes.contains(node)) : node + " " + field;
		return constructed.contains(node, field);
	}

	public void construct(MemNode src, FieldSelector field) {
		G.constructNum++;
		Set<MemLoc> locs = src.getLocs();
		assert (locs.size() == 1) : locs;
		MemLoc loc = locs.iterator().next();
		assert (loc instanceof AllocLoc) : loc;
		AllocLoc alloc = (AllocLoc) loc;
		Quad srcSite = alloc.getSite();
		Set<Quad> wrapper = new HashSet<Quad>();
		wrapper.add(srcSite);
		CIObj o1 = new CIObj(wrapper);
		assert (field instanceof RegFieldSelector || field instanceof IndexFieldSelector) : field;
		CIObj o2 = null;
		if (field instanceof RegFieldSelector) {
			jq_Field f = ((RegFieldSelector) field).getField();
			if (!Env.v().reachableFields.contains(f)) {
				return;
			}
			o2 = Env.v().getCIPA().pointsTo(o1, f);
		} else if (field instanceof IndexFieldSelector) {
			o2 = Env.v().getCIPA().pointsTo(o1, null);
		}
		assert (o2 != null) : src + " " + field;
		for (Quad site : o2.pts) {
			MemNode tgt = Env.v().getAllocNode(site);
			Env.v().shared.addCIPANode(tgt);
			src.weakUpdate(field, tgt, CstFactory.f().genTrue());
		}
		constructed.add(src, field);
		if (G.collect) {
			System.out.println("reading " + G.constructNum);
			G.readDetails.append("(" + G.constructNum + "). " + "read " + "["
					+ src + ", " + field + "]" + "\n");
			G.readDetails.append("SRC quad is in method : "
					+ srcSite.getMethod() + "\n");
			G.readDetails.append("Analyzing method : "
					+ Env.v().currCtrlor.getMemGraph().getMethod() + "\n");
			G.readDetails
					.append("----------------------------------------------\n");
		}
	}

	public void addConstructed(MemNode node, FieldSelector field) {
		constructed.add(node, field);
	}

	public int getCIPANodesNum() {
		return cipaNodes.size();
	}

	// --------- MemGraph -----------
	@Override
	public jq_Method getMethod() {
		return null;
	}

	// @Override
	// public void addSumEdge(MemEdge edge) {
	// assert false : edge;
	// }

	// @Override
	// public Iterator<MemEdge> sumEdgesIterator() {
	// assert false;
	// return null;
	// }

	public void addSum1Edge(MemEdge edge) {
		sumEdges1.add(edge);
	}

	public void addSum2Edge(MemEdge edge) {
		sumEdges2.add(edge);
	}

	public Iterator<MemEdge> sumEdges1Iterator() {
		return sumEdges1.iterator();
	}

	public Iterator<MemEdge> sumEdges2Iterator() {
		return sumEdges2.iterator();
	}
}