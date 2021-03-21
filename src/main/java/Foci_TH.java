/*
 * Find foci MECP2 C2 in TH cells C1, C0 = DAPI
 * compute integrated intensity of nucleus in C2
 * Find foci in dapi C0 compare to foci in C2
 * Author Philippe Mailly
 */





import static Tools.Foci_tools.DOGmax;
import static Tools.Foci_tools.DOGmin;
import static Tools.Foci_tools.closeImages;
import static Tools.Foci_tools.dialogBox;
import static Tools.Foci_tools.findChannels;
import static Tools.Foci_tools.findDots;
import static Tools.Foci_tools.findFociInNucleus;
import static Tools.Foci_tools.findImageCalib;
import static Tools.Foci_tools.findImages;
import static Tools.Foci_tools.findnucleus2;
import static Tools.Foci_tools.labelsObject;
import static Tools.Foci_tools.maxVolFoci;
import static Tools.Foci_tools.minVolFoci;
import static Tools.Foci_tools.readXML;
import static Tools.Foci_tools.thMet;
import static Tools.Foci_tools.thNucleus;
import static Tools.Foci_tools.touch;
import Tools.Nucleus;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

/**
 *
 * @author phm
 */

public class Foci_TH implements PlugIn {
    
String imageDir, outDirResults;    
public static Calibration cal = new Calibration();

boolean spining = false;
// Default Z step
public String xmlFile = "";
private double minVolDapiFoci = 0.01;    // max volume for foci Dapi (pixels^3)
private double maxVolDapiFoci = 10;    // max volume for foci Dapi (pixels^3)


    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
    try {
        FileWriter fwAnalyze = null;
        imageDir = IJ.getDirectory("Choose Directory Containing images files...");
        if (imageDir == null) {
            return;
        }
        // Find images with nd extension
        ArrayList<String> imageFiles = findImages(imageDir, "nd");
        if (imageFiles == null) {
            IJ.showMessage("Error", "No images found with nd extension");
            return;
        }
        // create output folder
        outDirResults = imageDir + File.separator+ "Results"+ File.separator;
        File outDir = new File(outDirResults);
        if (!Files.exists(Paths.get(outDirResults))) {
            outDir.mkdir();
        }
        
        // write results headers
        fwAnalyze = new FileWriter(outDirResults + "results.xls",false);
        BufferedWriter outputAnalyze = new BufferedWriter(fwAnalyze);
        outputAnalyze.write("image Name\t#Nucleus\tFoci Dapi nb\tFoci Dapi Vol\tFoci nb\tFoci Vol\tFoci Int\tDiffuse Int\tTH cell\n");
        outputAnalyze.flush();
        
        
        // create OME-XML metadata store of the latest schema version
        ServiceFactory factory;
        factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        ImageProcessorReader reader = new ImageProcessorReader();
        reader.setMetadataStore(meta);
        reader.setId(imageFiles.get(0));

        // Find channel names
        List<String> channels = findChannels(imageFiles.get(0));

        // Find image calibration
        cal = findImageCalib(meta);
        
        dialogBox();
        int series = 0;
        for (String imageFile : imageFiles) {
            String rootName = FilenameUtils.getBaseName(imageFile);
            xmlFile = imageDir+rootName+".xml";
            if (!new File(xmlFile).exists()) {
               IJ.showStatus("No XML file found !") ;
               return;
            }
            reader.setId(imageFile);
               
            ImporterOptions options = new ImporterOptions();
            options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
            options.setId(imageFile);
            options.setSplitChannels(true);
            options.setCBegin(series, 0);
            options.setCEnd(series, 0);
            
             /**
             * Find selected nucleus in xml file
             * find th nucleus population
             */

            ArrayList<Point3D> ptList = readXML(xmlFile, cal);

            /*
            * Open Channel 0 (DAPI)
            */
            ImagePlus imgC0 = BF.openImagePlus(options)[0];
            
            /*
            * find DAPI foci population in channel 0
            */
            Objects3DPopulation fociDapiPop = new Objects3DPopulation(findDots(imgC0, 1, 4, "Moments", outDirResults+rootName+"_FociDapi.tif").getObjectsWithinVolume(minVolDapiFoci, maxVolDapiFoci, true));

            System.out.println("DAPI foci "+channels.get(0)+" = " + fociDapiPop.getNbObjects());


            /*
            * Find nucleus in C0
            */ 

            Objects3DPopulation nucleusPop = findnucleus2(imgC0, outDirResults+rootName, touch);
            System.out.println("Image : " +imgC0.getTitle());
            System.out.println("DAPI nucleus "+channels.get(0)+" = " + nucleusPop.getNbObjects());
            
            ArrayList<Nucleus> nucleus = new ArrayList<>();
            // find th nucleus population
            Objects3DPopulation ThCellPop = thNucleus(nucleusPop, ptList, nucleus);
            System.out.println("TH cell = " + ThCellPop.getNbObjects());

            closeImages(imgC0);

            /*
            * Open Channel 2 (Foci)
            */
            options.setCBegin(series, 2);
            options.setCEnd(series, 2);
            ImagePlus imgC2 = BF.openImagePlus(options)[0];
            
            /*
            * find foci population in channel 2
            */
 
            Objects3DPopulation fociPop = new Objects3DPopulation(findDots(imgC2, (int)DOGmin, (int)DOGmax, thMet, outDirResults+rootName+"_Foci.tif").getObjectsWithinVolume(minVolFoci, maxVolFoci, true));
            System.out.println("foci "+channels.get(2)+" = " + fociPop.getNbObjects());            

            /*
            * find foci population nucleus
            * compute parameters
            */
            
            int[] fociInNuc = findFociInNucleus(fociPop, fociDapiPop, nucleusPop, nucleus, imgC2);
            System.out.println("foci "+channels.get(2)+" in nucleus = " + fociInNuc[0]);
            System.out.println("foci Dapi"+channels.get(0)+" in nucleus = " + fociInNuc[1]);
            closeImages(imgC2);
            
            /*
            Write parameters
            */
            
            for (Nucleus nuc : nucleus) {
                outputAnalyze.write(rootName+"\t"+nuc.getIndex()+"\t"+nuc.getFociDapiNb()+"\t"+nuc.getFociDapiVol()+"\t"+nuc.getFociNb()+"\t"+nuc.getFociVol()+"\t"+
                        nuc.getFociInt()+"\t"+nuc.getDiffuseInt()+"\t"+nuc.getTh()+"\n");
                outputAnalyze.flush();
            }
            
            
            /*
            * Save images object
            * 
            */
            options.setCBegin(series, 1);
            options.setCEnd(series, 1);
            ImagePlus imgC1 = BF.openImagePlus(options)[0];

            /*
            * create image objects population
            */
            ImageHandler imgObjNuc = ImageInt.wrap(imgC1).createSameDimensions();
            ImageHandler imgObjThNuc = imgObjNuc.duplicate();
            ImageHandler imgObjFociDapi = imgObjNuc.duplicate();
            ImageHandler imgObjFociTh = imgObjNuc.duplicate();

            /*
            * Nucleus blue
            */

            if (nucleusPop.getNbObjects() > 0) {
                for (int i = 0; i < nucleusPop.getNbObjects(); i++) {
                    Object3D obj = nucleusPop.getObject(i);
                    obj.draw(imgObjNuc, 255);
                    labelsObject(obj, imgObjNuc.getImagePlus(), i, 255, 48);
                }
            }

            /*
            * TH cells green
            */

            if (ThCellPop.getNbObjects() > 0)
                ThCellPop.draw(imgObjThNuc, 255);

            /*
            * Foci Th population in red
            */

            if (fociPop.getNbObjects() > 0)
                fociPop.draw(imgObjFociTh, 255);  

            /*
            * Foci Dapi nucleus yellow
            */

            if (fociDapiPop.getNbObjects() > 0)
                fociDapiPop.draw(imgObjFociDapi, 255);

            /*
            * Create Image composite
            */
            ImagePlus[] img = {imgObjFociTh.getImagePlus(), imgObjThNuc.getImagePlus(), imgObjNuc.getImagePlus(), imgC1, null, null, imgObjFociDapi.getImagePlus()};
            ImagePlus imgComposite = RGBStackMerge.mergeChannels(img, false);
            imgComposite.setCalibration(cal);
            
            /*
            * save image
            */

            FileSaver ObjectsFile = new FileSaver(imgComposite);
            ObjectsFile.saveAsTiffStack(outDirResults +File.separator+ rootName + "_Objects.tif");

            /*
            * close images
            */

            imgComposite.close();
            closeImages(imgC1);
        }
        outputAnalyze.close();
        } catch (DependencyException | FormatException | IOException | ServiceException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(Foci_TH.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done ...");
    }

}
