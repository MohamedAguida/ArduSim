package pollution.pojo;

/**
 * Represents a value in a 2d grid.
 * @author Carlos Martínez Úbeda
 *
 */
public class Value {
	int x, y;
	double v;
	
	public Value(int x, int y, double v) {
		this.x = x;
		this.y = y;
	}
	
	/**
	 * @return The serialised data.
	 */
	@Override
	public String toString() {
		return "[" + x + ", " + y + "] = " + v;
	}
	
	/**
	 * Deep copy of another Point object onto this object.
	 * @param p Object to copy from.
	 */
	public void copyFrom(Value p) {
		x = p.x;
		y = p.y;
		v = p.v;
	}
	
	/**
	 * Make a deep copy of this object
	 * @return A deep copy of the object.
	 */
	public Value clone() {
		return new Value(x, y, v);
	}
	
	/**
	 * Calculate the distance vector to this Point from another Point.
	 * @param p The Point from which to calculate the distance.
	 * @return The distance vector as new Point object.
	 */
	public Point distVector(Point p) {
		return new Point(x - p.x, y - p.y);
	}
	
	public double distance(Point p) {
		int a = this.x - p.x;
		int b = this.y - p.y;
		return Math.sqrt(a*a + b*b);
	}
	
	/**
	 * Adds a Point to this Point.
	 * Useful for adding Points representing distance vectors.
	 * @param p The Point to add to this object.
	 * @return Itself to allow chaining.
	 */
	public Value add(Point p) {
		x += p.x;
		y += p.y;
		return this;
	}
	
	/**
	 * Adds to the x and y components of the Point.
	 * @param x The x to add.
	 * @param y The y to add.
	 * @return
	 */
	public Value add(int x, int y) {
		this.x += x;
		this.y += y;
		return this;
	}
	
	/**
	 * Checks if the point would be contained within a grid of specified size.
	 * @param sizeX x axis size of the grid.
	 * @param sizeY y axis size of the grid.
	 * @return True if the point is in the grid, false otherwise.
	 */
	public boolean isInside(int sizeX, int sizeY) {
		if (x < 0 || x >= sizeX || y < 0 || y >= sizeY) return false;
		return true;
	}
	
	/**
	 * @return The x
	 */
	public int getX() {
		return x;
	}
	/**
	 * @param x The x to set
	 */
	public void setX(int x) {
		this.x = x;
	}
	/**
	 * @return The y
	 */
	public int getY() {
		return y;
	}
	/**
	 * @param y The y to set
	 */
	public void setY(int y) {
		this.y = y;
	}
	/**
	 * @return The v
	 */
	public double getV() {
		return v;
	}
	/**
	 * @param v The v to set
	 */
	public void setV(double v) {
		this.v = v;
	}
	/**
	 * Add to x
	 * @param i Amount to add to x
	 * @return Itself to allow chaining.
	 */
	public Value addX(int i) {
		this.x += i;
		return this;
	}
	/**
	 * Add to y
	 * @param i Amount to add to y
	 * @return Itself to allow chaining.
	 */
	public Value addY(int i) {
		this.y += i;
		return this;
	}
}