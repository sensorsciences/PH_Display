// v3.0 may'21
//
// calibration is now channels per unit, not units per channel
// scale and fit ignoring lowest and highest channel as an option
// add gaussian fitting of PHD curves
// 
// wish list: select .phd files only on startup
//

import ij.plugin.*;
import ij.measure.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.awt.*;
import java.lang.Math.*;

/** Uses the JFileChooser from Swing to open one or more images. */
public class PH_Display implements PlugIn {
	static File dir;

	public void run(String arg) {
		openFiles();
		IJ.register( PH_Display .class);
	}

	public void openFiles() {
		JFileChooser fc = null;
		try {fc = new JFileChooser();}
		catch (Throwable e) {IJ.error("This plugin requires Java 2 or Swing."); return;}
		fc.setMultiSelectionEnabled(true);
		fc.addChoosableFileFilter(new PHDfilter());
		
		if (dir==null) {
			String sdir = OpenDialog.getDefaultDirectory();
			if (sdir!=null)
				dir = new File(sdir);
		}
		if (dir!=null)
			fc.setCurrentDirectory(dir);
		int returnVal = fc.showOpenDialog(IJ.getInstance());
		if (returnVal!=JFileChooser.APPROVE_OPTION)
			return;
		File[] files = fc.getSelectedFiles();
		if (files.length==0) { // getSelectedFiles does not work on some JVMs
			files = new File[1];
			files[0] = fc.getSelectedFile();
		}
		String path = fc.getCurrentDirectory().getPath()+Prefs.getFileSeparator();
		dir = fc.getCurrentDirectory();
		
		int rows=256; //this is fixed so assume there are 256 rows in each PHD file
		int columns=files.length; // each 'column' is a separate PHD file
		
		
		double[][] PHDdata=new double[columns][rows]; // note that the words are swapped to agree with the .phd file convention
		String []filenames=new String[columns];
		String[] cblabel = new String[columns];
		boolean[] cbdef = new boolean[columns];
		int[] counts = new int[columns];
		int maxcounts=0;
		double[][] fitfit=new double[columns][rows];
		double[] scaledy=new double[rows];
		double scalefactor=1;
		
		for (int i=0; i<columns; i++) {
			filenames[i]=files[i].getName();
			PHDdata[i] = (double [])readPHDfiles(path, filenames[i]);
			counts[i]=0;
			for (int j=0; j<rows; j++) {
				counts[i]=counts[i]+(int)PHDdata[i][j];
			}
			if (counts[i]>maxcounts)
				maxcounts=counts[i];
			cblabel[i]=files[i].getName()+"; "+counts[i];
			cbdef[i]=true;
		}      
		
		double[] dummy={0,1};
		double PHymax=0;
		double PHxmax=(double)rows;
		double[] PHymaxin = new double[columns];
		double PHymaxinput=0;
		double PHxmaxinput=0;
		double xmax,ymax;
		double calin=0;
		double cal=1;
		String xlabel="channel";
		String calname="";
		double[] x=new double[rows];
		boolean autoscale=false;
		boolean names=true;
		boolean chzero=true;
		int minchannel=1;
		boolean fitsyn=true;
		double[] fitdata=new double[rows];
		int startchannel=0, endchannel=0;
		String[] fitlabel=new String[rows];
				
		// initialise x-axis
				
		while (true) {
			// find max value in array and numbers of counts
			// PHymaxin[i] is the maximum number of counts in a channel in PHD #i
			// PHymax is the maximum of all the PHymaxin's
			PHymax=0;
			for (int i=0; i<columns; i++) {
				if (cbdef[i]==false) {;} else { // ignore PHD that are not ticked
					PHymaxin[i]=0;
					if (chzero==false) {startchannel=0; endchannel=rows;} else {startchannel=minchannel; endchannel=rows-1;}
					for (int j=startchannel; j<endchannel; j++) { // ignore channel 0 of flag is set.  Still plot it though
						if (PHDdata[i][j]>PHymaxin[i])
							PHymaxin[i]=PHDdata[i][j];
					}
				if (PHymaxin[i]>PHymax)
					PHymax=PHymaxin[i];
				}
				
			}
			
			// plot frame first with no data, then adjust
			String plot_title="PHD plots from "+path;
			ImagePlus new_pw=WindowManager.getImage(plot_title);
			if (new_pw!=null) {
				new_pw.hide();
			}
			
			if (calname=="") {xlabel=xlabel;} else {xlabel=calname;}
			if (calin==0) {cal=1;} else {cal=calin;}
			
			for (int i=0; i<rows; i++) {
				x[i]=(double)i/cal;
			}
			
			if (PHymaxinput==0) {ymax=PHymax;} else {ymax=PHymaxinput;}
			if (PHxmaxinput==0) {xmax=PHxmax;} else {xmax=PHxmaxinput;}
			
			Plot pw = new Plot(plot_title, xlabel, "counts", dummy, dummy);
			pw.setLimits(0, xmax, 0, ymax);
			pw.setSize(600,400);
						
			// put all the data on the plot
			for (int i=0; i<columns; i++) {
		
			// do fits, if required
			if (fitsyn) {
				// exclude up to x[minchannel] and x[max] from fit if chzero is set
				for (int j=0; j<rows; j++) fitdata[j]=PHDdata[i][j];
				if (chzero==true) {
					for (int j=0; j<minchannel; j++) fitdata[j]=0;
					fitdata[rows-1]=0;
				}
				CurveFitter cf = new CurveFitter(x,fitdata);
				cf.doFit(CurveFitter.GAUSSIAN_NOOFFSET);
				double[] fitresult=cf.getParams();
				// make up curve for fit
				// [1] center, [2] sigma
				for (int j=0; j<rows; j++) {
					fitfit[i][j]=cf.f(fitresult, x[j]);
				}
				fitlabel[i]="; "+Round_off((fitresult[2]*2.355*100)/fitresult[1],3)+"%; "+Round_off(fitresult[1],3);
			}

				if (cbdef[i]==false) {;} else {
				if (i==0) pw.setColor(Color.black);
				if (i==1) pw.setColor(Color.blue);
				if (i==2) pw.setColor(Color.red);
				if (i==3) pw.setColor(Color.green);
				if (i==4) pw.setColor(Color.magenta);
				if (i==5) pw.setColor(Color.orange);
				if (i==6) pw.setColor(Color.pink);
				if (i==7) pw.setColor(Color.cyan);
				if (i==8) pw.setColor(Color.gray);
				if (i==9) pw.setColor(Color.yellow);
				if (i==10) pw.setColor(Color.darkGray);
				if (autoscale==true) {
					scalefactor=PHymaxin[i]/PHymax;
					for (int j=0; j<rows; j++) {
						scaledy[j]=PHDdata[i][j]/scalefactor;
					}
					pw.addPoints(x,scaledy,1); // type 1 is an 'X'; type 2 is a line
					pw.addPoints(x,scaledy,2);
				} else {pw.addPoints(x,PHDdata[i],1);
					pw.addPoints(x,PHDdata[i],2);}
				if (fitsyn&&!autoscale) 
					pw.addPoints(x,fitfit[i],6);
				if (names==true) {
					pw.setFontSize(10);
					pw.addLabel(0.60,0.07+i*0.04,cblabel[i]+fitlabel[i]);
					}
				}
			}
				
			// display the plot
			pw.show();
			
			GenericDialog gd = new GenericDialog("PHD displayer");
			gd.addMessage("PHD displayer v3.0\nby Adrian Martin, 18 May 2021\n\nSend bugs, suggestions and cash to:\nadrian@sensorsciences.com");
			gd.addNumericField("Calibration (channel/units, 0=raw)", cal,0);
			gd.addStringField("X-axis unit", xlabel);
			gd.addNumericField("X max (0=auto)",PHxmaxinput,0);
			gd.addNumericField("Y max (0=auto)",PHymaxinput,0);
			gd.addCheckbox("Autoscale each PH independently, \n scaled to first PHD", autoscale);
			gd.addCheckbox("Put filenames and counts on graph", names);
			gd.addCheckbox("Ignore min & max channel when scaling", chzero);
			gd.addNumericField("Min channel to use for fitting and scaling", minchannel);
			gd.addCheckbox("Fit Gaussians to PHDs", fitsyn);
			gd.addCheckboxGroup(columns, 1, cblabel, cbdef);
			gd.showDialog();
			calin=gd.getNextNumber();
			calname=gd.getNextString();
			PHxmaxinput=gd.getNextNumber();
			PHymaxinput=gd.getNextNumber();
			autoscale=gd.getNextBoolean();
			names=gd.getNextBoolean();
			chzero=gd.getNextBoolean();
			minchannel=(int)gd.getNextNumber();
			fitsyn=gd.getNextBoolean();
			for (int i=0; i<columns; i++) {
				cbdef[i]=gd.getNextBoolean();
			}
			
			if (gd.wasCanceled()) {
				return;
			}
			
		}
	}
	
	public double[] readPHDfiles(String path, String filename) {
		String line;
		double[] result = new double[256];
	
		try {
			FileInputStream fis = new FileInputStream(path+filename);
			DataInputStream dis = new DataInputStream(fis);
			BufferedReader br = new BufferedReader(new InputStreamReader(dis));
			String[] st;
			
			for (int i=0; i<256; i++) {
				line=br.readLine();
				st=line.split("	");
				result[i]=Integer.valueOf(st[1]).intValue();
				System.out.println(line);
			}
			
			dis.close();
			
		} catch (Exception e) { 
			System.err.println("Error: " + e.getMessage());
		}
		
		
		return result;
	}
	
	static double Round_off(double N, double n)
   	{
  	      	int h;
        	double l, a, b, c, d, e, i, j, m, f, g;
	      	b = N;
 	       	c = Math.floor(N);
 
 	    	// Counting the no. of digits to the left of decimal point
 	       	// in the given no.
  	      	for (i = 0; b >= 1; ++i)
  	          	b = b / 10;
 
  	      	d = n - i;
 	       	b = N;
 	       	b = b * Math.pow(10, d);
   	     	e = b + 0.5;
 	       	if ((float)e == (float)Math.ceil(b)) {
   	        		f = (Math.ceil(b));
   	         		h = (int)(f - 2);
     	       		if (h % 2 != 0) {
                		e = e - 1;
      	      		}
    	    	}
     	   	j = Math.floor(e);
       	 	m = Math.pow(10, d);
       	 	j = j / m;
        	return(j);
    	}

	public class PHDfilter extends javax.swing.filechooser.FileFilter {
		public boolean accept(File f) {
			if (f.isDirectory()) {
				return true;
			}

		String extension = getExtension(f);
		if (extension != null) {
		    if (extension.equals("phd") || extension.equals("ph")) {
			    return true;
		    } else {
			return false;
		    }
		}
	
		return false;
	}
	
	    //The description of this filter
	    public String getDescription() {
		return "Just PHDs";
	    }
	}
	
public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 &&  i < s.length() - 1) {
            ext = s.substring(i+1).toLowerCase();
        }
        return ext;
    }
}
