package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.bddbddb.Rel.RelView;
import chord.program.Program;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.helper.G;

public class P2SetComparison {

	protected ProgramRel relVH;
	// kobj
	protected ProgramRel relVC;

	protected SummaryBasedAnalysis analysis;

	public static enum Comp {
		BETTER, WORSE, SAME, DIFF;
	}

	public P2SetComparison(ProgramRel vh, SummaryBasedAnalysis sum) {
		relVH = vh;
		analysis = sum;
	}

	private Comp compScubaWithCIPA(Set<AllocLoc> scubaP2Set, Set<Quad> cipaP2Set) {
		Set<Quad> sites = new HashSet<Quad>();
		for (AllocLoc alloc : scubaP2Set) {
			sites.add(alloc.getSite());
		}
		if (cipaP2Set.containsAll(sites) && sites.containsAll(cipaP2Set)) {
			return Comp.SAME;
		} else if (cipaP2Set.containsAll(sites)) {
			return Comp.BETTER;
		} else if (sites.containsAll(cipaP2Set)) {
			return Comp.WORSE;
		} else {
			return Comp.DIFF;
		}
	}

	private Comp compScubaWithKOBJ(Set<AllocLoc> scubaP2Set, Set<Ctxt> kobjP2Set) {
		Set<Quad> kobjSites = new HashSet<Quad>();
		for (Ctxt c : kobjP2Set) {
			Quad alloc = c.get(0);
			kobjSites.add(alloc);
		}
		return compScubaWithCIPA(scubaP2Set, kobjSites);
	}

	public void run() {
		if (!relVH.isOpen())
			relVH.load();

		relVC = (ProgramRel) ClassicProject.g().getTrgt("VC");

		if (!relVC.isOpen())
			relVC.load();

		System.out
				.println("================ P2Set comparision begins ==================");
		int scubaBetterCIPA = 0;
		int scubaWorseCIPA = 0;
		int scubaSameCIPA = 0;
		int scubaDiffCIPA = 0;
		int scubaBetterKOBJ = 0;
		int scubaWorseKOBJ = 0;
		int scubaSameKOBJ = 0;
		int scubaDiffKOBJ = 0;

		int kobjEmpty = 0;
		int cipaEmpty = 0;
		int scubaEmpty = 0;
		int potentialBugs = 0;
		for (Iterator<Register> it = Env.v().appLocalsIterator(); it.hasNext();) {
			Register r = it.next();
			jq_Method meth = Env.v().getMethodByReg(r);

			// context-insensitive pointer analysis result
			RelView viewChord = relVH.getView();
			viewChord.selectAndDelete(0, r);
			if (viewChord.size() == 0) {

			}
			Iterable<Quad> resChord = viewChord.getAry1ValTuples();
			Set<Quad> cipaP2Set = SetUtils.newSet(viewChord.size());
			for (Quad inst : resChord)
				cipaP2Set.add(inst);

			// kobj result
			RelView viewObj = relVC.getView();
			viewObj.selectAndDelete(0, r);
			Iterable<Ctxt> resChordObj = viewObj.getAry1ValTuples();
			Set<Ctxt> kobjP2Set = SetUtils.iterableToSet(resChordObj,
					viewObj.size());
			if (kobjP2Set.isEmpty()) {

			}

			// scuba result
			Set<AllocLoc> scubaP2Set = SummariesEnv.v().rm.getReport(r);

			// printout information
			System.out.println("P2Set for " + r + " method " + meth + " ("
					+ G.dumpMap.get(meth) + ")");
			// p2set printout
			System.out.println("[Scuba] size: " + scubaP2Set.size());
			for (AllocLoc llc : scubaP2Set)
				System.out.println(llc);
			System.out.println("-------------------------------");
			System.out.println("[CIPA] size: " + cipaP2Set.size());
			for (Quad oo : cipaP2Set)
				System.out.println(oo);
			System.out.println("-------------------------------");
			System.out.println("[KOBJ] size: " + kobjP2Set.size());
			for (Ctxt ko : kobjP2Set)
				System.out.println(ko);
			System.out.println("-------------------------------");

			// scuba and cipa comparison
			Comp scubaVScipa = compScubaWithCIPA(scubaP2Set, cipaP2Set);
			System.out.println("Scuba V.S. CIPA : " + scubaVScipa);
			if (scubaVScipa == Comp.BETTER) {
				scubaBetterCIPA++;
			} else if (scubaVScipa == Comp.WORSE) {
				scubaWorseCIPA++;
			} else if (scubaVScipa == Comp.SAME) {
				scubaSameCIPA++;
			} else if (scubaVScipa == Comp.DIFF) {
				scubaDiffCIPA++;
			}
			// scuba and kobj comparison
			Comp scubaVSkobj = compScubaWithKOBJ(scubaP2Set, kobjP2Set);
			System.out.println("Scuba V.S. KOBJ : " + scubaVSkobj);
			if (scubaVSkobj == Comp.BETTER) {
				scubaBetterKOBJ++;
			} else if (scubaVSkobj == Comp.WORSE) {
				scubaWorseKOBJ++;
			} else if (scubaVSkobj == Comp.SAME) {
				scubaSameKOBJ++;
			} else if (scubaVSkobj == Comp.DIFF) {
				scubaDiffKOBJ++;
			}
			System.out.println("-------------------------------");

			// Empty checking (unsoundness checking)
			if (kobjP2Set.size() == 0) {
				kobjEmpty++;
			}

			if (cipaP2Set.isEmpty()) {
				cipaEmpty++;
			}

			if (scubaP2Set.isEmpty()) {
				scubaEmpty++;
				if (cipaP2Set.isEmpty())
					continue;
				Quad q = cipaP2Set.iterator().next();

				jq_Type c = null;
				if (q.getOperator() instanceof New) {
					c = (New.getType(q)).getType();
				} else if (q.getOperator() instanceof NewArray) {
					c = (NewArray.getType(q)).getType();
				}

				if (c instanceof jq_Class) {
					if (((jq_Class) c).extendsClass((jq_Class) Program.g()
							.getClass("java.lang.Exception"))) {

					} else {
						if (kobjP2Set.size() != 0) {
							potentialBugs++;
							System.out.println("Potential bug happens");
						}
					}
				} else {
					if (kobjP2Set.size() != 0) {
						potentialBugs++;
						System.out.println("Potential bug happens");
					}
				}
			}
			System.out
					.println("====================================================");
		}

		System.out
				.println("============================================================");
		System.out.println("[Scuba] [Exhausitive Comparision Statistics]");
		System.out.println("[Scuba] and [CIPA] exactly the same: "
				+ scubaSameCIPA);
		System.out.println("[Scuba] is better than [CIPA]: " + scubaBetterCIPA);
		System.out.println("[Scuba] is worse than [CIPA]: " + scubaWorseCIPA);
		System.out.println("[Scuba] and [CIPA] have different results: "
				+ scubaDiffCIPA);
		System.out.println("------------------------------------------");

		System.out.println("[Scuba] and [KOBJ] exactly the same: "
				+ scubaSameKOBJ);
		System.out.println("[Scuba] is better than [KOBJ]: " + scubaBetterKOBJ);
		System.out.println("[Scuba] is worse than [KOBJ]: " + scubaWorseKOBJ);
		System.out.println("[Scuba] and [KOBJ] have different results: "
				+ scubaDiffKOBJ);
		System.out.println("------------------------------------------");

		System.out.println("[Scuba] potential bugs: " + potentialBugs);
		System.out.println("[Scuba] empty: " + scubaEmpty);
		System.out.println("[CIPA] emtpy: " + cipaEmpty);
		System.out.println("[KBOJ] empty: " + kobjEmpty);
		System.out
				.println("============================================================");
	}
}
