package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Array;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class PhiTypeFactory {

	private static PhiTypeFactory instance = new PhiTypeFactory();

	private jq_Type ot = PrimordialClassLoader.JavaLangObject;

	public static PhiTypeFactory f() {
		return instance;
	}

	private final Map<Register, jq_Reference> phiTypeFactory = new HashMap<Register, jq_Reference>();

	private final HashSet<ControlFlowGraph> analyzedCFG = new HashSet<ControlFlowGraph>();

	// to be conservative, only infer the type related to jq_Array.
	public void update(ControlFlowGraph cfg) {
		if (analyzedCFG.contains(cfg))
			return;
		// only analyze each method once.
		analyzedCFG.add(cfg);
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (!(op instanceof Phi))
					continue;
				RegisterOperand lo = Phi.getDest(q);
				jq_Type t = lo.getType();
				if (!t.isReferenceType())
					continue;
				Register phiReg = lo.getRegister();
				jq_Reference phiType = phiTypeFactory.get(phiReg);
				if (phiType == null || phiType.equals(ot))
					phiTypeFactory.put(phiReg, (jq_Reference) t);

				ParamListOperand po = Phi.getSrcs(q);
				for (int i = 0; i < po.length(); i++) {
					RegisterOperand rhs = po.get(i);
					if (rhs == null)
						continue;

					Register reg = rhs.getRegister();
					jq_Type rt = rhs.getType();
					jq_Reference rtRef = get(reg);

					if (!rt.isReferenceType())
						break;

					if (rtRef == null || rtRef.equals(ot))
						phiTypeFactory.put(reg, (jq_Reference) rt);

					rtRef = phiTypeFactory.get(reg);
					// only infer new type info related to jq_Array.
					if (rt instanceof jq_Array) {
						phiTypeFactory.put(phiReg, (jq_Reference) rt);
					} else if (rtRef instanceof jq_Array) {
						phiTypeFactory.put(phiReg, rtRef);
					}
				}

			}
		}
	}

	public jq_Reference get(Register ro) {
		return phiTypeFactory.get(ro);
	}

	public void clear() {
		instance = new PhiTypeFactory();
	}
}
