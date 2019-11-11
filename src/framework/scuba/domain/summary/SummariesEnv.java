package framework.scuba.domain.summary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Trio;
import framework.scuba.analyses.alias.ReportManager;
import framework.scuba.controller.CMemGraphController;
import framework.scuba.controller.S1MMemGraphController;
import framework.scuba.controller.S2MMemGraphController;
import framework.scuba.controller.S3MMemGraphController;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.S1MMemGraph;
import framework.scuba.domain.memgraph.S2MMemGraph;
import framework.scuba.domain.memgraph.S3MMemGraph;

public class SummariesEnv {

	private static SummariesEnv instance = new SummariesEnv();

	// final P2Set report
	public ReportManager rm;

	protected final Map<jq_Method, MMemGraph> mMemGraphs = new HashMap<jq_Method, MMemGraph>();
	public CMemGraph conclusion;

	public static enum PropType {
		ALL, NOLOCAL, APPLOCALs, FORMALs;
	}

	public static enum FieldSmashLevel {
		REG;
	}

	// a node will first be marked as prop-node, then app-node if not
	public static enum MemNodeType {
		PROP_LOCAL, APP_LOCAL, LIB_LOCAL, PARAM, RET, GLOBAL, LOCAL_AP, GLOBAL_AP, ALLOC;
	}

	public static enum MemEdgeType {

	}

	// Standard: only run experiments on Scuba
	// Comparison: run experiments on both Scuba and Chord
	public static enum ExpMode {
		Standard, Comparison;
	}

	// location types
	public static enum LocType {
		PARAMETER, LOCAL_VAR, GLOBAL, RET, LOCAL_AP, GLOBAL_AP, ALLOC;
	}

	// field merge types
	public static enum FieldMergeType {
		NO_MERGE, NAME_ME;
	}

	// a field classification used for generating access paths
	public static enum FieldType {
		BACK, STAY, FORWARD;
	}

	public static SummariesEnv v() {
		return instance;
	}

	/* ================ Begin Analysis configuration ================ */
	/* ---------------------- fixed parameters ----------------------- */
	// true: only smash for loops and SCC
	protected boolean smashCtrl = true;
	// a more fine-grained weak update cache
	public boolean weakUpdateCache = true;
	// smash level
	public FieldSmashLevel smashLevel = FieldSmashLevel.REG;
	// which kind of local need to be propagated
	public SummariesEnv.PropType propType = SummariesEnv.PropType.FORMALs;
	// analyzing scc with respect to the call relationship
	public boolean orderScc = true;
	// instantiation skipping
	public boolean instnSkip = true;
	// do some heuristics when we need widening. e.g. for constraints
	public boolean useWidening = true;
	// use the CIPA to filter fields of a memory location
	public boolean cipaFiltering = true;
	// merge fields
	public FieldMergeType fieldMergeType = FieldMergeType.NO_MERGE;
	// field classification
	public boolean classifyFields = true;
	// share summary
	public boolean shareSum = true;
	// append context only when caller is application method
	// true will cause LONGER running time
	public boolean ctxtCtrl = true;
	// use a different way to do instantiation
	public boolean autoInstn = true;
	// a type observer
	public boolean typeObserver = true;
	// share allocations for exceptions
	public boolean shareExceptionAllocs = true;
	/* ------------------- tunable parameters -------------------- */
	// true: use constraints
	public boolean disableCst = false;
	// default allocation depth, 0 means infinity
	public int allocDepth = 3;
	// default levels to propagate locals
	public int propLevel = 2;
	// the default number for duplicated fields
	public int dup = 1;
	// the # of SCC from which to begin to use shared summary
	public int lift = 3500;
	// manually construct a shared summary for some uninteresting methods
	public boolean cipaSum = true;
	// whether using models for containers
	public boolean containerModel = false;
	// clean duplicate ap like r0.e.header.previous.next where previous and next
	// are smashed
	public boolean cleanDupAccessPath = false;
	/* ---------------- experimental parameters ----------------- */
	// Run experiment with or w/o comparison?
	protected ExpMode expMode = ExpMode.Comparison;
	// timeout in seconds
	public int timeout = 1800;
	/* ================ End configuration ================ */

	// all reachable methods
	protected Set<jq_Method> reachableMethods = new HashSet<jq_Method>();
	// all library methods
	protected Set<jq_Method> libMeths = new HashSet<jq_Method>();
	// alias pairs
	protected LinkedHashSet<Trio<jq_Method, Register, Register>> aliasPairs = new LinkedHashSet<Trio<jq_Method, Register, Register>>();

	public ExpMode getExpMode() {
		return expMode;
	}

	public void setExpMode(ExpMode val) {
		expMode = val;
	}

	public void turnOffCst(boolean val) {
		disableCst = val;
	}

	public int timeout() {
		return timeout;
	}

	public void setAllocDepth(int val) {
		allocDepth = val;
	}

	public MMemGraph getMMemGraph(jq_Method meth) {
		return mMemGraphs.get(meth);
	}

	public Iterator<MMemGraph> iterator() {
		return mMemGraphs.values().iterator();
	}

	public Map<jq_Method, MMemGraph> getMemGraphs() {
		return mMemGraphs;
	}

	public CMemGraph initConclusion(MMemGraph mainMemGraph,
			Set<MMemGraph> clinits, CMemGraphController controller) {
		conclusion = new CMemGraph(mainMemGraph, clinits, controller);
		return conclusion;
	}

	public MMemGraph initS1MMemGraph(jq_Method m, S1MMemGraphController ctrlor) {
		MMemGraph ret = mMemGraphs.get(m);
		if (ret == null) {
			ret = new S1MMemGraph(m, ctrlor);
			mMemGraphs.put(m, ret);
		}
		return ret;
	}

	public MMemGraph initS2MMemGraph(jq_Method m, S2MMemGraphController ctrlor) {
		MMemGraph ret = mMemGraphs.get(m);
		if (ret == null) {
			ret = new S2MMemGraph(m, ctrlor);
			mMemGraphs.put(m, ret);
		}
		return ret;
	}

	public MMemGraph initS3MMemGraph(jq_Method m, S3MMemGraphController ctrlor) {
		MMemGraph ret = mMemGraphs.get(m);
		if (ret == null) {
			ret = new S3MMemGraph(m, ctrlor);
			mMemGraphs.put(m, ret);
		}
		return ret;
	}

	public Set<jq_Method> getReachableMethods() {
		return reachableMethods;
	}

	public void setReachableMethods(Set<jq_Method> reachableMethods) {
		this.reachableMethods = reachableMethods;
	}

	public Set<jq_Method> getLibMeths() {
		return libMeths;
	}

	public void setLibMeths(Set<jq_Method> libMeths) {
		this.libMeths = libMeths;
	}

	public void addAliasPairs(jq_Method m, Register r1, Register r2) {
		Trio<jq_Method, Register, Register> trio = new Trio<jq_Method, Register, Register>(
				m, r1, r2);
		aliasPairs.add(trio);
	}

	public Set<Trio<jq_Method, Register, Register>> getAliasPairs() {
		return aliasPairs;
	}
}