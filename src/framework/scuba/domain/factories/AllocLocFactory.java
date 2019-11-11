package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Trio;
import framework.scuba.domain.context.Ctxt;
import framework.scuba.domain.location.AllocLoc;

public class AllocLocFactory {

	private static AllocLocFactory instance = new AllocLocFactory();

	public static AllocLocFactory f() {
		return instance;
	}

	private final Trio<Quad, jq_Type, Ctxt> trio = new Trio<Quad, jq_Type, Ctxt>(
			null, null, null);

	// we need jq_Type due to MULTINEW statements
	public final Map<Trio<Quad, jq_Type, Ctxt>, AllocLoc> locFactory = new HashMap<Trio<Quad, jq_Type, Ctxt>, AllocLoc>();
	private final Map<AllocLoc, Integer> locToId = new HashMap<AllocLoc, Integer>();

	// numbers of AllocLoc start from 1
	public int maxNum;

	public AllocLoc get(Quad stmt, jq_Type type, Ctxt ctxt) {
		trio.val0 = stmt;
		trio.val1 = type;
		trio.val2 = ctxt;
		AllocLoc ret = locFactory.get(trio);
		if (ret == null) {
			ret = new AllocLoc(stmt, ctxt, type, ++maxNum);
			update(stmt, ctxt, type, maxNum, ret);
		}
		return ret;
	}

	private void update(Quad stmt, Ctxt ctxt, jq_Type type, int number,
			AllocLoc loc) {
		Trio<Quad, jq_Type, Ctxt> trio = new Trio<Quad, jq_Type, Ctxt>(stmt,
				type, ctxt);
		locFactory.put(trio, loc);
		locToId.put(loc, number);
	}

	public Iterator<AllocLoc> iterator() {
		return locFactory.values().iterator();
	}

}
