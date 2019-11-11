package framework.scuba.controller;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CIObj;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AbsMemLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.PrimitiveLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.S2MMemGraph;
import framework.scuba.domain.summary.Env;
import framework.scuba.helper.MemLocHelper;
import framework.scuba.helper.TypeHelper;

public class S2MMemGraphController extends MMemGraphController {

	public void construct() {
		S2MMemGraph graph = (S2MMemGraph) memGraph;
		// construct the summary for an S2 method only once
		if (graph.constructed()) {
			return;
		}
		// construct the summary of this S2 method
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		BoolExpr cst = CstFactory.f().genTrue();
		// formals
		Iterator<AbsMemLoc> it = graph.getFormalLocs().iterator();
		while (it.hasNext()) {
			AbsMemLoc absLoc = it.next();
			if (absLoc instanceof ParamLoc) {
				ParamLoc param = (ParamLoc) absLoc;
				Register r = param.getRegister();
				CIObj obj = Env.v().findCIP2Set(r);
				MemLoc loc = MemLocHelper.h().getDefault(param, e, false);
				// creating the source node in the summary of this S2 method
				MemNode src = MemNodeFactory.f().get(graph, loc);
				for (Quad quad : obj.pts) {
					Set<Quad> wrapper = new HashSet<Quad>();
					wrapper.add(quad);
					Operator op = quad.getOperator();
					jq_Type t = null;
					Set<jq_Field> fields = new HashSet<jq_Field>();
					if (op instanceof New) {
						t = New.getType(quad).getType();
						jq_Class clz = (jq_Class) t;
						fields.addAll(Arrays.asList(clz.getInstanceFields()));
					} else if (op instanceof NewArray) {
						t = NewArray.getType(quad).getType();
						fields.add(null);
					}
					assert (t != null) : op;
					CIObj o1 = new CIObj(wrapper);
					for (jq_Field f : fields) {
						if (!Env.v().reachableFields.contains(f)) {
							continue;
						}
						// creating the target node in shared summary
						CIObj o2 = Env.v().getCIPA().pointsTo(o1, f);
						for (Quad site : o2.pts) {
							MemNode tgt = Env.v().getAllocNode(site);
							Env.v().shared.addCIPANode(tgt);
							FieldSelector field = Env.v()
									.getRegFieldSelector(f);
							// connect to the shared summary
							src.weakUpdate(field, tgt, cst);
						}
					}
				}
			} else {
				assert (absLoc instanceof PrimitiveLoc) : absLoc;
			}
		}
		// handle return.
		ControlFlowGraph g = graph.getMethod().getCFG();
		if (TypeHelper.h().isRefType(graph.getMethod().getReturnType())) {
			for (BasicBlock bb : g.reversePostOrder()) {
				for (Quad q : bb.getQuads()) {
					Operator op = q.getOperator();
					if (!(op instanceof RETURN_A)) {
						continue;
					}
					Operand operand = Return.getSrc(q);
					// e.g. return "abc";
					if (!(operand instanceof RegisterOperand)) {
						continue;
					}
					// initialize the return value if there is a return value
					graph.setRetLoc(Env.v().getRetLoc(graph.getMethod()));
					// weak update the return value
					RegisterOperand ret = ((RegisterOperand) operand);
					Register r = ret.getRegister();
					RetLoc retLoc = graph.getRetLoc();
					// creating the target node in shared summary
					for (Quad site : Env.v().findCIP2Set(r).pts) {
						if (site.getOperator() instanceof New
								|| site.getOperator() instanceof NewArray) {
							MemNode src = MemNodeFactory.f().get(graph, retLoc);
							MemNode tgt = Env.v().getAllocNode(site);
							Env.v().shared.addCIPANode(tgt);
							src.weakUpdate(e, tgt, cst);
						} else {
							assert false : site;
						}
					}
				}
			}
		}
		// set this method as constructed to avoid being constructed again
		graph.set(true);
	}

	@Override
	public void generateSummaryFromHeap() {
		Iterator<MemEdge> it = memGraph.heapEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			memGraph.addSumEdge(edge);
			memGraph.addSumNode(edge.getSrc());
			memGraph.addSumNode(edge.getTgt());
		}
	}

	@Override
	public void setFormals() {
		jq_Method meth = memGraph.getMethod();
		ControlFlowGraph g = meth.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = meth.getParamTypes();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			jq_Type t = paramTypes[zIdx];
			Register r = rf.get(zIdx);
			if (TypeHelper.h().isRefType(t)) {
				MemLoc loc = Env.v().getParamLoc(r, meth, t);
				memGraph.addFormal(loc);
			} else {
				PrimitiveLoc loc = Env.v().getPrimitiveLoc();
				memGraph.addFormal(loc);
			}
		}
	}

}
