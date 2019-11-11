package framework.scuba.analyses.alias;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceField;
import joeq.Class.jq_Method;
import joeq.Class.jq_StaticField;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.analyses.alias.CIObj;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.Dumper;
import framework.scuba.helper.G;
import framework.scuba.helper.TypeHelper;
import framework.scuba.utils.ChordUtil;

public class CIPASumConstructor {

	protected CICG callGraph;
	protected final int unrollNum = 3;

	public void setEverything(CICG callGraph) {
		this.callGraph = callGraph;
	}

	public void run() {
		classify();
		construct1();
	}

	/* classify the methods into S1, S2 or S3 */
	protected void classify() {
		Set<jq_Method> methods = callGraph.getNodes();
		if (SummariesEnv.v().cipaSum) {
			int cnt = 0;
			Set<jq_Method> w2 = new HashSet<jq_Method>();
			Set<jq_Method> worklist = new HashSet<jq_Method>();
			for (jq_Method method : methods) {
				if (!ChordUtil.isLibMeth(method)) {
					worklist.add(method);
					Env.v().addS1Method(method);
				}
			}
			// s1.
			while (cnt < unrollNum) {
				Set<jq_Method> swapSet = new HashSet<jq_Method>();
				for (jq_Method worker : worklist) {
					for (jq_Method succ : callGraph.getSuccs(worker)) {
						jq_Class clz = succ.getDeclaringClass();
						if (ChordUtil.isLibMeth(succ)) {
							// ignore security methods that are transitively
							// called by app.
							if (cnt > 0 && ChordUtil.isInSecurity(clz))
								continue;
							swapSet.add(succ);
							// include all instance methods in the same class.
							for (jq_Method lib : Arrays.asList(clz
									.getDeclaredInstanceMethods())) {
								if (methods.contains(lib)) {
									Env.v().addS1Method(lib);
								}
							}
						}
					}
				}
				worklist.clear();
				worklist.addAll(swapSet);
				cnt++;
			}
			// w2.
			for (jq_Method method : methods) {
				String clzName = method.getDeclaringClass().getName();
				if (!Env.v().isS1Method(method)) {
					if (clzName.startsWith("java.util")
							|| clzName.equals("java.lang.String")
							|| method.toString().matches("^getProperty:.*java.lang.System")
							|| method.toString().startsWith("arraycopy:")) {
						if(clzName.startsWith("java.util.regex")) {
							w2.add(method);
							continue;
						}
							
						Env.v().addS1Method(method);
					} else {
						w2.add(method);
					}
				} else {
					// do not put security methods in s1.
					if (clzName.startsWith("java.security")
							|| method.toString().startsWith("printStackTrace:")) {
						Env.v().s1.remove(method);
						w2.add(method);
					}
				}
			}
			// divide w2 into s2 and s3.
			for (jq_Method method : w2) {
				Set<jq_Method> preds = callGraph.getPreds(method);
				boolean callByS1 = false;
				for (jq_Method caller : preds) {
					if (Env.v().isS1Method(caller)) {
						callByS1 = true;
						break;
					}
				}
				// only consider side effect methods in s2.
				if (callByS1 && ChordUtil.hasSideEffect(method)) {
					Env.v().addS2Method(method);
				} else {
					// otherwise add it to s3.
					Env.v().addS3Method(method);
				}
			}
			// move all clinit(lib) from s3 to s2.
			for (jq_Method root : callGraph.getRoots()) {
				if (root.getDeclaringClass().getName()
						.startsWith("sun.security")
						|| root.getDeclaringClass().getName()
								.startsWith("java.security"))
					continue;
				if (ChordUtil.isLibMeth(root)) {
					Env.v().addS2Method(root);
					Env.v().s3.remove(root);
					Env.v().s1.remove(root);
				}
			}
		} else {
			for (jq_Method method : methods) {
				Env.v().addS1Method(method);
			}
		}

		if (G.debug) {
			Iterator<jq_Method> it = null;
			int count = 0;
			System.out.println("================ S1 =================");
			it = Env.v().s1Iterator();
			count = 0;
			while (it.hasNext()) {
				System.out.println(++count + " : " + it.next());
			}
			System.out.println("================ S2 =================");
			it = Env.v().s2Iterator();
			count = 0;
			while (it.hasNext()) {
				System.out.println(++count + " : " + it.next());
			}
			System.out.println("================ S3 =================");
			it = Env.v().s3Iterator();
			count = 0;
			while (it.hasNext()) {
				System.out.println(++count + " : " + it.next());
			}
		}
	}

	/* construct CIPA summary in the shared summary */
	protected void construct2() {
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		Iterator<jq_Method> it = callGraph.getRoots().iterator();
		while (it.hasNext()) {
			jq_Method method = it.next();
			if (!method.toString().startsWith("<clinit>")) {
				continue;
			}
			jq_Class clz = method.getDeclaringClass();
			List<jq_StaticField> globals = Arrays.asList(clz.getStaticFields());
			for (jq_StaticField global : globals) {
				if (!TypeHelper.h().isRefType(global.getType())) {
					continue;
				}
				GlobalLoc loc = Env.v().getGlobalLoc(global);
				MemNode src = MemNodeFactory.f().get(Env.v().shared, loc);
				Env.v().shared.addCIPANode(src);
				Env.v().shared.addConstructed(src, e);
				CIObj obj = Env.v().getCIPA().pointsTo(global);
				for (Quad site : obj.pts) {
					MemNode tgt = Env.v().getAllocNode(site);
					Env.v().shared.addCIPANode(tgt);
					src.weakUpdate(e, tgt, CstFactory.f().genTrue());
				}
			}
		}
		if (G.debug) {
			System.out
					.println("============= finish constructing shared summary ==============");
			System.out.println("Heap nodes : "
					+ Env.v().shared.getHeapNodesNum());
			System.out.println("Heap edges : "
					+ Env.v().shared.getHeapEdgesNum());
			Dumper.d().dumpHeapToFile(Env.v().shared, "CIPASum");
		}
	}

	/* construct CIPA summary in the shared summary */
	protected void construct1() {
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		Iterator<jq_Method> it = Env.v().s2Iterator();
		while (it.hasNext()) {
			jq_Method method = it.next();
			if (method.toString().startsWith("<clinit>")) {
				jq_Class clz = method.getDeclaringClass();
				List<jq_StaticField> globals = Arrays.asList(clz
						.getStaticFields());
				for (jq_StaticField global : globals) {
					if (!TypeHelper.h().isRefType(global.getType())) {
						continue;
					}
					GlobalLoc loc = Env.v().getGlobalLoc(global);
					MemNode src = MemNodeFactory.f().get(Env.v().shared, loc);
					Env.v().shared.addCIPANode(src);
					Env.v().shared.addConstructed(src, e);
					CIObj obj = Env.v().getCIPA().pointsTo(global);
					for (Quad site : obj.pts) {
						MemNode tgt = Env.v().getAllocNode(site);
						Env.v().shared.addCIPANode(tgt);
						src.weakUpdate(e, tgt, CstFactory.f().genTrue());
					}
				}
			}
		}
		if (G.debug) {
			System.out
					.println("============= finish constructing shared summary ==============");
			System.out.println("Heap nodes : "
					+ Env.v().shared.getHeapNodesNum());
			System.out.println("Heap edges : "
					+ Env.v().shared.getHeapEdgesNum());
			Dumper.d().dumpHeapToFile(Env.v().shared, "CIPASum");
		}
	}

	/* construct CIPA summary in the shared summary */
	protected void construct() {
		/* find all allocations which will be put in shared summary */
		/* and mark them all as allocations in shared summary */
		Set<AllocLoc> sharedAllocs = new HashSet<AllocLoc>();
		Iterator<jq_Method> it = Env.v().s2Iterator();
		while (it.hasNext()) {
			jq_Method method = it.next();
			Set<Quad> worklist = initWorklist1(method);

			// dfs to find all pts.
			HashSet<Quad> visited = new HashSet<Quad>();
			while (!worklist.isEmpty()) {
				Quad worker = worklist.iterator().next();
				Operator op = worker.getOperator();
				worklist.remove(worker);
				if (visited.contains(worker))
					continue;

				jq_Type type = null;
				Set<jq_Field> fields = new HashSet<jq_Field>();
				if (op instanceof New) {
					type = New.getType(worker).getType();
					jq_Class clz = (jq_Class) type;
					fields.addAll(Arrays.asList(clz.getInstanceFields()));
				} else if (op instanceof NewArray) {
					type = NewArray.getType(worker).getType();
					// null for index field.
					fields.add(null);
				} else if (op instanceof MultiNewArray) {
					type = NewArray.getType(worker).getType();
					fields.add(null);
				} else {
					assert false : op;
				}
				visited.add(worker);
				// AllocLoc alloc = Env.v().addSharedAlloc(worker, type);
				AllocLoc alloc = Env.v().getAllocLoc(worker, type);
				sharedAllocs.add(alloc);

				Set<Quad> p2setWrapper = new HashSet<Quad>();
				p2setWrapper.add(worker);
				CIObj src = new CIObj(p2setWrapper);
				for (jq_Field f : fields) {
					if (!Env.v().reachableFields.contains(f))
						continue;
					CIObj tgt = Env.v().getCIPA().pointsTo(src, f);
					for (Quad q : tgt.pts) {
						Operator opq = q.getOperator();
						if (opq instanceof New || opq instanceof NewArray
								|| opq instanceof MultiNewArray) {
							worklist.add(q);
						}
					}
				}
			}
		}

		/* construct the shared summary */
		for (AllocLoc alloc : sharedAllocs) {
			// for each shared allocation, construct its points-to set
			MemNode src = MemNodeFactory.f().get(Env.v().shared, alloc);
			assert src != null : alloc;
			jq_Type type = alloc.getType();
			Quad site = alloc.getSite();
			Set<Quad> srcWrapper = new HashSet<Quad>();
			srcWrapper.add(site);
			CIObj srcObj = new CIObj(srcWrapper);
			if (type instanceof jq_Array) {
				// null for index field.
				FieldSelector sel = Env.v().getIndexFieldSelector();
				CIObj tgtObj = Env.v().getCIPA().pointsTo(srcObj, null);
				assert tgtObj != null : srcObj;
				for (Quad q : tgtObj.pts) {
					MemNode tgt = Env.v().getAllocNode(q);
					src.weakUpdate(sel, tgt, CstFactory.f().genTrue());
				}
			} else {
				assert type instanceof jq_Class : alloc;
				jq_Class clz = (jq_Class) type;
				List<jq_InstanceField> fields = Arrays.asList(clz
						.getInstanceFields());
				for (jq_InstanceField field : fields) {
					if (Env.v().reachableFields.contains(field)) {
						FieldSelector regSel = Env.v().getRegFieldSelector(
								field);
						assert regSel != null : field;
						CIObj tgtObj = Env.v().getCIPA()
								.pointsTo(srcObj, field);
						assert tgtObj != null : srcObj;
						for (Quad q : tgtObj.pts) {
							MemNode tgt = Env.v().getAllocNode(q);
							assert tgt != null : q;
							src.weakUpdate(regSel, tgt, CstFactory.f()
									.genTrue());
						}
					}
				}
			}
		}

		/* some printout information */
		if (G.debug) {
			System.out
					.println("============= finish constructing shared summary ==============");
			System.out.println("Heap nodes : "
					+ Env.v().shared.getHeapNodesNum());
			System.out.println("Heap edges : "
					+ Env.v().shared.getHeapEdgesNum());
			Dumper.d().dumpHeapToFile(Env.v().shared, "CIPASum");
		}
	}

	protected Set<Quad> initWorklist1(jq_Method method) {
		ControlFlowGraph g = method.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = method.getParamTypes();
		int numArgs = method.getParamTypes().length;
		LinkedHashSet<Quad> worklist = new LinkedHashSet<Quad>();
		// collect pts of all stacks objects: param, ret and SF.
		// parameters.
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			jq_Type t = paramTypes[zIdx];
			Register r = rf.get(zIdx);
			if (TypeHelper.h().isRefType(t)) {
				CIObj obj = Env.v().findCIP2Set(r);
				for (Quad alloc : obj.pts) {
					Operator op = alloc.getOperator();
					jq_Type type = null;
					Set<jq_Field> fields = new HashSet<jq_Field>();
					if (op instanceof New) {
						type = New.getType(alloc).getType();
						jq_Class clz = (jq_Class) type;
						fields.addAll(Arrays.asList(clz.getInstanceFields()));
					} else if (op instanceof NewArray) {
						type = NewArray.getType(alloc).getType();
						// null for index field.
						fields.add(null);
					} else {
						assert false : op;
					}
					assert (type != null) : method;
					Set<Quad> p2setWrapper = new HashSet<Quad>();
					p2setWrapper.add(alloc);
					CIObj src = new CIObj(p2setWrapper);
					for (jq_Field f : fields) {
						if (!Env.v().reachableFields.contains(f))
							continue;
						CIObj tgt = Env.v().getCIPA().pointsTo(src, f);
						for (Quad q : tgt.pts) {
							if (q.getOperator() instanceof New
									|| q.getOperator() instanceof NewArray) {
								worklist.add(q);
							}
						}
					}

				}
			}
		}
		// return.
		if (TypeHelper.h().isRefType(method.getReturnType())) {
			// handle return.
			for (BasicBlock bb : g.reversePostOrder()) {
				for (Quad q : bb.getQuads()) {
					Operator op = q.getOperator();
					if (!(op instanceof RETURN_A)) {
						continue;
					}
					Operand operand = Return.getSrc(q);
					// e.g. return "abc";
					if (!(operand instanceof RegisterOperand))
						continue;
					RegisterOperand ret = ((RegisterOperand) operand);
					Register r = ret.getRegister();
					for (Quad alloc : Env.v().findCIP2Set(r).pts) {
						Operator op1 = alloc.getOperator();
						jq_Type type = null;
						Set<jq_Field> fields = new HashSet<jq_Field>();
						if (op1 instanceof New) {
							type = New.getType(alloc).getType();
							jq_Class clz = (jq_Class) type;
							fields.addAll(Arrays.asList(clz.getInstanceFields()));
						} else {
							assert op1 instanceof NewArray : alloc;
							type = NewArray.getType(alloc).getType();
							// null for index field.
							fields.add(null);
						}
						Set<Quad> p2setWrapper = new HashSet<Quad>();
						p2setWrapper.add(alloc);
						CIObj src = new CIObj(p2setWrapper);
						for (jq_Field f : fields) {
							if (!Env.v().reachableFields.contains(f))
								continue;
							CIObj tgt = Env.v().getCIPA().pointsTo(src, f);
							for (Quad q1 : tgt.pts) {
								if (q1.getOperator() instanceof New
										|| q1.getOperator() instanceof NewArray) {
									worklist.add(q1);
								}
							}
						}
					}
				}
			}
		}
		// static field, if any.
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		BoolExpr cst = CstFactory.f().genTrue();
		if (method.toString().startsWith("<clinit>")) {
			jq_Class clz = method.getDeclaringClass();
			List<jq_StaticField> globals = Arrays.asList(clz.getStaticFields());
			for (jq_StaticField global : globals) {
				if (!TypeHelper.h().isRefType(global.getType())) {
					continue;
				}
				GlobalLoc loc = Env.v().getGlobalLoc(global);
				MemNode src = MemNodeFactory.f().get(Env.v().shared, loc);
				CIObj obj = Env.v().getCIPA().pointsTo(global);
				for (Quad site : obj.pts) {
					if (site.getOperator() instanceof New) {
						jq_Type type = New.getType(site).getType();
						AllocLoc alloc = Env.v().getAllocLoc(site, type);
						MemNode tgt = MemNodeFactory.f().get(Env.v().shared,
								alloc);
						src.weakUpdate(e, tgt, cst);
						worklist.add(site);
					} else if (site.getOperator() instanceof NewArray) {
						jq_Type type = NewArray.getType(site).getType();
						AllocLoc alloc = Env.v().getAllocLoc(site, type);
						MemNode tgt = MemNodeFactory.f().get(Env.v().shared,
								alloc);
						src.weakUpdate(e, tgt, cst);
						worklist.add(site);
					} else {
						assert false : site;
					}
				}
			}

		}
		return worklist;
	}

	protected Set<Quad> initWorklist(jq_Method method) {
		ControlFlowGraph g = method.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = method.getParamTypes();
		int numArgs = method.getParamTypes().length;
		LinkedHashSet<Quad> worklist = new LinkedHashSet<Quad>();
		// collect pts of all stacks objects: param, ret and SF.
		// parameters.
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			jq_Type t = paramTypes[zIdx];
			Register r = rf.get(zIdx);
			if (TypeHelper.h().isRefType(t)) {
				CIObj obj = Env.v().findCIP2Set(r);
				for (Quad alloc : obj.pts) {
					if (alloc.getOperator() instanceof New
							|| alloc.getOperator() instanceof NewArray) {
						worklist.add(alloc);
					}
				}
			}
		}

		// return.
		if (method.getReturnType().isReferenceType()) {
			// handle return.
			for (BasicBlock bb : g.reversePostOrder()) {
				for (Quad q : bb.getQuads()) {
					Operator op = q.getOperator();
					if (!(op instanceof Return))
						continue;

					Operand operand = Return.getSrc(q);
					// e.g. return "abc";
					if (!(operand instanceof RegisterOperand))
						continue;
					RegisterOperand ret = ((RegisterOperand) operand);
					Register retV = ret.getRegister();
					for (Quad alloc : Env.v().findCIP2Set(retV).pts) {
						if (alloc.getOperator() instanceof New
								|| alloc.getOperator() instanceof NewArray) {
							worklist.add(alloc);
						}
					}
				}
			}
		}

		// static field, if any.
		if (method.toString().startsWith("<clinit>")) {
			jq_Class clz = method.getDeclaringClass();
			List<jq_StaticField> staticFields = Arrays.asList(clz
					.getStaticFields());
			for (jq_StaticField sf : staticFields) {
				CIObj obj = Env.v().getCIPA().pointsTo(sf);
				for (Quad alloc : obj.pts) {
					if (alloc.getOperator() instanceof New
							|| alloc.getOperator() instanceof NewArray) {
						worklist.add(alloc);
					}
				}
			}

		}
		return worklist;
	}

}
