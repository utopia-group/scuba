package framework.scuba.domain.summary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.program.Program;
import chord.util.tuple.object.Pair;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.controller.MemGraphController;
import framework.scuba.domain.context.Ctxt;
import framework.scuba.domain.context.ProgPoint;
import framework.scuba.domain.factories.AllocLocFactory;
import framework.scuba.domain.factories.ContextFactory;
import framework.scuba.domain.factories.EpsilonFieldSelectorFactory;
import framework.scuba.domain.factories.GlobalAPLocFactory;
import framework.scuba.domain.factories.GlobalLocFactory;
import framework.scuba.domain.factories.IndexFieldSelectorFactory;
import framework.scuba.domain.factories.LocalAPLocFactory;
import framework.scuba.domain.factories.LocalVarLocFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.factories.MergedFieldSelectorFactory;
import framework.scuba.domain.factories.ParamLocFactory;
import framework.scuba.domain.factories.PrimitiveLocFactory;
import framework.scuba.domain.factories.ProgPointFactory;
import framework.scuba.domain.factories.RegFieldSelectorFactory;
import framework.scuba.domain.factories.RetLocFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.field.IndexFieldSelector;
import framework.scuba.domain.field.MergedFieldSelector;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.PrimitiveLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.memgraph.SMemGraph;
import framework.scuba.helper.MemLocHelper;

public class Env {

	private static Env instance = new Env();

	public static Env v() {
		return instance;
	}

	/*-------------- Scuba environment --------------*/
	// instance of context-insensitive pointer analysis
	private CIPAAnalysis cipa;
	// call graph instance
	public CICG cg;
	// used for parameter propagation
	public ScubaQuadVisitor qv;
	// locals that will appear in the conclusion as stack objects
	public final Set<Register> propLocals = new HashSet<Register>();
	// application locals whose point-to sets will be queried
	public final Set<Register> appLocals = new HashSet<Register>();
	// field --> all possible dynamic types
	private final Map<jq_Field, Set<jq_Type>> fieldToTypes = new HashMap<jq_Field, Set<jq_Type>>();
	// type --> fields of all sub-types (including itself)
	public final Map<jq_Class, Set<jq_Field>> typeToFields = new HashMap<jq_Class, Set<jq_Field>>();

	// used for getDefault(R0.\e, f/i), use null to represent idx field
	public final Map<Register, Set<jq_Field>> formalToFields = new HashMap<Register, Set<jq_Field>>();
	// used for getDefault(R0.\e.f, g/i)
	public final Map<jq_Field, Set<jq_Field>> fieldToFields = new HashMap<jq_Field, Set<jq_Field>>();
	// used for getDefault(R0.\e.i, f/i)
	public final Map<Register, Set<jq_Field>> formalIndexToFields = new HashMap<Register, Set<jq_Field>>();
	// used for getDefault(R0.\e.f.\i, g/i)
	public final Map<jq_Field, Set<jq_Field>> fieldIndexToFields = new HashMap<jq_Field, Set<jq_Field>>();
	// used for getFields of allocations (not very necessary)
	public final Map<Quad, Set<jq_Field>> allocToFields = new HashMap<Quad, Set<jq_Field>>();

	// register --> p2set of CIPA
	public final Map<Register, CIObj> regToCipaP2Set = new HashMap<Register, CIObj>();
	// register --> method it is created in
	public final Map<Register, jq_Method> regToMethod = new HashMap<Register, jq_Method>();
	// statements where we do not do recursive field smashing
	public final Set<Quad> notSmashStmts = new HashSet<Quad>();
	// current graph controller
	public MemGraphController currCtrlor;
	// shared summary
	public SMemGraph shared;
	// duplicated fields mapping, 0 means infinity (not smash)
	public final Map<FieldSelector, Integer> dupFields = new HashMap<FieldSelector, Integer>();
	// allocation depth mapping, 0 means infinity
	public final Map<Pair<Quad, jq_Type>, Integer> allocDepth = new HashMap<Pair<Quad, jq_Type>, Integer>();
	// local propagation level mapping, -1 means not propagating
	public final Map<LocalVarLoc, Integer> localPropLevel = new HashMap<LocalVarLoc, Integer>();
	private final Pair<Quad, jq_Type> wrapper = new Pair<Quad, jq_Type>(null,
			null);
	// fields we want to merge
	// each element in the set is a set of fields we want to merge
	// e.g. [(f1, f2), (f3, f4, f5)]
	// the intersection of any two sets in <clusters> should be empty
	public final Set<Set<jq_Field>> fieldClusters = new HashSet<Set<jq_Field>>();
	public final Set<jq_Field> mergedFields = new HashSet<jq_Field>();
	// classify fields
	public final Map<jq_Field, SummariesEnv.FieldType> fieldTypes = new HashMap<jq_Field, SummariesEnv.FieldType>();

	// reachable fields by CIPA
	public final Set<jq_Field> reachableFields = new HashSet<jq_Field>();
	public final Map<jq_Class, List<jq_Class>> classToSubclasses = new HashMap<jq_Class, List<jq_Class>>();
	public final Map<jq_Class, Integer> class2Term = new HashMap<jq_Class, Integer>();
	public final Map<jq_Class, Integer> class2Min = new HashMap<jq_Class, Integer>();
	public final Map<Integer, jq_Class> class2TermRev = new HashMap<Integer, jq_Class>();
	// scuba point-to result for locals
	public final ScubaResult scubaResult = new ScubaResult();
	// memory graph for conclusion
	protected CMemGraph conclusion;
	// in scc or not.
	protected boolean inSCC = false;
	public boolean multiCallees = true;
	public Set<MemNode> badLocals = new HashSet<MemNode>();

	public jq_Class excpetion = (jq_Class) Program.g().getClass(
			"java.lang.Exception");
	public jq_Class error = (jq_Class) Program.g().getClass("java.lang.Error");
	// allocations which will be put in the shared summary (VERY careful!)
	public final Set<AllocLoc> sharedAllocs = new HashSet<AllocLoc>();
	public final Set<MemLoc> sharedMemLocs = new HashSet<MemLoc>();
	// registers to their own R0's (this pointer)
	public final Map<Register, Register> regToR0 = new HashMap<Register, Register>();
	public final Map<jq_Method, Register> methToR0 = new HashMap<jq_Method, Register>();

	// CIPA summary
	public final Set<jq_Method> s1 = new HashSet<jq_Method>();
	public final Set<jq_Method> s2 = new HashSet<jq_Method>();
	public final Set<jq_Method> s3 = new HashSet<jq_Method>();

	public IntraProcSumAnalysis intraProc;
	// whether it is doing widening on constraints
	public boolean widening = false;

	public void setConclusion(CMemGraph conclusion) {
		this.conclusion = conclusion;
	}

	/*-------------- call graph and class related APIs--------------*/
	public int getConstTerm4Class(jq_Class cls) {
		return class2Term.get(cls);
	}

	public void put(Map<jq_Class, List<jq_Class>> m, jq_Class key,
			jq_Class value) {
		List<jq_Class> l = m.get(key);
		if (l == null) {
			m.put(key, l = new ArrayList<jq_Class>());
		}
		l.add(value);
	}

	public void buildClassHierarchy() {
		/* First build the inverse maps */
		for (jq_Reference r : Program.g().getClasses()) {
			if (r instanceof jq_Array)
				continue;
			final jq_Class cl = (jq_Class) r;
			if (!cl.isInterface() && (cl.getSuperclass() != null))
				put(classToSubclasses, cl.getSuperclass(), cl);
		}
		/* Now do a post order traversal to get the numbers */
		jq_Reference rootObj = Program.g().getClass("java.lang.Object");
		assert rootObj != null : "Fails to load java.lang.Object";
		pfsVisit(1, (jq_Class) rootObj);
		/* pre-compute all the type intervals */
		for (jq_Reference r : Program.g().getClasses())
			if (r instanceof jq_Class) {
				jq_Class rcls = (jq_Class) r;
				if (!rcls.isInterface())
					getMinSubclass(rcls);
			}

	}

	public int getMinSubclass(jq_Class clz) {
		if (class2Min.get(clz) != null)
			return class2Min.get(clz);

		jq_Class[] subClaz = clz.getSubClasses();
		if (subClaz.length == 0) {
			int self = class2Term.get(clz);
			class2Min.put(clz, self);
			return self;
		}

		int min = class2Term.get(clz);
		LinkedHashSet<jq_Class> wl = new LinkedHashSet<jq_Class>();
		wl.add(clz);
		while (!wl.isEmpty()) {
			jq_Class sub = wl.iterator().next();
			wl.remove(sub);
			int subInt = class2Term.get(sub);
			// subclass is unreachable.
			if (class2Term.get(sub) == null)
				continue;

			if (subInt < min)
				min = subInt;

			jq_Class[] succs = sub.getSubClasses();
			for (int i = 0; i < succs.length; i++) {
				jq_Class succ = succs[i];
				if (class2Term.get(succ) != null)
					wl.add(succ);
			}
		}
		// cache the result.
		class2Min.put(clz, min);
		return min;
	}

	public Set<jq_Class> getAllSubclasses(jq_Class cls) {
		Set<jq_Class> subclasses = new HashSet<jq_Class>();
		if (cls.isInterface()) {
			subclasses.add(cls);
			return subclasses;
		}
		int self = class2Term.get(cls);
		int min = class2Min.get(cls);
		for (int i = min; i <= self; i++)
			subclasses.add(class2TermRev.get(i));

		assert !subclasses.isEmpty();
		return subclasses;
	}

	public List<jq_Class> getSuccessors(jq_Class clz) {
		return classToSubclasses.get(clz);
	}

	public jq_Field getFieldByName(String name) {
		for (jq_Field f : reachableFields) {
			if (f.toString().equals(name))
				return f;
		}
		// assert false : "Unreachable field:" + name;
		return null;
	}

	protected int pfsVisit(int start, jq_Class c) {
		if (classToSubclasses.get(c) != null) {
			for (jq_Class subCls : classToSubclasses.get(c)) {
				if (subCls.isInterface())
					continue;
				start = pfsVisit(start, subCls);
			}
		}
		if (c.isInterface()) {
			throw new RuntimeException("Attempt to pfs visit interface " + c);
		}
		class2Term.put(c, start);
		class2TermRev.put(start, c);

		start++;
		return start;
	}

	public void setCIPA(CIPAAnalysis pa) {
		cipa = pa;
	}

	public CIPAAnalysis getCIPA() {
		return cipa;
	}

	public CIObj findCIP2Set(Register r) {
		CIObj ret = regToCipaP2Set.get(r);
		if (ret == null) {
			ret = cipa.pointsTo(r);
			regToCipaP2Set.put(r, ret);
		}
		return ret;
	}

	public void setScc(boolean flag) {
		inSCC = flag;
		if (SummariesEnv.v().useWidening) {
			if (inSCC) {
				setWidening(true);
			} else {
				setWidening(false);
			}
		}
	}

	public boolean inSCC() {
		return inSCC;
	}

	/*-------------- fieldToTypes APIs --------------*/
	public void fillFieldToTypesMapping(jq_Field key, Set<jq_Type> types) {
		fieldToTypes.put(key, types);
	}

	public Set<jq_Type> getTypesByField(jq_Field key) {
		assert fieldToTypes.containsKey(key) : key;
		return fieldToTypes.get(key);
	}

	/*-------------- regToMethod APIs --------------*/
	public void fillRegToMethodMapping(Register r, jq_Method m) {
		regToMethod.put(r, m);
	}

	public jq_Method getMethodByReg(Register r) {
		return regToMethod.get(r);
	}

	/*-------------- typeToFields APIs --------------*/
	public void putTypeToFields(jq_Class type, Set<jq_Field> fields) {
		assert (!typeToFields.containsKey(type)) : type;
		typeToFields.put(type, fields);
	}

	public Set<jq_Field> getFieldsFromType(jq_Class type) {
		assert (typeToFields.containsKey(type)) : type;
		return typeToFields.get(type);
	}

	/*-------------- memory node factory APIs--------------*/
	public MemNode getLocalVarNode(jq_Method meth, Register r, jq_Type type,
			MMemGraph memGraph) {
		return MemNodeFactory.f().get(memGraph, getLocalVarLoc(r, meth, type));
	}

	public MemNode getParamNode(jq_Method meth, Register r, jq_Type type,
			MMemGraph memGraph) {
		return MemNodeFactory.f().get(memGraph, getParamLoc(r, meth, type));
	}

	public MemNode getGlobalNode(MMemGraph memGraph, jq_Field staticField) {
		return MemNodeFactory.f().get(memGraph, getGlobalLoc(staticField));
	}

	public MemNode getAllocNode(MMemGraph memGraph, Quad stmt, jq_Type type) {
		return MemNodeFactory.f().get(memGraph, getAllocLoc(stmt, type));
	}

	public MemNode getRetNode(jq_Method meth, MMemGraph memGraph) {
		return MemNodeFactory.f().get(memGraph, getRetLoc(meth));
	}

	public PrimitiveLoc getPrimitiveLoc() {
		return PrimitiveLocFactory.f().get();
	}

	/* ----------------- memory location factory APIs ------------------ */
	public Ctxt getContext(ProgPoint point, Ctxt prevCtx) {
		return ContextFactory.f().get(point, prevCtx);
	}

	public ProgPoint getProgPoint(Quad stmt) {
		return ProgPointFactory.f().get(stmt);
	}

	public LocalVarLoc getLocalVarLoc(Register r, jq_Method meth, jq_Type type) {
		return LocalVarLocFactory.f().get(r, meth, type);
	}

	public LocalVarLoc findLocalVarLoc(Register r) {
		return LocalVarLocFactory.f().get(r);
	}

	public RetLoc getRetLoc(jq_Method meth) {
		return RetLocFactory.f().get(meth);
	}

	public GlobalLoc getGlobalLoc(jq_Field global) {
		return GlobalLocFactory.f().get(global);
	}

	public ParamLoc getParamLoc(Register r, jq_Method meth, jq_Type type) {
		return ParamLocFactory.f().get(r, meth, type);
	}

	// this is used for new statement
	public AllocLoc getAllocLoc(Quad stmt, jq_Type type) {
		// ProgPoint point = getProgPoint(stmt);
		Ctxt ctx = ContextFactory.f().get(null, null);
		return AllocLocFactory.f().get(stmt, type, ctx);
	}

	// this is used when instantiating an allocation node
	public AllocLoc getAllocLoc(AllocLoc alloc, ProgPoint point) {
		Quad stmt = alloc.getSite();
		Set<jq_Type> types = MemLocHelper.h().getTypes(alloc);
		assert (types.size() == 1) : types + " " + alloc;
		jq_Type type = types.iterator().next();
		Ctxt prevCtxt = alloc.getContext();
		Ctxt ctxt = ContextFactory.f().get(point, prevCtxt);
		return AllocLocFactory.f().get(stmt, type, ctxt);
	}

	public LocalAccessPathLoc getLocalAPLoc(MemLoc inner, FieldSelector outer) {
		return LocalAPLocFactory.f().get(inner, outer);
	}

	public GlobalAccessPathLoc getGlobalAPLoc(MemLoc inner, FieldSelector outer) {
		return GlobalAPLocFactory.f().get(inner, outer);
	}

	public EpsilonFieldSelector getEpsilonFieldSelector() {
		return EpsilonFieldSelectorFactory.f().get();
	}

	public IndexFieldSelector getIndexFieldSelector() {
		return IndexFieldSelectorFactory.f().get();
	}

	public MergedFieldSelector getMergedFieldSelector(jq_Field field) {
		return MergedFieldSelectorFactory.f().get(field);
	}

	public RegFieldSelector getRegFieldSelector(jq_Field field) {
		return RegFieldSelectorFactory.f().get(field);
	}

	// ---------- propagation set -------------
	public void addPropSet(Register v) {
		propLocals.add(v);
	}

	public void delPropSet(Register v) {
		propLocals.remove(v);
	}

	public void addAllPropSet(Set<Register> v) {
		propLocals.addAll(v);
	}

	public boolean isPropLocal(Register v) {
		return propLocals.contains(v);
	}

	public Set<Register> getProps() {
		return propLocals;
	}

	public Iterator<Register> propLocalsIterator() {
		return propLocals.iterator();
	}

	// ---------- application locals --------------
	public boolean isAppLocal(Register v) {
		return appLocals.contains(v);
	}

	public void addAllAppLocalsSet(Set<Register> v) {
		appLocals.addAll(v);
	}

	public Iterator<Register> appLocalsIterator() {
		return appLocals.iterator();
	}

	public Set<Register> getAppLocals() {
		return appLocals;
	}

	// ------- statements that we allow smashing recursive fields ---------
	public void addNotSmashStmt(Quad stmt) {
		notSmashStmts.add(stmt);
	}

	public void addNotSmashStmts(Set<Quad> stmts) {
		notSmashStmts.addAll(stmts);
	}

	public boolean isNotSmashStmt(Quad stmt) {
		return notSmashStmts.contains(stmt);
	}

	// ---------- some interpretation methods -----------
	public Set<AllocLoc> interpretAPLoc(AccessPathObject ap) {
		return interpretAPLoc(ap, ap.getSmashedFields());
	}

	public Set<AllocLoc> interpretAPLoc(AccessPathObject ap,
			Set<FieldSelector> smasheds) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		Set<MemNode> nodes = interpret(ap, smasheds);
		for (MemNode node : nodes) {
			Set<MemLoc> locs = node.getLocs();
			assert (locs.size() == 1) : locs;
			MemLoc loc = locs.iterator().next();
			ret.add((AllocLoc) loc);
		}
		return ret;
	}

	public Set<MemNode> interpret(AccessPathObject ap,
			Set<FieldSelector> smasheds) {
		Set<MemNode> ret = new HashSet<MemNode>();
		MemLoc inner = ap.getInner();
		FieldSelector outer = ap.getOuter();
		Set<FieldSelector> smasheds1 = ap.getSmashedFields();
		if (inner instanceof GlobalLoc) {
			MemNode node = MemNodeFactory.f().get(conclusion, inner);
			P2Set p2set = node.getP2Set(outer);
			assert (outer instanceof EpsilonFieldSelector) : ap;
			for (MemNode tgt : p2set.keySet()) {
				assert (tgt.isAllocNode()) : tgt;
				ret.add(tgt);
			}
		} else if (inner instanceof ParamLoc) {
			Register r = ((ParamLoc) inner).getRegister();
			LocalVarLoc local = LocalVarLocFactory.f().get(r);
			assert (local != null) : ap + " " + ((ParamLoc) inner).getMethod();
			MemNode node = MemNodeFactory.f().get(conclusion, local);
			P2Set p2set = node.getP2Set(outer);
			assert (outer instanceof EpsilonFieldSelector) : ap;
			for (MemNode tgt : p2set.keySet()) {
				assert (tgt.isAllocNode()) : tgt;
				ret.add(tgt);
			}
		} else if (inner instanceof AccessPathObject) {
			Set<MemNode> allocs = interpret((AccessPathObject) inner, smasheds1);
			for (MemNode alloc : allocs) {
				P2Set p2set = alloc.getP2Set(outer);
				for (MemNode tgt : p2set.keySet()) {
					assert (tgt.isAllocNode()) : tgt;
					ret.add(tgt);
				}
			}
			Set<MemNode> wl = new LinkedHashSet<MemNode>();
			Set<MemNode> visited = new HashSet<MemNode>();
			wl.addAll(ret);
			while (!wl.isEmpty()) {
				MemNode node = wl.iterator().next();
				wl.remove(node);
				visited.add(node);
				// add results
				Iterator<MemEdge> it = node.outgoingEdgesIterator(outer);
				while (it.hasNext()) {
					MemEdge edge = it.next();
					MemNode tgt = edge.getTgt();
					assert (tgt.isAllocNode()) : tgt;
					ret.add(tgt);
				}
				// do transitive closure
				for (FieldSelector smashed : smasheds) {
					Iterator<MemEdge> it1 = node.outgoingEdgesIterator(smashed);
					while (it1.hasNext()) {
						MemEdge edge = it1.next();
						MemNode tgt = edge.getTgt();
						if (!visited.contains(tgt)) {
							wl.add(tgt);
						}
					}
				}
			}
		} else {
			assert false;
		}
		return ret;
	}

	// -------- field merging -----------
	public void addMergedFields(Set<jq_Field> cluster) {
		fieldClusters.add(cluster);
		mergedFields.addAll(cluster);
	}

	public Iterator<Set<jq_Field>> fieldClustersIterator() {
		return fieldClusters.iterator();
	}

	public boolean isMergedField(jq_Field field) {
		return mergedFields.contains(field);
	}

	// -------- fine-grained getDefault filter -------------
	public void putFormalToFields(Register formal, Set<jq_Field> fields) {
		// (r0.e, f) --> VF
		formalToFields.put(formal, fields);
	}

	public void putFieldToFields(jq_Field field, Set<jq_Field> fields) {
		// (f, g) --> FF
		fieldToFields.put(field, fields);
	}

	public void putFormalIndexToFields(Register formal, Set<jq_Field> fields) {
		// (r0.e.i, f) --> VF + HF1H(F=i)
		formalIndexToFields.put(formal, fields);
	}

	public void putFieldIndexToFields(jq_Field field, Set<jq_Field> fields) {
		// (f.i, g) --> FF + HF1H(F=i)
		fieldIndexToFields.put(field, fields);
	}

	public void putAllocToFields(Quad alloc, Set<jq_Field> fields) {
		allocToFields.put(alloc, fields);
	}

	public Set<jq_Field> getFieldsOfFormal(Register formal) {
		return formalToFields.get(formal);
	}

	public Set<jq_Field> getFieldsOfField(jq_Field field) {
		return fieldToFields.get(field);
	}

	public Set<jq_Field> getFieldsOfFormalIndex(Register formal) {
		return formalIndexToFields.get(formal);
	}

	public Set<jq_Field> getFieldsOfFieldIndex(jq_Field field) {
		return fieldIndexToFields.get(field);
	}

	public Set<jq_Field> getFieldsOfAlloc(Quad alloc) {
		return allocToFields.get(alloc);
	}

	// ------------ field attributes ----------------
	public void putFieldType(jq_Field field, SummariesEnv.FieldType type) {
		fieldTypes.put(field, type);
	}

	public SummariesEnv.FieldType getFieldType(jq_Field field) {
		if (SummariesEnv.v().classifyFields) {
			return fieldTypes.get(field);
		} else {
			return SummariesEnv.FieldType.FORWARD;
		}
	}

	// ------------- java.lang.Exception or java.lang.Error -----------
	public boolean extendExceptionOrError(jq_Type t) {
		if (t instanceof jq_Class) {
			jq_Class clz = (jq_Class) t;
			return (getAllSubclasses(excpetion).contains(clz) || getAllSubclasses(
					error).contains(clz));
		}
		return false;
	}

	// ------------ duplicated fields --------------
	public void putDupField(jq_Field field, int dup) {
		dupFields.put(Env.v().getRegFieldSelector(field), dup);
	}

	public int getDupFiled(FieldSelector field) {
		if (dupFields.containsKey(field)) {
			return dupFields.get(field);
		} else {
			return SummariesEnv.v().dup;
		}
	}

	public void putAllocDepth(Quad site, jq_Type type, int length) {
		allocDepth.put(new Pair<Quad, jq_Type>(site, type), length);
	}

	public int getAllocDepth(Quad site, jq_Type type) {
		wrapper.val0 = site;
		wrapper.val1 = type;
		if (allocDepth.containsKey(wrapper)) {
			return allocDepth.get(wrapper);
		} else {
			return SummariesEnv.v().allocDepth;
		}
	}

	public void putLocalPropLevel(Register r, jq_Method meth, jq_Type type,
			int level) {
		localPropLevel.put(getLocalVarLoc(r, meth, type), level);
	}

	public int getLocalPropLevel(LocalVarLoc local) {
		if (localPropLevel.containsKey(local)) {
			return localPropLevel.get(local);
		} else {
			return SummariesEnv.v().propLevel;
		}
	}

	// ------------ shared allocations ------------
	public AllocLoc addSharedAlloc(Quad site, jq_Type type) {
		AllocLoc ret = getAllocLoc(site, type);
		sharedAllocs.add(ret);
		sharedMemLocs.add(ret);
		return ret;
	}

	public boolean isSharedAlloc(AllocLoc alloc) {
		return sharedAllocs.contains(alloc);
	}

	public void addSharedMemLoc(MemLoc loc) {
		sharedMemLocs.add(loc);
	}

	public boolean isSharedMemLoc(MemLoc loc) {
		return sharedMemLocs.contains(loc);
	}

	// ------------- register, methods and R0's ----------------
	public void putRegToR0(Register r, Register r0) {
		regToR0.put(r, r0);
	}

	public Register getR0ByReg(Register r) {
		return regToR0.get(r);
	}

	public void putMethToR0(jq_Method method, Register r0) {
		methToR0.put(method, r0);
	}

	public Register getR0ByMethod(jq_Method method) {
		return methToR0.get(method);
	}

	// ------------ CIPA summary ----------------
	public void addS1Method(jq_Method method) {
		s1.add(method);
	}

	public void addS2Method(jq_Method method) {
		s2.add(method);
	}

	public void addS3Method(jq_Method method) {
		s3.add(method);
	}

	public boolean isS1Method(jq_Method method) {
		return s1.contains(method);
	}

	public boolean isS2Method(jq_Method method) {
		return s2.contains(method);
	}

	public boolean isS3Method(jq_Method method) {
		return s3.contains(method);
	}

	public Iterator<jq_Method> s1Iterator() {
		return s1.iterator();
	}

	public Iterator<jq_Method> s2Iterator() {
		return s2.iterator();
	}

	public Iterator<jq_Method> s3Iterator() {
		return s3.iterator();
	}

	public MemNode getAllocNode(Quad site) {
		Operator op = site.getOperator();
		jq_Type type = null;
		if (op instanceof New) {
			type = New.getType(site).getType();
		} else if (op instanceof NewArray) {
			type = NewArray.getType(site).getType();
		} else if (op instanceof MultiNewArray) {
			type = MultiNewArray.getType(site).getType();
		} else {
			assert false : site;
		}
		AllocLoc alloc = Env.v().getAllocLoc(site, type);
		MemNode ret = MemNodeFactory.f().get(Env.v().shared, alloc);
		return ret;
	}

	// ---- bad local nodes -------
	public void addBadLocal(MemNode local) {
		badLocals.add(local);
	}

	public boolean isBadLocal(MemNode local) {
		return badLocals.contains(local);
	}

	// ---- widening -------
	public void setWidening(boolean widening) {
		this.widening = widening;
	}

	public boolean isWidening() {
		return widening;
	}
}