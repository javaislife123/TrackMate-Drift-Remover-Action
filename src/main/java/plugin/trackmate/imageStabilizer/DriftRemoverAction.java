package plugin.trackmate.imageStabilizer;


import java.awt.Frame;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;


import javax.swing.JOptionPane;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;




public class DriftRemoverAction implements TrackMateAction
{
	//Boolean to store value if user has been notified 
	private static boolean isNotified = false;
	
	//Logger object to interact with the TrackMate log
	private Logger logger;
	
	//Unit of measurement of distance
	public static String unit;
	
	@Override
	public void execute( final TrackMate trackmate, final SelectionModel selectionModel, final DisplaySettings displaySettings, final Frame parent )
	{
		
		//Current image stack containing imagePlus object to be drift corrected
		ImagePlus currentImage = WindowManager.getCurrentImage();
		
		float[] aux = null;
		float[] auy = null;
		
		
		
		if (currentImage.getRoi() instanceof PointRoi) {
			 PointRoi points = (PointRoi)currentImage.getRoi();
			 aux = points.getFloatPolygon().xpoints;
			 auy = points.getFloatPolygon().ypoints;
		}else {
			IJ.showMessage("Too many rois or roi is not a point roi");
		}
		Model trackmateModel = trackmate.getModel();
		
		//ArrayList to store each list of transformations
		ArrayList<ArrayList<Double>> xTransformListTotal = new ArrayList<>();
		ArrayList<ArrayList<Double>> yTransformListTotal = new ArrayList<>();
		
		for(int n = 0; n<aux.length; n++) {
			
			
			
			
			//Checks to see if roi is a point
			Roi roi = new Roi((double)aux[n], (double)auy[n],1,1);
			
			//Current Roi Attributes
			double xRoi = roi.getXBase();
			double yRoi = roi.getYBase();
			
			
			//stores close spots that are near the user inputted point
			ArrayList<Spot> closeSpots = getCloseSpots(trackmateModel,xRoi,yRoi);
			
			if(closeSpots.size()==0) {
				IJ.showMessage("Roi is not near enough to a tracked point");
				currentImage.killRoi();
				//Resets plugin
				execute(trackmate, selectionModel, displaySettings, parent);
				return;
			}
			
			//Sorts by time in order to always choose the closest point at frame 1 or closest to
			closeSpots.sort(Spot.timeComparator);
			
			//Gets track based off of the closest spot to ROI and sorts it
			int trackID = trackmateModel.getTrackModel().trackIDOf(closeSpots.get(0));
			Set< Spot > track = trackmateModel.getTrackModel().trackSpots( trackID );
			final TreeSet< Spot > sortedTrack = new TreeSet<>( Spot.timeComparator );
			sortedTrack.addAll( track );
			
			//Stores list of transformations that each frame of the image will be needed to be shifted
			ArrayList<Double> xTransformList = calcDistanceOfX(sortedTrack);
			ArrayList<Double> yTransformList = calcDistanceOfY(sortedTrack);
			
			xTransformListTotal.add(xTransformList);
			yTransformListTotal.add(yTransformList);
		}
		
		// Setup new image stack
		ImageStack currentStack = currentImage.getImageStack();
		ImagePlus[] newImages = new ImagePlus[currentStack.size()];
		int imageIndex = 0, processorIndex = 1, transIndex = -1;
		
		newImages[imageIndex] = new ImagePlus(""+imageIndex,currentStack.getProcessor(processorIndex));
		
		//Averages the transformations of each arraylist in x/yTransformListTotal
		ArrayList<Double> xTransformList = averageArrOfArr(xTransformListTotal);
		ArrayList<Double> yTransformList = averageArrOfArr(yTransformListTotal);
		
		
		
	
		
		
		while (processorIndex < currentStack.size()) {
			
			imageIndex++;
			processorIndex++;
			transIndex++;
			
			ImageProcessor ip = currentStack.getProcessor(processorIndex);
			ip.setInterpolationMethod(ImageProcessor.BICUBIC);
			// Only translate if 
			if(transIndex<xTransformList.size()) {
				ip.translate(-xTransformList.get(transIndex),-yTransformList.get(transIndex));
			}
			
			//adds newly 
			newImages[imageIndex] = new ImagePlus(""+imageIndex,ip);
		}
		
		ImageStack stack = (ImageStack)ImageStackModified.create(newImages);
		ImagePlus ip = new ImagePlus("DriftCorrected", stack);
		
		autoAdjust(ip,ip.getProcessor());
		ip.show();
		
		//check for frame 1
		//use point tool
		//ImagePlus img = new ImagePlus();
		//ImagePlus interpolated = Views.interpolate(img, new NLinearInterpolatorFactory()).realRandomAccess();
		
	}
	
	
	private static ArrayList<Double> averageArrOfArr(ArrayList<ArrayList<Double>> arr) {
		
		int largestLength = getLargestLength(arr);
		int nArrayLists = arr.size();
		ArrayList<Double> output = new ArrayList<>();
		
		for(int i = 0; i<largestLength; i++) {
			double sum = 0;
			int divisor = nArrayLists;
			for(int j = 0; j<nArrayLists; j++) {
				if(!(i>=arr.get(j).size())) {
					sum+=arr.get(j).get(i);
				}else {
					divisor--;
				}
			}
			output.add(sum/divisor);
		}
		return output;
	}
	
	private static int getLargestLength(ArrayList<ArrayList<Double>> arr) {
		int largestLength = -1;
		for(ArrayList<Double> a: arr) {
			if(a.size()>largestLength) {
				largestLength = a.size();
			}
		}
		return largestLength;
	}
	
	private ArrayList<Double> calcDistanceOfX(TreeSet< Spot > a){
		ArrayList<Double> output = new ArrayList<>();
		double cumSum = 0;
		Spot prevS = null;
		for(Spot s: a) {
			if(prevS == null) {
				prevS = s;
			}else {
				output.add(s.getFeature(Spot.POSITION_X)-prevS.getFeature(Spot.POSITION_X)+cumSum);
				cumSum+=s.getFeature(Spot.POSITION_X)-prevS.getFeature(Spot.POSITION_X);
				prevS = s;
			}
		}
		return output;
	}
	
	private ArrayList<Spot> getCloseSpots(Model trackmateModel, double xRoi, double yRoi){
		ArrayList<Spot> closeSpots = new ArrayList<>();
		
		final Set< Integer > trackIDs = trackmateModel.getTrackModel().trackIDs( true );
		for ( final Integer trackID : trackIDs )
		{
			final Set< Spot > track = trackmateModel.getTrackModel().trackSpots( trackID );
			// Sort them by time
			final TreeSet< Spot > sortedTrack = new TreeSet<>( Spot.timeComparator );
			sortedTrack.addAll( track );
			
			
			
			for ( final Spot spot : sortedTrack )
			{
				//Filters out points that are not close
				double xSpot = spot.getFeature(Spot.POSITION_X);
				double ySpot = spot.getFeature(Spot.POSITION_Y);
				if(Math.sqrt((xRoi-xSpot)*(xRoi-xSpot)+(yRoi-ySpot)*(yRoi-ySpot))<10) {
					
					closeSpots.add(spot);
				}
			}
		}
		return closeSpots;
		
	}
	private ArrayList<Double> calcDistanceOfY(TreeSet< Spot > a){
		ArrayList<Double> output = new ArrayList<>();
		double cumSum = 0;
		Spot prevS = null;
		for(Spot s: a) {
			if(prevS == null) {
				prevS = s;
			}else {
				output.add(s.getFeature(Spot.POSITION_Y)-prevS.getFeature(Spot.POSITION_Y)+cumSum);
				cumSum+=s.getFeature(Spot.POSITION_Y)-prevS.getFeature(Spot.POSITION_Y);
				prevS = s;
			}
		}
		return output;
	}
	
	private void autoAdjust(ImagePlus imp, ImageProcessor ip) {
		
		ImageStatistics stats = imp.getRawStatistics();
		int limit = stats.pixelCount/10;
		int[] histogram = stats.histogram;
		double min;
		double max;
		int autoThreshold = 2500;
		int threshold = stats.pixelCount/autoThreshold;
		int i = -1;
		boolean found = false;
		int count;
		do {
			i++;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count> threshold;
		} while (!found && i<255);
		int hmin = i;
		i = 256;
		do {
			i--;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count > threshold;
		} while (!found && i>0);
		int hmax = i;
		
		if (hmax>=hmin) {
			
			min = stats.histMin+hmin*stats.binSize;
			max = stats.histMin+hmax*stats.binSize;
			if (min==max)
				{min=stats.min; max=stats.max;}
			imp.setDisplayRange(min, max);
			
	}
	
	}

	@Override
	public void setLogger( final Logger logger )
	{
		this.logger = logger;
	}
	
}