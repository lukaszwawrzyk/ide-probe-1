package org.virtuslab.intellij.scala.probe;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.extensions.PluginId;
import org.virtuslab.ProbeHandlers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Initialize implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.findId("org.virtuslab.ideprobe"));
        PluginClassLoader pluginClassLoader = (PluginClassLoader) plugin.getPluginClassLoader();
        PluginClassLoader classLoader = (PluginClassLoader) Initialize.class.getClassLoader();
        classLoader.detachParent(pluginClassLoader);
        URL resource = pluginClassLoader.getResource("scala/Option.class");
//        new File(System.getProperty("idea.plugins.path"));
        Path resolve = Paths.get(System.getProperty("idea.plugins.path")).resolve("ideprobe/lib/");
        try {
            List<Path> pathStream = Files.list(resolve).filter(path -> !path.getFileName().toString().equals("scala-library.jar")).collect(toList());
            ArrayList<URL> list = new ArrayList<>();
            for(Path path : pathStream) {
                list.add(path.toUri().toURL());
            }
            URLClassLoader loader = new URLClassLoader(list.toArray(new URL[0]));
            classLoader.attachParent(loader);
            Class aClass = classLoader.loadClass("org.virtuslab.intellij.scala.probe.SbtProbeHandlerContributor");
            Constructor constructor = aClass.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            classLoader.loadClass("org.virtuslab.ProbeHandlers").getMethod("registerHandler").invoke(null, instance);
            System.out.println("");
        } catch (Exception e) {
            e.printStackTrace();
        }
        ///tmp/intellij-instance-202.6397.59-EAP-SNAPSHOT-2560263820243299797/plugins/ideprobe/lib/probe-plugin.jar
    }
}
