package framework.scuba.domain.factories;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;

public class RegFieldSelectorFactory {

	private static RegFieldSelectorFactory instance = new RegFieldSelectorFactory();

	public static RegFieldSelectorFactory f() {
		return instance;
	}

	private final Map<jq_Field, RegFieldSelector> fieldSelectorFactory = new HashMap<jq_Field, RegFieldSelector>();

	private final Map<jq_Field, Integer> fieldSelectorToId = new HashMap<jq_Field, Integer>();

	// numbers of RegFieldSelector start from 1
	private int maxNum;

	// only used in the analysis
	public RegFieldSelector get(jq_Field field) {
		RegFieldSelector ret = fieldSelectorFactory.get(field);
		SummariesEnv.FieldType fType = setFieldType(field);
		if (ret == null) {
			ret = new RegFieldSelector(field, ++maxNum, fType);
			update(field, maxNum, ret);
		}
		return ret;
	}

	public SummariesEnv.FieldType setFieldType(jq_Field field) {
		return Env.v().getFieldType(field);
	}

	private void update(jq_Field field, int number, RegFieldSelector ret) {
		fieldSelectorFactory.put(field, ret);
		fieldSelectorToId.put(field, number);
	}

}