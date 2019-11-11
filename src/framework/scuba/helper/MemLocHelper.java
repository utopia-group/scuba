package framework.scuba.helper;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;
import framework.scuba.domain.factories.EpsilonFieldSelectorFactory;
import framework.scuba.domain.factories.IndexFieldSelectorFactory;
import framework.scuba.domain.field.EpsilonFieldSelector;
import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.field.IndexFieldSelector;
import framework.scuba.domain.field.RegFieldSelector;
import framework.scuba.domain.location.AccessPathObject;
import framework.scuba.domain.location.AllocLoc;
import framework.scuba.domain.location.GlobalAccessPathLoc;
import framework.scuba.domain.location.GlobalLoc;
import framework.scuba.domain.location.LocalAccessPathLoc;
import framework.scuba.domain.location.LocalVarLoc;
import framework.scuba.domain.location.MemLoc;
import framework.scuba.domain.location.ParamLoc;
import framework.scuba.domain.location.RetLoc;
import framework.scuba.domain.location.StackObject;
import framework.scuba.domain.summary.Env;
import framework.scuba.domain.summary.SummariesEnv;

public class MemLocHelper {

	private static MemLocHelper instance = new MemLocHelper();

	public static MemLocHelper h() {
		return instance;
	}

	// get the static type of <loc>.<field>
	public Set<jq_Type> getTypes(MemLoc loc, FieldSelector field) {
		Set<jq_Type> ret = new HashSet<jq_Type>();
		if (loc instanceof ParamLoc || loc instanceof GlobalLoc
				|| loc instanceof LocalVarLoc || loc instanceof RetLoc) {
			assert (field instanceof EpsilonFieldSelector) : loc + " " + field;
			ret.add(loc.getType());
		} else if (loc instanceof AllocLoc) {
			if (field instanceof IndexFieldSelector) {
				jq_Type type = loc.getType();
				assert (type instanceof jq_Array) : loc + " " + type;
				jq_Array arr = (jq_Array) type;
				jq_Type elemType = arr.getElementType();
				if (TypeHelper.h().isRefType(elemType)) {
					ret.add(elemType);
				}
			} else if (field instanceof RegFieldSelector) {
				jq_Field f = ((RegFieldSelector) field).getField();
				ret.add(f.getType());
			}
		} else if (loc instanceof AccessPathObject) {
			if (field instanceof IndexFieldSelector) {
				Set<jq_Type> types = ((AccessPathObject) loc)
						.getPossibleTypes();
				for (jq_Type type : types) {
					if (type instanceof jq_Array) {
						jq_Array arr = (jq_Array) type;
						jq_Type elemType = arr.getElementType();
						if (TypeHelper.h().isRefType(elemType)) {
							ret.add(elemType);
						}
					} else if (type.equals(Program.g().getClass(
							"java.lang.Object"))) {
						ret.add(Program.g().getClass("java.lang.Object"));
						if (G.warning) {
							System.out
									.println("[Warning] "
											+ "element type is set to java.lang.Object");
						}
					}
				}
			} else if (field instanceof RegFieldSelector) {
				jq_Field regF = ((RegFieldSelector) field).getField();
				// using dynamic types instead of static type
				// ret.addAll(Env.v().getTypesByField(regF));
				ret.add(regF.getType());
			}
		}

		assert (field instanceof IndexFieldSelector || !ret.isEmpty()) : loc
				+ " " + field;
		return ret;
	}

	// get static types of <loc> (access path might have multiple types)
	public Set<jq_Type> getTypes(MemLoc loc) {
		Set<jq_Type> ret = new HashSet<jq_Type>();
		if (loc instanceof ParamLoc || loc instanceof GlobalLoc
				|| loc instanceof LocalVarLoc || loc instanceof RetLoc
				|| loc instanceof AllocLoc) {
			ret.add(loc.getType());
		} else if (loc instanceof AccessPathObject) {
			ret.addAll(((AccessPathObject) loc).getPossibleTypes());
		}
		return ret;
	}

	public jq_Type getAllocType(Quad stmt) {
		TypeOperand to = null;
		if (stmt.getOperator() instanceof New) {
			to = New.getType(stmt);
		} else if (stmt.getOperator() instanceof NewArray) {
			to = NewArray.getType(stmt);
		} else if (stmt.getOperator() instanceof MultiNewArray) {
			to = MultiNewArray.getType(stmt);
		}
		assert (to != null) : stmt;
		return to.getType();
	}

	// a naive default filter
	public boolean hasDefault(MemLoc loc, FieldSelector field) {
		if (loc instanceof ParamLoc || loc instanceof GlobalLoc
				|| loc instanceof AccessPathObject) {
			return loc.hasField(field);
		} else if (loc instanceof AllocLoc || loc instanceof RetLoc
				|| loc instanceof LocalVarLoc) {
			return false;
		}
		assert false : loc;
		return false;
	}

	// a fine-grained default filter
	public AccessPathObject getDefault(MemLoc loc, FieldSelector f,
			boolean notSmash) {
		AccessPathObject ret = null;
		if (hasDefault(loc, f)) {
			if (SummariesEnv.v().smashLevel == SummariesEnv.FieldSmashLevel.REG) {
				if (f instanceof IndexFieldSelector) {
					// we do not smash non-consecutive index fields
					if (loc instanceof GlobalLoc) {
						ret = Env.v().getGlobalAPLoc(loc, f);
					} else if (loc instanceof ParamLoc) {
						ret = Env.v().getLocalAPLoc(loc, f);
					} else if (loc instanceof AccessPathObject) {
						AccessPathObject path = (AccessPathObject) loc;
						FieldSelector f1 = path.getOuter();
						if (f1 instanceof IndexFieldSelector) {
							ret = path;
							Set<jq_Type> types = getTypes(path, f1);
							ret.addSmashedField(f1);
							// add possible types for smashing index field
							ret.addPossibleTypes(types);
						} else if (loc instanceof GlobalAccessPathLoc) {
							ret = Env.v().getGlobalAPLoc(
									(GlobalAccessPathLoc) loc, f);
						} else if (loc instanceof LocalAccessPathLoc) {
							ret = Env.v().getLocalAPLoc(
									(LocalAccessPathLoc) loc, f);
						}
					}
				} else {
					if (loc instanceof GlobalLoc) {
						ret = Env.v().getGlobalAPLoc(loc, f);
					} else if (loc instanceof ParamLoc) {
						ret = Env.v().getLocalAPLoc(loc, f);
					} else if (loc instanceof AccessPathObject) {
						if (notSmash) {
							if (loc instanceof GlobalAccessPathLoc) {
								GlobalAccessPathLoc ap = (GlobalAccessPathLoc) loc;
								if (ap.isSmashed()) {
									ret = Env.v().getGlobalAPLoc(ap, f);
								} else {
									FieldSelector outer = ap.getOuter();
									if (outer instanceof EpsilonFieldSelector) {
										ret = Env.v().getGlobalAPLoc(ap, f);
									} else if (outer instanceof IndexFieldSelector) {
										if (f.isBack()) {
											ret = Env.v().getGlobalAPLoc(ap, f);
										} else if (f.isForward()) {
											ret = Env.v().getGlobalAPLoc(ap, f);
										} else if (f.isStay()) {
											ret = ap;
										} else {
											assert false : f.getFieldType();
										}
									} else if (outer instanceof RegFieldSelector) {
										if (f.isBack()) {
											MemLoc inner = ap.getInner();
											assert (inner instanceof GlobalAccessPathLoc) : inner;
											ret = (GlobalAccessPathLoc) inner;
										} else if (f.isForward()) {
											ret = Env.v().getGlobalAPLoc(ap, f);
										} else if (f.isStay()) {
											ret = Env.v().getGlobalAPLoc(ap, f);
										} else {
											assert false : f.getFieldType();
										}
									} else {
										assert false : outer;
									}
								}
							} else if (loc instanceof LocalAccessPathLoc) {
								LocalAccessPathLoc ap = (LocalAccessPathLoc) loc;
								if (ap.isSmashed()) {
									assert (!f.isBack()) : f;
									ret = Env.v().getLocalAPLoc(ap, f);
								} else {
									FieldSelector outer = ap.getOuter();
									if (outer instanceof EpsilonFieldSelector) {
										ret = Env.v().getLocalAPLoc(ap, f);
									} else if (outer instanceof IndexFieldSelector) {
										if (f.isBack()) {
											ret = Env.v().getLocalAPLoc(ap, f);
										} else if (f.isForward()) {
											ret = Env.v().getLocalAPLoc(ap, f);
										} else if (f.isStay()) {
											ret = ap;
										} else {
											assert false : f.getFieldType();
										}
									} else if (outer instanceof RegFieldSelector) {
										if (f.isBack()) {
											MemLoc inner = ap.getInner();
											assert (inner instanceof LocalAccessPathLoc) : inner;
											ret = (LocalAccessPathLoc) inner;
										} else if (f.isForward()) {
											ret = Env.v().getLocalAPLoc(ap, f);
										} else if (f.isStay()) {
											ret = Env.v().getLocalAPLoc(ap, f);
										} else {
											assert false : f.getFieldType();
										}
									} else {
										assert false : outer;
									}
								}
							}
						} else {
							AccessPathObject path = (AccessPathObject) loc;
							ret = path.getPrefix(f);
							if (ret != null) {
								// do smashing
								Set<FieldSelector> smashedFields = path
										.getPreSmashedFields(f);
								ret.addSmashedFields(smashedFields);
							} else {
								if (loc instanceof GlobalAccessPathLoc) {
									GlobalAccessPathLoc ap = (GlobalAccessPathLoc) loc;
									if (ap.isSmashed()) {
										ret = Env.v().getGlobalAPLoc(ap, f);
									} else {
										FieldSelector outer = ap.getOuter();
										if (outer instanceof EpsilonFieldSelector) {
											ret = Env.v().getGlobalAPLoc(ap, f);
										} else if (outer instanceof IndexFieldSelector) {
											if (f.isBack()) {
												ret = Env.v().getGlobalAPLoc(
														ap, f);
											} else if (f.isForward()) {
												ret = Env.v().getGlobalAPLoc(
														ap, f);
											} else if (f.isStay()) {
												ret = ap;
											} else {
												assert false : f.getFieldType();
											}
										} else if (outer instanceof RegFieldSelector) {
											if (f.isBack()) {
												MemLoc inner = ap.getInner();
												assert (inner instanceof GlobalAccessPathLoc) : inner;
												ret = (GlobalAccessPathLoc) inner;
											} else if (f.isForward()) {
												ret = Env.v().getGlobalAPLoc(
														ap, f);
											} else if (f.isStay()) {
												ret = Env.v().getGlobalAPLoc(
														ap, f);
											} else {
												assert false : f.getFieldType();
											}
										} else {
											assert false : outer;
										}
									}
								} else if (loc instanceof LocalAccessPathLoc) {
									LocalAccessPathLoc ap = (LocalAccessPathLoc) loc;
									if (ap.isSmashed()) {
										ret = Env.v().getLocalAPLoc(ap, f);
									} else {
										FieldSelector outer = ap.getOuter();
										if (outer instanceof EpsilonFieldSelector) {
											ret = Env.v().getLocalAPLoc(ap, f);
										} else if (outer instanceof IndexFieldSelector) {
											if (f.isBack()) {
												ret = Env.v().getLocalAPLoc(ap,
														f);
											} else if (f.isForward()) {
												ret = Env.v().getLocalAPLoc(ap,
														f);
											} else if (f.isStay()) {
												ret = ap;
											} else {
												assert false : f.getFieldType();
											}
										} else if (outer instanceof RegFieldSelector) {
											if (f.isBack()) {
												MemLoc inner = ap.getInner();
												assert (inner instanceof LocalAccessPathLoc) : inner;
												ret = (LocalAccessPathLoc) inner;
											} else if (f.isForward()) {
												ret = Env.v().getLocalAPLoc(ap,
														f);
											} else if (f.isStay()) {
												ret = Env.v().getLocalAPLoc(ap,
														f);
											} else {
												assert false : f.getFieldType();
											}
										} else {
											assert false : outer;
										}
									}
								}
							}
						}
					}
				}
			} else {
				assert false : SummariesEnv.v().smashLevel;
			}
		}

		assert (hasDefault(loc, f) ? ret != null : ret == null);
		return ret;
	}

	// whether the memory location is appeared in the shared summary
	public boolean isShared(MemLoc loc) {
		boolean ret = false;
		if (loc instanceof LocalVarLoc) {
			ret = false;
		} else if (loc instanceof ParamLoc) {
			ret = false;
		} else if (loc instanceof GlobalLoc) {
			ret = true;
		} else if (loc instanceof RetLoc) {
			ret = false;
		} else if (loc instanceof LocalAccessPathLoc) {
			ret = false;
		} else if (loc instanceof GlobalAccessPathLoc) {
			ret = true;
		} else if (loc instanceof AllocLoc) {
			if (Env.v().isSharedAlloc((AllocLoc) loc)) {
				return true;
			} else if (G.countScc > SummariesEnv.v().lift) {
				AllocLoc alloc = ((AllocLoc) loc);
				int length = alloc.ctxtLength();
				jq_Type type = alloc.getType();
				Quad site = alloc.getSite();
				int maxLength = Env.v().getAllocDepth(site, type);
				if (length >= maxLength) {
					ret = true;
				} else {
					ret = false;
				}
			} else {
				ret = false;
			}
		} else {
			assert false : loc;
		}
		if (ret) {
			Env.v().addSharedMemLoc(loc);
		}
		return ret;
	}

	// check the type of <other> is sub-type of <loc>.<field>
	public boolean isSubType(MemLoc loc, FieldSelector field, MemLoc other) {
		Set<jq_Type> otherTypes = getTypes(other);
		Set<jq_Type> types = getTypes(loc, field);
		for (jq_Type otherType : otherTypes) {
			for (jq_Type type : types) {
				if (otherType.isSubtypeOf(type)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isSubType(MemLoc loc, MemLoc other) {
		Set<jq_Type> otherTypes = getTypes(other);
		Set<jq_Type> types = getTypes(loc);
		for (jq_Type otherType : otherTypes) {
			for (jq_Type type : types) {
				if (otherType.isSubtypeOf(type)) {
					return true;
				}
			}
		}
		return false;
	}

	// check the type of <other> is compatible with that of <loc>.<field>
	public boolean isTypeCompatible(MemLoc loc, FieldSelector field,
			MemLoc other) {
		Set<jq_Type> otherTypes = getTypes(other);
		Set<jq_Type> types = getTypes(loc, field);
		for (jq_Type otherType : otherTypes) {
			for (jq_Type type : types) {
				if (otherType.isSubtypeOf(type) || type.isSubtypeOf(otherType)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isTypeCompatible(MemLoc loc, MemLoc other) {
		Set<jq_Type> otherTypes = getTypes(other);
		Set<jq_Type> types = getTypes(loc);
		for (jq_Type otherType : otherTypes) {
			for (jq_Type type : types) {
				if (otherType.isSubtypeOf(type) || type.isSubtypeOf(otherType)) {
					return true;
				}
			}
		}
		return false;
	}

	// get all fields of a memory location
	public Set<FieldSelector> getFields(MemLoc loc, jq_Type type) {
		Set<FieldSelector> ret = null;
		if (SummariesEnv.v().cipaFiltering) {
			ret = getFieldsCIPA(loc, type);
		} else {
			ret = getFieldsType(loc, type);
		}
		return ret;
	}

	// get all fields of the memory location based on the static type
	private Set<FieldSelector> getFieldsType(MemLoc loc, jq_Type type) {
		Set<FieldSelector> ret = new HashSet<FieldSelector>();
		// a stack object only has \e field
		if (loc instanceof StackObject) {
			ret.add(Env.v().getEpsilonFieldSelector());
			return ret;
		}
		// fields of a heap object depend on the type information
		if (type instanceof jq_Array) {
			// an array has only \i field
			ret.add(Env.v().getIndexFieldSelector());
		} else if (type instanceof jq_Class) {
			jq_Class c = (jq_Class) type;
			Set<jq_Field> fields = Env.v().getFieldsFromType(c);
			// collect all the regular fields
			for (jq_Field field : fields) {
				if (Env.v().isMergedField(field)) {
					ret.add(Env.v().getMergedFieldSelector(field));
				} else {
					ret.add(Env.v().getRegFieldSelector(field));
				}
			}
			// an "object" object can also have \i field
			if (type.equals(Program.g().getClass("java.lang.Object"))) {
				ret.add(Env.v().getIndexFieldSelector());
			}
		} else {
			assert false : type;
		}
		return ret;
	}

	// get all fields of the memory location based on CIPA
	private Set<FieldSelector> getFieldsCIPA(MemLoc loc, jq_Type type) {
		Set<FieldSelector> ret = new HashSet<FieldSelector>();
		EpsilonFieldSelector e = EpsilonFieldSelectorFactory.f().get();
		IndexFieldSelector idx = IndexFieldSelectorFactory.f().get();
		if (loc instanceof ParamLoc) {
			ret.add(e);
		} else if (loc instanceof LocalVarLoc) {
			ret.add(e);
		} else if (loc instanceof RetLoc) {
			ret.add(e);
		} else if (loc instanceof GlobalLoc) {
			ret.add(e);
		} else if (loc instanceof LocalAccessPathLoc) {
			LocalAccessPathLoc ap = (LocalAccessPathLoc) loc;
			FieldSelector outer = ap.getOuter();
			if (outer instanceof EpsilonFieldSelector) {
				// R0.\e
				MemLoc inner = ap.getInner();
				assert (inner instanceof ParamLoc) : inner;
				Register r = ((ParamLoc) inner).getRegister();
				Set<jq_Field> fields = Env.v().getFieldsOfFormal(r);
				if (fields == null) {
					return ret;
				}
				for (jq_Field field : fields) {
					if (field == null) {
						ret.add(idx);
					} else {
						if (Env.v().isMergedField(field)) {
							ret.add(Env.v().getMergedFieldSelector(field));
						} else {
							ret.add(Env.v().getRegFieldSelector(field));
						}
					}
				}
			} else if (outer instanceof IndexFieldSelector) {
				MemLoc inner = ap.getInner();
				assert (inner instanceof LocalAccessPathLoc) : inner;
				LocalAccessPathLoc ap1 = (LocalAccessPathLoc) inner;
				FieldSelector outer1 = ap1.getOuter();
				if (outer1 instanceof EpsilonFieldSelector) {
					// R0.\e.\i
					MemLoc inner1 = ap1.getInner();
					assert (inner1 instanceof ParamLoc) : inner1;
					Register r = ((ParamLoc) inner1).getRegister();
					Set<jq_Field> fields = Env.v().getFieldsOfFormalIndex(r);
					if (fields == null) {
						return ret;
					}
					for (jq_Field field : fields) {
						if (field == null) {
							ret.add(idx);
						} else {
							if (Env.v().isMergedField(field)) {
								ret.add(Env.v().getMergedFieldSelector(field));
							} else {
								ret.add(Env.v().getRegFieldSelector(field));
							}
						}
					}
				} else if (outer1 instanceof IndexFieldSelector) {
					// R0.\e....\i.\i
					assert false : ap;
				} else if (outer1 instanceof RegFieldSelector) {
					// R0.\e.f.\i
					jq_Field field = ((RegFieldSelector) outer1).getField();
					Set<jq_Field> fields1 = Env.v()
							.getFieldsOfFieldIndex(field);
					if (fields1 == null) {
						return ret;
					}
					for (jq_Field field1 : fields1) {
						if (Env.v().isMergedField(field)) {
							ret.add(Env.v().getMergedFieldSelector(field1));
						} else {
							ret.add(Env.v().getRegFieldSelector(field1));
						}
					}
				} else {
					assert false : outer1;
				}
			} else if (outer instanceof RegFieldSelector) {
				// R0.\e....f
				jq_Field field = ((RegFieldSelector) outer).getField();
				Set<jq_Field> fields1 = Env.v().getFieldsOfField(field);
				if (fields1 == null) {
					return ret;
				}
				for (jq_Field field1 : fields1) {
					if (field1 == null) {
						ret.add(idx);
					} else {
						if (Env.v().isMergedField(field)) {
							ret.add(Env.v().getMergedFieldSelector(field1));
						} else {
							ret.add(Env.v().getRegFieldSelector(field1));
						}
					}
				}
			} else {
				assert false : outer + " " + loc + " " + type;
			}
		} else if (loc instanceof GlobalAccessPathLoc) {
			GlobalAccessPathLoc ap = (GlobalAccessPathLoc) loc;
			FieldSelector outer = ap.getOuter();
			if (outer instanceof EpsilonFieldSelector) {
				// global.\e
				MemLoc inner = ap.getInner();
				assert (inner instanceof GlobalLoc) : inner;
				jq_Field field = ((GlobalLoc) inner).getStaticField();
				Set<jq_Field> fields1 = Env.v().getFieldsOfField(field);
				if (fields1 == null) {
					return ret;
				}
				for (jq_Field field1 : fields1) {
					if (field1 == null) {
						ret.add(idx);
					} else {
						if (Env.v().isMergedField(field)) {
							ret.add(Env.v().getMergedFieldSelector(field1));
						} else {
							ret.add(Env.v().getRegFieldSelector(field1));
						}
					}
				}
			} else if (outer instanceof IndexFieldSelector) {
				MemLoc inner = ap.getInner();
				assert (inner instanceof GlobalAccessPathLoc) : inner;
				GlobalAccessPathLoc ap1 = (GlobalAccessPathLoc) inner;
				FieldSelector outer1 = ap1.getOuter();
				if (outer1 instanceof EpsilonFieldSelector) {
					// global.\e.\i
					MemLoc inner1 = ap1.getInner();
					assert (inner1 instanceof GlobalLoc) : inner1;
					jq_Field field = ((GlobalLoc) inner1).getStaticField();
					Set<jq_Field> fields1 = Env.v()
							.getFieldsOfFieldIndex(field);
					if (fields1 == null) {
						return ret;
					}
					for (jq_Field field1 : fields1) {
						if (field1 == null) {
							ret.add(idx);
						} else {
							if (Env.v().isMergedField(field)) {
								ret.add(Env.v().getMergedFieldSelector(field1));
							} else {
								ret.add(Env.v().getRegFieldSelector(field1));
							}
						}
					}
				} else if (outer1 instanceof IndexFieldSelector) {
					// global.\e....\i.\i
					assert false : ap;
				} else if (outer1 instanceof RegFieldSelector) {
					// global.\e.f.\i
					jq_Field field = ((RegFieldSelector) outer1).getField();
					Set<jq_Field> fields1 = Env.v()
							.getFieldsOfFieldIndex(field);
					if (fields1 == null) {
						return ret;
					}
					for (jq_Field field1 : fields1) {
						if (field1 == null) {
							ret.add(idx);
						} else {
							if (Env.v().isMergedField(field)) {
								ret.add(Env.v().getMergedFieldSelector(field1));
							} else {
								ret.add(Env.v().getRegFieldSelector(field1));
							}
						}
					}
				} else {
					assert false : outer1;
				}
			} else if (outer instanceof RegFieldSelector) {
				// global.\e.f
				jq_Field field = ((RegFieldSelector) outer).getField();
				Set<jq_Field> fields1 = Env.v().getFieldsOfField(field);
				if (fields1 == null) {
					return ret;
				}
				for (jq_Field field1 : fields1) {
					if (field1 == null) {
						ret.add(idx);
					} else {
						if (Env.v().isMergedField(field)) {
							ret.add(Env.v().getMergedFieldSelector(field1));
						} else {
							ret.add(Env.v().getRegFieldSelector(field1));
						}
					}
				}
			} else {
				assert false : outer;
			}
		} else if (loc instanceof AllocLoc) {
			// alloc
			Quad alloc = ((AllocLoc) loc).getSite();
			Set<jq_Field> fields = Env.v().getFieldsOfAlloc(alloc);
			if (fields == null) {
				return ret;
			}
			for (jq_Field field : fields) {
				if (field == null) {
					ret.add(idx);
				} else {
					if (Env.v().isMergedField(field)) {
						ret.add(Env.v().getMergedFieldSelector(field));
					} else {
						ret.add(Env.v().getRegFieldSelector(field));
					}
				}
			}
		} else {
			assert false : loc + " " + type;
		}
		return ret;
	}

	// get the method creating the memory location
	public jq_Method getParentMethod(MemLoc loc) {
		jq_Method ret = null;
		if (loc instanceof GlobalLoc) {
			// ret = null
		} else if (loc instanceof LocalVarLoc) {
			ret = ((LocalVarLoc) loc).getMethod();
		} else if (loc instanceof GlobalAccessPathLoc) {
			// ret = null
		} else if (loc instanceof ParamLoc) {
			ret = ((ParamLoc) loc).getMethod();
		} else if (loc instanceof RetLoc) {
			ret = ((RetLoc) loc).getMethod();
		} else if (loc instanceof AllocLoc) {
			ret = ((AllocLoc) loc).getSite().getMethod();
		} else if (loc instanceof LocalAccessPathLoc) {
			MemLoc base = ((LocalAccessPathLoc) loc).getBase();
			if (base instanceof ParamLoc) {
				ret = ((ParamLoc) base).getMethod();
			} else if (base instanceof LocalVarLoc) {
				ret = ((LocalVarLoc) base).getMethod();
			} else {
				assert false : loc + " " + base;
			}
		} else {
			assert false : loc;
		}
		return ret;
	}

	// is this a param or local. helper function.
	public SummariesEnv.LocType getVarType(jq_Method meth, Register r) {
		SummariesEnv.LocType ret = SummariesEnv.LocType.LOCAL_VAR;
		ControlFlowGraph cfg = meth.getCFG();
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			if (v.equals(r)) {
				ret = SummariesEnv.LocType.PARAMETER;
				break;
			}
		}
		return ret;
	}
}