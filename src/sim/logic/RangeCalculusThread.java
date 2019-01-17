package sim.logic;

import api.Tools;
import main.Param;
import uavController.UAVParam;

/** Developed by: Francisco José Fabra Collado, from GRC research group in Universitat Politècnica de València (Valencia, Spain). */

public class RangeCalculusThread extends Thread {
	
	@Override
	public void run() {
		long checkTime = System.currentTimeMillis();
		int waitingTime;
		while (Param.simStatus == Param.SimulatorState.STARTING_UAVS
				|| Param.simStatus == Param.SimulatorState.UAVS_CONFIGURED
				|| Param.simStatus == Param.SimulatorState.SETUP_IN_PROGRESS
				|| Param.simStatus == Param.SimulatorState.READY_FOR_TEST
				|| Param.simStatus == Param.SimulatorState.TEST_IN_PROGRESS) {
			boolean isInRange;
			if (UAVParam.distanceCalculusIsOnline) {
				SimParam.communicationsOnline = true;
				for (int i = 0; i < Param.numUAVs - 1; i++) {
					for (int j = i + 1; j < Param.numUAVs; j++) {
						isInRange = SimTools.isInRange(UAVParam.distances[i][j].get());
						UAVParam.isInRange[i][j].set(isInRange);
						UAVParam.isInRange[j][i].set(isInRange);
					}
				}
			}
			checkTime = checkTime + UAVParam.RANGE_CHECK_PERIOD;
			waitingTime = (int)(checkTime - System.currentTimeMillis());
			if (waitingTime > 0) {
				Tools.waiting(waitingTime);
			}
		}
	}

}
