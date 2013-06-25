package org.spoofax.modelware.emf.origin;

import java.util.Hashtable;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.spoofax.modelware.emf.origin.model.EObjectOrigin;
import org.spoofax.modelware.emf.origin.model.ESlotOrigin;

public class ModelOriginHashtable extends Hashtable<EObject, EObjectOrigin> {

	private static final long serialVersionUID = -7448225581804889626L;
	
//	public static final ModelOriginHashtable INSTANCE = new ModelOriginHashtable();
//	
//	private ModelOriginHashtable() {
//	}
//	
	public EObjectOrigin get(EObject object) {
		return null;
	}
	
	public ESlotOrigin get(EObject object, EStructuralFeature feature) {
		return null;
	}
	
	public ESlotOrigin get(EObject object, EStructuralFeature feature, int value) {
		return null;
	}
	
//	/**
//	 * Removes all objects and all objects associated to eObject's containing EObjects
//	 */
//    public synchronized void clear(EObject eObject) {
//    	TreeIterator<Object> it = EcoreUtil.getAllContents(eObject, true);
//    	while (it.hasNext()) {
//        	remove(it.next());
//    	}
//    }
	
	// term2model: once (construct)
	// model2term: once (read)
	// textSelection: hold hashtable with alllll origin terms that map to EObject?? 
	
	//match based on origin term? (if object/feature/value has origin term.. which is a subterm of selection...).
	// diagramselection: object2subterm... or object2eOrigin
	
}
