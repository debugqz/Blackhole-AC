package com.blackhole.util;

/**
 * Immutable 3D double vector. Kept independent of Bukkit's Vector/Location so
 * physics code can run safely off the main thread.
 */
public final class Vector3d {

    public static final Vector3d ZERO = new Vector3d(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3d add(Vector3d other) {
        return new Vector3d(x + other.x, y + other.y, z + other.z);
    }

    public Vector3d add(double dx, double dy, double dz) {
        return new Vector3d(x + dx, y + dy, z + dz);
    }

    public Vector3d subtract(Vector3d other) {
        return new Vector3d(x - other.x, y - other.y, z - other.z);
    }

    public Vector3d multiply(double scalar) {
        return new Vector3d(x * scalar, y * scalar, z * scalar);
    }

    public Vector3d multiply(double mx, double my, double mz) {
        return new Vector3d(x * mx, y * my, z * mz);
    }

    public Vector3d withX(double newX) {
        return new Vector3d(newX, y, z);
    }

    public Vector3d withY(double newY) {
        return new Vector3d(x, newY, z);
    }

    public Vector3d withZ(double newZ) {
        return new Vector3d(x, y, newZ);
    }

    public double horizontalLengthSquared() {
        return x * x + z * z;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distanceSquared(Vector3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vector3d other) {
        return Math.sqrt(distanceSquared(other));
    }

    @Override
    public String toString() {
        return "Vector3d{" + x + ", " + y + ", " + z + '}';
    }
}
