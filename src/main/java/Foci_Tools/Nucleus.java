/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Foci_Tools;

/**
 *
 * @author phm
 */
public class Nucleus {
    // index
    private int index;
    private double nucVol;
    private int fociDapiNb;
    private double fociDapiVol;
    private int fociNb;
    private double fociVol;
    private double fociInt;
    private double diffuseInt;
    private boolean th;

   
	
	public Nucleus(int index, double nucVol, int fociDapiNb, double fociDapiVol, int fociNb, double fociVol, double fociInt, double diffuseInt, boolean th) {
            this.index = index;
            this.nucVol = nucVol;
            this.fociDapiNb = fociDapiNb; 
            this.fociDapiVol = fociDapiVol;
            this.fociNb = fociNb;
            this.fociVol = fociVol;
            this.fociInt = fociInt;
            this.diffuseInt = diffuseInt;
            this.th = th;
	}
        
        public void setIndex(int index) {
            this.index = index;
	}
        
        public void setNucVol(double nucVol) {
            this.nucVol = nucVol;
	}
        
        public void setFociDapiNb(int fociDapiNb) {
            this.fociDapiNb = fociDapiNb;
	}
        
        public void setFociDapiVol(double fociDapiVol) {
            this.fociDapiVol = fociDapiVol;
	}
        
        public void setFociNb(int fociNb) {
            this.fociNb = fociNb;
	}
        
        public void setFociVol(double fociVol) {
            this.fociVol = fociVol;
	}
        
        public void setFociInt(double fociInt) {
            this.fociInt = fociInt;
	}
        
        public void setDiffuseInt(double diffuseInt) {
            this.diffuseInt = diffuseInt;
        }
        
        public void setTh(boolean th) {
            this.th = th;
        }      
        
        public int getIndex() {
            return index;
        }
        
        public double getNucVol() {
            return nucVol;
        }
        
        public int getFociDapiNb() {
            return fociDapiNb;
        }
        
        public double getFociDapiVol() {
            return fociDapiVol;
	}
        
        public int getFociNb() {
            return fociNb;
        }
        
        public double getFociVol() {
            return fociVol;
	}
        
        public double getFociInt() {
            return fociInt;
	}
        
        public double getDiffuseInt() {
            return diffuseInt;
	}
        
        public boolean getTh() {
            return th;
	}
        
}
