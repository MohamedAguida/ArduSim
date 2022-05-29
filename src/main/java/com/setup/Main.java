package com.setup;


import com.api.API;
import com.api.ArduSim;
import com.api.ArduSimTools;
import com.setup.arduSimSetup.ArduSimSetupPCCompanion;
import com.setup.arduSimSetup.ArduSimSetupReal;
import com.setup.arduSimSetup.ArduSimSetupSimulatorCLI;
import com.setup.arduSimSetup.ArduSimSetupSimulatorGUI;

import java.io.*;
import java.util.Properties;

/** This class contains the main method and the chronological logic followed by the whole application.
 * <p>Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain).</p> */

public class Main {
//-0.3492282939337654,39.48164558256651,0 -0.3424171686933319,39.47974905591905,0 -0.3407873714609699,39.48348063959362,0 -0.3451797662663447,39.48473259836601,0 -0.3496356483618546,39.48205963492051,0
	public static void main(String[] args) throws IOException {
		ArduSimTools.parseArgs(args);
		//int [] nums = {5,10};
		//double [] malicious= {0.5,0.75};
		if(Param.role == ArduSim.MULTICOPTER){
			new ArduSimSetupReal().start();
		}else if(Param.role == ArduSim.SIMULATOR_GUI){
			new ArduSimSetupSimulatorGUI().start();
		}else if(Param.role == ArduSim.SIMULATOR_CLI){
			new ArduSimSetupSimulatorCLI().start();
		}else if(Param.role == ArduSim.PCCOMPANION){
			new ArduSimSetupPCCompanion().start();
		}
	}

}
