package com.thirikkale.userservice.exception;

public class CustomExceptions {

    public static class UnauthorizedAppAccessException extends RuntimeException {
        public UnauthorizedAppAccessException(String message) {
            super(message);
        }
    }

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    public static class InvalidPhoneNumberException extends RuntimeException {
        public InvalidPhoneNumberException(String message) {
            super(message);
        }
    }

    public static class UserNotActiveException extends RuntimeException {
        public UserNotActiveException(String message) {
            super(message);
        }
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) {
            super(message);
        }
    }

    public static class FileStorageException extends RuntimeException {
        public FileStorageException(String message) {
            super(message);
        }
    }

    public static class DocumentUploadException extends RuntimeException {
        public DocumentUploadException(String message) {
            super(message);
        }
    }

    public static class GenderDetectionException extends RuntimeException {
        public GenderDetectionException(String message) {
            super(message);
        }
    }

    // Firebase-specific exceptions
    public static class FirebaseTokenExpiredException extends RuntimeException {
        public FirebaseTokenExpiredException(String message) {
            super(message);
        }
    }

    public static class PhoneNotVerifiedException extends RuntimeException {
        public PhoneNotVerifiedException(String message) {
            super(message);
        }
    }

    // Authentication-specific exceptions
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    // Vehicle-specific exceptions
    public static class VehicleAlreadyExistsException extends RuntimeException {
        public VehicleAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class VehicleNotFoundException extends RuntimeException {
        public VehicleNotFoundException(String message) {
            super(message);
        }
    }

    public static class VehicleNotVerifiedException extends RuntimeException {
        public VehicleNotVerifiedException(String message) {
            super(message);
        }
    }
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}