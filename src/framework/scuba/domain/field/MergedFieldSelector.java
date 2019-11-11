package framework.scuba.domain.field;

import java.util.HashSet;
import java.util.Set;

import framework.scuba.domain.summary.SummariesEnv;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;

public class MergedFieldSelector extends FieldSelector {

	protected final Set<jq_Field> fields = new HashSet<jq_Field>();
	protected final Set<jq_Type> types = new HashSet<jq_Type>();

	public MergedFieldSelector(Set<jq_Field> fields, int number) {
		super(number, SummariesEnv.FieldType.FORWARD);
		for (jq_Field field : fields) {
			addField(field);
		}
	}

	public void addField(jq_Field field) {
		fields.add(field);
		types.add(field.getType());
	}

	// ----------- Object -------------
	@Override
	public String toString() {
		return fields.toString();
	}

	@Override
	public String shortName() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (jq_Field field : fields) {
			sb.append(field.getName().toString());
		}
		sb.append("]");
		return sb.toString();
	}

}
