package org.spoofax.modelware.emf.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.jsglr.client.imploder.ImploderOriginTermFactory;
import org.spoofax.modelware.emf.trans.Constants;
import org.spoofax.terms.AbstractTermFactory;
import org.spoofax.terms.TermFactory;
import org.spoofax.terms.attachments.OriginAttachment;
import org.strategoxt.imp.generator.construct_textual_change_4_0;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.FileState;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.dynamicloading.RefactoringFactory;
import org.strategoxt.imp.runtime.editor.SpoofaxEditor;
import org.strategoxt.imp.runtime.parser.SGLRParseController;
import org.strategoxt.imp.runtime.services.StrategoObserver;
import org.strategoxt.imp.runtime.stratego.SourceAttachment;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.Strategy;

public class Utils {

	public static AbstractTermFactory termFactory = new TermFactory();

	public static SpoofaxEditor findSpoofaxEditor(IPath path) {
		return (SpoofaxEditor) findEditor(path, SpoofaxEditor.EDITOR_ID);
	}

	public static IEditorPart findEditor(IPath path, String editorID) {
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path);

		if (file == null)
			return null;

		IEditorInput editorInput = new FileEditorInput(file);

		Collection<IWorkbenchPage> pages = getAllWorkbenchPages();
		Iterator<IWorkbenchPage> it = pages.iterator();

		while (it.hasNext()) {
			IWorkbenchPage page = it.next();
			IEditorReference[] editors = page.findEditors(editorInput, null, IWorkbenchPage.MATCH_INPUT);
			for (int i = 0; i < editors.length; i++) {
				if (editors[i].getId().equals(editorID)) {
					return editors[i].getEditor(false);
				}
			}
		}

		return null;
	}

	public static Collection<IWorkbenchPage> getAllWorkbenchPages() {
		Collection<IWorkbenchPage> result = new LinkedList<IWorkbenchPage>();
		for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			result.addAll(Arrays.asList(window.getPages()));
		}
		return result;
	}

	public static FileState getEditorOrFileState(IPath path) {
		try {
			IEditorPart part = findSpoofaxEditor(path);
			if (part != null) {
				return EditorState.getEditorFor(part);
			}
			else {
				return FileState.getFile(path, null);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Hashtable<IResource, ASTPair> ASTPairs = new Hashtable<IResource, ASTPair>();

	public static IStrategoTerm getASTgraph(FileState fileState) {
		if (fileState.getCurrentAst() == null) { // empty document
			return null;
		}

		IStrategoTerm result = null;
		try {
			IStrategoTerm ASTtext = fileState.getCurrentAnalyzedAst();

			// hack to avoid race condition on start-up: wait till file is analyzed
			while (ASTtext == null) {
				Thread.sleep(25);
				ASTtext = fileState.getCurrentAnalyzedAst();
			}

			ASTPair ASTPair = ASTPairs.get(fileState.getResource());
			if (ASTPair != null && ASTPair.ASTtext == ASTtext) {
				result = ASTPair.ASTgraph;
			}
			else {
				if (ASTPair != null) {
					result = ASTtoAST(ASTtext, ASTPair.ASTtext, fileState, Constants.STRATEGY_TREE2MODEL);
				}
				else {
					IStrategoTerm none = termFactory.makeAppl(termFactory.makeConstructor("None", 0));
					result = ASTtoAST(ASTtext, none, fileState, Constants.STRATEGY_TREE2MODEL);
	
					// hack to avoid race condition on start-up: wait till strategy has been loaded
					while (result == null) {
						Thread.sleep(25);
						result = ASTtoAST(ASTtext, none, fileState, Constants.STRATEGY_TREE2MODEL);
					}
				}
				
				ASTPairs.put(fileState.getResource(), new ASTPair(ASTtext, result));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public static IStrategoTerm getASTtext(IStrategoTerm ASTgraph, FileState fileState) {
		ASTPair analyzedAdjustedPair = ASTPairs.get(fileState.getResource());
		return ASTtoAST(ASTgraph, analyzedAdjustedPair.ASTtext, fileState, Constants.STRATEGY_MODEL2TREE);
	}

	private static IStrategoTerm ASTtoAST(IStrategoTerm newAST, IStrategoTerm oldAST, FileState fileState, String strategy) {
		IStrategoTerm result = invokeStrategy(fileState, strategy, termFactory.makeTuple(newAST, oldAST));
		
		// ensures propagation of origin information
		if (result != null && OriginAttachment.getOrigin(newAST) != null) {
			ImploderOriginTermFactory factory = new ImploderOriginTermFactory(termFactory);
			factory.makeLink(result, newAST);
		}

		return result;
	}
	
	public static IStrategoTerm invokeStrategy(FileState fileState, String strategy, IStrategoTerm... inputTerms) {
		StrategoObserver observer = getObserver(fileState);

		IStrategoTerm input = null;
		if (inputTerms.length == 1) {
			input = inputTerms[0];
		}
		else {
			input = termFactory.makeTuple(inputTerms);
		}
		
		IStrategoTerm result = null;
		if (strategyExists(fileState, strategy)) {
			try {
				result = observer.invoke(strategy, input, fileState.getResource().getFullPath().toFile());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (result == null) {
				observer.reportRewritingFailed();
			}
		}
		
		return result;
	}
	
	public static boolean strategyExists(FileState fileState, String strategy) {
		StrategoObserver observer = getObserver(fileState);
		observer.getLock().lock();
		try {
			if (observer.getRuntime().lookupUncifiedSVar(strategy) != null) {
				return true;
			}
		} finally {
			observer.getLock().unlock();
		}
		
		return false;
	}
	
	public static boolean isTextToDiagramSynchronizationEnabled(FileState fileState) {
		return invokeStrategy(fileState, "disable-sync-text-to-diagram", Utils.createNone()) == null;
	}
	
	public static boolean isDiagramToTextSynchronizationEnabled(FileState fileState) {
		return invokeStrategy(fileState, "disable-sync-diagram-to-text", Utils.createNone()) == null;
	}
	
	public static boolean isTextToDiagramSelectionEnabled(FileState fileState) {
		return invokeStrategy(fileState, "disable-select-text-to-diagram", Utils.createNone()) == null;
	}
	
	public static boolean isDiagramToTextSelectionEnabled(FileState fileState) {
		return invokeStrategy(fileState, "disable-select-diagram-to-text", Utils.createNone()) == null;
	}
	
	public static boolean isIncrementalModelToTree(FileState fileState) {
		return invokeStrategy(fileState, "incremental-model-to-tree", Utils.createNone()) != null;
	}

	public static StrategoObserver getObserver(FileState fileState) {
		try {
			return fileState.getDescriptor().createService(StrategoObserver.class, fileState.getParseController());
		}
		catch (BadDescriptorException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	// TODO: use StrategoTextChangeCalculator instead
	public static String calculateTextReplacement(IStrategoTerm oldTree, IStrategoTerm newTree, FileState fileState) {
		SGLRParseController controller = fileState.getParseController();
		Descriptor descriptor = fileState.getDescriptor();

		try {
			StrategoObserver observer = descriptor.createService(StrategoObserver.class, controller);
			if (oldTree == null) {
				IStrategoString result = (IStrategoString) invokeStrategy(fileState, RefactoringFactory.getPPStrategy(descriptor), newTree);
				return result.stringValue();
			}
			else {
				File file = SourceAttachment.getFile(controller.getCurrentAst());
				
				observer.getLock().lock();
				try {
					IStrategoTerm textreplace = construct_textual_change_4_0.instance.invoke(
							observer.getRuntime().getCompiledContext(),
							termFactory.makeTuple(oldTree, newTree),
							createStrategy(RefactoringFactory.getPPStrategy(descriptor), file, observer),
							createStrategy(RefactoringFactory.getParenthesizeStrategy(descriptor), file, observer),
							createStrategy(RefactoringFactory.getOverrideReconstructionStrategy(descriptor), file, observer),
							createStrategy(RefactoringFactory.getResugarStrategy(descriptor), file, observer));
					return ((IStrategoString) textreplace.getSubterm(2)).stringValue();
				}
				finally {
					observer.getLock().unlock();
				}
			}
		}
		catch (BadDescriptorException e) {
			e.printStackTrace();
		}

		return null;
	}

	private static Strategy createStrategy(final String sname, final File file, final StrategoObserver observer) {
		return new Strategy() {
			@Override
			public IStrategoTerm invoke(Context context, IStrategoTerm current) {
				if (sname != null)
					return observer.invokeSilent(sname, current, file);
				return null;
			}
		};
	}

	// not used at the moment
	public static List<Integer> term2path(IStrategoTerm term, IStrategoTerm root) {
		return term2pathHelper(term, root, new ArrayList<Integer>());
	}

	// not used at the moment
	private static List<Integer> term2pathHelper(IStrategoTerm term, IStrategoTerm root, List<Integer> parentPath) {
		if (term == root) {
			return parentPath;
		}
		else {
			IStrategoTerm[] subterms = root.getAllSubterms();
			for (int i = 0; i < subterms.length; i++) {
				List<Integer> subtermParentPath = new ArrayList<Integer>();
				subtermParentPath.addAll(parentPath);
				subtermParentPath.add(i);

				List<Integer> subtermResult = term2pathHelper(term, subterms[i], subtermParentPath);
				if (subtermResult != null) {
					return subtermResult;
				}
			}
		}

		return null;
	}

	public static EStructuralFeature getFeature(EClass c, int i) {
		EAnnotation featureIndexes = c.getEAnnotation(Constants.ANNO_FEATURE_INDEX);
		if (featureIndexes != null) {
			String featureName = featureIndexes.getDetails().get(Integer.toString(i));
			return c.getEStructuralFeature(featureName);
		} else {
			return c.getEAllStructuralFeatures().get(i);
		}
	}
	
	public static boolean isSome(IStrategoTerm term) {
		return term.getTermType() == IStrategoAppl.APPL && ((IStrategoAppl) term).getConstructor().getName().equals("Some") && term.getSubtermCount() == 1;
	}

	public static boolean isNone(IStrategoTerm term) {
		return term.getTermType() == IStrategoAppl.APPL && ((IStrategoAppl) term).getConstructor().getName().equals("None") && term.getSubtermCount() == 0;
	}
	
	public static boolean isEmptyList(IStrategoTerm term) {
		return term.getTermType() == IStrategoAppl.LIST && ((IStrategoList) term).isEmpty();
	}
	
	public static boolean isUnresolved(IStrategoTerm term) {
		return term.getTermType() == IStrategoAppl.APPL && ((IStrategoAppl) term).getConstructor().getName().equals("Unresolved") && term.getSubtermCount() == 0;
	}
	
	public static IStrategoTerm createNone() {
		return termFactory.makeAppl(termFactory.makeConstructor("None", 0));
	}

	public static EStructuralFeature index2feature(EClass eClass, int index) {
		EMap<String, String> index2name = eClass.getEAnnotation(Constants.ANNO_FEATURE_INDEX).getDetails();
		return eClass.getEStructuralFeature(index2name.get(Integer.toString(index)));
	}
	
	public static int feature2index(EClass eClass, EStructuralFeature eFeature) {
		EMap<String, String> index2name = eClass.getEAnnotation(Constants.ANNO_FEATURE_INDEX).getDetails();
		return Integer.parseInt(getKeyByValue(index2name, eFeature.getName()));
	}
	
	private static <T, E> T getKeyByValue(EMap<T, E> map, E value) {
		for (Entry<T, E> entry : map.entrySet()) {
			if (value.equals(entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}
}

class ASTPair {

	IStrategoTerm ASTtext;
	IStrategoTerm ASTgraph;

	public ASTPair(IStrategoTerm ASTtext, IStrategoTerm ASTgraph) {
		this.ASTtext = ASTtext;
		this.ASTgraph = ASTgraph;
	}
}
