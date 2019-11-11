package framework.scuba.analyses.alias;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getfield.GETFIELD_A;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Getstatic.GETSTATIC_A;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putfield.PUTFIELD_A;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Putstatic.PUTSTATIC_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.alloc.DomH;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.RelView;
import chord.program.ClassHierarchy;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.controller.CMemGraphController;
import framework.scuba.controller.SMemGraphController;
import framework.scuba.domain.factories.GlobalAPLocFactory;
import framework.scuba.domain.factories.LocalAPLocFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.factories.MergedFieldSelectorFactory;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.SMemGraph;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.domain.summary.SummariesEnv.ExpMode;
import framework.scuba.domain.summary.SummariesEnv.PropType;
import framework.scuba.helper.Dumper;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper4CG;
import framework.scuba.helper.TypeHelper;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;
import framework.scuba.utils.OrderedComparator;
import framework.scuba.utils.StringUtil;

/**
 * Summary-based analysis. 1. Build and get a CHA-based CallGraph. 2. Compute
 * SCC 3. Run the worklist algorithm author: Yu Feng email: yufeng@cs.utexas.edu
 */

@Chord(name = "sum-java", consumes = { "rootM", "reachableM", "IM", "MM", "cha" })
public class SummaryBasedAnalysis extends JavaAnalysis {

	protected DomM domM;
	protected ProgramRel relRootM;
	protected ProgramRel relReachableM;
	protected ProgramRel relIM;
	protected ProgramRel relMM;
	protected ProgramRel relCHA;
	protected ProgramRel relDcm;
	protected ProgramRel relDVH;
	protected ProgramRel relAppLocal;
	protected ProgramRel relDcLocal;
	protected ProgramRel relLibM;
	protected ProgramRel relVH;
	protected ProgramRel relMV;
	protected ProgramRel relVValias;

	protected CICG callGraph;
	protected HashMap<Node, Set<jq_Method>> nodeToScc = new HashMap<Node, Set<jq_Method>>();
	protected HashMap<Set<jq_Method>, Node> sccToNode = new HashMap<Set<jq_Method>, Node>();
	protected HashMap<jq_Method, Node> methToNode = new HashMap<jq_Method, Node>();
	protected IntraProcSumAnalysis intraProc = new IntraProcSumAnalysis();
	protected CMemGraphController cController;
	protected SMemGraphController sController = new SMemGraphController();
	protected ReportManager querier;
	protected Set<String> dupFields = new HashSet<String>();

	/*------------- public APIs -------------*/
	public Set<AllocLoc> query(jq_Class clazz, jq_Method method,
			Register variable) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();

		return ret;
	}

	public void run() {
		Env.v().intraProc = this.intraProc;
		domM = (DomM) ClassicProject.g().getTrgt("M");
		relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
		relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
		relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
		relCHA = (ProgramRel) ClassicProject.g().getTrgt("cha");
		relMV = (ProgramRel) ClassicProject.g().getTrgt("MV");
		relVH = (ProgramRel) ClassicProject.g().getTrgt("VH");
		relAppLocal = (ProgramRel) ClassicProject.g().getTrgt("appV");
		relDcLocal = (ProgramRel) ClassicProject.g().getTrgt("DcLocal");
		relDcm = (ProgramRel) ClassicProject.g().getTrgt("dcm");
		relDVH = (ProgramRel) ClassicProject.g().getTrgt("dcmVH");
		relVValias = (ProgramRel) ClassicProject.g().getTrgt("VValias");

		if (!relReachableM.isOpen()) {
			relReachableM.load();
		}
		Iterable<jq_Method> resM = relReachableM.getAry1ValTuples();
		Set<jq_Method> reaches = SetUtils.iterableToSet(resM,
				relReachableM.size());
		SummariesEnv.v().setReachableMethods(reaches);
		// set up insensitive point-to analysis
		CIPAAnalysis cipa = (CIPAAnalysis) ClassicProject.g().getTask(
				"cipa-java");
		Env.v().setCIPA(cipa);
		// pass relCha ref to SummariesEnv
		Env.v().buildClassHierarchy();
		// initialize Scuba
		init();
	}

	/*------------- protected methods -------------*/
	protected void init() {
		long start3 = System.nanoTime();
		initPreProcess();
		long end3 = System.nanoTime();
		StringUtil.reportSec("Pre-process running time: ", start3, end3);

		long start = System.nanoTime();
		sumAnalyze();
		long end = System.nanoTime();
		System.out
				.println("================== Reporting running time ==================");
		StringUtil.reportSec("Sum-based analysis running time: ", start, end);

		LocalAPLocFactory.f().dumpIdToLoc("localAP");
		GlobalAPLocFactory.f().dumpIdToLoc("staticAP");
		Env.v().scubaResult.dumpToFile("scubaResult");

		if (SummariesEnv.v().shareSum) {
			setShared();
			genSumForShared();
		}

		long start1 = System.nanoTime();
		conclude();
		long end1 = System.nanoTime();
		StringUtil.reportSec("Conclusion running time: ", start1, end1);

		long start2 = System.nanoTime();
		genReport();
		long end2 = System.nanoTime();
		StringUtil.reportSec("Generation of report running time: ", start2,
				end2);

		StringUtil
				.reportInfo("----------------------------------------------------");
		StringUtil.reportInfo("Whole analysis time sketch: ");
		StringUtil.reportSec("Instantiation time", G.instnTime);
		StringUtil.reportSec("Intra-procedure analysis time", G.intraTime);
		StringUtil
				.reportInfo("----------------------------------------------------");
		StringUtil.reportInfo("Instantiation time sketch: ");
		StringUtil.reportSec("Instantiate node time", G.instnNodeTime);
		StringUtil.reportSec("Instantiate constraint time", G.instnCstTime);
		StringUtil.reportSec("Instantiate edge time", G.instnEdgeTime);
		StringUtil
				.reportInfo("----------------------------------------------------");
		StringUtil.reportInfo("Instantiation total wk " + G.totalWK);
		StringUtil.reportInfo("Instantiation wk " + G.wk);
		StringUtil.reportInfo("Instantiation done wk " + G.doneWK);
		StringUtil.reportInfo("Instantiation true done wk " + G.trueDoneWK);
		StringUtil.reportInfo("Hit " + G.hit);
		StringUtil.reportInfo("Hit node cache " + G.hitNodeCache);
		StringUtil.reportInfo("Hit cst cache " + G.hitCstCache);
		StringUtil.reportInfo("Hit hasType cache " + G.hitHashTypeCache);
		StringUtil.reportInfo("Hit edge cache " + G.hitEdgeCache);
		StringUtil
				.reportInfo("----------------------------------------------------");
		StringUtil.reportInfo("Construct allocations " + G.constructNum);
		StringUtil.reportInfo("CIPA nodes " + Env.v().shared.getCIPANodesNum());

		initPostProcess();
	}

	protected void initPreProcess() {
		/* set up Scuba environment */
		// new TaskTimer(SummariesEnv.v().timeout());
		initScubaArgs();
		getCallGraph();
		emptyP2SetFieldsFilter();
		fillField2TypesMap();
		extractAppLocals();
		extractPropLocals();
		extractNotSmashStmts();
		fillTypeToFieldsMapping();
		filterDefaultTgtByCIPA();
		fillReg2MethMapping();
		cleanPropLocals();
		mergeFields();
		if (SummariesEnv.v().shareSum) {
			initShared();
		}
		setDupFields();
		setAllocDepth();
		setLocalPropLevel();
		fillMethToR0Mapping();
		fillRegToR0Mapping();
		constructCIPASum();
	}

	private void constructCIPASum() {
		CIPASumConstructor constructor = new CIPASumConstructor();
		constructor.setEverything(callGraph);
		Env.v().currCtrlor = sController;
		constructor.run();
	}

	private void initShared() {
		Env.v().shared = new SMemGraph(sController);
		sController.setEverything(Env.v().shared);
		MemNodeFactory.f().init(Env.v().shared);
	}

	private void setShared() {
		Env.v().shared.setHasBeenAnalyzed();
		Env.v().shared.setTerminated();
	}

	private void genSumForShared() {
		sController.generateSummaryFromHeap();
	}

	protected void initPostProcess() {
		logCollectInfo();
		dumpCallGraph();
		if (SummariesEnv.v().getExpMode() == ExpMode.Comparison) {
			// perform points to set.
			new P2SetComparison(relVH, this).run();
			// perform downcast analysis
			new DowncastAnalysis(relDcm, relDVH, this).run();
			// May-alias analysis
			new MayAliasAnalysis(relMV, relVValias, this).run();
			// virtual call analysis.
			new VirtCallAnalysis(this).run();
		} else {// standard.
			new DowncastAnalysis(relDcm, relDVH, this).runScubaOnly();
			new MayAliasAnalysis(relMV, relVValias, this).runScubaOnly();
		}
		// regression test.
		new RegressionAnalysis(this).run();
		printSummary();
	}

	private void logCollectInfo() {
		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "readShared"));
			bufw.write(G.readDetails.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private void printSummary() {
		System.out
				.println("\n====================== Summary =======================");
		System.out.println("Analysis settings: ");
		System.out.println("[Alloc context length] "
				+ SummariesEnv.v().allocDepth);
		System.out.println("[Using constraints] "
				+ !SummariesEnv.v().disableCst);
		System.out.println("[Weak update cache] "
				+ SummariesEnv.v().weakUpdateCache);
		System.out
				.println("[Field smash level] " + SummariesEnv.v().smashLevel);
		System.out.println("[Propagation type] " + SummariesEnv.v().propType);
		System.out.println("[Local propagation level] "
				+ SummariesEnv.v().propLevel);
		System.out.println("[Order SCC] " + SummariesEnv.v().orderScc);
		System.out.println("[Instn skip] " + SummariesEnv.v().instnSkip);
		System.out.println("[Classify fields] "
				+ SummariesEnv.v().classifyFields);
		System.out.println("[Context control] " + SummariesEnv.v().ctxtCtrl);
		System.out.println("[Using widening] " + SummariesEnv.v().useWidening);
		System.out.println("[Using shared summary] "
				+ SummariesEnv.v().shareSum);
		calStmts();
	}
	
	private void calStmts() {
		int total = 0;
		for(jq_Method meth : callGraph.getNodes()) {
			total += meth.getCFG().getNumberOfQuads();
		}
		System.out.println("[Total quads:] " + total);
	}

	private void genReport() {
		SummariesEnv.v().rm = new ReportManager(SummariesEnv.v().conclusion);
		Iterator<Register> it = Env.v().propLocalsIterator();
		while (it.hasNext()) {
			Register r = it.next();
			SummariesEnv.v().rm.generate(r, relVH);
		}
		it = Env.v().appLocalsIterator();
		while (it.hasNext()) {
			Register r = it.next();
			if (!Env.v().isPropLocal(r)) {
				SummariesEnv.v().rm.generate(r, relVH);
			}
		}
	}

	protected void sumAnalyze() {
		Set<Node> worklist = new LinkedHashSet<Node>();
		sumAnalyzePreProcess(worklist);

		// for each leaf in the call graph, add them to the work-list
		Set<Node> visited = new HashSet<Node>();
		while (!worklist.isEmpty()) {
			Node worker = worklist.iterator().next();
			worklist.remove(worker);
			// each node will be visited exactly once.
			if (visited.contains(worker))
				continue;
			// now just analyze once.
			assert worker != null : "Worker can not be null";

			if (allTerminated(worker.getSuccessors())) {
				workOn(worker);
				visited.add(worker);
				// add m's preds to work-list
				worklist.addAll(worker.getPreds());
			} else {
				// append worker to the end of the List.class
				worklist.add(worker);
			}
		}
	}

	protected void sumAnalyzePreProcess(Set<Node> worklist) {
		Graph repGraph = collapseSCCs();

		if (G.tuning) {
			StringUtil.reportInfo("Total # of SCCs: "
					+ repGraph.getNodes().size());
			StringUtil.reportInfo("Total # of reachable Methods: "
					+ relReachableM.size());
		}

		for (Node methNode : repGraph.getNodes()) {
			if ((methNode.getSuccessors().size() == 0)) {
				assert methNode != null : "Entry can not be null";
				worklist.add(methNode);
			}
		}
	}

	/*---------------- init() related helpers -----------------*/
	private void initScubaArgs() {
		// init scuba based on command line info, if any.
		String heapLen = System.getProperty("chord.scuba.H");
		String expMode = System.getProperty("chord.scuba.expMode");
		String turnOffCst = System.getProperty("chord.scuba.cstOff");
		String proj = System.getProperty("chord.scuba.proj");

		if (heapLen != null)
			SummariesEnv.v().setAllocDepth(Integer.valueOf(heapLen));

		if (turnOffCst != null)
			SummariesEnv.v().turnOffCst(turnOffCst.equals("1") ? true : false);

		// FIXME: Put those codes in a config file instead of hard code.
		if (proj != null) {
			if ("luindex".equals(proj)) {
				SummariesEnv.v().propLevel = 2;
				SummariesEnv.v().lift = 5600;
				dupFields
						.add("table:[Ljava/util/HashMap$Entry;@java.util.HashMap");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable$Enumerator");
				dupFields
						.add("key:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.Hashtable$Entry");
				dupFields.add("key:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
				dupFields
						.add("element:Ljava/lang/Object;@java.util.LinkedList$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("elementData:[Ljava/lang/Object;@java.util.ArrayList");
			} else if ("antlr".equals(proj)) {
				SummariesEnv.v().dup = 2;
				SummariesEnv.v().propLevel = 50;
				dupFields
						.add("table:[Ljava/util/HashMap$Entry;@java.util.HashMap");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable$Enumerator");
				dupFields
						.add("key:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.HashMap$Entry");
				// dupFields
				// .add("value:Ljava/lang/Object;@java.util.Hashtable$Entry");
				dupFields.add("key:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
				dupFields
						.add("element:Ljava/lang/Object;@java.util.LinkedList$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("elementData:[Ljava/lang/Object;@java.util.ArrayList");

			} else if ("sunflow".equals(proj)) {
				SummariesEnv.v().cleanDupAccessPath = true;
				SummariesEnv.v().containerModel = true;
				SummariesEnv.v().propLevel = 2;
				SummariesEnv.v().lift = 7500;
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable");
				dupFields
						.add("entries:[Lorg/sunflow/util/FastHashMap$Entry;@org.sunflow.util.FastHashMap");
				dupFields
						.add("v:Ljava/lang/Object;@org.sunflow.util.FastHashMap$Entry");
				dupFields
						.add("queue:[Ljava/lang/Object;@java.util.PriorityQueue");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable$Enumerator");
				dupFields
						.add("key:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.Hashtable$Entry");
				dupFields.add("key:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
				dupFields
						.add("element:Ljava/lang/Object;@java.util.LinkedList$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
			} else if ("hedc".equals(proj) || "avrora".equals(proj)
					|| "toba-s".equals(proj)) {
				SummariesEnv.v().cleanDupAccessPath = true;
				SummariesEnv.v().containerModel = true;
				SummariesEnv.v().propLevel = 50;
				dupFields
						.add("table:[Ljava/util/HashMap$Entry;@java.util.HashMap");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable");
				dupFields
						.add("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable$Enumerator");
				dupFields
						.add("key:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.Hashtable$Entry");
				dupFields.add("key:Ljava/lang/Object;@java.util.HashMap$Entry");
				dupFields
						.add("header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
				dupFields
						.add("element:Ljava/lang/Object;@java.util.LinkedList$Entry");
				dupFields
						.add("value:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry");
				dupFields
						.add("elementData:[Ljava/lang/Object;@java.util.ArrayList");
			} else if ("hsqldb".equals(proj)) {
				SummariesEnv.v().containerModel = true;
				SummariesEnv.v().cleanDupAccessPath = true;
				SummariesEnv.v().propType = PropType.APPLOCALs;

				dupFields
						.add("schemaMap:Lorg/hsqldb/lib/HashMappedList;@org.hsqldb.SchemaManager");
				dupFields
						.add("objectValueTable:[Ljava/lang/Object;@org.hsqldb.store.BaseHashMap");
				dupFields
						.add("constraintList:[Lorg/hsqldb/Constraint;@org.hsqldb.Table");
				dupFields
						.add("refTable:Lorg/hsqldb/Table;@org.hsqldb.ConstraintCore");
			} else {
				dupFields.add("dummy");
			}
		} 

		if (expMode != null) {
			if (expMode.equals("standard"))
				SummariesEnv.v().setExpMode(ExpMode.Standard);
			else
				SummariesEnv.v().setExpMode(ExpMode.Comparison);
		}
	}

	private void setDupFields() {
		/* populate dupFields in Env */
		for (jq_Field field : Env.v().reachableFields) {
			if (dupFields.isEmpty()) {
				if (field.toString().equals(
						"table:[Ljava/util/HashMap$Entry;@java.util.HashMap")
						|| field.toString()
								.equals("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable")
						// sunflow
						|| field.toString()
								.equals("entries:[Lorg/sunflow/util/FastHashMap$Entry;@org.sunflow.util.FastHashMap")
						|| field.toString()
								.equals("v:Ljava/lang/Object;@org.sunflow.util.FastHashMap$Entry")
						|| field.toString()
								.equals("queue:[Ljava/lang/Object;@java.util.PriorityQueue")
						|| field.toString()
								.equals("table:[Ljava/util/Hashtable$Entry;@java.util.Hashtable$Enumerator")
						|| field.toString()
								.equals("value:Ljava/lang/Object;@java.util.HashMap$Entry")
						|| field.toString()
								.equals("value:Ljava/lang/Object;@java.util.Hashtable$Entry")
						|| field.toString()
								.equals("value:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry")
						|| field.toString()
								.equals("key:Ljava/lang/Object;@java.util.HashMap$Entry")
						|| field.toString()
								.equals("header:Ljava/util/LinkedList$Entry;@java.util.LinkedList")
						|| field.toString()
								.equals("element:Ljava/lang/Object;@java.util.LinkedList$Entry")
						|| field.toString()
								.equals("key:Ljava/lang/Object;@java.util.concurrent.ConcurrentHashMap$HashEntry")
						|| field.toString()
								.equals("elementData:[Ljava/lang/Object;@java.util.ArrayList")) {
					Env.v().putDupField(field, 0);
				}
			} else {
				if (dupFields.contains(field.toString())) {
					Env.v().putDupField(field, 0);
				}
			}
		}
	}

	private void setAllocDepth() {
		/* populate allocDepth in Env */
		jq_Reference specClz = Program.g().getClass(
				"org.sunflow.util.FastHashMap");
		if (specClz != null) {
			jq_Class clz = (jq_Class) specClz;
			int len = clz.getDeclaredInstanceMethods().length;
			for (int i = 0; i < len; i++) {
				jq_Method meth = clz.getDeclaredInstanceMethods()[i];
				ControlFlowGraph cfg = meth.getCFG();
				for (BasicBlock bb : cfg.reversePostOrder()) {
					for (Quad q : bb.getQuads()) {
						Operator op = q.getOperator();
						if (op instanceof NewArray) {
							Env.v().putAllocDepth(q,
									NewArray.getType(q).getType(), 4);
						}
					}
				}
			}
		}
	}

	private void setLocalPropLevel() {
		/* populate localPropLevel in Env */
		//
		//
		//
	}

	private void mergeFields() {
		if (SummariesEnv.v().fieldMergeType == SummariesEnv.FieldMergeType.NO_MERGE) {
			// do nothing
		} else if (SummariesEnv.v().fieldMergeType == SummariesEnv.FieldMergeType.NAME_ME) {
			/* codes to merge the fields */
			//
			//
			Iterator<Set<jq_Field>> it = Env.v().fieldClustersIterator();
			while (it.hasNext()) {
				Set<jq_Field> fields = it.next();
				MergedFieldSelectorFactory.f().merge(fields);
			}
		} else {
			assert false : SummariesEnv.v().fieldMergeType;
		}
	}

	/* extract the application locals */
	private void extractAppLocals() {
		if (!relAppLocal.isOpen()) {
			relAppLocal.load();
		}
		Iterable<Register> res = relAppLocal.getAry1ValTuples();
		Set<Register> appLocals = SetUtils.iterableToSet(res,
				relAppLocal.size());
		Env.v().addAllAppLocalsSet(appLocals);
	}

	/* extract the locals that we will propagate */
	private void extractPropLocals() {
		if (SummariesEnv.v().propType == PropType.APPLOCALs) {
			Env.v().addAllPropSet(Env.v().getAppLocals());
		} else if (SummariesEnv.v().propType == PropType.NOLOCAL) {
			// do nothing
		} else if (SummariesEnv.v().propType == PropType.ALL) {
			DomV domV = (DomV) ClassicProject.g().getTrgt("V");
			Iterator<Register> it = domV.iterator();
			while (it.hasNext()) {
				Env.v().addPropSet(it.next());
			}
		} else if (SummariesEnv.v().propType == PropType.FORMALs) {
			if (!relAppLocal.isOpen())
				relAppLocal.load();
			Iterable<Register> res = relAppLocal.getAry1ValTuples();
			Set<Register> propSet = SetUtils.iterableToSet(res,
					relAppLocal.size());
			for (jq_Method m : callGraph.getNodes()) {
				ControlFlowGraph g = m.getCFG();
				RegisterFactory rf = g.getRegisterFactory();
				jq_Type[] paramTypes = m.getParamTypes();
				int numArgs = m.getParamTypes().length;
				for (int zIdx = 0; zIdx < numArgs; zIdx++) {
					jq_Type t = paramTypes[zIdx];
					Register r = rf.get(zIdx);
					if (TypeHelper.h().isRefType(t) && propSet.contains(r))
						Env.v().addPropSet(r);
				}
			}
		} else {
			assert false : SummariesEnv.v().propType;
		}
	}

	/* further clean unreachable locals that were propagated */
	private void cleanPropLocals() {
		for (jq_Method m : Env.v().cg.getNodes()) {
			ControlFlowGraph cfg = m.getCFG();
			for (BasicBlock bb : cfg.reversePostOrder()) {
				for (Quad stmt : bb.getQuads()) {
					Operator op = stmt.getOperator();
					if (op instanceof PUTFIELD_A) {
						jq_Field field = Putfield.getField(stmt).getField();
						Operand rhso = Putfield.getSrc(stmt);
						// x.f = null.
						if (!(rhso instanceof RegisterOperand))
							continue;
						if (!Env.v().reachableFields.contains(field)) {
							Register lhs = ((RegisterOperand) rhso)
									.getRegister();
							Env.v().delPropSet(lhs);
						}
					}
					if (op instanceof GETFIELD_A) {
						jq_Field field = Getfield.getField(stmt).getField();
						if (!Env.v().reachableFields.contains(field)) {
							RegisterOperand lhs = Getfield.getDest(stmt);
							Env.v().delPropSet(lhs.getRegister());
						}
					}
					if (op instanceof PUTSTATIC_A) {
						jq_Field field = Putstatic.getField(stmt).getField();
						Operand rhso = Putstatic.getSrc(stmt);
						if (!(rhso instanceof RegisterOperand))
							continue;
						if (!Env.v().reachableFields.contains(field)) {
							RegisterOperand rhs = (RegisterOperand) Putstatic
									.getSrc(stmt);
							Env.v().delPropSet(rhs.getRegister());
						}
					}
					if (op instanceof GETSTATIC_A) {
						jq_Field field = Getstatic.getField(stmt).getField();
						if (!Env.v().reachableFields.contains(field)) {
							RegisterOperand lhs = Getstatic.getDest(stmt);
							Env.v().delPropSet(lhs.getRegister());
						}
					}
				}
			}
		}
	}

	/* field --> all possible dynamic types */
	private void fillField2TypesMap() {
		ProgramRel relFT = (ProgramRel) ClassicProject.g().getTrgt("FT");
		if (!relFT.isOpen()) {
			relFT.load();
		}
		for (jq_Field f : Env.v().reachableFields) {
			RelView view = relFT.getView();
			view.selectAndDelete(0, f);
			Iterable<jq_Type> resT = view.getAry1ValTuples();
			Set<jq_Type> types = SetUtils.iterableToSet(resT, view.size());
			Env.v().fillFieldToTypesMapping(f, types);
		}
	}

	/* filter empty point-to set fields */
	private void emptyP2SetFieldsFilter() {
		ProgramRel relReachF = (ProgramRel) ClassicProject.g().getTrgt(
				"reachableF");
		if (!relReachF.isOpen()) {
			relReachF.load();
		}
		Iterable<jq_Field> resF = relReachF.getAry1ValTuples();
		Set<jq_Field> reaches = SetUtils.iterableToSet(resF, relReachF.size());
		// get rid of index field
		reaches.remove(null);
		Env.v().reachableFields.addAll(reaches);

		// heuristic for field properties. back, forward or stay.
		for (jq_Field f : reaches) {
			String fstr = f.toString();
			if (fstr.matches("this\\$0:.*;@java.util.*")) {
				// System.out.println("Back edges:" + f);
				Env.v().putFieldType(f, SummariesEnv.FieldType.BACK);
			} else if (fstr.matches("^next:.*java.util.*")
					|| fstr.matches("^before:.*java.util.*")
					|| fstr.matches("^previous:.*java.util.*")
					|| fstr.matches("^after:.*.java.util.*")) {
				// System.out.println("Stay edges:" + f);
				Env.v().putFieldType(f, SummariesEnv.FieldType.STAY);
			} else {
				// System.out.println("Forward edges:" + f);

				Env.v().putFieldType(f, SummariesEnv.FieldType.FORWARD);
			}
		}
	}

	/* extract statements we do not do recursive smashing for */
	private void extractNotSmashStmts() {
		SCCHelper4CG s4g = new SCCHelper4CG(callGraph, callGraph.getRoots());
		for (Set<jq_Method> scc : s4g.getComponents()) {
			if (scc.size() == 1) {
				jq_Method single = scc.iterator().next();
				if (!callGraph.getSuccs(single).contains(single)) {
					Graph repGraph = new Graph();
					HashMap<Node, Set<BasicBlock>> nodeToScc = new HashMap<Node, Set<BasicBlock>>();
					ControlFlowGraph cfg = single.getCFG();
					intraProc.computeSCC(cfg, repGraph, nodeToScc);
					// analyze the method in a post reverse orders
					for (Node rep : repGraph.getReversePostOrder()) {
						Set<BasicBlock> sccBB = nodeToScc.get(rep);
						if (sccBB.size() == 1) {
							BasicBlock sccB = sccBB.iterator().next();
							// self loop in current block.
							if (!sccB.getSuccessors().contains(sccB)) {
								// dump GETFIELD
								for (Quad q : sccB.getQuads()) {
									Operator op = q.getOperator();
									if (op instanceof Getfield)
										Env.v().addNotSmashStmt(q);
								}
							}
						}
					}
				}
			}
		}
	}

	private void fillRegToR0Mapping() {
		Iterator<Register> it = Env.v().appLocalsIterator();
		while (it.hasNext()) {
			Register r = it.next();
			if (Env.v().isPropLocal(r)) {
				continue;
			}
			jq_Method method = Env.v().getMethodByReg(r);
			if (method.isStatic()) {
				continue;
			}
			assert (method != null) : r;
			Register r0 = Env.v().getR0ByMethod(method);
			assert (r0 != null) : r + " " + method;
			Env.v().putRegToR0(r, r0);
		}
	}

	private void fillMethToR0Mapping() {
		for (jq_Method m : callGraph.getNodes()) {
			if (!m.isStatic()) {
				ControlFlowGraph g = m.getCFG();
				RegisterFactory rf = g.getRegisterFactory();
				Register r0 = rf.get(0);
				Env.v().putMethToR0(m, r0);
			}
		}
	}

	private void fillReg2MethMapping() {
		if (!relMV.isOpen())
			relMV.load();

		for (Iterator<Register> it = Env.v().appLocalsIterator(); it.hasNext();) {
			Register v = it.next();
			RelView view = relMV.getView();
			view.selectAndDelete(1, v);
			Iterable<jq_Method> mIt = view.getAry1ValTuples();
			jq_Method m = mIt.iterator().next();
			Env.v().fillRegToMethodMapping(v, m);
		}
	}

	private void filterDefaultTgtByCIPA() {
		ProgramRel relReachF = (ProgramRel) ClassicProject.g().getTrgt(
				"reachableF");
		ProgramRel relFF = (ProgramRel) ClassicProject.g().getTrgt("FF");
		ProgramRel relVF = (ProgramRel) ClassicProject.g().getTrgt("VF");
		ProgramRel relHF = (ProgramRel) ClassicProject.g().getTrgt("HF");
		ProgramRel relFFSec = (ProgramRel) ClassicProject.g().getTrgt("FFSec");
		ProgramRel relVFSec = (ProgramRel) ClassicProject.g().getTrgt("VFSec");

		// (r0.e, f) --> VF
		if (!relVF.isOpen())
			relVF.load();

		if (!relFF.isOpen())
			relFF.load();

		if (!relFFSec.isOpen())
			relFFSec.load();

		if (!relVFSec.isOpen())
			relVFSec.load();

		if (!relReachF.isOpen()) {
			relReachF.load();
		}

		if (!relHF.isOpen()) {
			relHF.load();
		}

		Iterable<jq_Field> resF = relReachF.getAry1ValTuples();
		Set<jq_Field> reachableF = SetUtils.iterableToSet(resF,
				relReachF.size());
		Set<Register> iFormalSet = new HashSet<Register>();
		Set<jq_Field> iFieldSet = new HashSet<jq_Field>();

		// alloc to field.
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		@SuppressWarnings("rawtypes")
		Iterator hit = domH.iterator();
		while (hit.hasNext()) {
			Quad alloc = (Quad) hit.next();
			RelView hfView = relHF.getView();
			hfView.selectAndDelete(0, alloc);
			if (hfView.size() == 0)
				continue;

			Iterable<jq_Field> itH = hfView.getAry1ValTuples();
			Set<jq_Field> fields = SetUtils.iterableToSet(itH, hfView.size());
			// System.out.println("HF: " + alloc + " ----> " + fields);
			Env.v().putAllocToFields(alloc, fields);
		}

		for (jq_Method m : callGraph.getNodes()) {
			ControlFlowGraph g = m.getCFG();
			RegisterFactory rf = g.getRegisterFactory();
			jq_Type[] paramTypes = m.getParamTypes();
			int numArgs = m.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				jq_Type t = paramTypes[zIdx];
				Register formal = rf.get(zIdx);
				if (TypeHelper.h().isRefType(t)) {
					RelView vfView = relVF.getView();
					vfView.selectAndDelete(0, formal);
					if (vfView.size() == 0)
						continue;

					Iterable<jq_Field> itF = vfView.getAry1ValTuples();
					Set<jq_Field> fields = SetUtils.iterableToSet(itF,
							vfView.size());
					Env.v().putFormalToFields(formal, fields);
					// System.out.println("VF: " + formal + " ----> " + fields);

					// Is i field reachable? store for next step.
					if (fields.contains(null))
						iFormalSet.add(formal);
				}
			}
		}

		// (f, g) --> FF
		for (jq_Field f : reachableF) {
			RelView ffView = relFF.getView();
			ffView.selectAndDelete(0, f);
			if (ffView.size() == 0)
				continue;

			Iterable<jq_Field> itFF = ffView.getAry1ValTuples();
			Set<jq_Field> fields = SetUtils.iterableToSet(itFF, ffView.size());
			Env.v().putFieldToFields(f, fields);
			// System.out.println("FF: " + f + " ----> " + fields);
			if (fields.contains(null))
				iFieldSet.add(f);
		}

		// (r0.e.i, f) --> VF + HF1H(F=i)
		for (Register formal : iFormalSet) {
			RelView vfsecView = relVFSec.getView();
			vfsecView.selectAndDelete(0, formal);
			if (vfsecView.size() == 0)
				continue;

			Iterable<jq_Field> itF = vfsecView.getAry1ValTuples();
			Set<jq_Field> fields = SetUtils
					.iterableToSet(itF, vfsecView.size());

			// System.out.println("VFSec: " + formal + " ----> " + fields);
			Env.v().putFormalIndexToFields(formal, fields);
		}

		// (f.i, g) --> FF + HF1H(F=i)
		for (jq_Field f : iFieldSet) {
			RelView ffsecView = relFFSec.getView();
			ffsecView.selectAndDelete(0, f);
			if (ffsecView.size() == 0)
				continue;

			Iterable<jq_Field> itF = ffsecView.getAry1ValTuples();
			Set<jq_Field> fields = SetUtils
					.iterableToSet(itF, ffsecView.size());
			// System.out.println("FFSec: " + f + " ----> " + fields);
			Env.v().putFieldIndexToFields(f, fields);
		}
	}

	private void fillTypeToFieldsMapping() {
		/* write codes to fill the typeToFields mapping in Env */
		ClassHierarchy ch = Program.g().getClassHierarchy();

		for (jq_Reference c : Program.g().getClasses()) {
			if (c instanceof jq_Class) {
				jq_Class clz = (jq_Class) c;
				Set<jq_Field> fields = new HashSet<jq_Field>();
				Set<String> subClz = ch.getConcreteSubclasses(clz.getName());
				if (subClz == null) {
					// some classes will be out of scope if we ignore lib.
					subClz = new HashSet<String>();
					subClz.add(clz.getName());
				}
				fields.addAll(Arrays.asList(clz.getInstanceFields()));
				// add fields of sub-classes
				for (String subStr : subClz) {
					jq_Reference ref = Program.g().getClass(subStr);
					if (ref == null)
						continue;
					jq_Class sub = (jq_Class) ref;
					fields.addAll(Arrays.asList(sub.getInstanceFields()));
				}
				fields.retainAll(Env.v().reachableFields);
				Env.v().putTypeToFields(clz, fields);
			}
		}
	}

	private void checkMain(MMemGraph mainGraph) {
		Iterator<MemEdge> it = mainGraph.sumEdgesIterator();
		Set<MemLoc> globals = new HashSet<MemLoc>();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			assert (src.isPropLocalVarNode() || src.isAllocNode()
					|| src.isGlobalAPNode() || src.isGlobalNode()) : edge;
			assert (tgt.isAllocNode() || tgt.isGlobalAPNode()) : edge;
			assert !(src.getMemGraph() == Env.v().shared && tgt.getMemGraph() == Env
					.v().shared) : edge;
		}
		int count = 0;
		System.out
				.println("=============== checking main method =================");
		for (MemLoc global : globals) {
			System.out.println("(" + count + ")" + global);
		}
		assert false : "finish checking main method";
	}

	private void checkS1Clinit(MMemGraph clinit) {
		Iterator<MemEdge> it = clinit.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
		}
	}

	private void checkS2Clinit(MMemGraph clinit) {

	}

	/* conclude main and <clinit>'s */
	private void conclude() {
		Set<MMemGraph> clinits = new HashSet<MMemGraph>();
		MMemGraph mainGraph = SummariesEnv.v().getMMemGraph(
				Program.g().getMainMethod());
		for (jq_Method m : callGraph.getRoots()) {
			if (!Program.g().getMainMethod().equals(m)) {
				MMemGraph graph = SummariesEnv.v().getMMemGraph(m);
				clinits.add(graph);
			}
		}
		if (G.dumpTopMethodsToFile) {
			dumpTopMethods(mainGraph, clinits);
		}
		if (G.check) {
			checkMain(mainGraph);
			for (MMemGraph clinit : clinits) {
				jq_Method method = clinit.getMethod();
				if (Env.v().isS1Method(method)) {
					checkS1Clinit(clinit);
				} else if (Env.v().isS2Method(method)) {
					checkS2Clinit(clinit);
				} else if (Env.v().isS3Method(method)) {
					assert false : method;
				} else {
					assert false : method;
				}
			}
		}
		cController = new CMemGraphController(mainGraph, clinits);
		Env.v().currCtrlor = cController;
		CMemGraph conclusion = SummariesEnv.v().initConclusion(mainGraph,
				clinits, cController);
		MemNodeFactory.f().init(conclusion);
		cController.setEverything(conclusion);
		cController.conclude();
		querier = new ReportManager(conclusion);
		Env.v().setConclusion(conclusion);
		if (G.dumpConclusionToFile) {
			Dumper.d().dumpHeapToFile(conclusion, "conclusion");
			if (SummariesEnv.v().shareSum) {
				Dumper.d().dumpHeapToFile(Env.v().shared, "share");
			}
		}
	}

	private void dumpTopMethods(MMemGraph mainGraph, Set<MMemGraph> clinits) {
		Dumper.d().dumpSumToFile(mainGraph, "Top_main");
		Dumper.d().dumpSumToFile(Env.v().shared, "Top_shared");
		int count = 0;
		Iterator<MMemGraph> it = clinits.iterator();
		while (it.hasNext()) {
			MMemGraph clinit = it.next();
			jq_Method method = clinit.getMethod();
			if (Env.v().isS1Method(method)) {
				Dumper.d().dumpSumToFile(clinit, "Top_clinit_S1_" + ++count);
			} else if (Env.v().isS2Method(method)) {
				Dumper.d().dumpSumToFile(clinit, "Top_clinit_S2_" + ++count);
			} else if (Env.v().isS3Method(method)) {
				assert false : method;
			} else {
				assert false : method;
			}
		}
	}

	private Graph collapseSCCs() {
		Graph repGraph = new Graph();
		Set<jq_Method> sccs = new HashSet<jq_Method>();
		SCCHelper4CG s4g = new SCCHelper4CG(callGraph, callGraph.getRoots());
		int maxSize = 0;
		int tltSCCMeths = 0;
		int idx = 0;
		int methsInCG = callGraph.getNodes().size();

		for (Set<jq_Method> scc : s4g.getComponents()) {
			// create a representation node for each scc.
			idx++;
			tltSCCMeths += scc.size();
			if (scc.size() > maxSize) {
				maxSize = scc.size();
			}
			Node node = new Node("scc" + idx);
			nodeToScc.put(node, scc);
			sccToNode.put(scc, node);
			for (jq_Method mb : scc)
				methToNode.put(mb, node);

			repGraph.addNode(node);
			sccs.addAll(scc);
		}

		assert tltSCCMeths == methsInCG : tltSCCMeths + " VS " + methsInCG;

		for (Set<jq_Method> scc : s4g.getComponents()) {
			Node cur = sccToNode.get(scc);
			for (jq_Method nb : scc) {
				// init successor.
				for (jq_Method sucb : callGraph.getSuccs(nb)) {
					if (scc.contains(sucb)) {
						if (scc.size() == 1 && nb.equals(sucb)) {
							cur.setSelfLoop(true);
						}
						continue;
					} else {
						Node scNode = methToNode.get(sucb);
						cur.addSuccessor(scNode);
					}
				}
				// init preds.
				for (jq_Method pred : callGraph.getPreds(nb)) {
					if (scc.contains(pred)) {
						if (scc.size() == 1 && nb.equals(pred)) {
							cur.setSelfLoop(true);
						}
						continue;
					} else {
						Node pdNode = methToNode.get(pred);
						cur.addPred(pdNode);
					}
				}
			}
		}
		
		// apply lift heuristic automatically
		// SummariesEnv.v().lift = s4g.getComponents().size() - 200;

		return repGraph;
	}

	/*---------------- sumAnalyze() related helpers -----------------*/
	public static int count = 0;

	// begin to work on a representative node. single node or scc.
	private void workOn(Node node) {
		start(node);

		if (G.tuning) {
			G.countScc++;
			StringUtil.reportInfo("Working on SCC: " + G.countScc
					+ nodeToScc.get(node));
			StringUtil.reportInfo("Size of SCC: " + nodeToScc.get(node).size());
		}

		assert !node.isTerminated() : "Should not analyze a node twice.";

		// get its corresponding scc
		Set<jq_Method> scc = nodeToScc.get(node);

		if (scc.size() == 1) {
			// self loop. perform scc.
			if (node.isSelfLoop()) {
				analyzeSCC(node);
			} else {
				jq_Method m = scc.iterator().next();
				analyze(m);
			}
		} else {
			analyzeSCC(node);
		}

		// at the end, mark it as terminated.
		terminate(node);
	}

	private void start(Node node) {
		count = 0;
	}

	private void terminate(Node node) {
		node.setTerminated(true);
		Set<jq_Method> methods = nodeToScc.get(node);
		for (jq_Method method : methods) {
			MMemGraph memGraph = SummariesEnv.v().getMMemGraph(method);
			memGraph.setTerminated();
		}
	}

	// check whether all nodes have terminated.
	private boolean allTerminated(Set<Node> succs) {
		boolean flag = true;
		for (Node node : succs)
			if (!node.isTerminated()) {
				flag = false;
				break;
			}
		return flag;
	}

	private boolean analyze(jq_Method m) {
		boolean ret = false;
		
		ControlFlowGraph cfg = m.getCFG();
		ret = intraProc.analyze(cfg);

		return ret;
	}

	private void analyzeSCC(Node node) {
		Env.v().setScc(true);

		Set<jq_Method> scc = nodeToScc.get(node);
		Set<jq_Method> wl = new LinkedHashSet<jq_Method>();
		// avoid duplicate element in tree set
		Set<jq_Method> guardSet = new LinkedHashSet<jq_Method>();
		if (SummariesEnv.v().orderScc) {
			wl = new TreeSet<jq_Method>(new OrderedComparator(callGraph));
			guardSet.addAll(scc);
		}
		// add all methods to work-list
		wl.addAll(scc);

		while (!wl.isEmpty()) {
			jq_Method worker = wl.iterator().next();
			if (wl instanceof TreeSet) {
				worker = ((TreeSet<jq_Method>) wl).pollFirst();
				guardSet.remove(worker);
			} else {
				wl.remove(worker);
			}
			// if summary is changed
			// FIXME: this should be the change of the summary
			boolean changed = analyze(worker);
			// only when changing the summary, we add all the callers
			if (changed) {
				if (!G.analyzeOnce) {
					for (jq_Method pred : callGraph.getPreds(worker))
						if (scc.contains(pred) && guardSet.add(pred)) {
							wl.add(pred);
						}
				}
			}

			if (G.tuning) {
				StringUtil.reportInfo("SCC counter: " + wl.size() + ":"
						+ worker);
			}
		}

		Env.v().setScc(false);
	}

	/* get the call graph from Chord (CIPA) */
	private ICICG getCallGraph() {
		if (callGraph == null) {
			callGraph = new CICG(domM, relRootM, relReachableM, relIM, relMM);
		}
		Env.v().cg = callGraph;
		return callGraph;
	}

	/* Dump method. Borrowed from Mayur */
	private void dumpCallGraph() {
		ClassicProject project = ClassicProject.g();

		ICICG cicg = this.getCallGraph();
		domM = (DomM) project.getTrgt("M");

		PrintWriter out = OutDirUtils.newPrintWriter("cicg.dot");
		out.println("digraph G {");
		for (jq_Method m1 : cicg.getNodes()) {
			String id1 = id(m1);
			out.println("\t" + id1 + " [label=\"" + str(m1) + "\"];");
			for (jq_Method m2 : cicg.getSuccs(m1)) {
				String id2 = id(m2);
				Set<Quad> labels = cicg.getLabels(m1, m2);
				for (Quad q : labels) {
					String el = q.toJavaLocStr();
					out.println("\t" + id1 + " -> " + id2 + " [label=\"" + el
							+ "\"];");
				}
			}
		}
		out.println("}");
		out.close();

	}

	private String id(jq_Method m) {
		return "m" + domM.indexOf(m);
	}

	private static String str(jq_Method m) {
		jq_Class c = m.getDeclaringClass();
		String desc = m.getDesc().toString();
		String args = desc.substring(1, desc.indexOf(')'));
		String sign = "(" + Program.typesToStr(args) + ")";
		return c.getName() + "." + m.getName().toString() + sign;
	}

}
