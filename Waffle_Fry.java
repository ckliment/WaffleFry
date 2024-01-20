import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import java.util.*; 
import ij.process.ImageStatistics.*;
import ij.measure.*;
import ij.plugin.filter.*; 
import ij.gui.GenericDialog.*;  

//WaffleFry - a plugin which creates lines to measure chord lengths in lung histology. This plugin accepts thresholded images of lung
//slices and draws horizontal and vertical lines across. When the line hits an alveolar wall, it will record the length
//of this line and continue to draw a line at the next white area. The interval in which these lines are drawn can be
//manually set at the beginning 

public class Waffle_Fry implements PlugIn{
	public void run(String arg) {
		//getting window and image
		ImageWindow firstwin = WindowManager.getCurrentWindow();
		ImagePlus imp = IJ.getImage();
		ImageProcessor ip = imp.getProcessor(); 

		//just getting values for the window 
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		int channels = imp.getNChannels(); 
		int totalslice = frames*slices*channels;
		int initialheight = imp.getHeight();
		int initialwidth = imp.getWidth(); 
		ImageStack stack = imp.getStack(); 

		//get manual input for the interval
        int hori=10,vert=10, min=8, thres = 255; 
        GenericDialog gd = new GenericDialog("Line Interval");
        gd.addNumericField("Horizontal: ", hori, 0);
        gd.addNumericField("Vertical: ", vert, 0);
        gd.addNumericField("Min line length: ", min, 0); 
        gd.addNumericField("Threshold (what value is wall?): ", thres, 0); 
        gd.showDialog();
        if (gd.wasCanceled()) return;
        hori = (int)gd.getNextNumber();
        vert = (int)gd.getNextNumber();
        min = (int)gd.getNextNumber();
        thres = (int)gd.getNextNumber(); 
        
        
        //initialize RoiManager
        RoiManager RoiM = RoiManager.getInstance(); 
        if(RoiM == null){
        	RoiM = new RoiManager(); 
        }
        else{
        	RoiM.reset(); 
        }
           
        Vector<Integer> masterlength = new Vector();
        Vector<Integer> lengthlist;
        int horiiterations = initialheight/hori;
        int vertiterations = initialwidth/vert; 
        
        //master loop for running everything = note starting at i=1
       
       	for(int i=1; i<horiiterations; i++){
       		int ycord = i*hori;
       		lengthlist = drawHori(imp, ycord, min, thres, RoiM);
      		for(int j=0; j<lengthlist.size(); j++){
        		Integer item = lengthlist.get(j);
        		masterlength.add(item);
        	}
        	
       	}
       	
       	for(int k=1; k<vertiterations; k++){
       		int xcord = k*vert;
       		lengthlist = drawVert(imp, xcord, min, thres, RoiM);
      		for(int l=0; l<lengthlist.size(); l++){
        		Integer item = lengthlist.get(l);
        		masterlength.add(item);
        	}
        	
       	}
       	for(int l =0; l<masterlength.size();l++){
       		Integer item = masterlength.get(l);
       		IJ.log(Integer.toString(item));
       	}
       		
        //IJ.log(Integer.toString(vert));
	}
//function for drawing horizontal lines. Requires the y coordinate for the horizontal plane. Keeps
//track of the lengths of lines and returns this as an int vector 

Vector<Integer> drawHori(ImagePlus imp, int y, int min, int thres, RoiManager RoiM){
	ImageProcessor ip = imp.getProcessor();
	int height = imp.getHeight(); 
	int width = imp.getWidth();
	int x = 0; 
	//iterate through the horizontal plane. Input image is thresholded so values should be either 255 or 0 
	Vector<Integer> linelength = new Vector();  
	Vector<Vector> start = new Vector();
	Vector<Vector> stop = new Vector(); 
	Boolean hitwall = true; 
	Boolean waswhite = false; 
	Integer length = 0; 
	for(int i=0; i<width; i++){
		Integer pix = ip.getPixel(i, y);
		//IJ.log(Integer.toString(pix)); 
		
		if(i == width-1 && waswhite == true){
			//hits the edge of the image and if currently drawing a line, make a stop 
			Vector<Integer> stopcoord = new Vector();
			stopcoord.add(i);
			linelength.add(length); 
			stopcoord.add(y);
			stop.add(stopcoord);
			//IJ.log(Integer.toString(i)); 
		}
		if(i == width-1 && waswhite == false){
			//hits the edge of the image and if not currently drawing a line, skip
			break;  
			//IJ.log(Integer.toString(i)); 
		}
		if(pix == thres && waswhite == false){
			//if the current pixel and last pixel was a wall, do nothing
			//return;
			//IJ.log(Integer.toString(i)); 
		}
		if(pix == thres && waswhite == true){
			//IJ.log(Integer.toString(i)); 
			//if current pixel is a wall but last pixel was space, save the coordinate as a stop, save the length of the line
			Vector<Integer> stopcoord = new Vector();
			stopcoord.add(i);
			linelength.add(length); 
			stopcoord.add(y);
			stop.add(stopcoord); 
			waswhite = false;
			hitwall = true; 
			length = 0; 
			
		}
		if(pix == Math.abs(thres-255) && waswhite == false){
			//if current pixel is space but last pixel was a wall, save coordinate as a start
			//return; 
			//IJ.log(Integer.toString(i)); 
			waswhite = true; 
			Vector<Integer> startcoord = new Vector();
			startcoord.add(i);
			startcoord.add(y); 
			start.add(startcoord); 
			length = 1; 
		}
		if(pix == Math.abs(thres-255) && waswhite == true){
			//if current pixel and space but last pixel was a wall, keep going 
			length = length+1; 
		}
	}
	
	Vector<Integer> linelengthclean = new Vector(); 
	Vector<Vector> startclean = new Vector(); 
	Vector<Vector> stopclean = new Vector(); 
	/*
	IJ.log(Integer.toString(linelength.size())); 
	IJ.log(Integer.toString(start.size())); 
	IJ.log(Integer.toString(stop.size()));
	*/
	//clean up the list of line sizes so they are larger than the minimum size and hot touching edges 
	//keep the upper end of index using start because sometimes there are lines with a stop but no start
	for(int l=0; l<start.size(); l++){
		Boolean starttouch = false;
		Boolean stoptouch = false;

		Vector<Integer> startitem = start.get(l);
		Vector<Integer> stopitem = stop.get(l); 
		
		if(startitem.get(0) == 0){
			starttouch = true;
		}
		if(stopitem.get(0) == width-1){
			stoptouch = true; 
		}
		
		Integer item = linelength.get(l); 
		
		if(item >= min && starttouch == false && stoptouch == false){
			linelengthclean.add(item);
			startclean.add(startitem);
			stopclean.add(stopitem); 
		}
	}

	//IJ.log(Integer.toString(stopclean.size()));
	
	//take the start/stop coordinates and create line ROIs 
	for(int k=0; k<startclean.size(); k++){
		//get the start coordinates 
		Vector<Integer> onestart = startclean.get(k);
		Integer xstart = onestart.get(0);
		Integer ystart = onestart.get(1);
		//get the stop coordinates
		Vector<Integer> onestop = stopclean.get(k);
		Integer xstop = onestop.get(0);
		Integer ystop = onestop.get(1);
		Line oneline = new Line(xstart,ystart,xstop,ystop); 
		RoiM.addRoi(oneline); 
		
	}
	return linelengthclean; 
}

Vector<Integer> drawVert(ImagePlus imp, int x, int min, int thres, RoiManager RoiM){
	ImageProcessor ip = imp.getProcessor();
	int height = imp.getHeight(); 
	int width = imp.getWidth();
	int y = 0; 
	//iterate through the horizontal plane. Input image is thresholded so values should be either 255 or 0 
	Vector<Integer> linelength = new Vector();  
	Vector<Vector> start = new Vector();
	Vector<Vector> stop = new Vector(); 
	Boolean hitwall = true; 
	Boolean waswhite = false; 
	Integer length = 0; 
	for(int i=0; i<height; i++){
		Integer pix = ip.getPixel(x, i);
		//IJ.log(Integer.toString(pix)); 
		
		if(i == height-1 && waswhite == true){
			//hits the edge of the image and if currently drawing a line, make a stop 
			Vector<Integer> stopcoord = new Vector();
			stopcoord.add(x);
			linelength.add(length); 
			stopcoord.add(i);
			stop.add(stopcoord);
			
		}
		if(i == height-1 && waswhite == false){
			//hits the edge of the image and if not currently drawing a line, skip
			break; 
			//IJ.log(Integer.toString(i)); 
		}
		if(pix == thres && waswhite == false){
			//if the current pixel and last pixel was a wall, do nothing
			//return;
			//IJ.log(Integer.toString(i)); 
		}
		if(pix == thres && waswhite == true){
			//IJ.log(Integer.toString(i)); 
			//if current pixel is a wall but last pixel was space, save the coordinate as a stop, save the length of the line
			Vector<Integer> stopcoord = new Vector();
			stopcoord.add(x);
			linelength.add(length); 
			stopcoord.add(i);
			stop.add(stopcoord); 
			waswhite = false;
			hitwall = true; 
			length = 0; 
			
		}
		if(pix == Math.abs(thres-255) && waswhite == false){
			//if current pixel is space but last pixel was a wall, save coordinate as a start
			//return; 
			//IJ.log(Integer.toString(i)); 
			waswhite = true; 
			Vector<Integer> startcoord = new Vector();
			startcoord.add(x);
			startcoord.add(i); 
			start.add(startcoord); 
			length = 1; 
		}
		if(pix == Math.abs(thres-255) && waswhite == true){
			//if current pixel and space but last pixel was a wall, keep going 
			length = length+1; 
		}
	}
	
	Vector<Integer> linelengthclean = new Vector(); 
	Vector<Vector> startclean = new Vector(); 
	Vector<Vector> stopclean = new Vector(); 
	/*
	IJ.log(Integer.toString(linelength.size())); 
	IJ.log(Integer.toString(start.size())); 
	IJ.log(Integer.toString(stop.size()));
	*/ 
	//clean up the list of line sizes so they are larger than the minimum size and hot touching edges 
	//keep the upper end of index using start because sometimes there are lines with a stop but no start
	for(int l=0; l<start.size(); l++){
		Boolean starttouch = false;
		Boolean stoptouch = false;

		Vector<Integer> startitem = start.get(l);
		Vector<Integer> stopitem = stop.get(l); 
		
		if(startitem.get(1) == 0){
			starttouch = true;
		}
		if(stopitem.get(1) == height-1){
			stoptouch = true; 
		}
		
		Integer item = linelength.get(l); 
		
		if(item >= min && starttouch == false && stoptouch == false){
			linelengthclean.add(item);
			startclean.add(startitem);
			stopclean.add(stopitem); 
		}
	}

	//IJ.log(Integer.toString(stopclean.size()));
	
	//take the start/stop coordinates and create line ROIs 
	for(int k=0; k<startclean.size(); k++){
		//get the start coordinates 
		Vector<Integer> onestart = startclean.get(k);
		Integer xstart = onestart.get(0);
		Integer ystart = onestart.get(1);
		//get the stop coordinates
		Vector<Integer> onestop = stopclean.get(k);
		Integer xstop = onestop.get(0);
		Integer ystop = onestop.get(1);
		Line oneline = new Line(xstart,ystart,xstop,ystop); 
		RoiM.addRoi(oneline); 
		
	}
	return linelengthclean; 
}
}