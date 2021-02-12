package com.api.formations;

import com.api.API;
import com.api.ValidationTools;

public class FormationPoint {


    public int position;
    public double offsetX;
    public double offsetY;
    ValidationTools validationTools = API.getValidationTools();

    /**
     * Build an object for a point in the formation.
     * @param position Position of the UAV in the formation.
     * @param offsetX Offset of the UAV in x axes from the center UAV.
     * @param offsetY Offset of the UAV in y axes from the center UAV.
     */
    public FormationPoint(int position, double offsetX, double offsetY) {
        this.position = position;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /**
     * Rotate the point over a specific Angle with center (0,0)
     * @param theta: angle used: counterclockwise radians
     */
    public void rotate(double theta){
        double x = offsetX;
        double y = offsetY;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        offsetX = validationTools.roundDouble(x*cosTheta - y*sinTheta,6);
        offsetY = validationTools.roundDouble(y*cosTheta + x*sinTheta,6);
    }
}
