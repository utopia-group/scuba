package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.invk.DomI;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;

/**
 * Analysis to resolve virtual callsites. We are interested in the following: 1.
 * Number of virtual calls that have single target; 2. Average number of
 * callsites in App; 3. Maximum number of callsites in App.
 */
public class VirtCallAnalysis {

	protected DomI domI;
	protected ProgramRel relPtsVH;
	protected ProgramRel relVC;

	SummaryBasedAnalysis analysis;

	public VirtCallAnalysis(SummaryBasedAnalysis sum) {
		analysis = sum;
		domI = (DomI) ClassicProject.g().getTrgt("I");
		relPtsVH = (ProgramRel) ClassicProject.g().getTrgt("ptsVH");
		if (!relPtsVH.isOpen())
			relPtsVH.load();

		relVC = (ProgramRel) ClassicProject.g().getTrgt("VC");
		if (!relVC.isOpen())
			relVC.load();
	}

	public void run() {
		System.out
				.println("[VirtCall] Begin to run virtual call experiment--------------");
		Iterator<Quad> it = domI.iterator();
		int totalVirtCall = 0;
		int exact = 0;
		int superSet = 0;
		int subSet = 0;
		int empty = 0;
		int other = 0;
		int singleTgtScuba = 0;
		int singleTgtChord = 0;

		while (it.hasNext()) {
			Quad ivk = it.next();
			Operator op = ivk.getOperator();
			if (op instanceof InvokeVirtual || op instanceof InvokeInterface) {
				RegisterOperand ro = Invoke.getParam(ivk, 0);
				jq_Method callee = Invoke.getMethod(ivk).getMethod();
				assert ro != null;
				Register recv = ro.getRegister();

				// only do virtual call analysis for application locals
				if (!Env.v().isAppLocal(recv)) {
					continue;
				}

				System.out.println("Resovling receiver : " + recv + " "
						+ Env.v().getMethodByReg(recv));

				totalVirtCall++;

				Set<jq_Method> tgts = Env.v().cg.getTargets(ivk);
				if (tgts.size() <= 1) {
					singleTgtScuba++;
					singleTgtChord++;
					continue;
				}
				System.out.println("---------------------------------------");
				// chord's result.
				RelView viewChord = relPtsVH.getView();
				viewChord.selectAndDelete(0, recv);
				Iterable<Quad> resChord = viewChord.getAry1ValTuples();
				Set<Quad> chordPts = SetUtils.iterableToSet(resChord,
						viewChord.size());
				Set<jq_Type> chordTypes = new HashSet<jq_Type>();
				for (Quad qd : chordPts) {
					if(qd.getOperator() instanceof NewArray)
						continue;
						
					assert qd.getOperator() instanceof New : qd;
					chordTypes.add(New.getType(qd).getType());
				}

				Set<jq_Method> tgtsChord = new HashSet<jq_Method>();
				for (jq_Type t : chordTypes) {
					assert t instanceof jq_Class : t;
					jq_Class clz = (jq_Class) t;
					jq_Method instMeth = clz.getInstanceMethod(callee
							.getNameAndDesc());
					if (instMeth != null)
						tgtsChord.add(instMeth);
				}

				if (tgtsChord.size() == 0) {
					singleTgtChord++;
					System.out.println("[VirtCall] Chord has 0 tgt.");
				} else if (tgtsChord.size() <= 1) {
					singleTgtChord++;
					System.out.println("[VirtCall] Chord has 1 tgt.");
				} else {
					System.out.println("[VirtCall] Chord has multiple tgt."
							+ " Possible Types: " + chordTypes);
				}

				// chord's kcfa/kobj point-to set result.
				RelView ctxChord = relVC.getView();
				ctxChord.selectAndDelete(0, recv);
				Iterable<Ctxt> resCtxt = ctxChord.getAry1ValTuples();
				Set<Ctxt> ctxPts = SetUtils.iterableToSet(resCtxt,
						ctxChord.size());

				int chordLen = chordPts.size();
				// scuba's result.
				Set<AllocLoc> allocs = SummariesEnv.v().rm.getReport(recv);
				Set<jq_Type> scubaTypes = new HashSet<jq_Type>();
				Set<Quad> scubaPts = new HashSet<Quad>();
				for (AllocLoc loc : allocs) {
					Quad newStmt = loc.getSite();
					assert newStmt.getOperator() instanceof New : newStmt;
					jq_Type t = New.getType(newStmt).getType();
					scubaTypes.add(t);
					scubaPts.add(loc.getSite());
				}

				Set<jq_Method> tgtsScuba = new HashSet<jq_Method>();
				for (jq_Type t : scubaTypes) {
					assert t instanceof jq_Class : t;
					jq_Class clz = (jq_Class) t;
					jq_Method instMeth = clz.getInstanceMethod(callee
							.getNameAndDesc());
					if (instMeth != null)
						tgtsScuba.add(instMeth);
				}

				if (tgtsScuba.size() == 0) {
					singleTgtScuba++;
					System.out.println("[VirtCall] Scuba has 0 tgt.");
				} else if (tgtsScuba.size() <= 1) {
					singleTgtScuba++;
					System.out.println("[VirtCall] Scuba has 1 tgt.");
				} else {
					System.out.println("[VirtCall] Scuba has multiple tgt."
							+ " Possible Types: " + scubaTypes);
				}

				int scubaLen = scubaPts.size();
				// exactly the same.
				if (chordPts.containsAll(scubaPts)
						&& scubaPts.containsAll(chordPts)) {
					exact++;
					continue;
				}

				// scuba is imprecise.
				if (scubaPts.containsAll(chordPts) && (scubaLen > chordLen)) {
					superSet++;
					System.out.println("[VirtCall-Worse] Scuba is imprecise.");
					dumpPts(recv, allocs, ctxPts, ivk);
					continue;
				}

				// chord is imprecise.
				if (chordPts.containsAll(scubaPts) && (chordLen > scubaLen)) {
					subSet++;
					if (scubaLen == 0) {
						empty++;
						System.out
								.println("[VirtCall-Empty] Scuba maybe unsound.");
					} else {
						System.out
								.println("[VirtCall-Better] Scuba is better.");
					}
					dumpPts(recv, allocs, ctxPts, ivk);
					continue;
				}

				System.out.println("[VirtCall-Strange] Strange case.");
				dumpPts(recv, allocs, ctxPts, ivk);
				other++;
			}
		}

		System.out
				.println("[VirtCall]Results of virtual call experiment--------------");
		System.out.println("[VirtCall-result] Total virtual callsites: "
				+ totalVirtCall);
		System.out.println("[VirtCall-result] Exact the same: " + exact);
		System.out.println("[VirtCall-result] Scuba is worse: " + superSet);
		System.out.println("[VirtCall-result] Scuba is better(Include empty): "
				+ subSet);
		System.out.println("[VirtCall-result] Scuba is better(Exclude empty): "
				+ (subSet - empty));
		System.out.println("[VirtCall-result] Strange cases: " + other);
		System.out.println("[VirtCall-result] Scuba(Single tgt): "
				+ singleTgtScuba);
		System.out.println("[VirtCall-result] Chord(Single tgt): "
				+ singleTgtChord);
	}

	void dumpPts(Register r, Set<AllocLoc> scuba, Set<Ctxt> chord, Quad ivk) {
		System.out
				.println("[VirtCall]----------------------------------------");
		System.out.println("[VirtCall]Receiver: " + r + " Method: "
				+ ivk.getMethod());
		System.out.println("[VirtCall]Points-To Set of Scuba.");
		for (AllocLoc loc : scuba)
			System.out.println("[VirtCall-scuba-pts]" + loc);

		System.out.println("[VirtCall]Points-To Set of Chord.");
		for (Ctxt o : chord)
			System.out.println("[VirtCall-Chord-pts]" + o);

	}
}
