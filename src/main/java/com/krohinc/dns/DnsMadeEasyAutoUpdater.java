/*
 * Copyright 2011 Andrew Kroh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krohinc.dns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * Tool for performing automatic DDNS record updates to DNS Made Easy when the
 * host's public IP address changes.
 * 
 * <p>
 * By default it expects to find a <code>config.properties</code> file in the
 * current working directory. This file should contain three properties:
 * 
 * <ul>
 * <li>username=recordusername</li>
 * <li>password=recordpassword</li>
 * <li>record-id=1234567</li>
 * </ul>
 * 
 * <p>
 * After it runs for the first time it will create a
 * <code>current-ip.properties</code> file in the directory containing the last
 * known public IP. It uses this to determine if it needs to perform an update
 * in the future. You can bootstrap the application by running it with the '-d'
 * flag which causes it to generate the <code>current-ip.properties</code> file
 * but not execute an update.
 * 
 * @author Original Author: Andrew Kroh
 */
public class DnsMadeEasyAutoUpdater
{
    private static final String DEFAULT_CONFIG_FILE_NAME = "config.properties";
    
    private static final String DEFAULT_CURRENT_IP_FILE_NAME = "current-ip.properties";
    
    private static boolean echoedDate;
    
    private static Properties loadPropertyFile(String filename) throws IOException
    {
        FileInputStream is = new FileInputStream(filename);
        Properties props = new Properties();
        props.load(is);
        return props;
    }
    
    private static void saveCurrentIp(String filename, String currentIp) 
        throws FileNotFoundException, IOException 
    {
        Properties properties = new Properties();
        properties.setProperty("current-ip", currentIp);
        properties.store(new FileOutputStream(filename), null);
    }
    
    private static void echoDate()
    {
    	if (!echoedDate)
    	{
    		System.out.println("---" + new Date() + "---");
    	}
    }
    
    private static class AutoUpdaterArguments
    {
        @Parameter(names = {"-c", "--config-file"}, description = "Property file containing " +
        		"username, password, and record-id.")
        public String configFile = DEFAULT_CONFIG_FILE_NAME;
        
        @Parameter(names = {"-i", "--current-ip-file"}, description = "Property file containing " +
                "the current-ip property. It will be created if it does not exist.")
        public String currentIpFile = DEFAULT_CURRENT_IP_FILE_NAME;
        
        @Parameter(names = {"-d", "--dont-update"}, description = "No update will be performed. " +
        		"Used to create the initial current-ip.properties file without doing an initial update.")
        public boolean dontUpdate = false;
    }
    
    public static void main(String[] args)
    {
        // Get command line arguments if any:
        AutoUpdaterArguments cmdArgs = new AutoUpdaterArguments();
        JCommander jCommander = new JCommander(cmdArgs);
        jCommander.setProgramName("autoupdater");
        try
        {
            jCommander.parse(args);
        }
        catch (Exception e)
        {
            jCommander.usage();
            System.exit(1);
        }
        
        // Read the properties file containing config data:
        Properties configProperties = null;
        try
        {
            configProperties = loadPropertyFile(cmdArgs.configFile);
        } catch (IOException e)
        {
            System.out.println("Error reading <" + cmdArgs.configFile + ">.");
            jCommander.usage();
            System.exit(1);
        }
        
        // Verify the required properties were set:
        String username = configProperties.getProperty("username", null);
        String password = configProperties.getProperty("password", null);
        String recordId = configProperties.getProperty("record-id", null);
        if (username == null || password == null || recordId == null)
        {
            System.out.println("Error: " + cmdArgs.configFile + " file must define values " +
            		"for username, password, and record-id.");
            System.exit(1);
        }
        
        // Check for a stored current IP:
        File currIpFile = new File(cmdArgs.currentIpFile);
        String currentIp = null;
        if (currIpFile.exists())
        {
            Properties currIpProperties = null;
            try
            {
                currIpProperties = loadPropertyFile(cmdArgs.currentIpFile);
                currentIp = currIpProperties.getProperty("current-ip", null);
            } 
            catch (IOException e)
            {
                System.out.println("Error reading <" + cmdArgs.currentIpFile + ">.");
                jCommander.usage();
                System.exit(1);
            }
        }

        // Retrieve the public IP address:
        String publicIp = null;
        try
        {
            publicIp = DnsMadeEasyManualUpdater.getPublicIp();
        } catch (IOException e)
        {
        	echoDate();
            System.out.println("Error retrieving public IP.");
            e.printStackTrace();
            System.exit(1);
        }
        
        // Update the DDNS record with the public IP if it has changed:
        if (currentIp == null || !currentIp.trim().equals(publicIp))
        {
            String returnCode = null;
            if (cmdArgs.dontUpdate)
            {
                System.out.println("Skipping DDNS record update as requested (-d, --dont-update)");
            }
            else
            {
            	echoDate();
                System.out.println("Previous IP: <" + currentIp + ">");
                System.out.println("New IP: <" + publicIp + ">");
                System.out.println("Updating IP...");
                try
                {
                    returnCode = DnsMadeEasyManualUpdater.updateDdnsIp(username, password, recordId, publicIp);
                    System.out.println(returnCode);
                } catch (IOException e)
                {
                    System.out.println("Error while updating IP.");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            
            if (returnCode == null 
                    || returnCode.contains("success")
                    || returnCode.contains("error-record-ip-same"))
            {
                // Save the public IP address to file now that the 
                // record has been successfully updated:
                try
                {
                    saveCurrentIp(cmdArgs.currentIpFile, publicIp);
                } 
                catch (IOException e)
                {
                    System.out.println("Error saving current IP to " + cmdArgs.currentIpFile + ".");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            
            if (returnCode != null && returnCode.contains("error"))
            {
                // Error updating IP.
                System.exit(1);
            }
            else
            {
                // IP updated successfully.
                System.exit(0);
            }
        }
        else
        {
            // No change in IP.
            System.exit(0);
        }
    }
}
