package framework.scuba.domain.location;


public class MemLocP2SetWrapper extends MemLocP2Set {

	public boolean allAllocs() {
		boolean ret = true;
		for (MemLoc loc : locToCst.keySet()) {
			if (!(loc instanceof AllocLoc)) {
				ret = false;
				break;
			}
		}
		return ret;
	}

}
