package org.spoofax.modelware.gmf;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.imp.editor.UniversalEditor;
import org.eclipse.imp.parser.IModelListener;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.ui.ide.ResourceUtil;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.modelware.gmf.BridgeEvent;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.parser.SGLRParseController;
import org.strategoxt.imp.runtime.services.StrategoObserver;

/**
 * @author Oskar van Rest
 */
public class TextChangeListener implements IModelListener {

	private EditorPair editorPair;
	private IStrategoTerm lastAST;
	private long timeOfLastChange;
	private Thread thread;
	private static final long keyStrokeTimeout = 700;
	private boolean debouncer;

	public TextChangeListener(EditorPair editorPair) {
		this.editorPair = editorPair;
		editorPair.registerObserver(new Debouncer());
	}

	@Override
	public AnalysisRequired getAnalysisRequired() {
		return AnalysisRequired.NONE;
	}

	@Override
	public void update(IParseController controller, IProgressMonitor monitor) {		
		if (debouncer) {
			debouncer = false;
			setLastAST();
			return;
		}
		
		timeOfLastChange = System.currentTimeMillis();
		if (thread == null || !thread.isAlive()) {
			thread = new Thread(new Timer());
			thread.start();
		}
	}
	
	private void setLastAST() {
		UniversalEditor textEditor = editorPair.getTextEditor();
		EditorState activeEditor = EditorState.getEditorFor(textEditor);
		lastAST = activeEditor.getCurrentAst();
	}

	private void executeTerm2Model() {
		UniversalEditor textEditor = editorPair.getTextEditor();
		EditorState activeEditor = EditorState.getEditorFor(textEditor);
		IStrategoTerm AST = activeEditor.getCurrentAst();

		if (lastAST != null && lastAST.equals(AST)) {
			return;
		} else {
			setLastAST();
		}

		IResource resource = ResourceUtil.getResource(textEditor.getEditorInput());
		Descriptor descriptor = activeEditor.getDescriptor();
		SGLRParseController parseController = activeEditor.getParseController();
		StrategoObserver observer = null;

		try {
			observer = descriptor.createService(StrategoObserver.class, null);
		} catch (BadDescriptorException e) {
			e.printStackTrace();
		}

		observer.getLock().lock();
		try {
			editorPair.notifyObservers(BridgeEvent.PreParse);
			observer.update(parseController, new NullProgressMonitor());
		} finally {
			observer.getLock().unlock();
			editorPair.notifyObservers(BridgeEvent.PostParse);
		}
		
		IStrategoTerm analysedAST = observer.getResultingAst(resource);
		if (analysedAST instanceof IStrategoTuple && analysedAST.getSubtermCount()>0 && analysedAST.getSubterm(0) instanceof IStrategoAppl) {
			analysedAST = analysedAST.getSubterm(0); 
		}
		if (analysedAST instanceof IStrategoAppl) {
			Bridge.getInstance().term2Model(editorPair, analysedAST);
		}
	}

	private class Timer implements Runnable {
		public void run() {
			try {
				long different = -1;
				while (different < keyStrokeTimeout) {
					different = System.currentTimeMillis() - timeOfLastChange;
					Thread.sleep(Math.max(0, keyStrokeTimeout - different));
				}
				executeTerm2Model();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private class Debouncer implements EditorPairObserver {

		@Override
		public void notify(BridgeEvent event) {			
			if (event == BridgeEvent.PreLayoutPreservation) {
				debouncer = true;
			}
		}
	}
}
