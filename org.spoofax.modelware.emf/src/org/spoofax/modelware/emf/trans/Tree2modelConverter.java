package org.spoofax.modelware.emf.trans;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.modelware.emf.utils.Utils;

public class Tree2modelConverter {

	private final EPackage pack;
	private final List<Reference> references = new LinkedList<Reference>();
	private final HashMap<IStrategoTerm, EObject> uriMap = new HashMap<IStrategoTerm, EObject>();

	public Tree2modelConverter(EPackage pack) {
		this.pack = pack;
	}

	public EObject convert(IStrategoTerm term) {
		EObject result = convert((IStrategoAppl) term.getSubterm(0));
		setReferences();
		return result;
	}

	private EObject convert(IStrategoAppl term) {
		IStrategoList URIs = (IStrategoList) term.getSubterm(0);
		IStrategoList QID = (IStrategoList) term.getSubterm(1);
		IStrategoList slots = (IStrategoList) term.getSubterm(3);

		EClass c = getClass(QID);
		EObject obj = pack.getEFactoryInstance().create(c);

		for (IStrategoTerm uri : URIs.getAllSubterms()) {
			if (!uriMap.containsKey(uri)) {
				uriMap.put(uri, obj);
			}
		}

		int i = 0;
		while (!slots.isEmpty()) {
			EStructuralFeature f = getFeature(c, i);
			setFeature(slots.head(), obj, f);
			slots = slots.tail();
			i++;
		}

		return obj;
	}

	private void setFeature(IStrategoTerm t, EObject obj, EStructuralFeature f) {
		if (Utils.isNone(t) || Utils.isEmptyList(t)) {
			return;
		}
		if (Utils.isSome(t)) {
			t = t.getSubterm(0); // normalization
		}
		final boolean isList = t.isList();
		if (!isList) {
			t = Utils.termFactory.makeList(t); // normalization
		}

		String featureType = ((IStrategoAppl) t).getConstructor().getName();

		if (featureType.equals("Link")) {
			references.add(new Reference(obj, f, t.getSubterm(0)));
		} else {
			List<Object> values = new LinkedList<Object>();
			for (IStrategoTerm subTerm : t.getSubterm(0).getAllSubterms()) {
				if (featureType.equals("Data")) {
					EDataType type = ((EAttribute) f).getEAttributeType();
					values.add(EcoreUtil.createFromString(type, ((IStrategoString) subTerm).stringValue()));
				} else if (featureType.equals("Contain")) {
					values.add(convert((IStrategoAppl) subTerm));
				}
			}

			obj.eSet(f, isList ? values : values.get(0));
		}
	}

	private EClass getClass(IStrategoTerm QID) {
		return (EClass) pack.getEClassifier(((IStrategoString) QID.getSubterm(1)).stringValue());
	}

	private EStructuralFeature getFeature(EClass c, int i) {
		EAnnotation featureIndexes = c.getEAnnotation(Constants.ANNO_FEATURE_INDEX);
		if (featureIndexes != null) {
			String featureName = featureIndexes.getDetails().get(Integer.toString(i));
			return c.getEStructuralFeature(featureName);
		} else {
			return c.getEAllStructuralFeatures().get(i);
		}
	}

	private void setReferences() {
		for (Reference ref : references) {
			Object result;

			if (ref.uris.getTermType() == IStrategoTerm.LIST) {
				EList<EObject> results = new BasicEList<EObject>();
				for (IStrategoTerm uri : ref.uris.getAllSubterms()) {
					results.add(uriMap.get(uri));
				}
				result = results;
			} else {
				result = uriMap.get(ref.uris);
			}

			ref.object.eSet(ref.feature, result);
		}
	}

	private class Reference {

		public final EObject object;
		public final EStructuralFeature feature;
		public final IStrategoTerm uris;

		public Reference(EObject object, EStructuralFeature feature, IStrategoTerm uris) {
			this.object = object;
			this.feature = feature;
			this.uris = uris;
		}
	}
}