package gym;

/** An error the user can fix (bad input, a scheduling conflict, a missing record). */
final class ApiException extends RuntimeException {

    final int status;

    ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    static ApiException notFound(String what) {
        return new ApiException(404, what + " not found.");
    }

    static ApiException conflict(String message) {
        return new ApiException(409, message);
    }
}
