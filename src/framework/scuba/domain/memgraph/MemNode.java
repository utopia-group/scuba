package framework.scuba.domain.memgraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.MemEdgeFactory;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.Numberable;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;
import framework.scuba.helper.MemLocHelper;

public class MemNode extends AbsMemNode implements Numberable {

	protected int number;
	protected final MemGraph memGraph;

	/* update followings when adding a location */
	// a memory node can represent more than one memory location
	protected final Set<MemLoc> locations = new HashSet<MemLoc>();
	// all types of locations this node represents
	protected final Set<jq_Type> types = new HashSet<jq_Type>();
	// the fields this memory node has
	protected final Set<FieldSelector> fields = new HashSet<FieldSelector>();
	// this node has a field selector because of which locations
	protected final Map<FieldSelector, Set<MemLoc>> fieldToLocs = new HashMap<FieldSelector, Set<MemLoc>>();
	// attributes of this node
	protected SummariesEnv.MemNodeType nodeType = null;
	// length of this node (maximum of all locations wrapped in this node)
	protected int length = 0;

	/* update followings when calling weakUpdate(*) */
	// a normal way to represent the outgoing nodes & edges in a graph
	protected final Map<MemGraph, Edges> outgoingEdges = new HashMap<MemGraph, Edges>();
	protected final Map<MemGraph, Nodes> outgoingNodes = new HashMap<MemGraph, Nodes>();
	// nodes that this node points to via some field
	protected final Map<MemGraph, Fields> outgoingFields = new HashMap<MemGraph, Fields>();
	// just used to return iterators
	private final Set<MemEdge> set1 = new HashSet<MemEdge>();
	// used for locals
	protected int currPropLevel = 0;
	// a type filter
	protected Set<jq_Type> typeFilter = new HashSet<jq_Type>();

	protected class Edges {
		final Set<MemEdge> set = new HashSet<MemEdge>();

		void add(MemEdge edge) {
			set.add(edge);
		}

		Iterator<MemEdge> iterator() {
			return set.iterator();
		}

		Set<MemEdge> keySet() {
			return set;
		}

		boolean contains(MemEdge edge) {
			return set.contains(edge);
		}

		MemNode get(MemEdge edge) {
			return edge.getTgt();
		}
	}

	protected class Nodes {
		final Map<MemNode, Set<MemEdge>> map = new HashMap<MemNode, Set<MemEdge>>();

		void add(MemNode node, MemEdge edge) {
			Set<MemEdge> curr = map.get(node);
			if (curr == null) {
				curr = new HashSet<MemEdge>();
				map.put(node, curr);
			}
			curr.add(edge);
		}

		Iterator<MemNode> iterator() {
			return map.keySet().iterator();
		}

		Set<MemNode> keySet() {
			return map.keySet();
		}

		Set<MemEdge> get(MemNode node) {
			return map.get(node);
		}
	}

	protected class Fields {
		final Map<FieldSelector, Set<MemEdge>> map = new HashMap<FieldSelector, Set<MemEdge>>();

		void add(FieldSelector field, MemEdge edge) {
			Set<MemEdge> curr = map.get(field);
			if (curr == null) {
				curr = new HashSet<MemEdge>();
				map.put(field, curr);
			}
			curr.add(edge);
		}

		Iterator<FieldSelector> iterator() {
			return map.keySet().iterator();
		}

		Iterator<MemEdge> iterator(FieldSelector field) {
			Set<MemEdge> edges = map.get(field);
			if (edges == null) {
				return set1.iterator();
			} else {
				return edges.iterator();
			}
		}

		Set<FieldSelector> keySet() {
			return map.keySet();
		}

		Set<MemEdge> get(FieldSelector field) {
			return map.get(field);
		}
	}

	public MemNode(MemGraph memGraph, MemLoc location, int number) {
		this.memGraph = memGraph;
		assert (locations != null);
		addLoc(location);
		setNumber(number);
		initMaps();
	}

	private void initMaps() {
		outgoingEdges.put(memGraph, new Edges());
		outgoingNodes.put(memGraph, new Nodes());
		outgoingFields.put(memGraph, new Fields());
	}

	public MemGraph getMemGraph() {
		return memGraph;
	}

	public Set<MemLoc> getLocs() {
		return locations;
	}

	public SummariesEnv.MemNodeType getNodeType() {
		return nodeType;
	}

	public int getLength() {
		return length;
	}

	public Set<FieldSelector> getFields() {
		return fields;
	}

	public Set<jq_Type> getTypes() {
		return types;
	}

	public void add1Type(jq_Type type) {
		types.add(type);
		if (SummariesEnv.v().typeObserver) {
			addTypeFilter(type);
		}
	}

	public void add1Field(MemLoc loc, FieldSelector field) {
		fields.add(field);
		Set<MemLoc> locs = fieldToLocs.get(field);
		if (locs == null) {
			locs = new HashSet<MemLoc>();
			fieldToLocs.put(field, locs);
		}
		locs.add(loc);
	}

	// MOST important method
	public void addLoc(MemLoc loc) {
		if (locations.contains(loc)) {
			return;
		}
		// add into locations
		locations.add(loc);
		// update types
		Set<jq_Type> locTypes = MemLocHelper.h().getTypes(loc);
		for (jq_Type locType : locTypes) {
			add1Type(locType);
		}
		// update fields & fieldToLocs
		Set<FieldSelector> fs = loc.getFields();
		fields.addAll(fs);
		for (FieldSelector f : fs) {
			Set<MemLoc> locs = fieldToLocs.get(f);
			if (locs == null) {
				locs = new HashSet<MemLoc>();
				fieldToLocs.put(f, locs);
			}
			locs.add(loc);
		}
		// set attributes
		setAttrs(loc);
		// update length
		setLength(loc.getLength());
	}

	protected void setLength(int length) {
		this.length = Math.max(length, this.length);
	}

	protected void setAttrs(MemLoc loc) {
		if (loc instanceof ParamLoc) {
			assert (nodeType == null) : nodeType + " " + loc;
			nodeType = SummariesEnv.MemNodeType.PARAM;
		} else if (loc instanceof RetLoc) {
			assert (nodeType == null) : nodeType + " " + loc;
			nodeType = SummariesEnv.MemNodeType.RET;
		} else if (loc instanceof GlobalLoc) {
			assert (nodeType == null) : nodeType + " " + loc;
			nodeType = SummariesEnv.MemNodeType.GLOBAL;
		} else if (loc instanceof LocalAccessPathLoc) {
			if (nodeType == null) {
				nodeType = SummariesEnv.MemNodeType.LOCAL_AP;
			} else {
				assert false : nodeType + " " + loc;
			}
		} else if (loc instanceof GlobalAccessPathLoc) {
			if (nodeType == null) {
				nodeType = SummariesEnv.MemNodeType.GLOBAL_AP;
			} else {
				assert false : nodeType + " " + loc;
			}
		} else if (loc instanceof AllocLoc) {
			if (nodeType == null) {
				nodeType = SummariesEnv.MemNodeType.ALLOC;
			} else {
				assert false : nodeType + " " + loc;
			}
		} else if (loc instanceof LocalVarLoc) {
			Register r = ((LocalVarLoc) loc).getRegister();
			if (Env.v().isPropLocal(r)) {
				assert (nodeType == null) : nodeType + " " + loc;
				nodeType = SummariesEnv.MemNodeType.PROP_LOCAL;
			} else if (Env.v().isAppLocal(r)) {
				assert (nodeType == null) : nodeType + " " + loc;
				nodeType = SummariesEnv.MemNodeType.APP_LOCAL;
			} else {
				assert (nodeType == null) : nodeType + " " + loc;
				nodeType = SummariesEnv.MemNodeType.LIB_LOCAL;
			}
		}
	}

	public boolean isParamNode() {
		return (nodeType == SummariesEnv.MemNodeType.PARAM);
	}

	public boolean isRetNode() {
		return (nodeType == SummariesEnv.MemNodeType.RET);
	}

	public boolean isGlobalNode() {
		return (nodeType == SummariesEnv.MemNodeType.GLOBAL);
	}

	public boolean isAllocNode() {
		return (nodeType == SummariesEnv.MemNodeType.ALLOC);
	}

	public boolean isLocalAPNode() {
		return (nodeType == SummariesEnv.MemNodeType.LOCAL_AP);
	}

	public boolean isGlobalAPNode() {
		return (nodeType == SummariesEnv.MemNodeType.GLOBAL_AP);
	}

	public boolean isAPNode() {
		return (nodeType == SummariesEnv.MemNodeType.GLOBAL_AP)
				|| (nodeType == SummariesEnv.MemNodeType.LOCAL_AP);
	}

	public boolean isPropLocalVarNode() {
		return (nodeType == SummariesEnv.MemNodeType.PROP_LOCAL);
	}

	public boolean isAppLocalVarNode() {
		return (nodeType == SummariesEnv.MemNodeType.APP_LOCAL);
	}

	public boolean isLibLocalVarNode() {
		return (nodeType == SummariesEnv.MemNodeType.LIB_LOCAL);
	}

	public boolean isLocalVarNode() {
		return (nodeType == SummariesEnv.MemNodeType.PROP_LOCAL)
				|| (nodeType == SummariesEnv.MemNodeType.APP_LOCAL)
				|| (nodeType == SummariesEnv.MemNodeType.LIB_LOCAL);
	}

	public Iterator<MemGraph> outgoingGraphsIterator() {
		if (G.assertion) {
			assert (outgoingEdges.keySet().equals(outgoingNodes.keySet()));
			assert (outgoingEdges.keySet().equals(outgoingFields.keySet()));
		}
		return outgoingEdges.keySet().iterator();
	}

	public Iterator<MemEdge> outgoingEdgesIterator() {
		return outgoingEdgesIterator(memGraph);
	}

	public Iterator<MemEdge> outgoingEdgesIterator(MemGraph graph) {
		Edges edges = outgoingEdges.get(graph);
		assert (edges != null);
		return edges.iterator();
	}

	public Iterator<MemNode> outgoingNodesIterator() {
		return outgoingNodesIterator(memGraph);
	}

	public Iterator<MemNode> outgoingNodesIterator(MemGraph graph) {
		Nodes nodes = outgoingNodes.get(graph);
		assert (nodes != null);
		return nodes.iterator();
	}

	public Iterator<MemEdge> outgoingEdgesIterator(FieldSelector field) {
		assert (memGraph instanceof CMemGraph);
		return outgoingEdgesIterator(memGraph, field);
	}

	public Iterator<MemEdge> outgoingEdgesIterator(MemGraph graph,
			FieldSelector field) {
		Fields fields = outgoingFields.get(graph);
		assert (fields != null);
		return fields.iterator(field);
	}

	public P2Set getP2Set(FieldSelector field) {
		P2Set ret = null;
		if (SummariesEnv.v().shareSum) {
			if (memGraph instanceof CMemGraph) {
				ret = getP2Set(field, memGraph);
			} else if (memGraph instanceof SMemGraph) {
				assert (memGraph == Env.v().shared) : memGraph.getClass();
				ret = getP2Set(field, Env.v().shared);
				if (Env.v().currCtrlor.getMemGraph() != Env.v().shared) {
					ret.join(getP2Set(field, Env.v().currCtrlor.getMemGraph()));
				}
			} else if (memGraph instanceof MMemGraph) {
				ret = getP2Set(field, memGraph);
				ret.join(getP2Set(field, Env.v().shared));
			} else {
				assert false : memGraph.getClass();
			}
		} else {
			ret = getP2Set(field, memGraph);
		}
		return ret;
	}

	/* this is the *only* method that read the P2Set of this node */
	public P2Set getP2Set(FieldSelector field, MemGraph graph) {
		P2Set ret = new P2Set();
		// construct the P2Set of this cipa node in shared summary
		if (Env.v().shared.hasCIPANode(this) && graph == Env.v().shared) {
			if (!Env.v().shared.hasConstructed(this, field)) {
				Env.v().shared.construct(this, field);
			}
		}
		// read the P2Set of this node
		Fields fieldToEdges = outgoingFields.get(graph);
		if (fieldToEdges == null) {
			return ret;
		}
		Set<MemEdge> edges = fieldToEdges.get(field);
		if (edges == null) {
			return ret;
		}
		for (MemEdge edge : edges) {
			BoolExpr cst = edge.getCst();
			MemNode tgt = edge.getTgt();
			assert (!ret.containsKey(tgt)) : this + " " + field + " " + tgt;
			ret.put(tgt, cst);
		}
		return ret;
	}

	// return true means that we need to filter(ignore) that update
	protected boolean notUpdate(FieldSelector field, MemNode node, BoolExpr cst) {
		boolean ret = true;

		// default edge cleanup
		Set<MemLoc> tgtLocs = node.getLocs();
		assert (tgtLocs.size() == 1) : tgtLocs;
		MemLoc tgtLoc = tgtLocs.iterator().next();
		assert (locations.size() == 1) : locations;
		MemLoc srcLoc = locations.iterator().next();
		if (tgtLoc instanceof AccessPathObject
				&& srcLoc instanceof AccessPathObject) {
			AccessPathObject tgtAP = (AccessPathObject) tgtLoc;
			if (!tgtAP.isSmashed()) {
				FieldSelector outer = tgtAP.getOuter();
				MemLoc inner = tgtAP.getInner();
				if (inner.equals(srcLoc) && outer.equals(field)) {
					return ret;
				}
			}
		}

		// false constraint filter
		if (CstFactory.f().eqFalse(cst)) {
			return true;
		}
		/* collecting evidence to not filter */
		Set<MemLoc> locs = fieldToLocs.get(field);
		// field filter
		// updating only when at least on location has the field
		if (locs == null) {
			return true;
		}

		// sub type filter
		// updating only when the target is sub-type of the source
		for (MemLoc myLoc : locs) {
			for (MemLoc loc : node.getLocs()) {
				if (loc instanceof AccessPathObject) {
					// FIXME
					if (MemLocHelper.h().isTypeCompatible(myLoc, field, loc)) {
						// if (MemLocHelper.h().isSubType(myLoc, field, loc)) {
						// not filter
						ret = false;
						break;
					} else {
						if (G.warning) {
							System.out.println("[Warning] "
									+ "AccessPathLoc in-compatible.");
						}
					}
				} else if (loc instanceof AllocLoc) {
					if (MemLocHelper.h().isSubType(myLoc, field, loc)) {
						// not filter
						ret = false;
						break;
					} else {
						if (G.warning) {
							System.out.println("[Warning] "
									+ "AllocLoc not sub-type.");
						}
					}
				} else {
					assert false : loc;
				}
			}
			if (!ret) {
				break;
			}
		}

		return ret;
	}

	public static int count = 0;

	protected void addAnEdge(MemEdge edge) {
		MemNode tgt = edge.getTgt();
		MemGraph graph = tgt.getMemGraph();
		FieldSelector field = edge.getField();
		if (G.collect) {
			if (memGraph instanceof S1MMemGraph
					&& Env.v().shared.hasCIPANode(tgt)) {
				AllocLoc tgtLoc = (AllocLoc) tgt.getLocs().iterator().next();
				if (Env.v().extendExceptionOrError(tgtLoc.getType())) {
					// do not record this
				} else {
					count++;
					System.out.println("connecting " + count);
					G.readDetails.append("[" + count + "]" + "connect " + this
							+ " --> " + tgt + " (" + field + ")" + "\n");
					if (isAllocNode()) {
						MemLoc loc = locations.iterator().next();
						Quad site = ((AllocLoc) loc).getSite();
						G.readDetails.append("SRC quad is in method : "
								+ site.getMethod() + "\n");
					}
					G.readDetails.append("Analyzing method : "
							+ Env.v().currCtrlor.getMemGraph().getMethod()
							+ "\n");
					G.readDetails
							.append("----------------------------------------------\n");
				}
			}
		}
		// update outgoing edges
		Edges edges = outgoingEdges.get(graph);
		if (edges == null) {
			edges = new Edges();
			outgoingEdges.put(graph, edges);
		}
		edges.add(edge);
		// update outgoing nodes
		Nodes nodes = outgoingNodes.get(graph);
		if (nodes == null) {
			nodes = new Nodes();
			outgoingNodes.put(graph, nodes);
		}
		nodes.add(tgt, edge);
		// update outgoing fields
		Fields fieldToEdges = outgoingFields.get(graph);
		if (fieldToEdges == null) {
			fieldToEdges = new Fields();
			outgoingFields.put(graph, fieldToEdges);
		}
		fieldToEdges.add(field, edge);
	}

	/* this is the *only* method that can write the P2Set of this node */
	public boolean weakUpdate(FieldSelector field, MemNode node, BoolExpr cst) {
		boolean ret = false;
		// weak update filtering
		if (notUpdate(field, node, cst)) {
			return ret;
		}
		// weak update
		MemEdge edge = MemEdgeFactory.f().get(this, field, node, cst);
		assert (edge.getMemGraph() == Env.v().currCtrlor.getMemGraph() || edge
				.getMemGraph() == Env.v().shared) : edge;
		MemGraph graph = node.getMemGraph();
		Edges edges = outgoingEdges.get(graph);
		if (edges == null) {
			addAnEdge(edge);
			ret = true;
		} else {
			if (edges.contains(edge)) {
				BoolExpr curr = edge.getCst();
				BoolExpr next = CstFactory.f().union(curr, cst);
				if (!CstFactory.f().isEq(curr, next)) {
					edge.setCst(next);
					ret = true;
				}
			} else {
				addAnEdge(edge);
				ret = true;
			}
		}
		// set whether this method's summary is changed
		if (ret && edge.getMemGraph() == Env.v().currCtrlor.getMemGraph()) {
			if (isLibLocalVarNode()) {

			} else {
				Env.v().intraProc.sumChanged = true;
			}
		}
		if (SummariesEnv.v().typeObserver) {
			if (this.isLocalVarNode() && node.isAPNode()
					&& edge.getCst().equals(CstFactory.f().genTrue())) {
				assert (types.size() == 1) : types;
				jq_Type tf = types.iterator().next();
				if (Env.v().isBadLocal(this)) {
					node.joinTypeFilter(types);
				} else {
					node.refTypeFilter(tf);
				}
			}
		}
		return ret;
	}

	public boolean weakUpdate(FieldSelector field, P2Set p2Set) {
		boolean ret = false;
		for (MemNode node : p2Set.keySet()) {
			BoolExpr cst = p2Set.get(node);
			ret = ret | weakUpdate(field, node, cst);
		}
		return ret;
	}

	public void setCurrPropLevel(int level) {
		currPropLevel = level;
	}

	public int getCurrPropLevel() {
		return currPropLevel;
	}

	public boolean toProp() {
		assert (locations.size() == 1) : locations;
		MemLoc location = locations.iterator().next();
		assert (location instanceof LocalVarLoc) : location;
		int maxLevel = Env.v().getLocalPropLevel((LocalVarLoc) location);
		return (currPropLevel <= maxLevel);
	}

	public String shortName() {
		StringBuilder ret = new StringBuilder();
		ret.append("[");
		for (MemLoc loc : locations) {
			ret.append(loc.shortName() + " ");
		}
		ret.append("]");
		return ret.toString();
	}

	public void cleanTypeFilter() {
		if (typeFilter.size() > 1) {
			typeFilter.remove(Program.g().getClass("java.lang.Object"));
		}
	}

	public void addTypeFilter(jq_Type type) {
		typeFilter.add(type);
	}

	// for intra-proc
	public void refTypeFilter(jq_Type type) {
		Set<jq_Type> rms = new HashSet<jq_Type>();
		for (jq_Type tf : typeFilter) {
			if (type.isSubtypeOf(tf) && !tf.equals(type)) {
				rms.add(tf);
			}
		}
		if (!rms.isEmpty()) {
			typeFilter.removeAll(rms);
			typeFilter.add(type);
		}
		assert (!typeFilter.isEmpty()) : this;
	}

	// for inter-proc
	public void joinTypeFilter(Set<jq_Type> types) {
		Set<jq_Type> toJoin = new HashSet<jq_Type>();
		for (jq_Type type : types) {
			boolean join = false;
			for (jq_Type tf : typeFilter) {
				if (!tf.isSubtypeOf(type)) {
					join = true;
					break;
				}
			}
			if (join) {
				toJoin.add(type);
			}
		}
		for (jq_Type type : toJoin) {
			addTypeFilter(type);
		}
		// assert (!typeFilter.isEmpty()) : this;
	}

	public Set<jq_Type> getTypeFilter() {
		Set<jq_Type> ret = new HashSet<jq_Type>();
		ret.addAll(typeFilter);
		assert (!ret.isEmpty()) : this;
		return ret;
	}

	// -------------- Regular -------------
	@Override
	public int hashCode() {
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		return (memGraph == Env.v().shared ? 1 : 0) + " "
				+ locations.toString() + " " + typeFilter;
	}

	// -------------- Numberable ----------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

}
