package net.fabricmc.test.hookchain;

import net.fabricmc.api.Hook;
import net.fabricmc.base.OrderedHookchain;

import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Ordered hookchain tests.
 *
 * @author greaser
 */
public class TestOrderedHookchain {
    private class TestCallback {
        private String name;

        public TestCallback(OrderedHookchain<PrintStream> hc, String name) {
            this.name = name;
            try {
                hc.addHook(name, MethodHandles.lookup().bind(this, "handle", MethodType.methodType(void.class, PrintStream.class)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void handle(PrintStream fp) {
            fp.printf("called: \"%s\"\n", this.name);
        }
    }

    private OrderedHookchain<PrintStream> hc = new OrderedHookchain<>();

    public TestOrderedHookchain() {
    }

    @Hook(name = "get_on_the_floor", after = {"obtain_floor"}, before = {"everybody_walk_the_dinosaur", "everybody_kill_the_dinosaur"})
    public void getOnTheFloor(PrintStream stream) {
        stream.printf("called annotated: \"get_on_the_floor\"\n");
    }

    public void run() {
        // add annotated hooks first
        hc.addAllHooks(this);

        // main events
        new TestCallback(hc, "open_the_door");
        new TestCallback(hc, "everybody_walk_the_dinosaur");
        new TestCallback(hc, "everybody_kill_the_dinosaur");

        // ensure we have things before we use them
        hc.addConstraint("obtain_door", "open_the_door");
        hc.addConstraint("obtain_floor", "get_on_the_floor");
        hc.addConstraint("obtain_dinosaur", "everybody_walk_the_dinosaur");
        hc.addConstraint("obtain_dinosaur", "everybody_kill_the_dinosaur");

        // obtain things
        new TestCallback(hc, "obtain_door");
        new TestCallback(hc, "obtain_floor");
        new TestCallback(hc, "obtain_dinosaur");
        new TestCallback(hc, "obtain_everybody");
        new TestCallback(hc, "obtain_rope");
        new TestCallback(hc, "obtain_knife");

        // we can add constraints after creation too!
        hc.addConstraint("obtain_rope", "everybody_walk_the_dinosaur");
        hc.addConstraint("obtain_knife", "everybody_kill_the_dinosaur");
        hc.addConstraint("obtain_everybody", "everybody_walk_the_dinosaur");
        hc.addConstraint("obtain_everybody", "everybody_kill_the_dinosaur");

        // we should do this in the correct order
        hc.addConstraint("open_the_door", "get_on_the_floor");

        // we cannot walk the dinosaur if it is dead
        hc.addConstraint("everybody_walk_the_dinosaur", "everybody_kill_the_dinosaur");

        // hookless constraints are allowed
        hc.addConstraint("everybody_walk_the_dinosaur", "a_few_verses_later");
        hc.addConstraint("a_few_verses_later", "obtain_knife");

        // cyclic dependency test
        //hc.addConstraint("everybody_walk_the_dinosaur", "open_the_door");

        hc.callChain(System.out);
    }

    public static void main(String[] args) {
        new TestOrderedHookchain().run();
    }
}
