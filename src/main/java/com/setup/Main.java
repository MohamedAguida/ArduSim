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
