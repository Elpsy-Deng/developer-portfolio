package dev.elpsy.portfolio.media;

public final class ImageStorageProblem extends RuntimeException {
    public ImageStorageProblem() { super("Image storage unavailable"); }
    public ImageStorageProblem(Exception cause) { super("Image storage unavailable",cause); }
}
