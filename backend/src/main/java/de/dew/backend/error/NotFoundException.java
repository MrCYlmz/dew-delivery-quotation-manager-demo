package de.dew.backend.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException delivery(long id) {
        return new NotFoundException("No delivery with id " + id);
    }

    public static NotFoundException pdf(long deliveryId, long pdfId) {
        return new NotFoundException("No pdf with id " + pdfId + " on delivery " + deliveryId);
    }
}
