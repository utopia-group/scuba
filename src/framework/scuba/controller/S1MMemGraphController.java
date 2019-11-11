package framework.scuba.controller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.LocalVarLocFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.field.IndexFieldSelector;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.MemLocP2SetWrapper;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.PrimitiveLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.memgraph.MMemGraph;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.memgraph.P2Set;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.CallGraphHelper;
import framework.scuba.helper.Checker;
import framework.scuba.helper.G;
import framework.scuba.helper.MemGraphHelper;
import framework.scuba.helper.MemLocHelper;
import framework.scuba.helper.TypeHelper;
import framework.scuba.utils.ChordUtil;

public class S1MMemGraphController extends MMemGraphController {

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

	/* --------------------- instruction handlers --------------------- */
	// parameter assign
	public boolean handleParamAssignStmt(jq_Method meth, Register lr,
			jq_Type lt, SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		MemNode v1 = null, v2 = null;
		// generate memory location for lhs
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);
		p2Setv2.join(v2.getP2Set(e));

		// include the targets in current P2Set
		P2Set currP2Set = v2.getP2Set(e);
		p2Setv2.join(currP2Set);

		return v1.weakUpdate(e, p2Setv2);
	}

	// v1 = v2
	public boolean handleAssignStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		MemNode v1 = null, v2 = null;
		// generate memory location for lhs
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		} else if (lvt == SummariesEnv.LocType.PARAMETER) {
			v1 = Env.v().getParamNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);

		return v1.weakUpdate(e, p2Setv2);
	}

	// v1 = CHECKCAST(v2)
	public boolean handleCheckCastStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		return handleAssignStmt(meth, lr, lt, lvt, rr, rt, rvt);
	}

	// v1 = v2.f
	public boolean handleLoadStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt, jq_Field rf, boolean notSmash) {
		MemNode v1 = null, v2 = null;
		// generate the memory location for lhs
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs's base
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		} else if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);
		FieldSelector field = null;
		if (Env.v().isMergedField(rf)) {
			field = Env.v().getMergedFieldSelector(rf);
		} else {
			field = Env.v().getRegFieldSelector(rf);
		}
		assert (field != null);
		P2Set p2Setv2f = lookup(p2Setv2, field, notSmash);
		return v1.weakUpdate(e, p2Setv2f);
	}

	// v1 = v2[0] where v2 is an array, e.g. v2 = new X[10][10]
	public boolean handleALoadStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		MemNode v1 = null, v2 = null;
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);
		// TODO
		Checker.c().checkMemNodeIsArrayType(v2);
		IndexFieldSelector index = Env.v().getIndexFieldSelector();
		P2Set p2Setv2i = lookup(p2Setv2, index);
		return v1.weakUpdate(e, p2Setv2i);
	}

	// v1 = A.f
	public boolean handleStatLoadStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, jq_Field rf) {
		MemNode v1 = null, v2 = null;
		// generate the memory location for lhs
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs's base
		v2 = Env.v().getGlobalNode(memGraph, rf);
		assert (v2 != null) : rf;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);

		return v1.weakUpdate(e, p2Setv2);
	}

	// v1[0] = v2 where v1 = new V[10][10]
	public boolean handleAStoreStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		boolean ret = false;

		MemNode v1 = null, v2 = null;
		// generate the memory location for rhs
		if (lvt == SummariesEnv.LocType.PARAMETER) {
			v1 = Env.v().getParamNode(meth, lr, lt, memGraph);
		} else if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs's base
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv1 = lookup(v1, e);
		// TODO
		Checker.c().checkMemNodeIsArrayType(v1);
		IndexFieldSelector index = Env.v().getIndexFieldSelector();

		for (MemNode node : p2Setv1.keySet()) {
			BoolExpr cst = p2Setv1.get(node);
			P2Set p2Setv2 = lookup(v2, e);
			p2Setv2.project(cst);
			ret = ret | node.weakUpdate(index, p2Setv2);
		}
		return ret;
	}

	// v1.f = v2
	public boolean handleStoreStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, jq_Field lf, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		boolean ret = false;

		MemNode v1 = null, v2 = null;
		// generate the memory location for lhs
		if (lvt == SummariesEnv.LocType.PARAMETER) {
			v1 = Env.v().getParamNode(meth, lr, lt, memGraph);
		} else if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v1 = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v1 != null) : lr;
		// generate the memory location for rhs's base
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv1 = lookup(v1, e);

		FieldSelector f = null;
		if (Env.v().isMergedField(lf)) {
			f = Env.v().getMergedFieldSelector(lf);
		} else {
			f = Env.v().getRegFieldSelector(lf);
		}
		assert (f != null);
		for (MemNode node : p2Setv1.keySet()) {
			BoolExpr cst = p2Setv1.get(node);
			P2Set p2Setv2 = lookup(v2, e);
			p2Setv2.project(cst);
			ret = ret | node.weakUpdate(f, p2Setv2);
		}
		return ret;
	}

	// A.f = v2
	public boolean handleStaticStoreStmt(jq_Method meth, jq_Field lf,
			Register rr, jq_Type rt, SummariesEnv.LocType rvt) {
		// generate the memory location for lhs's base
		MemNode v1 = Env.v().getGlobalNode(memGraph, lf);
		assert (v1 != null) : lf;
		MemNode v2 = null;
		// generate the memory location for rhs
		if (rvt == SummariesEnv.LocType.PARAMETER) {
			v2 = Env.v().getParamNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v2 = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		}
		assert (v2 != null) : rr;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Setv2 = lookup(v2, e);

		return v1.weakUpdate(e, p2Setv2);
	}

	// v = new T
	public boolean handleNewStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Quad stmt) {
		// generate the allocElem for rhs
		jq_Type type = MemLocHelper.h().getAllocType(stmt);
		MemNode allocT = Env.v().getAllocNode(memGraph, stmt, type);
		assert (allocT != null) : stmt;
		// generate the localVarElem for lhs
		MemNode v = null;
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v != null) : lr;

		P2Set p2SetT = new P2Set(allocT, CstFactory.f().genTrue());

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		return v.weakUpdate(e, p2SetT);
	}

	// X x1 = new X[10] by calling handleMultiNewArrayStmt method with dim = 1
	public boolean handleNewArrayStmt(jq_Method meth, Register lr, jq_Type lt,
			SummariesEnv.LocType lvt, Quad stmt) {
		return handleMultiNewArrayStmt(meth, lr, lt, lvt, 1, stmt);
	}

	// this method is just a helper method for handling array allocations
	private boolean handleArrayLoad(MemNode left, IndexFieldSelector index,
			MemNode right) {
		P2Set p2Set = new P2Set(right, CstFactory.f().genTrue());
		return left.weakUpdate(index, p2Set);
	}

	// handle multi-new statement, e.g. X x1 = new X[1][2][3]
	// dim is the dimension of this array, dim >= 2
	public boolean handleMultiNewArrayStmt(jq_Method meth, Register lr,
			jq_Type lt, SummariesEnv.LocType lvt, int dim, Quad stmt) {
		boolean ret = false;

		MemNode v = null;
		// generate the localVarElem for lhs
		if (lvt == SummariesEnv.LocType.LOCAL_VAR) {
			v = Env.v().getLocalVarNode(meth, lr, lt, memGraph);
		}
		assert (v != null) : lr;

		// generate the ArrayAllocElem for rhs
		jq_Type type = MemLocHelper.h().getAllocType(stmt);
		MemNode allocT = Env.v().getAllocNode(memGraph, stmt, type);
		assert (allocT != null) : stmt;

		BoolExpr t = CstFactory.f().genTrue();
		P2Set p2SetT = new P2Set(allocT, t);
		FieldSelector e = Env.v().getEpsilonFieldSelector();

		boolean changed = v.weakUpdate(e, p2SetT);
		ret = ret | changed;

		int i = dim;
		jq_Type lt1 = type;
		jq_Type rt1 = null;
		IndexFieldSelector index = Env.v().getIndexFieldSelector();
		while (i >= 2) {
			assert (lt1 instanceof jq_Array) : lt1;
			rt1 = ((jq_Array) lt1).getElementType();
			MemNode leftAllocT = Env.v().getAllocNode(memGraph, stmt, lt1);
			MemNode rightAllocT = Env.v().getAllocNode(memGraph, stmt, rt1);
			changed = handleArrayLoad(leftAllocT, index, rightAllocT);
			ret = ret | changed;

			lt1 = ((jq_Array) lt1).getElementType();
			assert (lt1 instanceof jq_Array) : lt1;
			i--;
		}

		return ret;
	}

	// return v;
	public boolean handleRetStmt(jq_Method meth, Register rr, jq_Type rt,
			SummariesEnv.LocType rvt) {
		MemNode v = null;
		if (rvt == SummariesEnv.LocType.LOCAL_VAR) {
			v = Env.v().getLocalVarNode(meth, rr, rt, memGraph);
		} else if (rvt == SummariesEnv.LocType.PARAMETER) {
			v = Env.v().getParamNode(meth, rr, rt, memGraph);
		}
		assert (v != null) : rr;
		// create return value element (only one return value for one method)
		MemNode retNode = Env.v().getRetNode(meth, memGraph);
		RetLoc retLoc = Env.v().getRetLoc(meth);
		assert (retNode != null) : meth;

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2Set = lookup(v, e);
		memGraph.setRetLoc(retLoc);

		return retNode.weakUpdate(e, p2Set);
	}

	// method call (lr: receiver)
	public boolean handleInvokeStmt(jq_Method meth, Quad callsite, Register lr,
			jq_Type lt, SummariesEnv.LocType lvt, List<Register> rrs,
			List<jq_Type> rts, List<SummariesEnv.LocType> rvts) {
		boolean ret = false;
		// instantiation
		Set<Pair<jq_Method, BoolExpr>> tgtCstPairs = rslvTargets4Callsite(callsite);
		// FIXME
		Env.v().multiCallees = (tgtCstPairs.size() > 1);
		for (Pair<jq_Method, BoolExpr> tgtCstPair : tgtCstPairs) {
			jq_Method callee = tgtCstPair.val0;
			BoolExpr hasType = tgtCstPair.val1;
			if (G.instn) {
				System.out.println("[Instn details] " + "[caller] " + meth
						+ "(" + G.dumpMap.get(meth) + ")" + " [callee] "
						+ callee + "(" + G.dumpMap.get(callee) + ")");
			}
			if (SummariesEnv.v().cipaSum) {
				System.out.println("caller : " + " S1: "
						+ Env.v().isS1Method(meth) + " S2: "
						+ Env.v().isS2Method(meth) + " S3: "
						+ Env.v().isS3Method(meth));
				System.out.println("callee : " + " S1: "
						+ Env.v().isS1Method(callee) + " S2: "
						+ Env.v().isS2Method(callee) + " S3: "
						+ Env.v().isS3Method(callee));
				if (Env.v().isS1Method(meth)) {
					if (ChordUtil.hasSideEffect(callee))
						assert (!Env.v().isS3Method(callee)) : meth + " "
								+ callee;
					// ignore s3.
					if (Env.v().isS3Method(callee))
						continue;

					// ignore lib methods w/o side effect.
					if (ChordUtil.isLibMeth(callee)
							&& !ChordUtil.hasSideEffect(callee))
						continue;
				}
			}
			MMemGraph calleeMemGraph = SummariesEnv.v().getMMemGraph(callee);
			if (calleeMemGraph == null) {
				continue;
			}
			if (SummariesEnv.v().autoInstn) {
				instnCtrlor.setEverything(callsite, callee, hasType);
				instnCtrlor.initFactories();
				instnCtrlor.initBasicMapping(meth, callsite, lr, lt, lvt, rrs,
						rts, rvts);
				ret = ret | instnCtrlor.instn();
			} else {
				assert false;
			}
		}
		return ret;
	}

	/* ------------------- S1 high-level models ------------------ */
	public void genericAdd() {
		jq_Method meth = memGraph.getMethod();
		jq_Field header = Env.v().getFieldByName(
				"header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
		if (header == null) {
			System.out.println("[WARN] Unreachable field for header");
			return;
		}
		RegFieldSelector headerSel = Env.v().getRegFieldSelector(header);
		jq_Field element = Env.v().getFieldByName(
				"element:Ljava/lang/Object;@java.util.LinkedList$Entry");
		if (element == null) {
			System.out.println("[WARN] Unreachable field for element");
			return;
		}
		RegFieldSelector elementSel = Env.v().getRegFieldSelector(element);
		FieldSelector e = Env.v().getEpsilonFieldSelector();

		// r0.header.element = r1
		ControlFlowGraph g = meth.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = meth.getParamTypes();
		Register r0 = rf.get(0);
		Register r1 = rf.get(1);
		jq_Type t0 = paramTypes[0];
		jq_Type t1 = paramTypes[1];

		MemNode v0 = Env.v().getParamNode(meth, r0, t0, memGraph);
		MemNode v1 = Env.v().getParamNode(meth, r1, t1, memGraph);

		assert (v0 != null) : r0;
		assert (v1 != null) : r1;

		P2Set P2Setv0 = lookup(v0, e);
		P2Set P2Seth = lookup(P2Setv0, headerSel);
		P2Set p2Setv1 = lookup(v1, e);

		for (MemNode node : P2Seth.keySet()) {
			node.weakUpdate(elementSel, p2Setv1);
		}
	}
	
	//org/hsqldb/store/BaseHashMap
	public void addForBaseMap() {
		jq_Method meth = memGraph.getMethod();

		jq_Field keyTable = Env
				.v()
				.getFieldByName(
						"objectKeyTable:[Ljava/lang/Object;@org.hsqldb.store.BaseHashMap");
		jq_Field valTable = Env
				.v()
				.getFieldByName(
						"objectValueTable:[Ljava/lang/Object;@org.hsqldb.store.BaseHashMap");
		if (keyTable == null || valTable == null) {
			System.out.println("[WARN] Unreachable field for header");
			return;
		}
		RegFieldSelector keyTableSel = Env.v().getRegFieldSelector(keyTable);
		RegFieldSelector valTableSel = Env.v().getRegFieldSelector(valTable);

		FieldSelector e = Env.v().getEpsilonFieldSelector();
		FieldSelector idx = Env.v().getIndexFieldSelector();

		// r0.header.element = r1
		ControlFlowGraph g = meth.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = meth.getParamTypes();
		Register r0 = rf.get(0);
		Register r3 = rf.get(3);
		Register r4 = rf.get(4);

		jq_Type t0 = paramTypes[0];
		jq_Type t3 = paramTypes[3];
		jq_Type t4 = paramTypes[4];

		MemNode v0 = Env.v().getParamNode(meth, r0, t0, memGraph);
		MemNode v3 = Env.v().getParamNode(meth, r3, t3, memGraph);
		MemNode v4 = Env.v().getParamNode(meth, r4, t4, memGraph);

		assert (v0 != null) : r0;
		assert (v3 != null) : r3;
		assert (v4 != null) : r4;

		P2Set P2Setv0 = lookup(v0, e);
		P2Set P2SetKeys = lookup(P2Setv0, keyTableSel);
		P2Set P2SetVals = lookup(P2Setv0, valTableSel);

		P2Set p2Setv3 = lookup(v3, e);
		P2Set p2Setv4 = lookup(v4, e);

		for (MemNode node : P2SetKeys.keySet()) {
			node.weakUpdate(idx, p2Setv3);
		}
		
		for (MemNode node : P2SetVals.keySet()) {
			node.weakUpdate(idx, p2Setv4);
		}
		
		// create return value element (only one return value for one method)
		MemNode retNode = Env.v().getRetNode(meth, memGraph);
		RetLoc retLoc = Env.v().getRetLoc(meth);
		assert (retNode != null) : meth;
		memGraph.setRetLoc(retLoc);
		retNode.weakUpdate(e, lookup(P2SetVals, idx));
	}

	public void genericGet() {
		jq_Method meth = memGraph.getMethod();
		jq_Field header = Env.v().getFieldByName(
				"header:Ljava/util/LinkedList$Entry;@java.util.LinkedList");
		if (header == null) {
			System.out.println("[WARN]Unreachable field for header");
			return;
		}
		RegFieldSelector headerSel = Env.v().getRegFieldSelector(header);
		jq_Field element = Env.v().getFieldByName(
				"element:Ljava/lang/Object;@java.util.LinkedList$Entry");
		if (element == null) {
			System.out.println("[WARN]Unreachable field for element");
			return;
		}
		RegFieldSelector elementSel = Env.v().getRegFieldSelector(element);
		FieldSelector e = Env.v().getEpsilonFieldSelector();

		// r0.header.element = r1
		ControlFlowGraph g = meth.getCFG();
		RegisterFactory rf = g.getRegisterFactory();
		jq_Type[] paramTypes = meth.getParamTypes();
		Register r0 = rf.get(0);
		jq_Type t0 = paramTypes[0];
		MemNode v0 = Env.v().getParamNode(meth, r0, t0, memGraph);
		assert (v0 != null) : r0;

		P2Set P2Setv0 = lookup(v0, e);
		assert !P2Setv0.isEmpty() : v0;
		P2Set P2Seth = lookup(P2Setv0, headerSel);
		assert !P2Seth.isEmpty() : P2Setv0;
		P2Set p2SetElem = lookup(P2Seth, elementSel);
		assert !p2SetElem.isEmpty() : P2Seth;

		// create return value element (only one return value for one method)
		MemNode retNode = Env.v().getRetNode(meth, memGraph);
		RetLoc retLoc = Env.v().getRetLoc(meth);
		assert (retNode != null) : meth;
		memGraph.setRetLoc(retLoc);
		retNode.weakUpdate(e, p2SetElem);
	}

	// -------------- Method Call Related -----------------
	protected Set<Pair<jq_Method, BoolExpr>> rslvTargets4Callsite(Quad callsite) {
		Set<Pair<jq_Method, BoolExpr>> ret = new HashSet<Pair<jq_Method, BoolExpr>>();
		/* write codes to resolve the targets and their constraints */
		BoolExpr cst = CstFactory.f().genTrue();
		Set<jq_Method> tgtSet = Env.v().cg.getTargets(callsite);
		// include stativinvoke, vitualinvoke with one target.
		if (tgtSet.size() == 1) {
			jq_Method callee = tgtSet.iterator().next();
			ret.add(new Pair<jq_Method, BoolExpr>(callee, cst));
			return ret;
		} else {
			// virtualinvoke
			if (tgtSet.size() == 0) {
				return ret;
			}
			jq_Method caller = callsite.getMethod();

			RegisterOperand ro = Invoke.getParam(callsite, 0);
			Register recv = ro.getRegister();
			jq_Type recvTp = ro.getType();
			MemNode vNode = null;
			SummariesEnv.LocType vt = MemLocHelper.h().getVarType(caller, recv);
			// generate memory location for lhs
			if (vt == SummariesEnv.LocType.LOCAL_VAR)
				vNode = Env.v().getLocalVarNode(caller, recv, recvTp, memGraph);
			else if (vt == SummariesEnv.LocType.PARAMETER)
				vNode = Env.v().getParamNode(caller, recv, recvTp, memGraph);

			EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
			P2Set from = lookup(vNode, e);
			MemLocP2SetWrapper p2Set = MemGraphHelper.h()
					.convertP2SetToMemLocP2SetWrapper(from);
			// generate pt-set for the receiver
			for (jq_Method tgt : tgtSet) {
				// generate constraint for each potential target
				jq_Class tgtType = tgt.getDeclaringClass();
				if (Env.v().widening) {
					cst = CstFactory.f().genTrue();
				} else if (p2Set.isEmpty()) {
					cst = CstFactory.f().genFalse();
				} else {
					cst = CstFactory.f().genCst(p2Set, tgt, tgtType, tgtSet);
				}
				ret.add(new Pair<jq_Method, BoolExpr>(tgt, cst));
			}
		}
		return ret;
	}

	// -------------- Generating Summary of MemGraph -----------------
	public void generateSummaryFromHeap() {
		memGraph.clearAppLocalNodes();
		memGraph.clearSumNodes();
		memGraph.clearSumEdges();
		if (SummariesEnv.v().shareSum) {
			addSumNodes1();
			addSumEdges1();
			refine();
		} else {
			addSumNodes();
			addSumEdges();
			refine();
		}
	}

	protected void addSumNodes1() {
		Set<ParamLoc> makeups = new HashSet<ParamLoc>();
		Set<MemNode> visited = new HashSet<MemNode>();
		if (!CallGraphHelper.h().isTopLevelMethod(memGraph.getMethod())) {
			Iterator<MemEdge> it = memGraph.heapEdgesIterator();
			while (it.hasNext()) {
				MemEdge edge = it.next();
				MemNode node = edge.getSrc();
				if (!visited.contains(node)) {
					if (MemGraphHelper.h().isSumNode(node)) {
						memGraph.addSumNode(node);
					} else if (node.isAppLocalVarNode()) {
						assert (!node.toProp()) : node;
						logNonPropLocal(node, makeups);
					}
					visited.add(node);
				}
				node = edge.getTgt();
				if (!visited.contains(node)) {
					if (MemGraphHelper.h().isSumNode(node)) {
						memGraph.addSumNode(node);
					} else if (node.isAppLocalVarNode()) {
						assert (!node.toProp()) : node;
						logNonPropLocal(node, makeups);
					}
					visited.add(node);
				}
			}
			/* make up some parameters to propagate */
			for (ParamLoc param : makeups) {
				makeup(param);
			}
		} else {
			Iterator<MemEdge> it = memGraph.heapEdgesIterator();
			while (it.hasNext()) {
				MemEdge edge = it.next();
				MemNode node = edge.getSrc();
				if (!visited.contains(node)) {
					if (MemGraphHelper.h().isTopLevelSumNode(node)) {
						memGraph.addSumNode(node);
					} else if (node.isAppLocalVarNode()) {
						logNonPropLocal(node, makeups);
					}
					visited.add(node);
				}
				node = edge.getTgt();
				if (!visited.contains(node)) {
					if (MemGraphHelper.h().isTopLevelSumNode(node)) {
						memGraph.addSumNode(node);
					} else if (node.isAppLocalVarNode()) {
						assert (!node.toProp()) : node;
						logNonPropLocal(node, makeups);
					}
					visited.add(node);
				}
			}
			for (ParamLoc param : makeups) {
				makeup(param);
			}
		}
	}

	protected void addSumEdges1() {
		Set<ParamLoc> makeups = new HashSet<ParamLoc>();
		Iterator<MemEdge> it = memGraph.heapEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			if (memGraph.hasSumNode(src)) {
				if (src.isAppLocalVarNode()) {
					if (tgt.isAllocNode() || tgt.isGlobalAPNode()) {
						BoolExpr cst = edge.getCst();
						logLocal(src, tgt, cst, makeups);
					} else {
						if (memGraph.hasSumNode(tgt)) {
							if (SummariesEnv.v().cleanDupAccessPath) {
								if (notDupEdge(edge))
									memGraph.addSumEdge(edge);
							} else {
								memGraph.addSumEdge(edge);
							}
						}
					}
				} else {
					if (memGraph.hasSumNode(tgt)) {
						if (SummariesEnv.v().cleanDupAccessPath) {
							if (notDupEdge(edge))
								memGraph.addSumEdge(edge);
						} else {
							memGraph.addSumEdge(edge);
						}
					}
				}
			}
		}
		// make up
		for (ParamLoc param : makeups) {
			makeup(param);
		}
	}

	protected boolean notDupEdge(MemEdge edge) {
		MemNode src = edge.getSrc();
		MemNode tgt = edge.getTgt();
		if (src.isLocalAPNode()) {
			LocalAccessPathLoc loc = (LocalAccessPathLoc) src.getLocs()
					.iterator().next();
			if (loc.toString().matches(".*header.*(next|previous).*element.*")) {
				return false;
			}
		}
		if (tgt.isLocalAPNode()) {
			LocalAccessPathLoc loc = (LocalAccessPathLoc) tgt.getLocs()
					.iterator().next();
			if (loc.toString().matches(".*header.*(next|previous).*element.*")) {
				return false;
			}
		}
		return true;
	}

	protected void addSumEdges() {
		Set<ParamLoc> makeups = new HashSet<ParamLoc>();
		Iterator<MemNode> it = memGraph.sumNodesIterator();
		while (it.hasNext()) {
			MemNode node = it.next();
			Iterator<MemGraph> it1 = node.outgoingGraphsIterator();
			while (it1.hasNext()) {
				MemGraph graph = it1.next();
				assert (graph == this.memGraph);
				Iterator<MemEdge> it2 = node.outgoingEdgesIterator(graph);
				while (it2.hasNext()) {
					assert (!node.isLibLocalVarNode()) : node;
					assert (!node.isAppLocalVarNode() || node.toProp()) : node;
					if (node.isAppLocalVarNode()) {
						MemEdge edge = it2.next();
						MemNode tgt = edge.getTgt();
						if (tgt.isAllocNode() || tgt.isGlobalAPNode()) {
							BoolExpr cst = edge.getCst();
							logLocal(node, tgt, cst, makeups);
						} else {
							if (graph.hasSumNode(tgt)) {
								graph.addSumEdge(edge);
							}
						}
					} else {
						MemEdge edge = it2.next();
						MemNode tgt = edge.getTgt();
						if (graph.hasSumNode(tgt)) {
							graph.addSumEdge(edge);
						}
					}
				}
			}
		}
		// make up
		for (ParamLoc param : makeups) {
			makeup(param);
		}
	}

	protected void addSumNodes() {
		if (!CallGraphHelper.h().isTopLevelMethod(memGraph.getMethod())) {
			Set<ParamLoc> makeups = new HashSet<ParamLoc>();
			for (Iterator<MemNode> it = memGraph.heapNodesIterator(); it
					.hasNext();) {
				MemNode node = it.next();
				if (MemGraphHelper.h().isSumNode(node)) {
					memGraph.addSumNode(node);
				} else if (node.isAppLocalVarNode()) {
					assert (!node.toProp()) : node;
					logNonPropLocal(node, makeups);
				}
			}
			/* make up some parameters to propagate */
			for (ParamLoc param : makeups) {
				makeup(param);
			}
		} else {
			Set<ParamLoc> makeups = new HashSet<ParamLoc>();
			for (Iterator<MemNode> it = memGraph.heapNodesIterator(); it
					.hasNext();) {
				MemNode node = it.next();
				if (MemGraphHelper.h().isTopLevelSumNode(node)) {
					memGraph.addSumNode(node);
				} else if (node.isAppLocalVarNode()) {
					logNonPropLocal(node, makeups);
				}
			}
			for (ParamLoc param : makeups) {
				makeup(param);
			}
		}
	}

	protected void refine() {
		// refine the summary nodes
		memGraph.clearSumNodes();
		Iterator<MemEdge> it = memGraph.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			memGraph.addSumNode(src);
			memGraph.addSumNode(tgt);
		}
	}

	private void logLocal(MemNode src, MemNode tgt, BoolExpr cst,
			Set<ParamLoc> makeups) {
		Set<MemLoc> srcLocs = src.getLocs();
		assert (srcLocs.size() == 1) : srcLocs;
		MemLoc srcLoc = srcLocs.iterator().next();
		assert (srcLoc instanceof LocalVarLoc) : srcLoc;
		Register r = ((LocalVarLoc) srcLoc).getRegister();
		assert (Env.v().isAppLocal(r)) : srcLoc;
		assert (!Env.v().isPropLocal(r)) : srcLoc;
		Set<MemLoc> tgtLocs = tgt.getLocs();
		assert (tgtLocs.size() == 1) : tgtLocs;
		MemLoc tgtLoc = tgtLocs.iterator().next();
		assert (tgtLoc instanceof AllocLoc || tgtLoc instanceof GlobalAccessPathLoc) : tgtLoc;
		Env.v().scubaResult.add(r, tgtLoc, cst);
		/* make up parameters encoded in the constraint */
		Set<AccessPathObject> aps = CstFactory.f().getAPsFromCst(cst);
		for (AccessPathObject ap : aps) {
			if (ap instanceof LocalAccessPathLoc) {
				MemLoc base = ap.getBase();
				assert (base instanceof ParamLoc) : base;
				ParamLoc param = (ParamLoc) base;
				Register r2 = param.getRegister();
				if (Env.v().isPropLocal(r2)) {
					continue;
				}
				// makeup(param);
				makeups.add(param);
			} else if (ap instanceof GlobalAccessPathLoc) {
				// do nothing
			} else {
				assert false;
			}
		}
	}

	private void logNonPropLocal(MemNode node, Set<ParamLoc> makeups) {
		/* log results for locals */
		Set<MemLoc> locs = node.getLocs();
		assert (locs.size() == 1) : locs;
		MemLoc loc = locs.iterator().next();
		assert (loc instanceof LocalVarLoc) : loc;
		Register r = ((LocalVarLoc) loc).getRegister();
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		P2Set p2set = node.getP2Set(e);
		MemLocP2SetWrapper wrapper = MemGraphHelper.h()
				.convertP2SetToMemLocP2SetWrapper(p2set);
		Env.v().scubaResult.add(r, wrapper);
		addToMakeups(r, wrapper, makeups);
	}

	private void addToMakeups(Register r, MemLocP2SetWrapper wrapper,
			Set<ParamLoc> makeups) {
		for (MemLoc loc : wrapper.keySet()) {
			if (loc instanceof LocalAccessPathLoc) {
				// make up registers wrapped in targets
				MemLoc base = ((LocalAccessPathLoc) loc).getBase();
				assert (base instanceof ParamLoc) : base + " " + loc;
				ParamLoc param = (ParamLoc) base;
				Register r2 = param.getRegister();
				if (Env.v().isPropLocal(r2)) {
					continue;
				}
				makeups.add(param);
			} else if (loc instanceof GlobalAccessPathLoc) {
				// do nothing
			} else if (loc instanceof AllocLoc) {
				// do nothing
			} else {
				assert false : r + " " + loc;
			}
			// make up registers wrapped in constraints
			BoolExpr cst = wrapper.get(loc);
			Set<AccessPathObject> aps = CstFactory.f().getAPsFromCst(cst);
			for (AccessPathObject ap : aps) {
				if (ap instanceof LocalAccessPathLoc) {
					MemLoc base = ap.getBase();
					assert (base instanceof ParamLoc) : base + " " + loc;
					ParamLoc param = (ParamLoc) base;
					Register r2 = param.getRegister();
					if (Env.v().isPropLocal(r2)) {
						continue;
					}
					makeups.add(param);
				} else if (ap instanceof GlobalAccessPathLoc) {
					// do nothing
				} else {
					assert false;
				}
			}
		}
	}

	private void makeup(ParamLoc param) {
		Register r = param.getRegister();
		Env.v().addPropSet(r);
		jq_Method meth = param.getMethod();
		jq_Type type = param.getType();
		handleAssignStmt(meth, r, type, SummariesEnv.LocType.LOCAL_VAR, r,
				type, SummariesEnv.LocType.PARAMETER);
		MemLoc local = LocalVarLocFactory.f().get(r, meth, type);
		MemNode node = MemNodeFactory.f().get(memGraph, local);
		memGraph.addSumNode(node);
		Iterator<MemNode> it = node.outgoingNodesIterator(memGraph);
		while (it.hasNext()) {
			MemNode tgt = it.next();
			memGraph.addSumNode(tgt);
		}
	}

}
