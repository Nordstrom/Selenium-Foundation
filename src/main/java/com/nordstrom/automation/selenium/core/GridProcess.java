package com.nordstrom.automation.selenium.core;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openqa.grid.selenium.GridLauncher;
import org.testng.ITestResult;

class GridProcess {
	
	private static final String OPT_ROLE = "-role";
	private static final Class<?>[] dependencies = { GridLauncher.class };
	
	static Process start(ITestResult testResult, String[] args) {
		List<String> argsList = new ArrayList<>(Arrays.asList(args));
		int optIndex = argsList.indexOf(OPT_ROLE);
		String gridRole = args[optIndex + 1];
		
		argsList.add(0, GridLauncher.class.getName());
		argsList.add(0, getClasspath(dependencies));
		argsList.add(0, "-cp");
		argsList.add(0, "c:\\tools\\java\\jdk1.8.0_112\\bin\\java");
		
		ProcessBuilder builder = new ProcessBuilder(argsList);
		
		String outputDir;
		if (testResult != null) {
			outputDir = testResult.getTestContext().getOutputDirectory();
		} else {
			Path currentRelativePath = Paths.get("");
			outputDir = currentRelativePath.toAbsolutePath().toString();
		}
		File outputFile = new File(outputDir, "grid-" + gridRole + ".log");
		
		builder.redirectErrorStream(true);
		builder.redirectOutput(outputFile);
		
		try {
			Files.createDirectories(outputFile.toPath().getParent());
			return builder.start();
		} catch (IOException e) {
			throw new RuntimeException("Failed to start grid " + gridRole + " process", e);
		}
	}
	
	private static String getClasspath(Class<?>[] dependencies) {
		List<String> pathList = new ArrayList<>();
		for (Class<?> clazz : dependencies) {
			pathList.add(findPathJar(clazz));
		}
		return String.join(File.pathSeparator, pathList);
	}
	
	/**
	 * If the provided class has been loaded from a jar file that is on the
	 * local file system, will find the absolute path to that jar file.
	 * 
	 * @param context
	 *            The jar file that contained the class file that represents
	 *            this class will be found. Specify {@code null} to let
	 *            {@code LiveInjector} find its own jar.
	 * @throws IllegalStateException
	 *             If the specified class was loaded from a directory or in some
	 *             other way (such as via HTTP, from a database, or some other
	 *             custom class-loading device).
	 */
	public static String findPathJar(Class<?> context) throws IllegalStateException {
	    String rawName = context.getName();
	    String classFileName;
	    /* rawName is something like package.name.ContainingClass$ClassName. We need to turn this into ContainingClass$ClassName.class. */ {
	        int idx = rawName.lastIndexOf('.');
	        classFileName = (idx == -1 ? rawName : rawName.substring(idx+1)) + ".class";
	    }

	    String uri = context.getResource(classFileName).toString();
	    if (uri.startsWith("file:")) throw new IllegalStateException("This class has been loaded from a directory and not from a jar file.");
	    if (!uri.startsWith("jar:file:")) {
	        int idx = uri.indexOf(':');
	        String protocol = idx == -1 ? "(unknown)" : uri.substring(0, idx);
	        throw new IllegalStateException("This class has been loaded remotely via the " + protocol +
	                " protocol. Only loading from a jar on the local file system is supported.");
	    }

	    int idx = uri.indexOf('!');
	    //As far as I know, the if statement below can't ever trigger, so it's more of a sanity check thing.
	    if (idx == -1) throw new IllegalStateException("You appear to have loaded this class from a local jar file, but I can't make sense of the URL!");

	    try {
	        String fileName = URLDecoder.decode(uri.substring("jar:file:".length(), idx), Charset.defaultCharset().name());
	        return new File(fileName).getAbsolutePath();
	    } catch (UnsupportedEncodingException e) {
	        throw new InternalError("default charset doesn't exist. Your VM is borked.");
	    }
	}
}
