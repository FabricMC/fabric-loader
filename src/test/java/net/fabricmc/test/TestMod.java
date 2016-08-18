package net.fabricmc.test;

import net.fabricmc.base.loader.Init;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestMod {

    private static final Logger LOGGER = LogManager.getFormatterLogger("TestMod");

    @Init
    public void init() {
        LOGGER.info("**************************");
        LOGGER.info("Hello from Fabric");
        LOGGER.info("**************************");
    }

}
