package framework.scuba.domain.summary;

import joeq.Compiler.Quad.Quad;
import framework.scuba.domain.context.Ctxt;

public class ScubaAlloc {

	protected final Quad site;

	protected final Ctxt context;

	public ScubaAlloc(Quad site, Ctxt context) {
		this.site = site;
		this.context = context;
	}

	public Quad getSite() {
		return site;
	}

	public Ctxt getCtxt() {
		return context;
	}
}
