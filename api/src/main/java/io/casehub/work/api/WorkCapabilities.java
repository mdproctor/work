package io.casehub.work.api;

/**
 * Platform-canonical capability name constants.
 * Engine case definitions and worker registrations import from here.
 * Examples and test code import from this class — never the reverse.
 */
public final class WorkCapabilities {

    public static final Capability LEGAL_REVIEW       = Capability.of("legal-review");
    public static final Capability CONTRACT_ANALYSIS  = Capability.of("contract-analysis");
    public static final Capability CONTRACT_REVIEW    = Capability.of("contract-review");
    public static final Capability NDA                = Capability.of("nda");
    public static final Capability IP_LICENSING       = Capability.of("ip-licensing");
    public static final Capability COMPLIANCE         = Capability.of("compliance");
    public static final Capability GDPR               = Capability.of("gdpr");

    private WorkCapabilities() {}
}
