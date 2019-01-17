package api.pojo.formations;

import api.Tools;

/** The formation numbering starts in 0, and increases from left to the right.
 * <p>Developed by: Francisco José Fabra Collado, from GRC research group in Universitat Politècnica de València (Valencia, Spain).</p> */

public class LinearFlightFormation extends FlightFormation {

	protected LinearFlightFormation(int numUAVs, double minDistance) {
		super(numUAVs, minDistance);
	}

	@Override
	protected void initializeFormation() {
		this.centerUAV = this.numUAVs / 2;
		
		double x;
		for (int i = 0; i < this.numUAVs; i++) {
			x = Tools.round((i - this.centerUAV) * this.minDistance, 6);
			this.point[i] = new FormationPoint(i, x, 0);
		}
	}

}
