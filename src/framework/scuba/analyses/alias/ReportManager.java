package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Rel.RelView;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.factories.CstFactory;
import framework.scuba.domain.factories.LocalVarLocFactory;
import framework.scuba.domain.factories.MemNodeFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.memgraph.CMemGraph;
import framework.scuba.domain.memgraph.MemNode;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.ScubaLUT;
import framework.scuba.domain.summary.ScubaResultP2SetWrapper;
import framework.scuba.helper.MemLocHelper;

public class ReportManager {

	protected final CMemGraph conclusion;

	protected final ScubaLUT report = new ScubaLUT();

	public ReportManager(CMemGraph conclusion) {
		this.conclusion = conclusion;
	}

	public void generate(Register r, ProgramRel relVH) {
		Set<AllocLoc> result = null;
		if (Env.v().isPropLocal(r)) {
			result = queryConclusion(r);
		} else if (Env.v().isAppLocal(r)) {
			Register r0 = Env.v().getR0ByReg(r);
			if (r0 == null) {
				result = queryScubaResult(r);
			} else {
				if (report.get(r0).isEmpty()) {
					result = new HashSet<AllocLoc>();
				} else {
					result = queryScubaResult(r);
				}
			}
		} else {
			assert false : r;
		}
		// CIPA filter
		if (!relVH.isOpen()) {
			relVH.load();
		}
		RelView viewChord = relVH.getView();
		viewChord.selectAndDelete(0, r);
		Iterable<Quad> resChord = viewChord.getAry1ValTuples();
		// context-insensitive pointer analysis result
		Set<Quad> cipaP2Set = SetUtils.newSet(viewChord.size());
		for (Quad inst : resChord) {
			cipaP2Set.add(inst);
		}
		Set<AllocLoc> rms = new HashSet<AllocLoc>();
		for (AllocLoc alloc : result) {
			Quad site = alloc.getSite();
			if (!cipaP2Set.contains(site)) {
				rms.add(alloc);
			}
		}
		result.removeAll(rms);

		report.addAll(r, result);
	}

	public Set<AllocLoc> getReport(Register r) {
		Set<AllocLoc> ret = report.get(r);
		assert (ret != null) : r + " " + Env.v().getMethodByReg(r);
		return ret;
	}

	public Set<AllocLoc> queryScubaResult(Register r) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		LocalVarLoc local = LocalVarLocFactory.f().get(r);
		if (local == null) {
			return ret;
		}
		ScubaResultP2SetWrapper wrapper = Env.v().scubaResult.get(r);
		if (wrapper == null) {
			return ret;
		}
		for (MemLoc loc : wrapper.keySet()) {
			BoolExpr cst = wrapper.getCst(loc);
			Set<FieldSelector> smasheds = wrapper.getSmasheds(loc);
			boolean sat = CstFactory.f().rslvCst(cst);
			if (sat) {
				ret.addAll(interpretMemLoc(loc, local, smasheds));
			}
		}
		return ret;
	}

	public Set<AllocLoc> queryConclusion(Register r) {
		LocalVarLoc local = LocalVarLocFactory.f().get(r);
		// assert (local != null) : r + " " + Env.v().getMethodByReg(r);
		if (local == null) {
			return new HashSet<AllocLoc>();
		}
		if (!MemNodeFactory.f().contains(conclusion, local)) {
			return new HashSet<AllocLoc>();
		}
		MemNode node = MemNodeFactory.f().get(conclusion, local);
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		Iterator<MemNode> it = node.outgoingNodesIterator(conclusion);
		while (it.hasNext()) {
			MemNode tgt = it.next();
			Set<MemLoc> locs = tgt.getLocs();
			for (MemLoc loc : locs) {
				assert (loc instanceof AllocLoc) : loc;
				ret.add((AllocLoc) loc);
			}
		}
		return ret;
	}

	// --------- interpret methods -----------
	protected Set<AllocLoc> interpretMemNode(LocalVarLoc local, MemNode node) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		Set<MemLoc> locs = node.getLocs();
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();
		for (MemLoc loc : locs) {
			if (loc instanceof AllocLoc) {
				Set<AllocLoc> set = interpretAllocLoc((AllocLoc) loc);
				for (AllocLoc alloc : set) {
					if (MemLocHelper.h().isSubType(local, e, alloc)) {
						ret.add(alloc);
					}
				}
			} else if (loc instanceof AccessPathObject) {
				Set<AllocLoc> set = Env.v().interpretAPLoc(
						(AccessPathObject) loc);
				for (AllocLoc alloc : set) {
					if (MemLocHelper.h().isSubType(local, e, alloc)) {
						ret.add(alloc);
					}
				}
			} else {
				assert false : node + " " + loc;
			}
		}
		return ret;
	}

	protected Set<AllocLoc> interpretMemLoc(MemLoc loc, LocalVarLoc local,
			Set<FieldSelector> smasheds) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		EpsilonFieldSelector e = Env.v().getEpsilonFieldSelector();

		if (loc instanceof AllocLoc) {
			Set<AllocLoc> set = interpretAllocLoc((AllocLoc) loc);
			for (AllocLoc alloc : set) {
				if (MemLocHelper.h().isSubType(local, e, alloc)) {
					ret.add(alloc);
				}
			}
		} else if (loc instanceof AccessPathObject) {
			Set<AllocLoc> set = Env.v().interpretAPLoc((AccessPathObject) loc,
					smasheds);
			for (AllocLoc alloc : set) {
				if (MemLocHelper.h().isSubType(local, e, alloc)) {
					ret.add(alloc);
				}
			}
		} else {
			assert false : local + " " + loc;
		}
		return ret;
	}

	protected Set<AllocLoc> interpretAllocLoc(AllocLoc alloc) {
		Set<AllocLoc> ret = new HashSet<AllocLoc>();
		ret.add(alloc);
		return ret;
	}
}