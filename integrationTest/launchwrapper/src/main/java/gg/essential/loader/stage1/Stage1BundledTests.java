package gg.essential.loader.stage1;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static gg.essential.loader.fixtures.BaseInstallation.withBranch;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1BundledTests {
    @Test
    public void testAutoUpdateWithBundledVersion(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");

        installation.launchFML();

        // Enable auto-update via config file (otherwise we'll default to update with-prompt when there's a pinned jar)
        Files.write(installation.stage1ConfigFile, "autoUpdate=true".getBytes(StandardCharsets.UTF_8));

        Files.delete(installation.stage2Meta);
        Files.copy(installation.stage2DummyMeta, installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage2.EssentialLoader").getDeclaredField("loaded").getBoolean(null));
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage2.EssentialLoader").getDeclaredField("initialized").getBoolean(null));
    }

    @Test
    public void testPromptUpdateAccepted(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");

        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);

        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));

        writeProps(installation.stage1ConfigFile, props("pendingUpdateVersion=2", "pendingUpdateResolution=true"));

        IsolatedLaunch secondLaunch = installation.launchFML();
        assertEquals("2", secondLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("overridePinnedVersion=2"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testPromptUpdateRejected(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");

        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);

        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));

        writeProps(installation.stage1ConfigFile, props("pendingUpdateVersion=2", "pendingUpdateResolution=false"));

        IsolatedLaunch secondLaunch = installation.launchFML();
        assertEquals("1", secondLaunch.getProperty("essential.stage2.version"));

        installation.assertModLaunched(secondLaunch);
        assertTrue(secondLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals(props("pendingUpdateVersion=2", "pendingUpdateResolution=false"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testPromptUpdateAcceptedFallback(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");

        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);

        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));

        IsolatedLaunch secondLaunch = installation.newLaunchFML();
        secondLaunch.setProperty("essential.stage1.fallback-prompt-auto-answer", "true");
        secondLaunch.launch();

        assertEquals("2", secondLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("overridePinnedVersion=2"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testPromptUpdateRejectedFallback(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");

        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);

        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));

        IsolatedLaunch secondLaunch = installation.newLaunchFML();
        secondLaunch.setProperty("essential.stage1.fallback-prompt-auto-answer", "false");
        secondLaunch.launch();

        assertEquals("1", secondLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2", "pendingUpdateResolution=false"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testBundledVersionUpgradeWithoutOverride(Installation installation) throws Exception {
        installation.addExampleMod("bundled-1");
        // Skip online update, we want to test local bundle update
        writeProps(installation.stage1ConfigFile, props("pendingUpdateVersion=stable", "pendingUpdateResolution=false"));

        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals("1", firstLaunch.getProperty("essential.stage2.version"));

        installation.addExampleMod("bundled-2");

        IsolatedLaunch secondLaunch = installation.launchFML();
        assertEquals("2", secondLaunch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=stable", "pendingUpdateResolution=false"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testBundledVersionUpgradeToNewerVersion(Installation installation) throws Exception {
        IsolatedLaunch launch;

        // First install at version 1
        Files.copy(withBranch(installation.stage2Meta, "1"), installation.stage2Meta, REPLACE_EXISTING);
        installation.addExampleMod("bundled-1");
        launch = installation.launchFML();
        assertEquals("1", launch.getProperty("essential.stage2.version"));

        // Then click-to-update to version 2
        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);
        launch = installation.launchFML();
        assertEquals("1", launch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));
        writeProps(installation.stage1ConfigFile, props("pendingUpdateVersion=2", "pendingUpdateResolution=true"));
        launch = installation.launchFML();
        assertEquals("2", launch.getProperty("essential.stage2.version"));
        assertEquals(props("overridePinnedVersion=2"), readProps(installation.stage1ConfigFile));

        // Finally upgrade the bundled mod to version 3
        installation.addExampleMod("bundled-3");
        launch = installation.launchFML();
        // it should use that version
        assertEquals("3", launch.getProperty("essential.stage2.version"));
        // and un-pin the existing one
        assertEquals(props(), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testBundledVersionUpgradeToOlderVersion(Installation installation) throws Exception {
        IsolatedLaunch launch;

        // First install at version 1
        installation.addExampleMod("bundled-1");
        launch = installation.launchFML();
        assertEquals("1", launch.getProperty("essential.stage2.version"));

        // Then click-to-update to version 3
        Files.copy(withBranch(installation.stage2Meta, "3"), installation.stage2Meta, REPLACE_EXISTING);
        launch = installation.launchFML();
        assertEquals("1", launch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=3"), readProps(installation.stage1ConfigFile));
        writeProps(installation.stage1ConfigFile, props("pendingUpdateVersion=3", "pendingUpdateResolution=true"));
        launch = installation.launchFML();
        assertEquals("3", launch.getProperty("essential.stage2.version"));
        assertEquals(props("overridePinnedVersion=3"), readProps(installation.stage1ConfigFile));

        // Finally upgrade the bundled mod to version 2
        installation.addExampleMod("bundled-2");
        launch = installation.launchFML();
        // it should stay at version 3 however
        assertEquals("3", launch.getProperty("essential.stage2.version"));
        assertEquals(props("overridePinnedVersion=3"), readProps(installation.stage1ConfigFile));
    }

    @Test
    public void testBundledVersionDowngrade(Installation installation) throws Exception {
        IsolatedLaunch launch;

        // First install at version 2
        installation.addExampleMod("bundled-2");
        Files.copy(withBranch(installation.stage2Meta, "2"), installation.stage2Meta, REPLACE_EXISTING);
        launch = installation.launchFML();
        assertEquals("2", launch.getProperty("essential.stage2.version"));

        // Then downgrade the bundled mod to version 1
        installation.addExampleMod("bundled-1");
        launch = installation.launchFML();
        // we should follow the downgrade, despite our local version being more up-to-date
        assertEquals("1", launch.getProperty("essential.stage2.version"));
        assertEquals(props("pendingUpdateVersion=2"), readProps(installation.stage1ConfigFile));
    }

    public static Properties readProps(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }

    public static void writeProps(Path path, Properties props) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer out = Files.newBufferedWriter(path)) {
            props.store(out, null);
        }
    }

    public static Properties props(String...args) {
        Properties props = new Properties();
        for (String arg : args) {
            String[] split = arg.split("=", 2);
            props.put(split[0], split[1]);
        }
        return props;
    }
}
