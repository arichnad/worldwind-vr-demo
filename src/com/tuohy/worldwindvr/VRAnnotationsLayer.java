package com.tuohy.worldwindvr;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AbstractBrowserBalloon;
import gov.nasa.worldwind.render.BalloonAttributes;
import gov.nasa.worldwind.render.BasicBalloonAttributes;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.ExtrudedPolygon;
import gov.nasa.worldwind.render.GlobeBrowserBalloon;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.Size;
import gov.nasa.worldwind.render.airspaces.Airspace;
import gov.nasa.worldwind.render.airspaces.Polygon;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWIO;
import gov.nasa.worldwindx.examples.util.ExampleUtil;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class VRAnnotationsLayer extends RenderableLayer {

	protected static final String BROWSER_BALLOON_CONTENT_PATH = "gov/nasa/worldwindx/examples/data/BrowserBalloonExample.html";

    protected static final String DEMO_AIRSPACES_PATH
        = "gov/nasa/worldwindx/examples/data/AirspaceBuilder-DemoShapes.zip";
    protected static String DEFAULT_IMAGE_URL = "gov/nasa/worldwindx/examples/images/build123sm.jpg";


	Font messageFont = new Font("Helvetica",Font.PLAIN,28);
	OutlinedTextAnnotation messageAnnotation;

	public VRAnnotationsLayer(){
		if(WorldWindVRConstants.RenderHorizontalResolution<1920){
			messageFont = new Font("Helvetica",Font.PLAIN,16);
		}
		
		this.showMessageImmediately("Welcome to the WorldWindVR Demo!", 7);
		this.queueMessage("Use W,A,S,D to Navigate");
		this.queueMessage("Use Shift Key to Change Navigation Speed");
		this.queueMessage("Use Space Bar to Change Locations");
		this.queueMessage("Note: on first visit to any location...");
		this.queueMessage("...it will take time to cache imagery.");
		this.queueMessage("Press Escape to Exit");


		createExtrudedShapes();     // lww - for extruded shapes
		makeBrowserBalloon();       // lww - for placemark & balloon
	}

	public void prepareForEye(boolean left){
		float anchorPointX = WorldWindVRConstants.RenderHorizontalResolution/4.0f;
		float anchorPointY = WorldWindVRConstants.RenderVerticalResolution/2.0f;
		float messageWidth = messageAnnotation.getGraphicsContext().getFontMetrics().stringWidth(messageAnnotation.getOutlinedText());
		float messageHeight = messageAnnotation.getGraphicsContext().getFontMetrics().getHeight();
		anchorPointX -= (messageWidth/2.0f);
		anchorPointY += messageHeight/2.0f;

		//not sure why we need this
		anchorPointX += 100;
//		anchorPointY += 110 ;

		int pixelSeparation = 20;
		if(left){
			messageAnnotation.setScreenPoint(new Point((int) (anchorPointX+pixelSeparation),(int) anchorPointY));
		}
		else{
			messageAnnotation.setScreenPoint(new Point((int) (anchorPointX-pixelSeparation),(int) anchorPointY));
		}
	}

	Timer timer;
	List<String> messageQueue = Collections.synchronizedList(new ArrayList<String>());

	public void showMessageImmediately(String messageText, int secondsToDisplay){
		messageQueue.clear();
		showMessage(messageText, secondsToDisplay);
	}


	public void queueMessage(String messageText){
		if(timer==null){
			this.showMessage(messageText, 4);
		}
		else{
			messageQueue.add(messageText);
		}
	}

	private void showMessage(String messageText, int secondsToDisplay) {
		if(messageAnnotation!=null){
			this.removeRenderable(messageAnnotation);
		}
		messageAnnotation = new OutlinedTextAnnotation(messageText, messageFont);
		messageAnnotation.setValue(AVKey.VIEW_OPERATION, AVKey.VIEW_PAN);
		messageAnnotation.getAttributes().setSize(new Dimension(500,500));
		this.addRenderable(messageAnnotation);

		//1- Taking an instance of Timer class.
		if(timer!=null){
			timer.cancel();
		}
		timer = new Timer("FadeOut");

		//2- Taking an instance of class contains your repeated method.
		FadeInOutMessageTask t = new FadeInOutMessageTask(secondsToDisplay);
		timer.schedule(t, 0, 100);
	}

	class FadeInOutMessageTask extends TimerTask {
		private int times = 0;
		private int totalTimes = 40;
		private double fadeInIntervals = 10;

		public FadeInOutMessageTask(int seconds){
			totalTimes = seconds*10;
			
			//if seconds is less than 0, we show the message until it is dismissed
			if(seconds<0){
				totalTimes = Integer.MAX_VALUE;
			}
		}

		public void run() {
			times++;
			if (times <= totalTimes) {
				if(times<fadeInIntervals){
					messageAnnotation.getAttributes().setOpacity(times/fadeInIntervals);
				}
				else if(times>(totalTimes-fadeInIntervals)){
					messageAnnotation.getAttributes().setOpacity((totalTimes-times)/fadeInIntervals);
				}
			} else {
				messageAnnotation.getAttributes().setOpacity(0.0);
				//Stop Timer.
				this.cancel();
				if(!messageQueue.isEmpty()){
					showMessage(messageQueue.remove(0),4);
				}
				else{
					timer = null;
				}
			}
		}
	}
	
	
    protected void makeBrowserBalloon()
    {
        String htmlString = null;
        InputStream contentStream = null;

        try
        {
            // Read the URL content into a String using the default encoding (UTF-8).
            contentStream = WWIO.openFileOrResourceStream(BROWSER_BALLOON_CONTENT_PATH, this.getClass());
            htmlString = WWIO.readStreamToString(contentStream, null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            WWIO.closeStream(contentStream, BROWSER_BALLOON_CONTENT_PATH);
        }

        if (htmlString == null)
            htmlString = Logging.getMessage("generic.ExceptionAttemptingToReadFile", BROWSER_BALLOON_CONTENT_PATH);

        Position balloonPosition = Position.fromDegrees(38.8826,-77.1059);

        // Create a Browser Balloon attached to the globe, and pointing at the NASA headquarters in Washington, D.C.
        // We use the balloon page's URL as its resource resolver to handle relative paths in the page content.
        AbstractBrowserBalloon balloon = new GlobeBrowserBalloon(htmlString, balloonPosition);
        // Size the balloon to provide enough space for its content.
        BalloonAttributes attrs = new BasicBalloonAttributes();
        attrs.setSize(new Size(Size.NATIVE_DIMENSION, 0d, null, Size.NATIVE_DIMENSION, 0d, null));
        balloon.setAttributes(attrs);

        // Create a placemark on the globe that the user can click to open the balloon.
        PointPlacemark placemark = new PointPlacemark(balloonPosition);
        placemark.setLabelText("Click to open balloon");
        // Associate the balloon with the placemark by setting AVKey.BALLOON. The BalloonController looks for this
        // value when an object is clicked.
        placemark.setValue(AVKey.BALLOON, balloon);

        this.addRenderable(balloon);
        this.addRenderable(placemark);
    }






	private void createExtrudedShapes() {
            try
            {
                this.setPickEnabled(true);

                // Retrieve the geometry from the World Wind demo site.
                List<Airspace> airspaces = new ArrayList<Airspace>();
                loadAirspacesFromPath(DEMO_AIRSPACES_PATH, airspaces);

                // Define attributes for the shapes.
                ShapeAttributes sideAttributes = new BasicShapeAttributes();
                sideAttributes.setInteriorMaterial(Material.LIGHT_GRAY);
                sideAttributes.setOutlineMaterial(Material.DARK_GRAY);

                ShapeAttributes capAttributes = new BasicShapeAttributes(sideAttributes);
                capAttributes.setInteriorMaterial(Material.GRAY);

                // Construct the extruded polygons from the demo data.
                int n = 0, m = 0;
                for (Airspace airspace : airspaces)
                {
                    if (airspace instanceof Polygon) // only polygons in the demo data are used
                    {
                        Polygon pgonAirspace = (Polygon) airspace;

                        // Collect the images to be applied to the shape's sides.
                        ArrayList<String> textures = new ArrayList<String>();
                        for (int i = 0; i < pgonAirspace.getLocations().size(); i++)
                        {
                            textures.add(DEFAULT_IMAGE_URL);
                        }

                        // Construct the extruded polygon. Use the default texture coordinates.
                        double height = 40; // building height
                        ExtrudedPolygon quad = new ExtrudedPolygon(pgonAirspace.getLocations(), height, textures);

                        // Apply the shape's attributes. Note the separate attributes for cap and sides.
                        quad.setSideAttributes(sideAttributes);
                        quad.setCapAttributes(capAttributes);

                        // Specify a cap for the extruded polygon, specifying its texture coordinates and image.
                        if (pgonAirspace.getLocations().size() == 4)
                        {
                            float[] texCoords = new float[] {0, 0, 1, 0, 1, 1, 0, 1};
                            quad.setCapImageSource("images/32x32-icon-nasa.png", texCoords, 4);
                        }

                        // Add the shape to the layer.
                        this.addRenderable(quad);

                        ++n;
                        m += ((Polygon) airspace).getLocations().size();
                    }
                }

                System.out.printf("NUM SHAPES = %d, NUM SIDES = %d\n", n, m);

                // This is how a select listener would notice that one of the shapes was picked.
                //getWwd().addSelectListener(new SelectListener()
                //{
                    //public void selected(SelectEvent event)
                    //{
                        //if (event.getTopObject() instanceof ExtrudedPolygon)
                            //System.out.println("EXTRUDED POLYGON");
                    //}
                //});
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
	}

    protected static void loadAirspacesFromPath(String path, Collection<Airspace> airspaces)
    {
        File file = ExampleUtil.saveResourceToTempFile(path, ".zip");
        if (file == null)
            return;

        try
        {
            ZipFile zipFile = new ZipFile(file);

            ZipEntry entry = null;
            for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements(); entry = e.nextElement())
            {
                if (entry == null)
                    continue;

                String name = WWIO.getFilename(entry.getName());

                if (!(name.startsWith("gov.nasa.worldwind.render.airspaces") && name.endsWith(".xml")))
                    continue;

                String[] tokens = name.split("-");

                try
                {
                    Class c = Class.forName(tokens[0]);
                    Airspace airspace = (Airspace) c.newInstance();
                    BufferedReader input = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
                    String s = input.readLine();
                    airspace.restoreState(s);
                    airspaces.add(airspace);

                    if (tokens.length >= 2)
                    {
                        airspace.setValue(AVKey.DISPLAY_NAME, tokens[1]);
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
