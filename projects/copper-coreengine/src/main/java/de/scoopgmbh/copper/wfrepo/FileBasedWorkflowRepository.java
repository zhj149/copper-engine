/*
 * Copyright 2002-2011 SCOOP Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.scoopgmbh.copper.wfrepo;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;

import de.scoopgmbh.copper.CopperRuntimeException;
import de.scoopgmbh.copper.Workflow;
import de.scoopgmbh.copper.WorkflowFactory;
import de.scoopgmbh.copper.instrument.ScottyFindInterruptableMethodsVisitor;
import de.scoopgmbh.copper.instrument.Transformed;
import de.scoopgmbh.copper.util.FileUtil;

public class FileBasedWorkflowRepository extends AbstractWorkflowRepository {

	private static final class VolatileState {
		Map<String,Class<?>> wfMap;
		ClassLoader classLoader;
		long checksum;
		VolatileState(Map<String, Class<?>> wfMap, ClassLoader classLoader, long checksum) {
			this.wfMap = wfMap;
			this.classLoader = classLoader;
			this.checksum = checksum;
		}
	}

	private static final Logger logger = Logger.getLogger(FileBasedWorkflowRepository.class);

	private String sourceDir;
	private String targetDir;
	private volatile VolatileState volatileState;
	private Thread observerThread;
	private int checkIntervalMSec = 15000;
	private List<Runnable> preprocessors = Collections.emptyList();
	private boolean stopped = false;

	public synchronized void setCheckIntervalMSec(int checkIntervalMSec) {
		this.checkIntervalMSec = checkIntervalMSec;
	}

	public synchronized void setSourceDir(String sourceDir) {
		this.sourceDir = sourceDir;
	}

	public synchronized void setTargetDir(String targetDir) {
		this.targetDir = targetDir;
	}

	public synchronized void setPreprocessors(List<Runnable> preprocessors) {
		if (preprocessors == null) throw new NullPointerException();
		this.preprocessors = preprocessors;
	}

	@Override
	public <E> WorkflowFactory<E> createWorkflowFactory(final String classname) throws ClassNotFoundException {
		return new WorkflowFactory<E>() {
			@SuppressWarnings("unchecked")
			@Override
			public Workflow<E> newInstance() throws InstantiationException, IllegalAccessException {
				return (Workflow<E>) volatileState.wfMap.get(classname).newInstance();
			}
		};
	}

	@Override
	public java.lang.Class<?> resolveClass(java.io.ObjectStreamClass desc) throws java.io.IOException ,ClassNotFoundException {
		return Class.forName(desc.getName(), false, volatileState.classLoader);
	};


	@Override
	public synchronized void start() {
		if (stopped) throw new IllegalStateException();
		try {
			if (volatileState == null) {
				File dir = new File(targetDir);
				deleteDirectory(dir);
				dir.mkdirs();

				for (Runnable preprocessor : preprocessors) {
					preprocessor.run();
				}
				volatileState = createWfClassMap();

				observerThread = new Thread("WfRepoObserver") {
					@Override
					public void run() {
						logger.info("Starting observation");
						while(!stopped) {
							try {
								Thread.sleep(checkIntervalMSec);
								for (Runnable preprocessor : preprocessors) {
									preprocessor.run();
								}
								final long checksum = processChecksum(new File(sourceDir));
								if (checksum != volatileState.checksum) {
									logger.info("Change detected - recreating workflow map");
									volatileState = createWfClassMap();
								}
							} 
							catch(InterruptedException e) {
								// ignore
							}
							catch (Exception e) {
								logger.error("",e);
							}
						}
						logger.info("Stopping observation");
					}
				};
				observerThread.setDaemon(true);
				observerThread.start();
			}
		} 
		catch (Exception e) {
			logger.error("start failed",e);
			throw new Error("start failed",e);
		}
	}

	@Override
	public synchronized void shutdown() {
		logger.info("shutting down...");
		if (stopped) return;
		stopped = true;
		observerThread.interrupt();
	}

	private synchronized VolatileState createWfClassMap() throws IOException, ClassNotFoundException {
		final File sourceDirectory = new File(sourceDir);
		if (!sourceDirectory.exists()) {
			throw new IllegalArgumentException("source directory "+sourceDir+" does not exist!");
		}

		final Map<String,Class<?>> map = new HashMap<String, Class<?>>();
		final long checksum = processChecksum(sourceDirectory);
		final File baseTargetDir = new File(targetDir,Long.toHexString(System.currentTimeMillis()));
		final File compileTargetDir = new File(baseTargetDir,"classes");
		final File adaptedTargetDir = new File(baseTargetDir,"adapted");
		if (!compileTargetDir.exists()) compileTargetDir.mkdirs();
		if (!adaptedTargetDir.exists()) adaptedTargetDir.mkdirs();

		compile(compileTargetDir);
		final Map<String, Clazz> clazzMap = findInterruptableMethods(compileTargetDir);
		instrumentWorkflows(adaptedTargetDir, clazzMap);
		final ClassLoader cl = createClasses(map, adaptedTargetDir, clazzMap);

		return new VolatileState(map, cl, checksum);
	}

	private ClassLoader createClasses(Map<String, Class<?>> map,
			File adaptedTargetDir, Map<String, Clazz> clazzMap)
	throws MalformedURLException, ClassNotFoundException {

		logger.info("Creating classes");
		URLClassLoader classLoader = new URLClassLoader(new URL[] { adaptedTargetDir.toURI().toURL() /*, compileTargetDir.toURI().toURL()*/ }, Thread.currentThread().getContextClassLoader()) {
			@Override
			protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				Class<?> c = findLoadedClass(name);
				if (c == null) {
					try {
						c = super.findClass(name);
						if (c.getAnnotation(Transformed.class) == null) throw new ClassFormatError("Copper workflow "+name+" is not transformed!");
						logger.info(c.getName()+" created");
					}
					catch (Exception e) {
						c = super.loadClass(name,false);
					}
				}
				if (resolve) {
					resolveClass(c);
				}
				return c;
			}
		};

		for (Clazz clazz : clazzMap.values()) {
			String name = clazz.classname.replace('/', '.');
			Class<?> c = classLoader.loadClass(name);
			map.put(name, c);
		}

		return classLoader;
	}

	private Map<String, Clazz> findInterruptableMethods(File compileTargetDir) throws FileNotFoundException, IOException {
		logger.info("Analysing classfiles");
		// Find and visit all classes
		Map<String,Clazz> clazzMap = new HashMap<String, Clazz>();
		File[] classfiles = findFiles(compileTargetDir, ".class");
		for (File f : classfiles) {
			ScottyFindInterruptableMethodsVisitor visitor = new ScottyFindInterruptableMethodsVisitor();
			InputStream is = new FileInputStream(f);
			try {
				ClassReader cr = new ClassReader(is);
				cr.accept(visitor, 0);
			}
			finally {
				is.close();
			}
			Clazz clazz = new Clazz();
			clazz.interruptableMethods = visitor.getInterruptableMethods();
			clazz.classfile = f;
			clazz.classname = visitor.getClassname();
			clazz.superClassname = visitor.getSuperClassname();
			clazzMap.put(clazz.classname, clazz);
		}

		// Remove all classes that are no workflow
		List<String> allClassNames = new ArrayList<String>(clazzMap.keySet());
		for (String classname : allClassNames) {
			Clazz clazz = clazzMap.get(classname);
			Clazz startClazz = clazz;
			inner : while(true) {
				startClazz.aggregatedInterruptableMethods.addAll(clazz.interruptableMethods);
				if ("de/scoopgmbh/copper/Workflow".equals(clazz.superClassname) || "de/scoopgmbh/copper/persistent/PersistentWorkflow".equals(clazz.superClassname)) {
					break inner;
				}
				clazz = clazzMap.get(clazz.superClassname);
				if (clazz == null) {
					break;
				}
			}
			if (clazz == null) {
				// this is no workflow 
				clazzMap.remove(classname);
			}
		}
		return clazzMap;
	}

	private void compile(File compileTargetDir) throws IOException {
		logger.info("Compiling workflows");
		List<String> options = new ArrayList<String>();
		options.add("-d");
		options.add(compileTargetDir.getAbsolutePath());
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) throw new NullPointerException("No java compiler available! Did you start from a JDK? JRE will not work.");
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		try {
			final StringWriter sw = new StringWriter();
			sw.append("Compilation failed!\n");
			final File[] files = findFiles(new File(sourceDir), ".java");
			if (files.length > 0) {
				final Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(files));
				final CompilationTask task = compiler.getTask(sw, fileManager, null, options, null, compilationUnits1);
				if (!task.call()) {
					logger.error(sw.toString());
					throw new CopperRuntimeException("Compilation failed, see logfile for details");
				}
			}
		}
		finally {
			fileManager.close();
		}
	}

	private File[] findFiles(File rootDir, String fileExtension) {
		List<File> files = new ArrayList<File>();
		findFiles(rootDir, fileExtension, files);
		return files.toArray(new File[files.size()]);
	}

	private void findFiles(final File rootDir, final String fileExtension, final List<File> files) {
		files.addAll(Arrays.asList(rootDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(fileExtension);
			}
		})));
		File[] subdirs = rootDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		for (File subdir : subdirs) {
			findFiles(subdir, fileExtension, files);
		}
	}

	private static long processChecksum(File directory) {
		return FileUtil.processChecksum(directory,".java");
	}

	private static boolean deleteDirectory(File path) {
		if( path.exists() ) {
			File[] files = path.listFiles();
			for(int i=0; i<files.length; i++) {
				if(files[i].isDirectory()) {
					deleteDirectory(files[i]);
				}
				else {
					files[i].delete();
				}
			}
		}
		return( path.delete() );
	}


	public static void main(String[] args) {
		FileBasedWorkflowRepository repo = new FileBasedWorkflowRepository();
		repo.setSourceDir("src/workflow/java");
		repo.setTargetDir("target/compiled_workflow");
		repo.start();
	}

}