package framework.scuba.domain.factories;

import framework.scuba.domain.field.IndexFieldSelector;

public class IndexFieldSelectorFactory {

	private static IndexFieldSelectorFactory instance = new IndexFieldSelectorFactory();

	private final IndexFieldSelector index = new IndexFieldSelector();

	public static IndexFieldSelectorFactory f() {
		return instance;
	}

	public IndexFieldSelector get() {
		return index;
	}

}
