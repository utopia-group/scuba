package framework.scuba.domain.location;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import framework.scuba.domain.context.Ctxt;
import framework.scuba.helper.TypeHelper;

public class LibAllocLoc extends HeapObject {

	// the context this allocLoc goes through
	protected final Ctxt context;

	protected final Set<jq_Type> types = new HashSet<jq_Type>();

	protected final Set<Quad> sites = new HashSet<Quad>();

	public LibAllocLoc(Set<Quad> sites, Set<jq_Type> types, Ctxt context,
			int number) {
		super(null, number, 0);
		this.context = context;
		this.sites.addAll(sites);
		for (jq_Type type : types) {
			assert (TypeHelper.h().isRefType(type)) : type;
			addType(type);
		}
	}

	public void addType(jq_Type type) {
		// prepare the type
		if (type != null && !type.isPrepared()) {
			type.prepare();
		}
		initFields(type);
	}

	public Set<jq_Type> getTypes() {
		return types;
	}

	public Set<Quad> getSites() {
		return sites;
	}

	@Override
	public jq_Type getType() {
		assert false : this;
		return null;
	}

	@Override
	public String shortName() {
		return sites.toString();
	}

	// ------------ Regular --------------
	@Override
	public String toString() {
		return sites.toString();
	}
}
