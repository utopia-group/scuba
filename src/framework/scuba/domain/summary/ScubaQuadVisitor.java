package framework.scuba.domain.summary;

import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.ALoad.ALOAD_A;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.AStore.ASTORE_A;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getfield.GETFIELD_A;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Getstatic.GETSTATIC_A;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Move.MOVE_A;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putfield.PUTFIELD_A;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Putstatic.PUTSTATIC_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;
import framework.scuba.controller.MMemGraphController;
import framework.scuba.controller.S1MMemGraphController;
import framework.scuba.domain.factories.PhiTypeFactory;
import framework.scuba.helper.MemLocHelper;

public class ScubaQuadVisitor extends QuadVisitor.EmptyVisitor {

	// controlling this memory graph
	protected MMemGraphController controller;

	// whether the controller changes the heap
	protected boolean changed;

	public void setEverything(MMemGraphController controller) {
		this.controller = controller;
	}

	public boolean changed() {
		return changed;
	}

	// no-op.
	@Override
	public void visitALength(Quad stmt) {
	}

	// y = x[1];
	@Override
	public void visitALoad(Quad stmt) {
		// only handle ALOAD_A.
		jq_Method meth = stmt.getMethod();
		RegisterOperand rhs = (RegisterOperand) ALoad.getBase(stmt);
		RegisterOperand lhs = (RegisterOperand) ALoad.getDest(stmt);

		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhs.getRegister());
		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleALoadStmt(
					meth, lhs.getRegister(), lhs.getType(), lvt,
					rhs.getRegister(), rhs.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}

	}

	// x[1] = y
	@Override
	public void visitAStore(Quad stmt) {
		if (!(stmt.getOperator() instanceof ASTORE_A))
			return;

		jq_Method meth = stmt.getMethod();
		RegisterOperand lhs = (RegisterOperand) AStore.getBase(stmt);
		RegisterOperand rhs = (RegisterOperand) AStore.getValue(stmt);
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhs.getRegister());

		if (controller instanceof S1MMemGraphController) {
			if(rhs.getType() instanceof jq_NullType)
				return;
			changed = ((S1MMemGraphController) controller).handleAStoreStmt(
					meth, lhs.getRegister(), lhs.getType(), lvt,
					rhs.getRegister(), rhs.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}

	}

	// no-op.
	@Override
	public void visitBinary(Quad stmt) {
	}

	// no-op.
	@Override
	public void visitBoundsCheck(Quad stmt) {
	}

	// no-op.
	@Override
	public void visitBranch(Quad stmt) {
	}

	// treat it as assignment.
	@Override
	public void visitCheckCast(Quad stmt) {
		Operand rx = CheckCast.getSrc(stmt);
		jq_Method meth = stmt.getMethod();
		RegisterOperand ro = (RegisterOperand) rx;
		Register r = ro.getRegister();
		RegisterOperand lo = CheckCast.getDest(stmt);
		Register l = lo.getRegister();
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lo.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), ro.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleCheckCastStmt(
					meth, l, lo.getType(), lvt, r, ro.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}

	}

	// v1 = v2.f
	@Override
	public void visitGetfield(Quad stmt) {
		FieldOperand field = Getfield.getField(stmt);
		RegisterOperand lhs = Getfield.getDest(stmt);
		RegisterOperand rhsBase = (RegisterOperand) Getfield.getBase(stmt);
		jq_Method meth = stmt.getMethod();
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhsBase.getRegister());

		boolean notSmash = SummariesEnv.v().smashCtrl
				&& Env.v().isNotSmashStmt(stmt);

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleLoadStmt(meth,
					lhs.getRegister(), lhs.getType(), lvt,
					rhsBase.getRegister(), rhsBase.getType(), rvt,
					field.getField(), notSmash);
		} else {
			assert false : controller.getClass();
		}

	}

	// v = A.f.
	@Override
	public void visitGetstatic(Quad stmt) {
		FieldOperand field = Getstatic.getField(stmt);
		jq_Method meth = stmt.getMethod();
		RegisterOperand lhs = Getstatic.getDest(stmt);
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleStatLoadStmt(
					meth, lhs.getRegister(), lhs.getType(), lvt,
					field.getField());
		} else {
			assert false : controller.getClass();
		}
	}

	// no-op.
	@Override
	public void visitInstanceOf(Quad stmt) {
	}

	@Override
	public void visitInvoke(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		// receiver
		RegisterOperand lo = Invoke.getDest(stmt);
		Register lr = null;
		jq_Type lt = null;
		SummariesEnv.LocType lvt = null;
		if (lo != null) {
			lr = lo.getRegister();
			lt = lo.getType();
			lvt = MemLocHelper.h().getVarType(meth, lr);
		}
		// actuals
		List<Register> rrs = new ArrayList<Register>();
		List<jq_Type> rts = new ArrayList<jq_Type>();
		List<SummariesEnv.LocType> rvts = new ArrayList<SummariesEnv.LocType>();
		ParamListOperand actuals = Invoke.getParamList(stmt);
		for (int i = 0; i < actuals.length(); i++) {
			RegisterOperand ro = actuals.get(i);
			jq_Type rt = ro.getType();
			Register rr = ro.getRegister();
			SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(meth, rr);
			rrs.add(rr);
			rts.add(rt);
			rvts.add(rvt);
		}
		assert (rrs.size() == rts.size()) : rrs + " " + rts;
		assert (rrs.size() == rvts.size()) : rrs + " " + rvts;

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleInvokeStmt(
					meth, stmt, lr, lt, lvt, rrs, rts, rvts);
		} else {
			assert false : controller.getClass();
		}

		// regression test.
		jq_Method callee = Invoke.getMethod(stmt).getMethod();
		if (callee.getDeclaringClass().toString()
				.equals("framework.scuba.helper.AliasHelper")) {
			Register r1 = Invoke.getParam(stmt, 0).getRegister();
			Register r2 = Invoke.getParam(stmt, 1).getRegister();
			SummariesEnv.v().addAliasPairs(callee, r1, r2);
		}
	}

	// no sure whether we should mark this as no op.
	@Override
	public void visitMemLoad(Quad stmt) {
		System.out.println(stmt);
		assert false : "MemLoad";
	}

	// no sure whether we should mark this as no op.
	@Override
	public void visitMemStore(Quad stmt) {
		System.out.println(stmt);
		assert false : "MemStore";
	}

	// no-op.
	@Override
	public void visitMonitor(Quad stmt) {
	}

	// v1 = v2
	@Override
	public void visitMove(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		RegisterOperand rhs = (RegisterOperand) Move.getSrc(stmt);
		RegisterOperand lhs = (RegisterOperand) Move.getDest(stmt);
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhs.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleAssignStmt(
					meth, lhs.getRegister(), lhs.getType(), lvt,
					rhs.getRegister(), rhs.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}
	}

	// v1 = new A();
	@Override
	public void visitNew(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		RegisterOperand rop = New.getDest(stmt);
		SummariesEnv.LocType vt = MemLocHelper.h().getVarType(meth,
				rop.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleNewStmt(meth,
					rop.getRegister(), rop.getType(), vt, stmt);
		} else {
			assert false : controller.getClass();
		}
	}

	// v = new Node[10][10]
	@Override
	public void visitMultiNewArray(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		RegisterOperand rop = MultiNewArray.getDest(stmt);
		SummariesEnv.LocType vt = MemLocHelper.h().getVarType(meth,
				rop.getRegister());
		ParamListOperand plo = MultiNewArray.getParamList(stmt);

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller)
					.handleMultiNewArrayStmt(meth, rop.getRegister(),
							rop.getType(), vt, plo.length(), stmt);
		} else {
			assert false : controller.getClass();
		}
	}

	// v = new Node[10];
	@Override
	public void visitNewArray(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		RegisterOperand rop = NewArray.getDest(stmt);
		SummariesEnv.LocType vt = MemLocHelper.h().getVarType(meth,
				rop.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleNewArrayStmt(
					meth, rop.getRegister(), rop.getType(), vt, stmt);
		} else {
			assert false : controller.getClass();
		}
	}

	// no-op.
	@Override
	public void visitNullCheck(Quad stmt) {
	}

	// we translate PHI node into a set of assignments.
	// PHI node: PHI T5, (T3, T4), { BB3, BB4 }
	public void visitPhi(Quad stmt) {
		changed = false;
		jq_Method meth = stmt.getMethod();
		RegisterOperand lhs = Phi.getDest(stmt);
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(meth,
				lhs.getRegister());

		jq_Type lhsType = PhiTypeFactory.f().get(lhs.getRegister());

		for (RegisterOperand rhs : stmt.getOperator().getUsedRegisters(stmt)) {
			// PHI T5, (null, T4), { BB3, BB4 }
			if (rhs == null || (rhs.getType() instanceof jq_NullType)
					|| (rhs.getType() instanceof jq_ReturnAddressType))
				continue;

			SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(meth,
					rhs.getRegister());

			jq_Type rhsType = PhiTypeFactory.f().get(rhs.getRegister());
			if (controller instanceof S1MMemGraphController) {
				changed = changed
						| ((S1MMemGraphController) controller)
								.handleAssignStmt(meth, lhs.getRegister(),
										lhsType, lvt, rhs.getRegister(),
										rhsType, rvt);
			} else {
				assert false : controller.getClass();
			}
		}
	}

	// v1.f = v2
	@Override
	public void visitPutfield(Quad stmt) {
		FieldOperand f = Putfield.getField(stmt);
		jq_Method meth = stmt.getMethod();
		Operand rhso = Putfield.getSrc(stmt);
		RegisterOperand rhs = (RegisterOperand) rhso;
		RegisterOperand lhs = (RegisterOperand) Putfield.getBase(stmt);
		SummariesEnv.LocType lvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), lhs.getRegister());
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhs.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleStoreStmt(
					meth, lhs.getRegister(), lhs.getType(), lvt, f.getField(),
					rhs.getRegister(), rhs.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}
	}

	// A.f = b;
	@Override
	public void visitPutstatic(Quad stmt) {
		FieldOperand field = Putstatic.getField(stmt);
		jq_Method meth = stmt.getMethod();
		Operand rhso = Putstatic.getSrc(stmt);
		RegisterOperand rhs = (RegisterOperand) rhso;
		SummariesEnv.LocType rvt = MemLocHelper.h().getVarType(
				stmt.getMethod(), rhs.getRegister());

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller)
					.handleStaticStoreStmt(meth, field.getField(),
							rhs.getRegister(), rhs.getType(), rvt);
		} else {
			assert false : controller.getClass();
		}
	}

	@Override
	public void visitReturn(Quad stmt) {
		Operand operand = Return.getSrc(stmt);
		RegisterOperand ret = ((RegisterOperand) operand);
		Register v = ret.getRegister();
		jq_Method meth = stmt.getMethod();
		SummariesEnv.LocType type = MemLocHelper.h().getVarType(meth, v);

		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller).handleRetStmt(meth,
					v, ret.getType(), type);
		} else {
			assert false : controller.getClass();
		}
	}

	// no sure whether we should mark this as no op.
	@Override
	public void visitSpecial(Quad stmt) {
		System.out.println(stmt);
		assert false : "Special stmt that we havn't consider. Abort.";
	}

	// no-op.
	@Override
	public void visitStoreCheck(Quad stmt) {
	}

	// no sure whether we should mark this as no op.
	@Override
	public void visitUnary(Quad stmt) {
	}

	// no-op.
	@Override
	public void visitZeroCheck(Quad stmt) {
	}

	// special treatment for parameters propagation
	public void visitParamAssign(jq_Method meth, Register param, jq_Type type) {
		// class name end with "$1" will always be assigned null.
		// Don't propagate it
		if (meth.toString().contains("<init>") && type.getName().contains("$1")
				&& !Program.g().getClasses().contains(type)) {
			return;
		}
		if (controller instanceof S1MMemGraphController) {
			changed = ((S1MMemGraphController) controller)
					.handleParamAssignStmt(meth, param, type,
							SummariesEnv.LocType.LOCAL_VAR, param, type,
							SummariesEnv.LocType.PARAMETER);
		} else {
			assert false : controller.getClass();
		}
	}

	// filter out stmts related to heap operations.
	public boolean isHeapStmt(Quad stmt) {
		Operator op = stmt.getOperator();
		// 1. the first clause make sure only if it's ref type.
		// 2. the second clause is to ignore string assignment like x="Hi"
		// The question is, do we need to handle string operation?
		if (op instanceof MOVE_A) {
			if (!(Move.getSrc(stmt) instanceof RegisterOperand))
				return false;

			RegisterOperand ro = (RegisterOperand) Move.getSrc(stmt);
			// ignore returnAddr type.
			if (ro.getType() instanceof jq_ReturnAddressType
					|| ro.getType() instanceof jq_NullType)
				return false;

			return true;
		}

		// ignore new Exception.
		if (op instanceof New) {
			if (SummariesEnv.v().shareExceptionAllocs) {
				jq_Type type = New.getType(stmt).getType();
				if (Env.v().extendExceptionOrError(type)) {
					Env.v().addSharedAlloc(stmt, type);
				}
			}
			return true;
		}

		// return
		if (op instanceof RETURN_A) {
			Operand operand = Return.getSrc(stmt);
			if (!(operand instanceof RegisterOperand))
				return false;

			RegisterOperand ro = (RegisterOperand) operand;
			jq_Type retType = ro.getType();
			if (retType instanceof jq_NullType
					|| retType instanceof jq_ReturnAddressType)
				return false;

			return true;
		}

		// phi
		if (op instanceof Phi) {
			if (!(Phi.getDest(stmt) instanceof RegisterOperand))
				return false;
			// if any of the register contains primitive type, ignore it.
			for (RegisterOperand rhs : stmt.getOperator()
					.getUsedRegisters(stmt)) {
				if (rhs == null)
					continue;

				jq_Type t = rhs.getType();
				if (t.isPrimitiveType())
					return false;
			}

			return true;
		}

		// special cases like: x.f = null, etc
		if (op instanceof PUTFIELD_A) {
			jq_Field field = Putfield.getField(stmt).getField();
			Operand rhso = Putfield.getSrc(stmt);
			// x.f = null.
			if (!(rhso instanceof RegisterOperand))
				return false;
			if (!Env.v().reachableFields.contains(field)) {
				// Register lhs = ((RegisterOperand) rhso).getRegister();
				// Env.v().delPropSet(lhs);
				return false;
			}
			// x = null, x is null-type
			if (((RegisterOperand) rhso).getType() instanceof jq_NullType)
				return false;

			return true;
		}

		if (op instanceof GETFIELD_A) {
			jq_Field field = Getfield.getField(stmt).getField();
			if (!Env.v().reachableFields.contains(field)) {
				// RegisterOperand lhs = Getfield.getDest(stmt);
				// Env.v().delPropSet(lhs.getRegister());
				return false;
			}
			return true;
		}

		if (op instanceof ASTORE_A) {
			// x[i] = null.
			if (!(AStore.getValue(stmt) instanceof RegisterOperand))
				return false;

			return true;
		}
		if (op instanceof PUTSTATIC_A) {
			jq_Field field = Putstatic.getField(stmt).getField();
			Operand rhso = Putstatic.getSrc(stmt);
			// A.f = null.
			if (!(rhso instanceof RegisterOperand))
				return false;
			if (!Env.v().reachableFields.contains(field)) {
				// RegisterOperand rhs = (RegisterOperand)
				// Putstatic.getSrc(stmt);
				// Env.v().delPropSet(rhs.getRegister());
				return false;
			}

			return true;
		}

		if (op instanceof GETSTATIC_A) {
			jq_Field field = Getstatic.getField(stmt).getField();
			if (!Env.v().reachableFields.contains(field)) {
				// RegisterOperand lhs = Getstatic.getDest(stmt);
				// Env.v().delPropSet(lhs.getRegister());
				return false;
			}
			return true;
		}

		if (op instanceof CheckCast) {
			Operand rx = CheckCast.getSrc(stmt);
			jq_Type castType = CheckCast.getType(stmt).getType();

			if (!(rx instanceof RegisterOperand)
					|| !Program.g().getClasses().contains(castType))
				return false;

			return true;
		}

		if (op instanceof NewArray || op instanceof MultiNewArray
				|| op instanceof Invoke || op instanceof ALOAD_A
				|| op instanceof GETSTATIC_A || op instanceof GETFIELD_A)
			return true;

		return false;
	}

}