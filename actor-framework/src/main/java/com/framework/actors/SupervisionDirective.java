package com.framework.actors;

/**
 * Directive for handling actor failures.
 */
public enum SupervisionDirective {
    RESUME,   // Continue processing
    RESTART,  // Restart the actor
    STOP,     // Stop the actor permanently
    ESCALATE  // Escalate to parent
}