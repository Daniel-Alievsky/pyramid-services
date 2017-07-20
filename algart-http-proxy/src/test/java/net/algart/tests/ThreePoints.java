package net.algart.tests;

/**
 * Created by Daniel on 20/06/2017.
 */
public class ThreePoints {
    private double x1, y1;
    private double x2, y2;
    private double x3, y3;

    public ThreePoints setPoint1(double x, double y) {
        this.x1 = x;
        this.y1 = y;
        return this;
    }

    public ThreePoints setPoint2(double x, double y) {
        this.x2 = x;
        this.y2 = y;
        return this;
    }

    public ThreePoints setPoint3(double x, double y) {
        this.x3 = x;
        this.y3 = y;
        return this;
    }

    public double getAverageDistance() {
        return (distance(x1, y1, x2, y2)
            + distance(x1, y1, x3, y3)
            + distance(x2, y2, x3, y3)) / 3.0;
    }

    @Override
    public String toString() {
        return "three points: (" + x1 + ", " + y1 + "), (" + x2 + ", " + y2 + "), (" + x3 + ", " + y3 + ")";
    }

    private static double distance(double xA, double yA, double xB, double yB) {
        return Math.sqrt((xB - xA) * (xB - xA) + (yB - yA) * (yB - yA));
    }

    public static void main(String[] args) {
        final ThreePoints[] tests = {
            new ThreePoints().setPoint1(1.0, 1.0).setPoint2(1.0, 1.0).setPoint3(1.0, 1.0),
            new ThreePoints().setPoint1(0.0, 0.0).setPoint2(0.0, 1.0).setPoint3(1.0, 0.0),
            new ThreePoints().setPoint1(0.0, 0.0).setPoint2(0.0, 1.0).setPoint3(0.0, 2.0),
            new ThreePoints().setPoint1(-1.0, 0.0).setPoint2(-1.0, 0.0).setPoint3(1.0, 0.0),
            new ThreePoints().setPoint1(-1.0, 0.0).setPoint2(1.0, 0.0).setPoint3(0.0, Math.sqrt(3.0)),
            // - last points are equilateral triangle
        };
        for (ThreePoints test : tests) {
            System.out.printf("Test points: %s, average distance: %f%n", test, test.getAverageDistance());
        }
    }
}
