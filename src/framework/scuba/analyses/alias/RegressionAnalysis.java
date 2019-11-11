package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Trio;
import framework.scuba.domain.factories.LocalVarLocFactory;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.summary.SummariesEnv;

public class RegressionAnalysis {

	SummaryBasedAnalysis analysis;

	LinkedList<String> resList = new LinkedList<String>();

	public boolean intersect(Set<AllocLoc> p2set1, Set<AllocLoc> p2set2) {
		boolean ret = false;
		if (SummariesEnv.v().propType == SummariesEnv.PropType.FORMALs) {
			for (AllocLoc alloc1 : p2set1) {
				for (AllocLoc alloc2 : p2set2) {
					Quad site1 = alloc1.getSite();
					Quad site2 = alloc2.getSite();
					framework.scuba.domain.context.Ctxt ctxt1 = alloc1
							.getContext();
					framework.scuba.domain.context.Ctxt ctxt2 = alloc2
							.getContext();
					if (site1 == site2
							&& ((ctxt1.contains(ctxt2)) || ctxt2
									.contains(ctxt1))) {
						ret = true;
					}
				}
			}
		} else {
			Set<AllocLoc> intersection = new HashSet<AllocLoc>(p2set1);
			intersection.retainAll(p2set2);
			if (!intersection.isEmpty()) {
				ret = true;
			}
		}
		return ret;
	}

	public RegressionAnalysis(SummaryBasedAnalysis sum) {
		analysis = sum;
	}

	public void run() {
		int numPassed = 0;
		System.out
				.println("-------------------- BEGIN REGRESSION TEST ---------------------");

		Set<Trio<jq_Method, Register, Register>> failures1 = new HashSet<Trio<jq_Method, Register, Register>>();
		Set<Trio<jq_Method, Register, Register>> failures2 = new HashSet<Trio<jq_Method, Register, Register>>();

		int counter = 0;
		for (Trio<jq_Method, Register, Register> trio : SummariesEnv.v()
				.getAliasPairs()) {

			System.out
					.println("----------------------------------------------------------------");
			jq_Method m = trio.val0;
			Register r1 = trio.val1;
			Register r2 = trio.val2;
			Set<AllocLoc> p2Set1 = SummariesEnv.v().rm.getReport(r1);
			Set<AllocLoc> p2Set2 = SummariesEnv.v().rm.getReport(r2);

			Set<AllocLoc> intersection = new HashSet<AllocLoc>(p2Set1);
			intersection.retainAll(p2Set2);

			LocalVarLoc loc = LocalVarLocFactory.f().get(r1);
			assert (loc != null) : r1;
			System.out.print("[" + ++counter + "/"
					+ SummariesEnv.v().getAliasPairs().size() + "] In method ("
					+ loc.getMethod() + ") ");
			if (m.toString().contains("notAlias")) {
				System.out.println(r1 + " and " + r2 + " is NOT alias");
				boolean passed = !intersect(p2Set1, p2Set2);
				if (passed) {
					numPassed++;
					System.out.println("[PASSED]");
				} else {
					System.out.println("[-----[FAILED]------]");
					Trio<jq_Method, Register, Register> failure = new Trio<jq_Method, Register, Register>(
							loc.getMethod(), r1, r2);
					failures1.add(failure);
				}
			} else {
				System.out.println(r1 + " and " + r2 + " is alias");
				boolean passed = intersect(p2Set1, p2Set2);
				if (passed) {
					numPassed++;
					System.out.println("[PASSED]");
				} else {
					System.out.println("[-----[FAILED]------]");
					Trio<jq_Method, Register, Register> failure = new Trio<jq_Method, Register, Register>(
							loc.getMethod(), r1, r2);
					failures2.add(failure);
				}
			}
		}

		System.out
				.println("-------------------- END REGRESSION TEST ----------------------- ");

		System.out.println("------------------------------------------");
		System.out.println("You have passed [" + numPassed + " out of "
				+ SummariesEnv.v().getAliasPairs().size() + "]");
		int numFailed = SummariesEnv.v().getAliasPairs().size() - numPassed;
		System.out.println("Number of failed cases: " + numFailed);
		int countFailure = 0;
		for (Trio<jq_Method, Register, Register> failure : failures1) {
			jq_Method m = failure.val0;
			Register r1 = failure.val1;
			Register r2 = failure.val2;
			System.out.println("------------------------------------------");
			System.out.println(++countFailure + ". In method [" + m + "] " + r1
					+ " " + r2);
			Set<AllocLoc> p2set1 = SummariesEnv.v().rm.getReport(r1);
			Set<AllocLoc> p2set2 = SummariesEnv.v().rm.getReport(r2);

			System.out.println("  " + r1 + " " + p2set1);
			System.out.println("  " + r2 + " " + p2set2);

			System.out.println("  " + "Expected result: NOT ALIAS");
		}
		for (Trio<jq_Method, Register, Register> failure : failures2) {
			jq_Method m = failure.val0;
			Register r1 = failure.val1;
			Register r2 = failure.val2;
			System.out.println("------------------------------------------");
			System.out.println(++countFailure + ". In method [" + m + "] " + r1
					+ " " + r2);
			Set<AllocLoc> p2set1 = SummariesEnv.v().rm.getReport(r1);
			Set<AllocLoc> p2set2 = SummariesEnv.v().rm.getReport(r2);

			System.out.println("  " + r1 + " " + p2set1);
			System.out.println("  " + r2 + " " + p2set2);

			System.out.println("  " + "Expected result: ALIAS");
		}
		System.out
				.println("======================================================\n");

	}
}
