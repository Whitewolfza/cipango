//========================================================================
//$Id: JarScanner.java 3313 2008-07-18 12:36:37Z janb $
//Copyright 2008 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================


package org.mortbay.jetty.annotations;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;

/**
 * JarScannerConfiguration
 *
 * Abstract base class for configurations that want to scan jars in
 * WEB-INF/lib and the classloader hierarchy.
 * 
 * Jar name matching based on regexp patterns is provided.
 * 
 * Subclasses should implement the processEntry(URL jarUrl, JarEntry entry)
 * method to handle entries in jar files whose names match the supplied 
 * pattern.
 */
public abstract class JarScanner
{

    public abstract void processEntry (URL jarUrl, JarEntry entry);
    
    public abstract void processFile (File directory, File file);
    
    /**
     * Find jar names from the classloader matching a pattern.
     * 
     * If the pattern is null and isNullInclusive is true, then
     * all jar names in the classloader will match.
     * 
     * A pattern is a set of acceptable jar names. Each acceptable
     * jar name is a regex. Each regex can be separated by either a
     * "," or a "|". If you use a "|" this or's together the jar
     * name patterns. This means that ordering of the matches is
     * unimportant to you. If instead, you want to match particular
     * jar names, and you want to match them in order, you should
     * separate the regexs with "," instead. 
     * 
     * Eg "aaa-.*\\.jar|bbb-.*\\.jar"
     * Will iterate over the jar names in the classloader and match
     * in any order.
     * 
     * Eg "aaa-*\\.jar,bbb-.*\\.jar"
     * Will iterate over the jar names in the classloader, matching
     * all those starting with "aaa-" first, then "bbb-".
     * 
     * If visitParent is true, then the pattern is applied to the
     * parent loader hierarchy. If false, it is only applied to the
     * classloader passed in.
     * 
     * @param pattern
     * @param loader
     * @param isNullInclusive
     * @param visitParent
     * @throws Exception
     */
    public void scan (Pattern pattern, ClassLoader loader, boolean isNullInclusive, boolean visitParent)
    throws Exception
    {
        String[] patterns = (pattern==null?null:pattern.pattern().split(","));

        List<Pattern> subPatterns = new ArrayList<Pattern>();
        for (int i=0; patterns!=null && i<patterns.length;i++)
            subPatterns.add(Pattern.compile(patterns[i]));
        if (subPatterns.isEmpty())
            subPatterns.add(pattern);
        
        
        while (loader!=null)
        {
            if (loader instanceof URLClassLoader)
            {
                URL[] urls = ((URLClassLoader)loader).getURLs();

                if (urls!=null)
                {
                	Log.debug("URLs in URLClassLoader");
					for (URL url : urls)
					{
						Log.debug("url = {}", url);
					}
                    if (subPatterns.isEmpty())
                    {
                        processJars(null, urls, isNullInclusive);
                    }
                    else
                    {
                        //for each subpattern, iterate over all the urls, processing those that match
                        for (Pattern p : subPatterns)
                        {
                           processJars(p, urls, isNullInclusive);
                        }
                    }
                }
            }     
            if (visitParent)
                loader=loader.getParent();
            else
                loader = null;
        }  
    }
    
    
    
    public void processJars (Pattern pattern, URL[] urls, boolean isNullInclusive)
    throws Exception
    {
        for (int i=0; i<urls.length;i++)
        {
        	File f = new File(urls[i].getPath()).getAbsoluteFile();
            // This happens sometimes, not sure why - trygve
            if(f.getParentFile() == null)
            {
                Log.debug("Skipping root directory {}", f);
            }

            if(f.isDirectory())
            {
                processDirectory(f);
            }
            else if (urls[i].toString().toLowerCase().endsWith(".jar"))
            {
                String jar = urls[i].toString();
                int slash=jar.lastIndexOf('/');
                jar=jar.substring(slash+1);
                
                if ((pattern == null && isNullInclusive)
                    ||
                    (pattern!=null && pattern.matcher(jar).matches()))
                {
                    processJar(urls[i]);
                }
            }
        }
    }
    
	protected void processDirectory(File dir) throws Exception
	{
		dir = dir.getCanonicalFile();
		Log.debug("Search of {}", dir);

		processDirectory(dir, dir, new HashSet<File>());
	}

	protected void processDirectory(File rootDir, File dir, Set<File> seenSet) throws Exception
	{
		if (!seenSet.add(dir))
		{
			Log.debug("processDirectory skipping: {}", dir.getAbsolutePath());
			return;
		}

		String absolutePath = dir.getAbsolutePath();
		Log.debug("processDirectory {}", absolutePath);

		File[] files = dir.listFiles();
		// The file list can be null on IO errors which normally would imply
		// that one should throw an IOException, but
		// it seems to happen when access is denied. - trygve
		if (files == null)
		{
			Log.warn("Unable to read directory {}", absolutePath);
			return;
		}
		Arrays.sort(files);
		for (int i = 0; files != null && i < files.length; i++)
		{
			File file = files[i].getCanonicalFile();
			try
			{
				if (file.isDirectory())
					processDirectory(rootDir, file, seenSet);
				String name = file.getName();
				if (name.endsWith(".class"))
				{
					processFile(rootDir, file);
				}
			}
			catch (Exception ex)
			{
				Log.warn(Log.EXCEPTION, ex);
			}
		}
	}
    
    public void processJar (URL url)
    throws Exception
    {
        Log.debug("Search of {}",url);
        
        InputStream in = Resource.newResource(url).getInputStream();
        if (in==null)
            return;

        JarInputStream jar_in = new JarInputStream(in);
        try
        { 
            JarEntry entry = jar_in.getNextJarEntry();
            while (entry!=null)
            {
                processEntry(url, entry);
                entry = jar_in.getNextJarEntry();
            }
        }
        finally
        {
            jar_in.close();
        }   
    }
}
