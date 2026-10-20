package io.github.foldaspect.unlock;

/** Pure shared policy only: no SystemUI View, layout or drawing emulation. */
public final class StatusBarStyleRegression {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("scenario required");
        switch (args[0]) {
            case "style-height-policy": heights(); break;
            case "style-request-gates": requested(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS host scenario: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void heights() {
        require(StatusBarStylePolicy.height(126, 2.5f) == 90
                && StatusBarStylePolicy.height(140, 2.5f) == 90,
            "Normal-size text uses the requested fixed 90px target rather than a percentage");
        require(StatusBarStylePolicy.height(80, 2.5f) == 80,
            "The fixed target must not enlarge a smaller native bar");
        require(StatusBarStylePolicy.height(126, 2.5f, 1.5f) == 105,
            "A 1.5x font scale must reserve 105px for the enlarged icon/text row");
        require(StatusBarStylePolicy.height(126, 2.5f, 2f) == 126,
            "A 2x font scale must be capped at the 126px native height");
        require(StatusBarStylePolicy.height(126, 3.5f, 0.75f) == 98,
            "A smaller font preference must not reduce the normal 28dp minimum");
        for (float fontScale : new float[]{Float.NaN, -1f, 0f,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            // A larger density makes the minimum decisive (98px) rather than
            // letting the fixed 90px target hide an incorrect fallback.
            require(StatusBarStylePolicy.height(126, 3.5f, fontScale) == 98,
                "Invalid font scale must fall back to the 1x minimum: " + fontScale);
        }
        require(StatusBarStylePolicy.height(50, 2.5f) == 50
                && StatusBarStylePolicy.height(1, 2.5f) == 1,
            "The minimum icon-space floor must never enlarge a smaller native bar");
        require(StatusBarStylePolicy.height(0, 2.5f) == 0
                && StatusBarStylePolicy.height(-10, 2.5f) == -10,
            "Unavailable native heights must be preserved");
        for (float density : new float[]{0f, -1f, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            require(StatusBarStylePolicy.height(126, density) == 126,
                "Invalid density must preserve native height: " + density);
        }
        require(StatusBarStylePolicy.SCRIM_COLOR == 0x33000000,
            "The scoped renderer color must encode black at exactly 51/255 (20%) alpha");
    }

    private static void requested() {
        int marker = 0x10000000;
        int semi = 32;
        require(StatusBarStylePolicy.COMPACT_APPEARANCE == marker,
            "Server and scoped renderer must agree on the inspected marker bit");
        require(StatusBarStylePolicy.requested(0, marker | semi, 1),
            "Primary display + compact marker + semi mode + visible status bar must qualify");
        require(StatusBarStylePolicy.requested(0, marker | semi | 2 | 16 | 64 | 256, 1 | 2),
            "Unrelated appearance and navigation visibility bits must not block the request");
        require(!StatusBarStylePolicy.requested(1, marker | semi, 1)
                && !StatusBarStylePolicy.requested(-1, marker | semi, 1),
            "Only the primary display may render the scoped style");
        require(!StatusBarStylePolicy.requested(0, semi, 1)
                && !StatusBarStylePolicy.requested(0, marker, 1)
                && !StatusBarStylePolicy.requested(0, 0, 1),
            "Both the private compact marker and native semi mode are required");
        require(!StatusBarStylePolicy.requested(0, marker | semi, 0)
                && !StatusBarStylePolicy.requested(0, marker | semi, 2),
            "Hidden status bars and navigation-only visibility must not request custom drawing");
    }
}
