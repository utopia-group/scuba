package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Trio;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.summary.SummariesEnv;
import framework.scuba.utils.StringUtil;

/**
 * Checking downcast safety based on points-to information Our percentage of
 * unsafety cast should be less than Chord
 */
public class DowncastAnalysis {

	protected ProgramRel relDcm;
	protected ProgramRel relDVH;
	SummaryBasedAnalysis analysis;

	public DowncastAnalysis(ProgramRel dcm, ProgramRel dvh,
			SummaryBasedAnalysis sum) {
		relDcm = dcm;
		relDVH = dvh;
		analysis = sum;
	}

	// downcast analysis.
	public void run() {
		System.out
				.println("[downcast]: begin Downcast Experiment---------------------------");
		ProgramRel relSub = (ProgramRel) ClassicProject.g().getTrgt("sub");
		if (!relSub.isOpen())
			relSub.load();
		if (!relDcm.isOpen())
			relDcm.load();
		if (!relDVH.isOpen())
			relDVH.load();

		RelView view = relDcm.getView();
		Iterable<Trio<jq_Method, Register, jq_Type>> res = view
				.getAry3ValTuples();

		StringUtil.reportInfo("Number of downcast: " + relDcm.size());
		int succScuba = 0;
		int succChord = 0;
		int empScuba = 0;
		int empChord = 0;

		for (Trio<jq_Method, Register, jq_Type> trio : res) {
			jq_Method meth = trio.val0;
			Register r = trio.val1;
			jq_Type castType = trio.val2;

			RelView subView = relSub.getView();
			subView.selectAndDelete(1, castType);
			Iterable<jq_Type> resSub = subView.getAry1ValTuples();
			Set<jq_Type> possibleSubTypes = SetUtils.iterableToSet(resSub,
					subView.size());

			StringUtil.reportInfo("[Scuba] method: " + meth);
			StringUtil.reportInfo("[Scuba] Downcast Type: " + castType);

			Set<AllocLoc> allocSet = SummariesEnv.v().rm.getReport(r);

			boolean dcScuba = true;
			if (allocSet.isEmpty())
				empScuba++;

			for (AllocLoc alloc : allocSet) {
				jq_Type allocClz = alloc.getType();
				if (!possibleSubTypes.contains(allocClz))
					dcScuba = false;
			}

			boolean dcChord = true;
			// p2set of r in chord.
			RelView viewChord = relDVH.getView();
			viewChord.selectAndDelete(0, r);
			Iterable<Quad> resChord = viewChord.getAry1ValTuples();
			Set<Quad> pts = SetUtils.newSet(viewChord.size());
			// no filter, add all
			for (Quad inst : resChord) {
				pts.add(inst);
				jq_Type tgtType = null;
				if (inst.getOperator() instanceof New) {
					TypeOperand to = New.getType(inst);
					tgtType = to.getType();
				} else if (inst.getOperator() instanceof NewArray) {
					TypeOperand to = NewArray.getType(inst);
					tgtType = to.getType();
				} else if (inst.getOperator() instanceof MultiNewArray) {
					TypeOperand to = MultiNewArray.getType(inst);
					tgtType = to.getType();
				}
				assert tgtType != null : inst;
				if (!possibleSubTypes.contains(tgtType))
					dcChord = false;
			}
			if (pts.size() == 0)
				empChord++;

			Set<Quad> sites = new HashSet<Quad>();
			for (AllocLoc alloc : allocSet) {
				sites.add(alloc.getSite());
			}

			StringUtil.reportInfo("[Scuba] p2Set of " + r + ": " + sites);
			StringUtil.reportInfo("[Scuba] cast result: " + dcScuba);
			StringUtil.reportInfo("[Chord] p2Set of " + r + ": " + pts);
			StringUtil.reportInfo("[Chord] cast result: " + dcChord);
			if (sites.isEmpty() && !pts.isEmpty()) {
				System.out.println("Downcast bug");
			}
			if (dcChord && !dcScuba) {
				System.out.println("Downcast imprecise");
			}

			if (dcChord)
				succChord++;
			if (dcScuba)
				succScuba++;
		}

		System.out
				.println("[downcast-result] safe cast by scuba: " + succScuba);
		System.out
				.println("[downcast-result] safe cast by chord: " + succChord);
		System.out.println("[downcast-result] empty in scuba: " + empScuba);
		System.out.println("[downcast-result] empty in chord: " + empChord);
		System.out
				.println("[downcast]: Finish Downcast Experiment----------------------");
	}
	
	public void runScubaOnly() {
		System.out
				.println("[downcast]: begin Downcast Experiment---------------------------");
		ProgramRel relSub = (ProgramRel) ClassicProject.g().getTrgt("sub");
		if (!relSub.isOpen())
			relSub.load();
		if (!relDcm.isOpen())
			relDcm.load();
		if (!relDVH.isOpen())
			relDVH.load();

		RelView view = relDcm.getView();
		Iterable<Trio<jq_Method, Register, jq_Type>> res = view
				.getAry3ValTuples();

		StringUtil.reportInfo("Number of downcast: " + relDcm.size());
		int succScuba = 0;
		int empScuba = 0;

		for (Trio<jq_Method, Register, jq_Type> trio : res) {
			jq_Method meth = trio.val0;
			Register r = trio.val1;
			jq_Type castType = trio.val2;

			RelView subView = relSub.getView();
			subView.selectAndDelete(1, castType);
			Iterable<jq_Type> resSub = subView.getAry1ValTuples();
			Set<jq_Type> possibleSubTypes = SetUtils.iterableToSet(resSub,
					subView.size());

			StringUtil.reportInfo("[Scuba] method: " + meth);
			StringUtil.reportInfo("[Scuba] Downcast Type: " + castType);

			Set<AllocLoc> allocSet = SummariesEnv.v().rm.getReport(r);

			boolean dcScuba = true;
			if (allocSet.isEmpty())
				empScuba++;

			for (AllocLoc alloc : allocSet) {
				jq_Type allocClz = alloc.getType();
				if (!possibleSubTypes.contains(allocClz))
					dcScuba = false;
			}

			Set<Quad> sites = new HashSet<Quad>();
			for (AllocLoc alloc : allocSet) {
				sites.add(alloc.getSite());
			}

			StringUtil.reportInfo("[Scuba] p2Set of " + r + ": " + sites);
			StringUtil.reportInfo("[Scuba] cast result: " + dcScuba);

			if (dcScuba)
				succScuba++;
		}

		System.out
				.println("[downcast-result] safe cast by scuba: " + succScuba);
		System.out.println("[downcast-result] empty in scuba: " + empScuba);
		System.out
				.println("[downcast]: Finish Downcast Experiment----------------------");
	}
}
