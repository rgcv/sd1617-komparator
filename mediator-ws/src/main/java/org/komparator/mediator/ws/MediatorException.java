package org.komparator.mediator.ws;


public class MediatorException extends Exception {

	private static final long serialVersionUID = 1L;

	public MediatorException() {
    }

    public MediatorException(String message) {
        super(message);
    }

    public MediatorException(Throwable cause) {
        super(cause);
    }

    public MediatorException(String message, Throwable cause) {
        super(message, cause);
    }

}
