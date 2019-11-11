package framework.scuba.domain.factories;

import framework.scuba.domain.location.PrimitiveLoc;

public class PrimitiveLocFactory {

	private static PrimitiveLocFactory instance = new PrimitiveLocFactory();

	private PrimitiveLoc primitive = new PrimitiveLoc();

	public static PrimitiveLocFactory f() {
		return instance;
	}

	public PrimitiveLoc get() {
		return primitive;
	}

}
