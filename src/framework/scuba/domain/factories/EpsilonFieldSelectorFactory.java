package framework.scuba.domain.factories;

import framework.scuba.domain.field.EpsilonFieldSelector;

public class EpsilonFieldSelectorFactory {

	private static EpsilonFieldSelectorFactory instance = new EpsilonFieldSelectorFactory();

	private final EpsilonFieldSelector e = new EpsilonFieldSelector();

	public static EpsilonFieldSelectorFactory f() {
		return instance;
	}

	public EpsilonFieldSelector get() {
		return e;
	}

}
