package husacct.graphics.task;

import husacct.analyse.IAnalyseService;
import husacct.common.dto.AbstractDTO;
import husacct.common.dto.DependencyDTO;
import husacct.common.dto.ViolationDTO;
import husacct.graphics.presentation.Drawing;
import husacct.graphics.presentation.DrawingView;
import husacct.graphics.presentation.GraphicsFrame;
import husacct.graphics.presentation.figures.BaseFigure;
import husacct.graphics.presentation.figures.FigureFactory;
import husacct.graphics.presentation.figures.RelationFigure;
import husacct.validate.IValidateService;

import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JInternalFrame;

import org.apache.log4j.Logger;

public abstract class BaseController implements MouseClickListener {

	private final int ITEMS_PER_ROW = 4;

	protected Drawing drawing;
	protected DrawingView view;
	protected GraphicsFrame drawTarget;
	protected String currentPath = "";
	private boolean showViolations = false;

	protected Logger logger = Logger.getLogger(BaseController.class);

	protected FigureFactory figureFactory;
	protected FigureConnectorStrategy connectionStrategy;
	protected BasicLayoutStrategy layoutStrategy;

	protected IAnalyseService analyseService;
	protected IValidateService validateService;

	protected FigureMap figureMap = new FigureMap();

	public BaseController() {
		figureFactory = new FigureFactory();
		connectionStrategy = new FigureConnectorStrategy();

		initializeComponents();
	}

	private void initializeComponents() {
		drawing = new Drawing();
		view = new DrawingView(drawing);
		view.addListener(this);

		drawTarget = new GraphicsFrame(view);
		drawTarget.addListener(this);

		layoutStrategy = new BasicLayoutStrategy(drawing);
	}

	public JInternalFrame getGUI() {
		return drawTarget;
	}

	public void clearDrawing() {
		this.figureMap.clearAll();
		this.drawing.clear();
		this.view.clearSelection();
	}

	public String getCurrentPath() {
		return this.currentPath;
	}

	public void resetCurrentPath() {
		this.currentPath = "";
	}

	public void setCurrentPath(String path) {
		this.currentPath = path;
	}

	@Override
	public void figureSelected(BaseFigure[] figures) {
		BaseFigure selectedFigure = figures[0];

		if (this.figureMap.isViolatedFigure(selectedFigure)) {
			this.drawTarget.showViolationsProperties(this.figureMap.getViolatedDTOs(selectedFigure));
		} else if (this.figureMap.isViolationLine(selectedFigure)) {
			this.drawTarget.showViolationsProperties(this.figureMap.getViolationDTOs(selectedFigure));
		} else if (this.figureMap.isDependencyLine(selectedFigure)) {
			this.drawTarget.showDependenciesProperties(this.figureMap.getDependencyDTOs(selectedFigure));
		} else {
			this.drawTarget.hidePropertiesPane();
		}
	}

	@Override
	public void figureDeselected(BaseFigure[] figures) {
		if (view.getSelectionCount() == 0) {
			drawTarget.hidePropertiesPane();
		}
	}

	public abstract void drawArchitecture(DrawingDetail detail);

	protected void drawModules(AbstractDTO[] modules) {
		this.clearDrawing();
		for (AbstractDTO dto : modules) {
			BaseFigure generatedFigure = figureFactory.createFigure(dto);
			drawing.add(generatedFigure);
			this.figureMap.linkModule(generatedFigure, dto);

			BasicLayoutStrategy bls = new BasicLayoutStrategy(drawing);
			bls.doLayout(ITEMS_PER_ROW);
		}
		this.drawTarget.setCurrentPathInfo(this.currentPath);
	}

	public void toggleViolations() {
		if (showViolations) {
			Logger.getLogger(this.getClass()).debug("hiding violations");
			showViolations = false;

			// TODO: Just use (implement) the logic from the Drawing.java to clear all violations?
			// clear violations
			for (BaseFigure figure : this.figureMap.getViolatedFigures()) {
				figure.setViolated(false);
			}
			// TODO: Just use (implement) the logic from the Drawing.java to clear all violation lines?
			for (BaseFigure figure : this.figureMap.getViolationLines()) {
				this.drawing.remove(figure);
			}
			this.figureMap.clearAllViolations();
		} else {
			Logger.getLogger(this.getClass()).debug("showing violations");
			showViolations = true;

			this.drawViolationsForShownModules();
		}
	}

	@Override
	public void exportToImage() {
		// TODO Make better
		this.drawing.showExportToImagePanel();
	}

	public boolean violationsAreShown() {
		return showViolations;
	}

	public boolean dependenciesAreShown() {
		return !violationsAreShown();
	}

	public void showViolations() {
		showViolations = true;
	}

	protected DrawingDetail getCurrentDrawingDetail() {
		DrawingDetail detail = DrawingDetail.WITHOUT_VIOLATIONS;
		if (violationsAreShown()) {
			detail = DrawingDetail.WITH_VIOLATIONS;
		}
		return detail;
	}

	// dependencies

	public void drawDependenciesForShownModules() {
		BaseFigure[] shownModules = this.drawing.getShownModules();
		for (BaseFigure figureFrom : shownModules) {
			for (BaseFigure figureTo : shownModules) {
				getAndDrawDependenciesBetween(figureFrom, figureTo);
			}
		}
		sizeRelationFigures(this.figureMap.getDependencyHashMap()); // TODO see TODO below
	}

	private void getAndDrawDependenciesBetween(BaseFigure figureFrom, BaseFigure figureTo) {
		DependencyDTO[] dependencies = (DependencyDTO[]) getDependenciesBetween(figureFrom, figureTo);
		if (dependencies.length > 0) {
			RelationFigure dependencyFigure = this.figureFactory.createFigure(dependencies);
			this.figureMap.linkDependencies(dependencyFigure, dependencies);
			this.connectionStrategy.connect(dependencyFigure, figureFrom, figureTo);
			drawing.add(dependencyFigure);
		}
	}

	protected abstract DependencyDTO[] getDependenciesBetween(BaseFigure figureFrom, BaseFigure figureTo);

	// violations

	public void drawViolationsForShownModules() {
		BaseFigure[] shownModules = this.drawing.getShownModules();
		for (BaseFigure figureFrom : shownModules) {
			for (BaseFigure figureTo : shownModules) {
				// are the violations in the same module?
				if (figureFrom == figureTo) { // TODO, use equals?
					getAndDrawViolationsIn(figureFrom);
				} else {
					getAndDrawViolationsBetween(figureFrom, figureTo);
				}
			}
		}
		this.sizeRelationFigures(this.figureMap.getViolationHashMap()); // TODO see TODO below
	}

	private void getAndDrawViolationsIn(BaseFigure figureFrom) {
		ViolationDTO[] violations = getViolationsBetween(figureFrom, figureFrom);
		if (violations.length > 0) {
			figureFrom.setViolated(true);
			this.figureMap.linkViolatedModule(figureFrom, violations);
		}
	}

	private void getAndDrawViolationsBetween(BaseFigure figureFrom, BaseFigure figureTo) {
		ViolationDTO[] violations = getViolationsBetween(figureFrom, figureTo);
		if (violations.length > 0) {
			RelationFigure violationFigure = this.figureFactory.createFigure(violations);
			this.figureMap.linkViolations(violationFigure, violations);
			this.connectionStrategy.connect(violationFigure, figureFrom, figureTo);
			drawing.add(violationFigure);
		}
	}

	protected abstract ViolationDTO[] getViolationsBetween(BaseFigure figureFrom, BaseFigure figureTo);

	// TODO: This doesn't belong here
	// Presentation logic
	private void sizeRelationFigures(HashMap<RelationFigure, ? extends AbstractDTO[]> figures) {
		// 1 relation, small
		if (figures.size() == 1) {
			figures.keySet().iterator().next().setLineThickness(1);
		}
		// 2 relations; both small, or one slightly bigger
		else if (figures.size() == 2) {
			Iterator<RelationFigure> iterator = figures.keySet().iterator();
			RelationFigure figure1 = iterator.next();
			RelationFigure figure2 = iterator.next();
			int length1 = figures.get(figure1).length;
			int length2 = figures.get(figure2).length;

			if (length1 == length2) {
				figure1.setLineThickness(1);
				figure2.setLineThickness(1);
			} else if (length1 < length2) {
				figure1.setLineThickness(1);
				figure2.setLineThickness(2);
			} else { // length1 > length2
				figure1.setLineThickness(2);
				figure2.setLineThickness(1);
			}
		}
		// 3 ore more relations; small, big or fat, according to scale
		else if (figures.size() >= 3) {
			// max amounts of dependencies
			int maxAmount = -1;
			for (RelationFigure fig : figures.keySet()) {
				int length = figures.get(fig).length;

				if (maxAmount == -1 || maxAmount < length) {
					maxAmount = length;
				}
			}

			// set line thickness according to scale
			for (RelationFigure fig : figures.keySet()) {
				double weight = (double) figures.get(fig).length / maxAmount;
				if (weight < 0.33) {
					fig.setLineThickness(1);
				} else if (weight < 0.66) {
					fig.setLineThickness(3);
				} else {
					fig.setLineThickness(4);
				}
			}
		}
	}
}
