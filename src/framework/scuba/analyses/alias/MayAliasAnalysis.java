package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Pair;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;

/**
 * Analysis to perform may-alias queries on Pair(v1,v2) Such that v1 and v2
 * dereference to the same field. e.g., x = v1.f; v2.f = y.
 */

public class MayAliasAnalysis {

	protected ProgramRel relVValias;
	protected ProgramRel relMV;
	protected ProgramRel relVC;

	SummaryBasedAnalysis analysis;

	public MayAliasAnalysis(ProgramRel mv, ProgramRel alias,
			SummaryBasedAnalysis sum) {
		relVValias = alias;
		relMV = mv;
		analysis = sum;
	}

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

	// run mayalias on scuba without comparison.
	public void runScubaOnly() {
		System.out
				.println("[mayAlias]: begin to run May-Alias Experiment--------------------------------");
		if (!relVValias.isOpen())
			relVValias.load();

		RelView view = relVValias.getView();
		int totalQuery = 0;
		int nonAlias = 0;

		Iterable<Pair<Register, Register>> res = view.getAry2ValTuples();
		for (Pair<Register, Register> vv : res) {
			Register v1 = vv.val0;
			Register v2 = vv.val1;

			totalQuery++;
			Set<AllocLoc> p2Set1 = SummariesEnv.v().rm.getReport(v1);
			Set<AllocLoc> p2Set2 = SummariesEnv.v().rm.getReport(v2);

			boolean alias = intersect(p2Set1, p2Set2);
			if (!alias) {
				nonAlias++;
			}
		}

		System.out
				.println("[mayAlias-result]May-alias Result--------------------------------");
		System.out.println("[mayAlias-result]Total queries: " + totalQuery);
		System.out
				.println("[mayAlias-result]Total non-alias by scuba(This contains empty set): "
						+ nonAlias);
		System.out.println("--------------------------------------------");
	}

	public void run() {
		System.out
				.println("[mayAlias]: begin to run May-Alias Experiment--------------------------------");
		if (!relVValias.isOpen())
			relVValias.load();

		if (!relMV.isOpen())
			relMV.load();

		relVC = (ProgramRel) ClassicProject.g().getTrgt("VC");
		if (!relVC.isOpen())
			relVC.load();

		RelView view = relVValias.getView();
		int totalQuery = 0;
		int agreeAlias = 0;
		int agreeNonAlias = 0;
		int superSet = 0;
		int subSet = 0;
		int agreeUnsound = 0;
		int betterUnsound = 0;
		int nonAlias = 0;

		int bothAlias = 0;
		int bothNonAlias = 0;
		int bothNonAliasScubaEmpty = 0;
		int scubaNonAlias = 0; // chord is alias
		int scubaNonAliasEmpty = 0;
		int chordNonAlias = 0; // scuba is alias
		int chordNonAliasEmpty = 0;

		Iterable<Pair<Register, Register>> res = view.getAry2ValTuples();
		for (Pair<Register, Register> vv : res) {
			Register v1 = vv.val0;
			Register v2 = vv.val1;

			// only do may-alias analysis for application locals
			if (!Env.v().isAppLocal(v1) || !Env.v().isAppLocal(v2)) {
				assert false : "not in applocal.";
				continue;
			}

			RelView viewMv1 = relMV.getView();
			viewMv1.selectAndDelete(1, v1);
			Iterable<jq_Method> m1It = viewMv1.getAry1ValTuples();
			jq_Method m1 = m1It.iterator().next();

			RelView viewMv2 = relMV.getView();
			viewMv2.selectAndDelete(1, v2);
			Iterable<jq_Method> m2It = viewMv2.getAry1ValTuples();
			jq_Method m2 = m2It.iterator().next();

			// chord's kcfa/kobj point-to set result.
			RelView ctxChord1 = relVC.getView();
			ctxChord1.selectAndDelete(0, v1);
			Iterable<Ctxt> resCtxt1 = ctxChord1.getAry1ValTuples();
			Set<Ctxt> ctxPts1 = SetUtils.iterableToSet(resCtxt1,
					ctxChord1.size());

			RelView ctxChord2 = relVC.getView();
			ctxChord2.selectAndDelete(0, v2);
			Iterable<Ctxt> resCtxt2 = ctxChord2.getAry1ValTuples();
			Set<Ctxt> ctxPts2 = SetUtils.iterableToSet(resCtxt2,
					ctxChord2.size());

			Set<Ctxt> insectChord = new HashSet<Ctxt>(ctxPts1);
			insectChord.retainAll(ctxPts2);

			totalQuery++;
			Set<AllocLoc> p2Set1 = SummariesEnv.v().rm.getReport(v1);
			Set<AllocLoc> p2Set2 = SummariesEnv.v().rm.getReport(v2);

			boolean alias = intersect(p2Set1, p2Set2);

			if (alias && !insectChord.isEmpty()) {
				// both alias
				bothAlias++;
			} else if (!alias && insectChord.isEmpty()) {
				// both non-alias
				bothNonAlias++;
				if (p2Set1.isEmpty() || p2Set2.isEmpty()) {
					if (!ctxPts1.isEmpty() && !ctxPts2.isEmpty()) {
						bothNonAliasScubaEmpty++;
					}
				}
			} else if (alias && insectChord.isEmpty()) {
				// scuba alias chord non-alias
				chordNonAlias++;
				if (ctxPts1.isEmpty() || ctxPts2.isEmpty()) {
					chordNonAliasEmpty++;
				}
				System.out.println("MayAlias imprecise ");
				System.out.println(v1 + " : " + Env.v().getMethodByReg(v1));
				System.out.println(v2 + " : " + Env.v().getMethodByReg(v2));
				System.out.println("[Chord]");
				System.out.println("Point-to Set 1 of " + v1 + " : " + ctxPts1);
				System.out.println("Point-to Set 2 of " + v2 + " : " + ctxPts2);
				System.out.println("[Scuba]");
				System.out.println("Point-to Set 1 of " + v1 + " : " + p2Set1);
				System.out.println("Point-to Set 2 of " + v2 + " : " + p2Set2);
			} else if (!alias && !insectChord.isEmpty()) {
				// scuba non-alias chord alias
				scubaNonAlias++;
				if (p2Set1.isEmpty() || p2Set2.isEmpty()) {
					scubaNonAliasEmpty++;
				}
				System.out.println("MayAlias bug ");
				System.out.println(v1 + " : " + Env.v().getMethodByReg(v1));
				System.out.println(v2 + " : " + Env.v().getMethodByReg(v2));
				System.out.println("[Chord]");
				System.out.println("Point-to Set 1 of " + v1 + " : " + ctxPts1);
				System.out.println("Point-to Set 2 of " + v2 + " : " + ctxPts2);
				System.out.println("[Scuba]");
				System.out.println("Point-to Set 1 of " + v1 + " : " + p2Set1);
				System.out.println("Point-to Set 2 of " + v2 + " : " + p2Set2);
			}

			boolean insectScuba = this.intersect(p2Set1, p2Set2);

			if (insectScuba && !insectChord.isEmpty()) {
				// not interested in this case. both say alias.
				agreeAlias++;
				continue;
			}

			if (insectChord.isEmpty() && insectScuba) {
				superSet++;
				System.out.println("[mayAlias-Worse]Scuba is imprecise.");
				dumpInfo(m1, v1, m2, v2, p2Set1, p2Set2, ctxPts1, ctxPts2);
				continue;
			}

			if (!insectScuba) {
				nonAlias++;
				// several cases.
				if (insectChord.isEmpty()) {
					if ((p2Set1.size() > 0) && (p2Set2.size() > 0)) {
						agreeNonAlias++;
					} else {
						System.out
								.println("[mayAlias-nonalias]Scuba may be unsound.");
						// cases that we may unsound.
						agreeUnsound++;
						dumpInfo(m1, v1, m2, v2, p2Set1, p2Set2, ctxPts1,
								ctxPts2);
					}
				} else {
					if ((p2Set1.size() > 0) && (p2Set2.size() > 0)) {
						subSet++;
						System.out
								.println("[mayAlias-nonalias]Scuba is strictly better.");
						dumpInfo(m1, v1, m2, v2, p2Set1, p2Set2, ctxPts1,
								ctxPts2);
					} else {
						// cases that we may unsound.
						betterUnsound++;
						System.out
								.println("[mayAlias-nonalias]Scuba maybe better but unsound.");
						dumpInfo(m1, v1, m2, v2, p2Set1, p2Set2, ctxPts1,
								ctxPts2);
					}
				}
			}
		}

		System.out.println("[mayAlias-result] ---------------------");
		System.out.println("both Alias : " + bothAlias);
		System.out.println("both Non-Alias : " + bothNonAlias);
		System.out
				.println("both Non-Alias (Scuba empty but Chord not empty) : "
						+ bothNonAliasScubaEmpty);
		System.out.println("Scuba Non-Alias (Chord Alias) : " + scubaNonAlias);
		System.out.println("Scuba Non-Alias (Chord Alias) because of empty : "
				+ scubaNonAliasEmpty);
		System.out.println("Chord Non-Alias (Scuba Alias) : " + chordNonAlias);
		System.out.println("Chord Non-Alias (Scuba Alias) because of empty : "
				+ chordNonAliasEmpty);
		System.out.println("--------------------------------------------");

		System.out
				.println("[mayAlias-result]May-alias Result--------------------------------");
		System.out.println("[mayAlias-result]Total queries: " + totalQuery);
		System.out.println("[mayAlias-result]Both Scuba and Chord say alias: "
				+ agreeAlias);
		System.out.println("[mayAlias-result]Scuba is imprecise(superSet): "
				+ superSet);
		System.out
				.println("[mayAlias-result]Total non-alias by scuba(This contains empty set): "
						+ nonAlias);
		System.out
				.println("[mayAlias-result]Scuba is nonalias as chord(non-empty): "
						+ agreeNonAlias);
		System.out
				.println("[mayAlias-result]Scuba is nonalias as chord(with empty): "
						+ agreeUnsound);
		System.out
				.println("[mayAlias-result]Scuba is strictly better(non-empty): "
						+ subSet);
		System.out.println("[mayAlias-result]Scuba is better(with empty): "
				+ betterUnsound);
	}

	public void dumpInfo(jq_Method m1, Register v1, jq_Method m2, Register v2,
			Set<AllocLoc> allocsV1, Set<AllocLoc> allocsV2, Set<Ctxt> ctxt1,
			Set<Ctxt> ctxt2) {
		// System.out
		// .println("[mayAlias-p2set]----------------------------------------");
		// System.out.println("[mayAlias-p2set]v1: " + v1 + " Method1: " + m1);
		// System.out.println("[mayAlias-p2set]v2: " + v2 + " Method2: " + m2);
		//
		// System.out.println("[mayAlias-p2set]Points-To Set of Scuba for " +
		// v1);
		// for (AllocLoc loc : allocsV1)
		// System.out.println(loc);
		//
		// System.out.println("[mayAlias-p2set]Points-To Set of Chord for " +
		// v1);
		// for (Ctxt o : ctxt1)
		// System.out.println(o);
		//
		// System.out.println("[mayAlias-p2set]Points-To Set of Scuba for " +
		// v2);
		// for (AllocLoc loc2 : allocsV2)
		// System.out.println(loc2);
		//
		// System.out.println("[mayAlias-p2set]Points-To Set of Chord for " +
		// v2);
		// for (Ctxt o2 : ctxt2)
		// System.out.println(o2);
	}
}