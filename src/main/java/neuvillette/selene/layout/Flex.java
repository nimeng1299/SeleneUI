package neuvillette.selene.layout;

/** Placement of space left after constraints and explicit spacing are satisfied. */
public enum Flex {
    /** Grow the last eligible child; a Max remains capped and Fill(0) remains empty. */
    LEGACY,
    /** Leave unused space after the children. */
    START,
    /** Leave unused space before the children. */
    END,
    /** Center the children, leaving an odd extra pixel at the end. */
    CENTER,
    /** Distribute unused space between children; a single child stays at the start. */
    SPACE_BETWEEN,
    /** Distribute unused space around children, with half shares at the outer edges. */
    SPACE_AROUND
}
