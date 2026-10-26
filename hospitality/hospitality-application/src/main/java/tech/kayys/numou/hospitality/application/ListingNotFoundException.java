package tech.kayys.numou.hospitality.application;

public final class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException() {
        super("Property listing was not found");
    }
}
