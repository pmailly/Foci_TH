/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Tools;

/**
 *
 * @author phm
 */



import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.GaussianBlur3D;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageFloat;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import mcib3d.image3d.distanceMap3d.EDT;
import mcib3d.image3d.processing.FastFilters3D;
import mcib3d.image3d.regionGrowing.Watershed3D;
import mcib3d.utils.ThreadUtil;
import mpicbg.ij.integral.RemoveOutliers;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.apache.commons.io.FilenameUtils;



public class Foci_tools {  
    
    
    private static double maxDistDot = 1;  // min distance nucleus border to dots
    private static double maxDistPt = 5;  // min distance nucleus border to nucleus markers
    private static double minVolDAPI = 20;    // max volume for nucleus in C0
    private static double maxVolDAPI = 1000;   // max volume for nucleus in C0
    public static double minVolFoci = 0.01;    // max volume for foci in C2 (microns^3)
    public static double maxVolFoci = 5;    // max volume for foci in C2 (microns^3)
    public static double DOGmin = 4;
    public static double DOGmax = 8;
    public static String thMet = "MaxEntropy";
    private static String [] methods = new AutoThresholder().getMethods();
    public static Boolean touch = false;
    
    public static CLIJ2 clij2 = CLIJ2.getInstance();
    
    /**
     * Find images with extension in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public static ArrayList findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    /**
     * Find channels name
     * @param imageName
     * @param imageExt
     * @return 
     */
    public static List<String> findChannels (String imageName) throws DependencyException, ServiceException, FormatException, IOException {
        List<String> channels = new ArrayList<>();
        // create OME-XML metadata store of the latest schema version
        ServiceFactory factory;
        factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        ImageProcessorReader reader = new ImageProcessorReader();
        reader.setMetadataStore(meta);
        reader.setId(imageName);
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                String channelsID = meta.getImageName(0);
                channels = Arrays.asList(channelsID.replace("_", "-").split("/"));
                break;
            case "lif" : case "czi" :
                String[] ch = new String[chs];
                if (chs > 1) {
                    for (int n = 0; n < chs; n++) 
                        if (meta.getChannelExcitationWavelength(0, n) == null)
                            channels.add(Integer.toString(n));
                        else 
                            channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                }
                break;
            default :
                chs = reader.getSizeC();
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));
        }
        return(channels);         
    }
    
    /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public static Calibration findImageCalib(IMetadata meta) {
        Calibration cal = new Calibration();  
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
        return(cal);
    }
    
    /*
Dialog parameters
*/
public static boolean dialogBox() {
    boolean ok = true;
    GenericDialog gd = new GenericDialog("Parameters");
    gd.centerDialog(true);
    gd.addMessage("Foci :");
    gd.centerDialog(false);
    gd.addMessage("Differences of gaussian parameters :");
    gd.addNumericField("DOG min : ", DOGmin, 2);
    gd.addNumericField("DOG max : ", DOGmax, 2);
    gd.addNumericField("Min Foci volume (µm) : ", minVolFoci, 2);
    gd.addNumericField("Max Foci Volume (µm) : ", maxVolFoci, 2);
    gd.addChoice("Threshold method :", methods, thMet);
    gd.centerDialog(true);
    gd.addMessage("Nucleus :");
    gd.centerDialog(false);
    gd.addNumericField("Min TH nucleus volume (µm) : ", minVolDAPI, 2);
    gd.addNumericField("Max TH nucleus Volume (µm) : ", maxVolDAPI, 2);
    gd.addNumericField("Max distance nucleus border to dot : ", maxDistDot, 2); 
    gd.addNumericField("Max distance nucleus border to point : ", maxDistPt, 2);
    gd.addCheckbox("Remove objects touching border :", touch);
    gd.showDialog();
    if (gd.wasCanceled()) ok = false;
    DOGmin = gd.getNextNumber();
    DOGmax = gd.getNextNumber();
    minVolFoci = gd.getNextNumber();
    maxVolFoci = gd.getNextNumber();
    thMet = gd.getNextChoice();
    minVolDAPI = gd.getNextNumber();
    maxVolDAPI = gd.getNextNumber();
    maxDistDot = gd.getNextNumber();
    maxDistPt = gd.getNextNumber();
    return(ok);
}

    
    /** 
     * Find dots in channels
     * @param imgCh channel
     * @param sig1 DOG sigma1
     * @param sig2 DOG sigma2
     * @param th   Threshold
     * @return dots population
     */
    public static Objects3DPopulation findDots(ImagePlus imgCh, int sig1, int sig2, String th, String name) {
        ClearCLBuffer imgCL = clij2.push(imgCh);
        ClearCLBuffer imgCLDOG = DOG(imgCL, sig1, sig1, sig2, sig2);
        ClearCLBuffer imgCLBin = threshold(imgCLDOG, thMet, false);
        ImagePlus img = clij2.pull(imgCLBin);
        img.setCalibration(imgCh.getCalibration());
        Objects3DPopulation objPop = getPopFromImage(img);
        FileSaver fociMaskFile = new FileSaver(img);
        fociMaskFile.saveAsTiff(name);
        clij2.release(imgCLBin);
        return(objPop);
    } 
    
    
/**
     * Nucleus segmentation 2
     * @param imgNuc
     * @param path
     * @return 
     */
    public static Objects3DPopulation findnucleus2(ImagePlus imgNuc, String path, Boolean touch) {
        removeOutliers(imgNuc, 20, 20, 1);
        ImageStack stack = new ImageStack(imgNuc.getWidth(), imgNuc.getHeight());
        for (int i = 1; i <= imgNuc.getStackSize(); i++) {
            IJ.showStatus("Finding nucleus section "+i+" / "+imgNuc.getStackSize());
            imgNuc.setZ(i);
            imgNuc.updateAndDraw();
            IJ.run(imgNuc, "Nuclei Outline", "blur=10 blur2=30 threshold_method=Li outlier_radius=0 outlier_threshold=0 max_nucleus_size=500 "
                    + "min_nucleus_size=10 erosion=0 expansion_inner=0 expansion=0 results_overlay");
            imgNuc.setZ(1);
            imgNuc.updateAndDraw();
            ImagePlus mask = new ImagePlus("mask", imgNuc.createRoiMask().getBufferedImage());
            ImageProcessor ip =  mask.getProcessor();
            ip.invertLut();
            stack.addSlice(ip);
        }
        ImagePlus imgStack = new ImagePlus("Nucleus", stack);
        imgStack.setCalibration(imgNuc.getCalibration());
        Objects3DPopulation nucPop = new Objects3DPopulation(getPopFromImage(imgStack).getObjectsWithinVolume(minVolDAPI, maxVolDAPI, true));
        if (touch) 
            nucPop.removeObjectsTouchingBorders(imgStack, false);
        closeImages(imgStack);
        return(nucPop);
    }
    
    /**
     * Find foci in nucleus
     * @param fociPop
     * @param nucleusPop
     * @return 
     */

    public static Objects3DPopulation findFociInNucleus(Objects3DPopulation fociPop, Objects3DPopulation fociDapiPop, Objects3DPopulation nucleusPop, ArrayList<Nucleus> nucleus, ImagePlus img) {
        IJ.showStatus("Finding foci in nucleus ...");
        Objects3DPopulation fociInNuc  = new Objects3DPopulation(); 
        ImageHandler imgHFoci = ImageHandler.wrap(img);
        
        for (int i = 0; i < nucleusPop.getNbObjects(); i++) {
            IJ.showStatus("Finding foci in nucleus "+i+"/"+nucleusPop.getNbObjects());
            int fociNb = 0, fociDapiNb = 0;
            double fociInt = 0;
            double fociVol = 0, fociDapiVol = 0;
            boolean hasFoci = false;
            Object3D nucObj = nucleusPop.getObject(i);
            // find foci in nucleus
            for (int j = 0; j < fociPop.getNbObjects(); j++) {
                boolean findFoci = false;
                Object3D fociObj = fociPop.getObject(j);
                // find if dot is inside nucleus or dist <= distMin
                if (fociObj.hasOneVoxelColoc(nucObj))
                    findFoci = true;
                else {
                    double dist = nucObj.distBorderUnit(fociObj);
                    if (dist <= maxDistDot) 
                        findFoci = true;
                }    
                if (findFoci) {
                    fociInNuc.addObject(fociObj);
                    fociNb++; 
                    fociInt += fociObj.getIntegratedDensity(imgHFoci);
                    fociVol += fociObj.getVolumeUnit();
                    // draw foci with zero for diffuse
                    fociObj.draw(imgHFoci, 0);
                    fociPop.removeObject(fociObj);
                    hasFoci = true;
                }   
            }
            Nucleus nuc = new Nucleus(i,nucObj.getVolumeUnit(),0,0,fociNb,fociVol,fociInt,nucObj.getIntegratedDensity(imgHFoci),hasFoci);
            nucleus.add(nuc);
            
            // Find foci Dapi in nucleus
            for (int k = 0; k < fociDapiPop.getNbObjects(); k++) {
                IJ.showStatus("Finding foci Dapi in nucleus "+i+"/"+nucleusPop.getNbObjects());
                Object3D fociObj = fociDapiPop.getObject(k);
                boolean findFoci = false;
                if (fociObj.hasOneVoxelColoc(nucObj))
                    findFoci = true;
                else {
                    double dist = nucObj.distBorderUnit(fociObj);
                    if (dist <= maxDistDot) 
                        findFoci = true;
                } 
                if (findFoci) {
                    fociDapiNb++; 
                    fociDapiVol += fociObj.getVolumeUnit();
                    fociDapiPop.removeObject(fociObj);
                }
            }
            nucleus.get(i).setFociDapiNb(fociDapiNb);
            nucleus.get(i).setFociDapiVol(fociDapiVol);
        }
        imgHFoci.closeImagePlus();
        return(fociInNuc);
    }

    /**
    * Find TH nucleus
    * Define th nucleus if pt in nucleus object or dist to border <= maxDistPt
     * @param nucleusPop
     * @param ptList
     * @return
     */
  
    public static Objects3DPopulation thNucleus (Objects3DPopulation nucleusPop, ArrayList<Point3D> ptList) {       
        Objects3DPopulation thCellPop = new Objects3DPopulation();
        for (int i = 0; i < ptList.size(); i++) {
            Point3D pt = ptList.get(i);
            for (int n = 0; n < nucleusPop.getNbObjects(); n++) {
                Object3D objNuc = nucleusPop.getObject(n);
                if (objNuc.inside(pt)) {
                    thCellPop.addObject(objNuc);
                    break;
                }
                else if (objNuc.distPixelBorderUnit(pt.x, pt.y, pt.z) <= maxDistPt) {
                    thCellPop.addObject(objNuc);                    
                    break;
                }
            }
        }
        return thCellPop;
    }
    
    
    /**
     * write object labels
     * @param obj
     * @param img
     * @param number
     * @param color
     * @param size 
     */
    public static void labelsObject (Object3D obj, ImagePlus img, int number, int color, int size) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, size);
        int[] box = obj.getBoundingBox();
        int z = (int)obj.getCenterZ();
        int x = box[0] - 2;
        int y = box[2] - 2;
        img.setSlice(z+1);
        ImageProcessor ip = img.getProcessor();
        ip.setFont(tagFont);
        ip.setColor(color);
        ip.drawString(Integer.toString(number+1), x, y);
        img.updateAndDraw();    
    }
    
    public static Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
    
    public static void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    
    
    /**
     * 
     * @param xmlFile
     * @param cal
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException 
     */
    public static ArrayList<Point3D> readXML(String xmlFile, Calibration cal) throws ParserConfigurationException, SAXException, IOException {
        ArrayList<Point3D> ptList = new ArrayList<>();
        double x = 0, y = 0 ,z = 0;
        File fXmlFile = new File(xmlFile);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();
        NodeList nList = doc.getElementsByTagName("Marker");
        for (int n = 0; n < nList.getLength(); n++) {
            Node nNode = nList.item(n);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                x = Double.parseDouble(eElement.getElementsByTagName("MarkerX").item(0).getTextContent());
                y = Double.parseDouble(eElement.getElementsByTagName("MarkerY").item(0).getTextContent());
                z = Double.parseDouble(eElement.getElementsByTagName("MarkerZ").item(0).getTextContent());
            }
            Point3D pt = new Point3D(x, y, z);
            ptList.add(pt);
        }
        return(ptList);
    }
    
     /**
    * 3D watershed
    * @param imgBinmask
    * @param rad
    * @return distance map image
    */
    public static ImagePlus watershedSplit(ImagePlus imgBinmask, int rad) {
        int nbCpus = ThreadUtil.getNbCpus();
        float resXY = 1;
        float resZ = 1;
        float radXY = rad;
        float radZ = rad;
        Calibration cal = imgBinmask.getCalibration();
        if (cal != null) {
            resXY = (float) cal.pixelWidth;
            resZ = (float) cal.pixelDepth;
            radZ = radXY * (resXY / resZ);
        }
        IJ.showStatus("Computing EDT");
        ImageInt imgMask = ImageInt.wrap(imgBinmask);
        ImageFloat edt = EDT.run(imgMask, 1, resXY, resZ, false, nbCpus);
        ImageHandler edt16 = edt.convertToShort(true);
        ImagePlus edt16Plus = edt16.getImagePlus();
        GaussianBlur3D.blur(edt16Plus, 6.0, 6.0, 6.0);
        edt16 = ImageInt.wrap(edt16Plus);
        edt16.intersectMask(imgMask);
        // seeds
        ImageHandler seedsImg;
        seedsImg = FastFilters3D.filterImage(edt16, FastFilters3D.MAXLOCAL, radXY, radXY, radZ, 0, false);
        IJ.showStatus("Computing watershed");
        Watershed3D water = new Watershed3D(edt16, seedsImg, 10, 1);
        ImagePlus imp = water.getWatershedImage3D().getImagePlus();
        WindowManager.getWindow("Log").dispose();
        IJ.setThreshold(imp, 1, 65535);
        Prefs.blackBackground = false;
        IJ.run(imp, "Convert to Mask", "method=Default background=Dark");
        closeImages(imgMask.getImagePlus());
        closeImages(edt.getImagePlus());
        closeImages(edt16.getImagePlus());
        closeImages(edt16Plus);
        closeImages(seedsImg.getImagePlus());
        return(imp);
    }
    
    
    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public static ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param sizeXY1
     * @param sizeXY2
     * @param sizeZ1
     * @param sizeZ2
     * @return imgCLDOG
     */ 
    public static ClearCLBuffer DOG(ClearCLBuffer imgCL, double sizeXY1, double sizeZ1, double sizeXY2, double sizeZ2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, sizeXY1, sizeXY1, sizeZ1, sizeXY2, sizeXY2, sizeZ2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public static ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed, boolean fill) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        if (fill)
            fillHole(imgCLBin);
        clij2.release(imgCL);
        return(imgCLBin);
    }
    
    /**
     * Fill hole
     * USING CLIJ2
     * @param imgCL
     */
    private static void fillHole(ClearCLBuffer imgCL) {
        long[] dims = clij2.getDimensions(imgCL);
        ClearCLBuffer slice = clij2.create(dims[0], dims[1]);
        ClearCLBuffer slice_filled = clij2.create(slice);
        for (int z = 0; z < dims[2]; z++) {
            clij2.copySlice(imgCL, slice, z);
            clij2.binaryFillHoles(slice, slice_filled);
            clij2.copySlice(slice_filled, imgCL, z);
        }
        clij2.release(slice);
        clij2.release(slice_filled);
    }
    
    /**
     * Remove Outliers
     * 
     * @param img
     * @param radX
     * @param radY
     * @param factor
     * @return img
     */
    public static ImagePlus removeOutliers(ImagePlus img, int radX, int radY, float factor) {
        
        for (int i = 0; i < img.getNSlices(); i++) {
            img.setSlice(i);
            ImageProcessor ip = img.getProcessor();
            RemoveOutliers removeOut = new RemoveOutliers(ip.convertToFloatProcessor());
            removeOut.removeOutliers(radX, radY, factor);
        }
        return(img);
    }
    
    /**
     * check if CLIJ is installed
     * @return 
     */
    public static boolean checkCLIJInstall() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }

        return true;
    }
    
    /**
     *  check if mcib3D is installed
     * @return 
     */
    public static boolean checkMCIB3DInstall() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom");
        } catch (ClassNotFoundException e) {
            IJ.log("MCIB3D not installed, please install from update site");
            return false;
        }

        return true;
    }
    
     /**
     * return objects population in an binary image
     * Using CLIJ2
     * @param imgCL
     * @return pop
     */

    public static Objects3DPopulation getPopFromClearBuffer(ClearCLBuffer imgCL) {
        ClearCLBuffer output = clij2.create(imgCL);
        clij2.connectedComponentsLabelingBox(imgCL, output);
        clij2.release(imgCL);
        ImageHandler imh = ImageHandler.wrap(clij2.pull(output));
        Objects3DPopulation pop = new Objects3DPopulation(imh);
        clij2.release(output);
        return pop;
    } 
    
}


