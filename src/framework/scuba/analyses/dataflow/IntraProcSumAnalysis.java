package framework.scuba.analyses.dataflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.controller.MMemGraphController;
import framework.scuba.controller.S1MMemGraphController;
import framework.scuba.controller.S2MMemGraphController;
import framework.scuba.controller.S3MMemGraphController;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.factories.PhiTypeFactory;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.S1MMemGraph;
import framework.scuba.domain.memgraph.S2MMemGraph;
import framework.scuba.domain.memgraph.S3MMemGraph;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.ScubaQuadVisitor;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.Checker;
import framework.scuba.helper.Dumper;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;
import framework.scuba.utils.StringUtil;

/**
 * Intra-proc summary-based analysis Check the rules in Figure 8 of our paper.
 * 
 * @author yufeng
 * 
 */
public class IntraProcSumAnalysis {

	protected MMemGraph memGraph;

	protected MMemGraphController controller;
	protected final S1MMemGraphController s1Controller = new S1MMemGraphController();
	protected final S2MMemGraphController s2Controller = new S2MMemGraphController();
	protected final S3MMemGraphController s3Controller = new S3MMemGraphController();

	protected final ScubaQuadVisitor qv = new ScubaQuadVisitor();

	// whether the current intra analysis changes the summary of this method
	public boolean sumChanged = false;

	// used for debugging
	private Map<jq_Method, Integer> sccCounter = new HashMap<jq_Method, Integer>();

	public IntraProcSumAnalysis() {
		Env.v().qv = qv;
	}

	public void setEverything(ControlFlowGraph g) {
		// initialize the memory graph (S1, S2 or S3) for this method
		jq_Method m = g.getMethod();
		MMemGraph memGraph = null;
		if (Env.v().isS1Method(m)) {
			controller = s1Controller;
			memGraph = SummariesEnv.v().initS1MMemGraph(m, s1Controller);
		} else if (Env.v().isS2Method(m)) {
			controller = s2Controller;
			memGraph = SummariesEnv.v().initS2MMemGraph(m, s2Controller);
		} else if (Env.v().isS3Method(m)) {
			controller = s3Controller;
			memGraph = SummariesEnv.v().initS3MMemGraph(m, s3Controller);
		} else {
			assert false : m;
		}
		assert (memGraph != null) : m;
		this.memGraph = memGraph;
		// hand the controller to this analysis
		controller = (MMemGraphController) memGraph.getController();
		controller.setMemGraph(memGraph);
		// hand the controller to quad visitor
		qv.setEverything(controller);
		// set current controller in Env
		Env.v().currCtrlor = controller;
	}

	// analyze a method based on the CFG of this method
	// return whether it changes the summary
	public boolean analyze(ControlFlowGraph g) {
		sumChanged = false;

		Graph repGraph = new Graph();
		HashMap<Node, Set<BasicBlock>> nodeToScc = new HashMap<Node, Set<BasicBlock>>();

		// pre-processing
		preProcess(g, repGraph, nodeToScc);

		jq_Method method = g.getMethod();
		if (Env.v().isS2Method(method)) {
			// analyze S2 method only once by manually constructing the summary
			// assert false : method;
			handleS2Method();
			// for sumChanged to be false
			sumChanged = false;
		} else if (Env.v().isS3Method(method)) {
			// analyze S3 method only once and get an empty summary
			// assert false : method;
			// for sumChanged to be false
			sumChanged = false;
		} else if (Env.v().isS1Method(method)) {
			if (SummariesEnv.v().containerModel) {
				// FIXME: models for container
				jq_Method meth = g.getMethod();
				String mStr = meth.toString();
				if (mStr.equals("add:(Ljava/lang/Object;)Z@java.util.LinkedList")
						|| meth.toString()
								.equals("addLast:(Ljava/lang/Object;)V@java.util.LinkedList")) {
					s1Controller.genericAdd();
					sumChanged = false;
				} else if (mStr
						.matches("(get|remove|peek|poll|element).*:.*@java.util.LinkedList")) {
					s1Controller.genericGet();
					sumChanged = false;
				} else if (mStr
						.matches("addOrRemove.*:.*@org.hsqldb.store.BaseHashMap")) {
					s1Controller.addForBaseMap();
					sumChanged = false;
				} else {
					// normal code.
					// analyze the method in a post reverse orders
					for (Node rep : repGraph.getReversePostOrder()) {
						Set<BasicBlock> scc = nodeToScc.get(rep);
						if (scc.size() == 1) {
							BasicBlock sccB = scc.iterator().next();
							// self loop in current block.
							if (sccB.getSuccessors().contains(sccB)) {
								handleSCC(scc);
							} else {
								handleBasicBlock(sccB);
							}
						} else {
							handleSCC(scc);
						}
					}
				}
			} else {
				// analyze the method in a post reverse orders
				for (Node rep : repGraph.getReversePostOrder()) {
					Set<BasicBlock> scc = nodeToScc.get(rep);
					if (scc.size() == 1) {
						BasicBlock sccB = scc.iterator().next();
						// self loop in current block.
						if (sccB.getSuccessors().contains(sccB)) {
							handleSCC(scc);
						} else {
							handleBasicBlock(sccB);
						}
					} else {
						handleSCC(scc);
					}
				}
			}
		}

		// post-processing
		postProcess(g);
		return sumChanged;
	}

	/* ----------------- S1 method handlers ----------------- */
	// compute the fixed-point for this SCC
	// return whether it changes the heap
	protected boolean handleSCC(Set<BasicBlock> scc) {
		LinkedList<BasicBlock> wl = new LinkedList<BasicBlock>();
		boolean ret = false;
		// add all nodes that have preds outside the scc as entry.
		for (BasicBlock mb : scc)
			if (!scc.containsAll(mb.getPredecessors()))
				wl.add(mb);

		// strange case.
		if (wl.size() == 0)
			wl.add(scc.iterator().next());

		Set<BasicBlock> set = new HashSet<BasicBlock>();

		while (true) {
			BasicBlock bb = wl.poll();
			if (set.contains(bb))
				continue;

			boolean changed = handleBasicBlock(bb);
			ret = ret | changed;

			assert scc.contains(bb) : "You can't analyze the node that is out of current scc.";

			// if changing the heap, we analyze all basic blocks in the SCC
			// again (conservative)
			if (changed)
				set.clear();
			else
				set.add(bb);

			// xinyu's algorithm: use counter to achieve O(1)
			if (set.size() == scc.size())
				break;

			// process all successors that belongs to current SCC
			for (BasicBlock suc : bb.getSuccessors())
				if (scc.contains(suc))
					wl.add(suc);
		}
		return ret;
	}

	// return whether it changes the heap
	protected boolean handleBasicBlock(BasicBlock bb) {
		boolean ret = false;
		// handle each quad in the basic block
		for (Quad q : bb.getQuads()) {
			// handle the statement
			ret = ret | handleStmt(q);
		}
		return ret;
	}

	public static int count = 0;

	protected boolean handleStmt(Quad quad) {
		SummaryBasedAnalysis.count++;
		if (!qv.isHeapStmt(quad)) {
			return false;
		}

		if (G.handleStmt) {
			System.out.println("+++++++++++++++++++++++++++++++++++++++++");
			System.out.println("[Handling Statement] " + quad);
		}

		long start = System.nanoTime();
		quad.accept(qv);
		long end = System.nanoTime();

		if (!(quad.getOperator() instanceof Invoke)) {
			G.intraTime += (end - start);
		} else {
			G.instnTime += (end - start);
		}
		if (G.handleStmt) {
			System.out.println("+++++++++++++++++++++++++++++++++++++++++");
		}
		return qv.changed();
	}

	/* ------------------- S2 handlers -------------------- */
	private void handleS2Method() {
		// TODO
		s2Controller.construct();
	}

	/* ------------------ pre-processing ------------------- */
	// the operations in preProcess should be done in order
	protected void preProcess(ControlFlowGraph g, Graph repGraph,
			HashMap<Node, Set<BasicBlock>> nodeToScc) {
		// VERY IMPORTANT set-up
		setEverything(g);
		// infer type info for PHI node
		inferTypeInfoForPhi(g);
		// compute SCC in current CFG
		computeSCC(g, repGraph, nodeToScc);
		// create the memory node factory
		createNodeFactory4Method();
		// initialize the formals list
		initFormals();
		// checking the controller is controlling g's memory graph
		check(g);
		// dump information before analyzing a method
		if (G.dump) {
			dump(g);
		}
	}

	public void computeSCC(ControlFlowGraph g, Graph repGraph,
			HashMap<Node, Set<BasicBlock>> nodeToScc) {
		// only compute SCC's in CFG for S1 methods
		if (memGraph instanceof S2MMemGraph || memGraph instanceof S3MMemGraph) {
			return;
		}
		BasicBlock entry = g.entry();
		HashSet<BasicBlock> roots = new HashSet<BasicBlock>();
		HashMap<Set<BasicBlock>, Node> sccToNode = new HashMap<Set<BasicBlock>, Node>();
		HashMap<BasicBlock, Node> bbToNode = new HashMap<BasicBlock, Node>();
		roots.add(entry);
		SCCHelper sccManager = new SCCHelper(g, roots);
		int idx = 0;
		for (Set<BasicBlock> scc : sccManager.getComponents()) {
			// create a representation node for each scc.
			idx++;
			Node node = new Node("scc" + idx);
			nodeToScc.put(node, scc);
			sccToNode.put(scc, node);
			for (BasicBlock mb : scc)
				bbToNode.put(mb, node);

			repGraph.addNode(node);
			if (scc.contains(entry))
				repGraph.setEntry(node);
		}
		for (Set<BasicBlock> scc : sccManager.getComponents()) {
			Node cur = sccToNode.get(scc);
			for (BasicBlock nb : scc) {
				for (BasicBlock sucb : nb.getSuccessors()) {
					if (scc.contains(sucb))
						continue;
					else {
						Node scNode = bbToNode.get(sucb);
						cur.addSuccessor(scNode);
					}
				}
			}
		}
	}

	private void initFormals() {
		// only initialize formals for S1 and S2 methods
		if (memGraph.hasInitedFormals()) {
			return;
		}
		memGraph.initFormals();
		controller.setFormals();
	}

	private void inferTypeInfoForPhi(ControlFlowGraph g) {
		if (memGraph instanceof S1MMemGraph) {
			PhiTypeFactory.f().update(g);
		}
	}

	private void check(ControlFlowGraph g) {
		Checker.c().checkController(controller, g);
	}

	private void createNodeFactory4Method() {
		if (memGraph instanceof S1MMemGraph || memGraph instanceof S2MMemGraph) {
			MemNodeFactory.f().init(memGraph);
		}
	}

	private void dump(ControlFlowGraph g) {
		System.out.println("=======================================");
		jq_Method method = g.getMethod();
		if (G.dumpMap.containsKey(method)) {
			int number = G.dumpMap.get(g.getMethod());
			System.out.println("[ByteCode]" + "[Method] " + method
					+ " with [Number] " + number);
			if (Env.v().isS1Method(method)) {
				System.out.println("S1 method");
			} else if (Env.v().isS2Method(method)) {
				System.out.println("S2 method");
			} else if (Env.v().isS3Method(method)) {
				System.out.println("S3 method");
			} else {
				assert false : method;
			}
		} else {
			System.out.println("[ByteCode]" + "[Method] " + method
					+ " with [Number] " + ++G.dumpCounter);
			G.dumpMap.put(method, G.dumpCounter);
		}
		System.out.println("------------------------------");
		System.out.println(g.fullDump());
		System.out.println("------------------------------");
		System.out.println("=======================================");
	}

	/* ------------------ post-processing ------------------- */
	protected void postProcess(ControlFlowGraph g) {
		memGraph.setHasBeenAnalyzed();
		// propagate parameters for later inquiry of parameter's point-to sets
		handleParamPropagation(g);
		// generate the summary for this method
		generateSummary();
		// debug information
		if (G.tuning) {
			debug();
		}
		if (G.dumpToFile) {
			dumpToFile();
		}
	}

	private void debug() {
		jq_Method m = memGraph.getMethod();
		StringUtil.reportInfo("# of nodes in the heap of " + m + " : "
				+ memGraph.getHeapNodesNum());
		StringUtil.reportInfo("# of edges in the heap of " + m + " : "
				+ memGraph.getHeapEdgesNum());
		StringUtil.reportInfo("# of nodes in the summary of " + m + " : "
				+ memGraph.getSumNodesNum());
		StringUtil.reportInfo("# of edges in the summary of " + m + " : "
				+ memGraph.getSumEdgesNum());
		if (SummariesEnv.v().shareSum) {
			StringUtil.reportInfo("# of edges in the heap of shared summary "
					+ Env.v().shared.getHeapEdgesNum());
		}
		if (memGraph.getHeapEdgesNum() > 1000) {
			int apNum = 0;
			int allocNum = 0;
			Iterator<MemNode> it = memGraph.sumNodesIterator();
			while (it.hasNext()) {
				MemNode n = it.next();
				MemLoc loc = n.getLocs().iterator().next();
				if (loc instanceof AccessPathObject) {
					apNum++;
					// StringUtil.reportInfo("accessPath: " + loc);
				}
				if (loc instanceof AllocLoc) {
					allocNum++;
				}
			}
			StringUtil.reportInfo("BAD heap: # of access paths " + apNum);
			StringUtil.reportInfo("BAD heap: # of allocs " + allocNum);
		}
	}

	private void dumpToFile() {
		jq_Method m = memGraph.getMethod();
		if (sccCounter.containsKey(m)) {
			int counter = sccCounter.get(m);
			counter++;
			sccCounter.put(m, counter);
		} else {
			sccCounter.put(m, 1);
		}
		Dumper.d().dumpHeapToFile(memGraph,
				"Heap" + G.dumpMap.get(m) + "_" + sccCounter.get(m));
		Dumper.d().dumpSumToFile(memGraph,
				"Sum" + G.dumpMap.get(m) + "_" + sccCounter.get(m));
		System.out.println("=======================================");
		System.out.println("[Dump] " + "[Method] " + m + " with [Number] "
				+ G.dumpMap.get(m) + " with [sccCounter] " + sccCounter.get(m));
		System.out.println("=======================================");
	}

	private void handleParamPropagation(ControlFlowGraph g) {
		if (memGraph instanceof S1MMemGraph) {
			RegisterFactory rf = g.getRegisterFactory();
			jq_Method meth = g.getMethod();
			jq_Type[] paramTypes = meth.getParamTypes();
			int numArgs = meth.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				jq_Type t = paramTypes[zIdx];
				Register param = rf.get(zIdx);
				if (Env.v().isPropLocal(param)) {
					qv.visitParamAssign(meth, param, t);
				}
			}
		} else if (memGraph instanceof S2MMemGraph) {
			// do nothing
		} else if (memGraph instanceof S3MMemGraph) {
			// do nothing
		} else {
			assert false : memGraph.getClass();
		}
	}

	private void generateSummary() {
		controller.generateSummaryFromHeap();
	}

}