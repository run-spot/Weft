package run.thespot.weft.push

/** An error returned while registering or unregistering the configured push provider. */
public class PushException(
    message: String,
) : Exception(message)
