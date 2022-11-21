package net.fabricmc.loader.impl.launch.knot;

/**
 * If the very first class transformed by mixin is also referenced by a mixin config then we'll crash due to an
 * "attempted duplicate class definition". To avoid this, {@link Knot} loads this class instead - since it's *very
 * unlikely* to be referenced by mixin plugin.
 */
final class UnusedEmptyTargetClass {
}
