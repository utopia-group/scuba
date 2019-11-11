package framework.scuba.domain.location;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import framework.scuba.domain.context.Ctxt;

public class AllocLoc extends AllocObject {

	// the site where this allocLoc is originally allocated
	final protected Quad site;

	public AllocLoc(Quad site, Ctxt context, jq_Type type, int number) {
		super(type, context, number, 0);
		this.site = site;
		initFields(type);
	}

	public Quad getSite() {
		return site;
	}

	public String shortName() {
		return site.toString();
	}

	// ------------ Object --------------
	@Override
	public String toString() {
		if (context.getCurr() == null) {
			return "[" + site + "]";
		} else {
			return "[" + site + "," + context + "]";
		}
	}
}