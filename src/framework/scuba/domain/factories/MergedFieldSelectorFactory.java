package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import framework.scuba.domain.field.MergedFieldSelector;

public class MergedFieldSelectorFactory {

	private static MergedFieldSelectorFactory instance = new MergedFieldSelectorFactory();

	public static MergedFieldSelectorFactory f() {
		return instance;
	}

	private final Map<jq_Field, MergedFieldSelector> fieldSelectorFactory = new HashMap<jq_Field, MergedFieldSelector>();
	private final Map<jq_Field, Integer> fieldSelectorToId = new HashMap<jq_Field, Integer>();
	private final Map<Integer, MergedFieldSelector> IdToFieldSelector = new HashMap<Integer, MergedFieldSelector>();

	// number starts from 1
	private int maxNum;

	// only used in the analysis
	public MergedFieldSelector get(jq_Field field) {
		MergedFieldSelector ret = fieldSelectorFactory.get(field);
		assert (ret != null) : field;
		return ret;
	}

	// only used in the pre-analysis to merge fields
	public void merge(Set<jq_Field> fields) {
		MergedFieldSelector ret = new MergedFieldSelector(fields, ++maxNum);
		for (jq_Field field : fields) {
			update(field, maxNum, ret);
		}
	}

	private void update(jq_Field field, int number, MergedFieldSelector ret) {
		fieldSelectorFactory.put(field, ret);
		fieldSelectorToId.put(field, number);
		IdToFieldSelector.put(number, ret);
	}

}
